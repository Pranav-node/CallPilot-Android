package com.example.callpilot

import android.content.Context
import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Opens the visible assistant activity through Android's voice-activity pathway. */
class CallPilotVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    private val sessionContext = context

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // A minimal session view gives visual feedback before the command UI appears.
        val content = LinearLayout(sessionContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 28, 32, 28)
            setBackgroundColor(0xFF111827.toInt())
        }
        content.addView(TextView(sessionContext).apply {
            text = "CallPilot is awake\nSay your command"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        })
        setContentView(content)

        try {
            val intent = Intent(sessionContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AssistantActions.EXTRA_START_SPEECH, true)
            }
            startVoiceActivity(intent)
        } catch (e: Exception) {
            Log.e("CallPilotSession", "Could not launch command activity", e)
        } finally {
            finish()
        }
    }
}
