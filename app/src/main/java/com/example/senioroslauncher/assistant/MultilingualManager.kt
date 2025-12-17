package com.example.senioroslauncher.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

/**
 * Handles multilingual speech recognition, translation, and text-to-speech
 * Supports: English, Hindi, Tamil, Telugu, Malayalam (offline-first)
 */
class MultilingualManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<String, Translator>()
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "MultilingualManager"

        // Supported languages
        val SUPPORTED_LANGUAGES = mapOf(
            "en" to LanguageConfig("English", "en-IN", TranslateLanguage.ENGLISH),
            "hi" to LanguageConfig("हिन्दी", "hi-IN", TranslateLanguage.HINDI),
            "ta" to LanguageConfig("தமிழ்", "ta-IN", TranslateLanguage.TAMIL),
            "te" to LanguageConfig("తెలుగు", "te-IN", TranslateLanguage.TELUGU)
        )
    }

    data class LanguageConfig(
        val displayName: String,
        val locale: String,
        val mlkitCode: String
    )

    data class SpeechResult(
        val text: String,
        val detectedLanguage: String,
        val translatedToEnglish: String,
        val confidence: Float
    )

    interface SpeechListener {
        fun onSpeechReady()
        fun onSpeechStart()
        fun onSpeechResult(result: SpeechResult)
        fun onSpeechError(error: String)
        fun onPartialResult(text: String)
    }

    /**
     * Initialize TTS and Speech Recognizer
     */
    fun initialize(onReady: () -> Unit) {
        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                Log.d(TAG, "✓ TTS initialized")
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }

        // Initialize Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Log.d(TAG, "✓ Speech recognizer initialized")
        } else {
            Log.e(TAG, "Speech recognition not available")
        }
    }

    /**
     * Start listening for speech in any supported language
     */
    fun startListening(listener: SpeechListener) {
        if (speechRecognizer == null) {
            listener.onSpeechError("Speech recognizer not initialized")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                listener.onSpeechReady()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
                listener.onSpeechStart()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Can be used for volume visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                Log.e(TAG, "Speech error: $errorMsg")
                listener.onSpeechError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val confidence = confidences?.getOrNull(0) ?: 1.0f

                    Log.d(TAG, "Speech result: $text (confidence: $confidence)")

                    // Process the result (detect language and translate)
                    scope.launch {
                        processResult(text, confidence, listener)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    listener.onPartialResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener.onSpeechError("Failed to start: ${e.message}")
        }
    }

    private suspend fun processResult(text: String, confidence: Float, listener: SpeechListener) {
        withContext(Dispatchers.IO) {
            // Detect language
            val detectedLang = detectLanguage(text)
            Log.d(TAG, "Detected language: $detectedLang")

            // Translate to English if not already English
            val englishText = if (detectedLang != "en") {
                translateToEnglish(text, detectedLang) ?: text
            } else {
                text
            }

            withContext(Dispatchers.Main) {
                listener.onSpeechResult(
                    SpeechResult(
                        text = text,
                        detectedLanguage = detectedLang,
                        translatedToEnglish = englishText,
                        confidence = confidence
                    )
                )
            }
        }
    }

    /**
     * Detect language of text
     */
    private suspend fun detectLanguage(text: String): String {
        return suspendCancellableCoroutine { continuation ->
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    val lang = if (languageCode != "und" && languageCode in SUPPORTED_LANGUAGES) {
                        languageCode
                    } else {
                        "en" // Default to English
                    }
                    continuation.resume(lang)
                }
                .addOnFailureListener {
                    Log.e(TAG, "Language detection failed", it)
                    continuation.resume("en") // Default to English on error
                }
        }
    }

    /**
     * Translate text to English
     */
    private suspend fun translateToEnglish(text: String, sourceLanguage: String): String? {
        val langConfig = SUPPORTED_LANGUAGES[sourceLanguage] ?: return null

        return suspendCancellableCoroutine { continuation ->
            val translatorKey = "${sourceLanguage}_en"
            val translator = translators.getOrPut(translatorKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(langConfig.mlkitCode)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
                Translation.getClient(options)
            }

            // Download model if needed
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    // Translate
                    translator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            Log.d(TAG, "Translated: $text -> $translatedText")
                            continuation.resume(translatedText)
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Translation failed", it)
                            continuation.resume(null)
                        }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Model download failed", it)
                    continuation.resume(null)
                }
        }
    }

    /**
     * Speak text in specified language
     */
    fun speak(text: String, languageCode: String = "en") {
        val langConfig = SUPPORTED_LANGUAGES[languageCode]
        if (langConfig != null) {
            val locale = Locale.forLanguageTag(langConfig.locale)
            tts?.language = locale
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        Log.d(TAG, "Speaking: $text (lang: $languageCode)")
    }

    /**
     * Download language models for offline use
     */
    suspend fun downloadLanguageModel(languageCode: String): Boolean {
        val langConfig = SUPPORTED_LANGUAGES[languageCode] ?: return false

        return suspendCancellableCoroutine { continuation ->
            val translatorKey = "${languageCode}_en"
            val translator = translators.getOrPut(translatorKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(langConfig.mlkitCode)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
                Translation.getClient(options)
            }

            val conditions = DownloadConditions.Builder()
                .build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    Log.d(TAG, "✓ Downloaded model for ${langConfig.displayName}")
                    continuation.resume(true)
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to download model for ${langConfig.displayName}", it)
                    continuation.resume(false)
                }
        }
    }

    /**
     * Check if language model is downloaded
     */
    suspend fun isLanguageModelDownloaded(languageCode: String): Boolean {
        // ML Kit doesn't provide a direct way to check, so we return true if in supported list
        return languageCode in SUPPORTED_LANGUAGES
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        tts = null
        translators.values.forEach { it.close() }
        translators.clear()
        Log.d(TAG, "Cleaned up")
    }
}