package com.example.callpilot

/** Internal app-only actions used to pause/resume the local wake-word detector. */
object AssistantActions {
    const val ACTION_PAUSE_DETECTION = "com.example.callpilot.action.PAUSE_DETECTION"
    const val ACTION_RESUME_DETECTION = "com.example.callpilot.action.RESUME_DETECTION"
    const val EXTRA_START_SPEECH = "com.example.callpilot.extra.START_SPEECH"
}
