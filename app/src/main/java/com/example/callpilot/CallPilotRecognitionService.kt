package com.example.callpilot

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Recognition-service bridge used by Android's VoiceInteractionService framework.
 * It delegates one-shot transcription to the device's configured speech recognizer,
 * then forwards all results back through the RecognitionService callback API.
 * The visible CallPilot app independently uses SpeechRecognizer for its command UI.
 */
class CallPilotRecognitionService : RecognitionService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var delegate: SpeechRecognizer? = null
    private var activeCallback: Callback? = null

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        if (listener == null) return
        mainHandler.post {
            cancelCurrentSession()

            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                listener.error(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            // Avoid recursion if the user explicitly set CallPilot itself as the
            // system-wide speech-recognition provider instead of a separate provider.
            val selectedProvider = Settings.Secure.getString(
                contentResolver,
                "voice_recognition_service"
            )
            val ownComponent = ComponentName(this, CallPilotRecognitionService::class.java)
            if (ComponentName.unflattenFromString(selectedProvider) == ownComponent) {
                Log.e(TAG, "CallPilot cannot delegate to itself as the system speech recognizer.")
                listener.error(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create the device speech recognizer", e)
                listener.error(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            activeCallback = listener
            delegate = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    activeCallback?.readyForSpeech(params ?: Bundle())
                }

                override fun onBeginningOfSpeech() {
                    activeCallback?.beginningOfSpeech()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    activeCallback?.rmsChanged(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    activeCallback?.bufferReceived(buffer)
                }

                override fun onEndOfSpeech() {
                    activeCallback?.endOfSpeech()
                }

                override fun onError(error: Int) {
                    val callback = activeCallback
                    activeCallback = null
                    releaseDelegate()
                    callback?.error(error)
                }

                override fun onResults(results: Bundle?) {
                    val callback = activeCallback
                    activeCallback = null
                    releaseDelegate()
                    callback?.results(results ?: Bundle())
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    activeCallback?.partialResults(partialResults ?: Bundle())
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    activeCallback?.event(eventType, params)
                }
            })

            try {
                val request = intent?.let { Intent(it) }
                    ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                recognizer.startListening(request)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start the device speech recognizer", e)
                val callback = activeCallback
                activeCallback = null
                releaseDelegate()
                callback?.error(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        mainHandler.post {
            val current = delegate
            if (current == null) {
                listener?.error(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }
            try {
                current.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop speech recognition cleanly", e)
                val callback = activeCallback
                activeCallback = null
                releaseDelegate()
                callback?.error(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    override fun onCancel(listener: Callback?) {
        mainHandler.post { cancelCurrentSession() }
    }

    private fun cancelCurrentSession() {
        val recognizer = delegate
        delegate = null
        activeCallback = null
        if (recognizer != null) {
            try { recognizer.cancel() } catch (_: Exception) { }
            try { recognizer.destroy() } catch (_: Exception) { }
        }
    }

    private fun releaseDelegate() {
        val recognizer = delegate
        delegate = null
        if (recognizer != null) {
            try { recognizer.destroy() } catch (e: Exception) {
                Log.w(TAG, "Unable to release speech recognizer", e)
            }
        }
    }

    override fun onDestroy() {
        cancelCurrentSession()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallPilotRecognition"
    }
}
