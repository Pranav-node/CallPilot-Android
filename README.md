# CallPilot Assistant Preview (Android/Kotlin)

This project upgrades the earlier CallPilot calling MVP with a system-assistant integration and on-device wake-word detection.

## What this preview includes

- Android `VoiceInteractionService` and `VoiceInteractionSessionService` registration.
- A setup button that asks Android to make CallPilot the default digital assistant.
- On-device wake-word detection through the open-source `openwakeword-android` library.
- Existing contact lookup, contact aliases, SIM-call flow, and WhatsApp chat opening.
- After wake-word detection, the assistant opens the command screen and starts Android speech recognition.
- Java and Kotlin JVM targets explicitly aligned to 17.

## Wake phrase limitation

The bundled library model detects **“Hey Jarvis”**, not the exact phrase “Hello Jarvis”. This is intentionally stated in the app UI. The next model task is to obtain/train a suitable custom **“Hello Jarvis”** ONNX model and test it against false activations before changing the bundled model. Do not treat the exact phrase as implemented yet.

## Licensing note

The Android library code is Apache-2.0. The pretrained openWakeWord models shipped by the library are licensed CC BY-NC-SA 4.0. That model licensing is non-commercial and share-alike; review the upstream terms before distributing or using this version commercially. See https://github.com/msnilsen/openwakeword-android.

## Build on Windows

1. Install Android Studio and Android SDK Platform 35. Open SDK Manager and ensure **Android SDK Build-Tools** and **Platform-Tools** are installed.
2. Extract this ZIP to a normal writable folder, for example `C:\Dev\CallPilot` (avoid OneDrive-synced folders for the first build).
3. Open Android Studio's **SDK Manager** and install platform 35. Then open the project root containing `settings.gradle.kts` and allow dependency sync if Studio offers it.
4. The ZIP includes `build-apk.ps1`, which downloads Gradle 8.10.2 into the user's local AppData cache if needed, then builds the APK. Open PowerShell in the project root and run:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\build-apk.ps1
   ```

5. Internet is required on the first build so Gradle can download the Android Gradle Plugin and the wake-word dependency from JitPack/Maven.
6. Connect the Moto G54 with Developer options and USB debugging enabled. Android Studio can install the app, or install `app\build\outputs\apk\debug\app-debug.apk` manually.
7. Launch CallPilot and grant microphone, contacts and phone permissions.
8. Tap **Set CallPilot as default assistant** and approve the Android system prompt.
9. Test the included wake phrase: **“Hey Jarvis”**. After the wake, speak a contact/call command.

The archive deliberately omits the binary Gradle wrapper JAR; `build-apk.ps1` avoids needing it for a local Windows build.

## Important Android limitations

- This is a development preview, not a guarantee of uninterrupted listening. Android/OEM battery policy, microphone privacy settings, force-stop, reboots, calls, and other apps holding the microphone can affect detection.
- Software wake-word detection consumes more battery than a dedicated DSP/hardware hotword path.
- The wake-word library/model is relatively new. It needs real-device tests for detection accuracy, false activations, screen-off behavior, and battery use.
- Android speech recognition availability, supported languages, and offline behavior depend on the selected recognition provider.
- SIM calling requires permission and a usable SIM. If direct calling is unavailable, the app falls back to the dialer.
- Android does not provide a dependable public intent for silently placing a WhatsApp voice call to an arbitrary contact. This version opens the WhatsApp chat; tap the call icon there.
- The current contact resolver deliberately avoids auto-selecting ties, but more testing is required for duplicate names, Marathi/Hindi suffixes, and aliases.

## Troubleshooting

- If wake detection does not start, confirm microphone permission is granted, CallPilot is selected as the Digital assistant app, and close any app currently using the microphone.
- For logs, use Android Studio **Logcat** and filter `CallPilotWakeWord` or `CallPilotSession`.
- To stop wake listening, change the default Digital assistant app back to Google or another assistant, or force-stop CallPilot from Android Settings.
