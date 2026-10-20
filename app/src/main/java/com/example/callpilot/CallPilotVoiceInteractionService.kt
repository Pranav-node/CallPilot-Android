package com.example.callpilot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.service.voice.VoiceInteractionService
import com.openwakeword.OpenWakeWord

/**
 * Runs only when the user selects CallPilot as the system digital assistant.
 * The detector runs locally; audio is not uploaded by this wake-word component.
 */
class CallPilotVoiceInteractionService : VoiceInteractionService() {
    private var detector: OpenWakeWord? = null
    @Volatile private var detecting = false
    private var receiverRegistered = false

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AssistantActions.ACTION_PAUSE_DETECTION -> stopDetection()
                AssistantActions.ACTION_RESUME_DETECTION -> startDetection()
            }
        }
    }

    override fun onReady() {
        super.onReady()
        registerControlReceiver()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startDetection()
        } else {
            Log.w(TAG, "Microphone permission has not been granted. Open CallPilot and grant it.")
        }
    }

    private fun registerControlReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AssistantActions.ACTION_PAUSE_DETECTION)
            addAction(AssistantActions.ACTION_RESUME_DETECTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(controlReceiver, filter)
        }
        receiverRegistered = true
    }

    @Synchronized
    private fun startDetection() {
        if (detecting) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start wake-word detection: RECORD_AUDIO denied.")
            return
        }
        try {
            if (detector == null) {
                detector = OpenWakeWord.Builder(applicationContext)
                    .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                    .setThreshold(0.55f)
                    .setDebounceMs(2500L)
                    .build()
            }
            detecting = true
            detector?.start { score ->
                if (!detecting) return@start
                Log.i(TAG, "Wake word detected, score=$score")
                stopDetection()
                val args = Bundle().apply { putFloat(EXTRA_WAKE_SCORE, score) }
                showSession(args, 0)
            }
            Log.i(TAG, "Wake-word detector started. Current model phrase: Hey Jarvis.")
        } catch (e: Exception) {
            detecting = false
            Log.e(TAG, "Could not start wake-word detection", e)
        }
    }

    @Synchronized
    private fun stopDetection() {
        if (!detecting) return
        detecting = false
        try {
            detector?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping detector", e)
        }
    }

    override fun onShutdown() {
        stopDetection()
        try {
            detector?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing detector", e)
        }
        detector = null
        if (receiverRegistered) {
            try { unregisterReceiver(controlReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        super.onShutdown()
    }

    companion object {
        private const val TAG = "CallPilotWakeWord"
        const val EXTRA_WAKE_SCORE = "com.example.callpilot.extra.WAKE_SCORE"
    }
}
