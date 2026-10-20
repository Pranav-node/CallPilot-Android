package com.example.callpilot

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var commandInput: EditText
    private lateinit var statusText: TextView
    private lateinit var aliasInput: EditText
    private lateinit var aliasTargetInput: EditText
    private var speechRecognizer: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("callpilot", MODE_PRIVATE) }
    private val requestCode = 42
    private val ASSISTANT_ROLE_REQUEST = 43
    private var pendingAction: (() -> Unit)? = null
    private var externalActionLaunched = false
    private var resumeDetectorAfterExternalAction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNeededPermissions()
        handleAssistantIntent(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "CallPilot"
            textSize = 30f
            setTextColor(0xFF172033.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "Voice-first calling · English, Hindi & Marathi"
            textSize = 14f
            setPadding(0, 4, 0, 20)
        }
        commandInput = EditText(this).apply {
            hint = "Try: Call Rahul / Rahul ko call karo / राहुलला कॉल कर"
            minLines = 2
            textSize = 16f
            gravity = Gravity.TOP
        }
        val assistantButton = Button(this).apply {
            text = "Set CallPilot as default assistant"
            setOnClickListener { requestAssistantRole() }
        }
        val micButton = Button(this).apply {
            text = "Speak a command now"
            setOnClickListener { startSpeechRecognition() }
        }
        val simButton = Button(this).apply {
            text = "Call via SIM"
            setOnClickListener { handleCommand(commandInput.text.toString(), CallMode.SIM) }
        }
        val whatsAppButton = Button(this).apply {
            text = "Open WhatsApp chat"
            setOnClickListener { handleCommand(commandInput.text.toString(), CallMode.WHATSAPP) }
        }
        statusText = TextView(this).apply {
            text = "Grant permissions, then set CallPilot as the default digital assistant. The included wake-word model currently detects: Hey Jarvis."
            textSize = 14f
            setTextColor(0xFF39445A.toInt())
            setPadding(0, 12, 0, 20)
        }
        val aliasTitle = TextView(this).apply {
            text = "Contact alias memory"
            textSize = 20f
            setTextColor(0xFF172033.toInt())
            setPadding(0, 12, 0, 8)
        }
        aliasInput = EditText(this).apply { hint = "Alias (e.g. Baba)" }
        aliasTargetInput = EditText(this).apply { hint = "Contact name (e.g. Dad)" }
        val saveAlias = Button(this).apply {
            text = "Save alias"
            setOnClickListener { saveAlias() }
        }
        val examples = TextView(this).apply {
            text = "Wake phrase (current model): Hey Jarvis. Say the wake phrase, then speak a command.\n\nExamples\n• Call Dad\n• Call Rahul on WhatsApp\n• Baba la call kar\n• राहुलला कॉल कर\n\nImportant: the included model detects Hey Jarvis, not the exact phrase Hello Jarvis. A custom Hello Jarvis model is a separate next step.\n\nWhatsApp note: Android does not provide a dependable public intent for silently starting a WhatsApp voice call to an arbitrary contact. This version opens the chat; tap the call icon there."
            textSize = 14f
            setTextColor(0xFF39445A.toInt())
            setPadding(0, 14, 0, 12)
        }
        content.addView(title)
        content.addView(subtitle)
        content.addView(commandInput, matchWidth())
        content.addView(assistantButton, matchWidth())
        content.addView(micButton, matchWidth())
        content.addView(simButton, matchWidth())
        content.addView(whatsAppButton, matchWidth())
        content.addView(statusText, matchWidth())
        content.addView(aliasTitle)
        content.addView(aliasInput, matchWidth())
        content.addView(aliasTargetInput, matchWidth())
        content.addView(saveAlias, matchWidth())
        content.addView(examples, matchWidth())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun requestNeededPermissions() {
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.READ_CONTACTS
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.CALL_PHONE
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), requestCode)
    }

    private fun requestAssistantRole() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Grant microphone permission first, then set CallPilot as the default assistant."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                statusText.text = "This Android build does not expose the assistant role. Try Settings → Apps → Default apps → Digital assistant app."
                return
            }
            if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                statusText.text = "CallPilot is already the default assistant. Try saying: Hey Jarvis."
                return
            }
            @Suppress("DEPRECATION")
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), ASSISTANT_ROLE_REQUEST)
        } else {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Exception) {
                statusText.text = "Open Android Settings and select CallPilot under Digital assistant app."
            }
        }
    }

    private fun handleAssistantIntent(incoming: Intent?) {
        if (incoming?.getBooleanExtra(AssistantActions.EXTRA_START_SPEECH, false) == true) {
            incoming.removeExtra(AssistantActions.EXTRA_START_SPEECH)
            window.decorView.postDelayed({ startSpeechRecognition() }, 500L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistantIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (resumeDetectorAfterExternalAction) {
            resumeDetectorAfterExternalAction = false
            sendDetectorAction(AssistantActions.ACTION_RESUME_DETECTION)
        }
    }

    private fun sendDetectorAction(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    private fun startSpeechRecognition() {
        sendDetectorAction(AssistantActions.ACTION_PAUSE_DETECTION)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Speech recognition is unavailable. Type a command instead."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a contact name and call command")
        }
        statusText.text = "Listening… If mixed-language recognition is poor, try the keyboard or change your phone's speech language settings."
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "Listening…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "Processing speech…" }
            override fun onError(error: Int) { statusText.text = "Speech recognition failed (code $error). Try again or type the command."; sendDetectorAction(AssistantActions.ACTION_RESUME_DETECTION) }
            override fun onResults(results: Bundle?) {
                val options = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val phrase = options.firstOrNull().orEmpty()
                commandInput.setText(phrase)
                if (phrase.isNotBlank()) {
                    externalActionLaunched = false
                    handleCommand(phrase, inferMode(phrase))
                    if (externalActionLaunched) {
                        resumeDetectorAfterExternalAction = true
                    } else {
                        sendDetectorAction(AssistantActions.ACTION_RESUME_DETECTION)
                    }
                } else {
                    sendDetectorAction(AssistantActions.ACTION_RESUME_DETECTION)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!phrase.isNullOrBlank()) commandInput.setText(phrase)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private enum class CallMode { SIM, WHATSAPP }

    private fun inferMode(command: String): CallMode =
        if (normalize(command).contains("whatsapp") || normalize(command).contains("व्हाट्सएप")) CallMode.WHATSAPP else CallMode.SIM

    private fun handleCommand(raw: String, mode: CallMode) {
        val command = raw.trim()
        if (command.isBlank()) {
            statusText.text = "Enter or say a command first."
            return
        }

        val candidate = extractContactQuery(command)
        val resolved = resolveAlias(candidate)
        val matches = findContacts(resolved)

        when {
            matches.isEmpty() -> statusText.text =
                "No contact match for '$candidate'. Try the saved contact name or add an alias below."
            matches.size > 1 -> statusText.text =
                "Several contacts match '$candidate':\n" + matches.take(5).joinToString("\n") {
                    "${it.name} (…${normalizeNumber(it.phone).takeLast(4)})"
                } + "\nType a more specific name or save a unique alias. No call was placed."
            else -> {
                val contact = matches.first()
                if (mode == CallMode.SIM) placeSimCall(contact.phone, contact.name)
                else openWhatsAppChat(contact.phone, contact.name)
            }
        }
    }

    private data class ContactMatch(val name: String, val phone: String)

    /** Remove common command words while preserving all words that may identify a person. */
    private fun extractContactQuery(command: String): String {
        var q = normalize(command)
        // Remove command verbs/phrases, not name tokens such as "Dada", "Baba", or "Tai".
        val phrases = listOf(
            "whatsapp var call kara", "whatsapp la call kara", "call via sim",
            "open whatsapp chat", "whatsapp call", "call me", "please call",
            "call karo", "call kar", "phone karo", "phone kar", "dial karo",
            "कॉल करा", "कॉल कर", "फोन करा", "फोन कर", "लावा", "लाव",
            "कॉल करो", "कॉल करना", "लगाओ", "लगा दो", "व्हाट्सएप", "व्हॉट्सअॅप"
        )
        phrases.forEach { phrase -> q = q.replace(normalize(phrase), " ") }
        q = q.replace(Regex("\\b(call|phone|dial|ring|please|the|on|via|using|whatsapp|number|ko|ki|ka|karo|kar|karde|la|na|lav|lawa|karaycha|karaychi)\\b"), " ")
        q = q.replace(Regex("\\s+"), " ").trim()
        // Marathi/Hindi suffixes are removed only from the end, after command words.
        q = q.replace(Regex("(ला|ना|को)$"), "").trim()
        return q
    }

    private fun findContacts(query: String): List<ContactMatch> {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Contacts permission is required. Grant it in Android Settings and retry."
            return emptyList()
        }
        val q = normalize(query).trim()
        if (q.isBlank()) return emptyList()

        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex) ?: continue
                results += ContactMatch(displayName.trim(), number.trim())
            }
        }

        // Score names rather than returning every substring match. Keep ties at the best score
        // so a short name like "Aditya" never silently selects one of several Adityas.
        val unique = results.distinctBy { normalize(it.name) + "|" + normalizeNumber(it.phone) }
        val qTokens = q.split(" ").filter { it.isNotBlank() }.toSet()
        val scored = unique.mapNotNull { contact ->
            val n = normalize(contact.name)
            val nTokens = n.split(" ").filter { it.isNotBlank() }.toSet()
            val score = when {
                n == q -> 1000
                n.startsWith("$q ") -> 850
                nTokens.containsAll(qTokens) -> 750 + qTokens.size
                n.contains(q) -> 600
                qTokens.size > 1 && nTokens.intersect(qTokens).size == qTokens.size -> 700
                else -> 0
            }
            if (score > 0) contact to score else null
        }
        if (scored.isEmpty()) return emptyList()
        val best = scored.maxOf { it.second }
        return scored.filter { it.second == best }
            .map { it.first }
            .sortedWith(compareBy<ContactMatch> { normalize(it.name) }.thenBy { normalizeNumber(it.phone) })
            .take(8)
    }

    private fun placeSimCall(number: String, name: String) {
        val clean = number.trim()
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(clean)}")))
                externalActionLaunched = true
                statusText.text = "Calling $name…"
            } catch (e: Exception) {
                statusText.text = "Couldn't start direct call. Opening the dialer instead."
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")))
                externalActionLaunched = true
            }
        } else {
            statusText.text = "Call permission not granted; opening dialer for $name."
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")))
            externalActionLaunched = true
        }
    }

    private fun openWhatsAppChat(number: String, name: String) {
        val digits = normalizeNumber(number)
        if (digits.isBlank()) {
            statusText.text = "No usable phone number found for $name."
            return
        }
        val international = when {
            digits.startsWith("+") -> digits.drop(1)
            digits.length == 10 -> "91$digits" // India default for local 10-digit mobile numbers
            else -> digits
        }
        val uri = Uri.parse("https://wa.me/$international")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
            externalActionLaunched = true
            statusText.text = "Opened WhatsApp chat for $name. Tap the voice-call icon to call."
        } catch (e: Exception) {
            statusText.text = "Couldn't open WhatsApp. Check that it is installed and the contact number includes the country code."
        }
    }

    private fun saveAlias() {
        val alias = aliasInput.text.toString().trim()
        val target = aliasTargetInput.text.toString().trim()
        if (alias.isBlank() || target.isBlank()) {
            statusText.text = "Enter both an alias and a saved contact name."
            return
        }
        prefs.edit().putString("alias:${normalize(alias)}", target).apply()
        statusText.text = "Saved alias '$alias' → '$target'."
        aliasInput.text.clear()
        aliasTargetInput.text.clear()
    }

    private fun resolveAlias(name: String): String = prefs.getString("alias:${normalize(name)}", name) ?: name

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun normalizeNumber(number: String): String = number.filter { it.isDigit() || it == '+' }

    @Deprecated("Deprecated in Android APIs; retained for role result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ASSISTANT_ROLE_REQUEST) {
            val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(RoleManager::class.java) else null
            statusText.text = if (roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                "CallPilot is now the default assistant. Say: Hey Jarvis."
            } else {
                "CallPilot was not selected as the default assistant. You can retry from this button."
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
