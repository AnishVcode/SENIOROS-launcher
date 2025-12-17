package com.example.senioroslauncher.ui.assistant

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.senioroslauncher.assistant.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Assistant States
 */
sealed class AssistantState {
    object Idle : AssistantState()
    data class Listening(val partialText: String? = null) : AssistantState()
    object Processing : AssistantState()
    data class Speaking(val message: String) : AssistantState()
    data class ConfirmationRequired(
        val message: String,
        val intent: String,
        val entities: EntityExtractor.ExtractedEntities
    ) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val intentClassifier = IntentClassifier(application)
    private val multilingualManager = MultilingualManager(application)
    private val entityExtractor = EntityExtractor()
    private val actionExecutor = ActionExecutor(application)

    private var pendingAction: PendingAction? = null
    private var lastMessage: String = ""

    companion object {
        private const val TAG = "VoiceAssistantVM"
    }

    data class PendingAction(
        val intent: String,
        val entities: EntityExtractor.ExtractedEntities
    )

    init {
        initializeComponents()
    }

    private fun initializeComponents() {
        viewModelScope.launch {
            // Initialize Intent Classifier
            val classifierResult = intentClassifier.initialize()
            if (classifierResult.isFailure) {
                Log.e(TAG, "Failed to initialize classifier", classifierResult.exceptionOrNull())
                _state.value = AssistantState.Error("Failed to initialize voice assistant")
                return@launch
            }
            Log.d(TAG, "✓ Intent classifier initialized")

            // Initialize Multilingual Manager
            multilingualManager.initialize {
                Log.d(TAG, "✓ Multilingual manager initialized")
            }
        }
    }

    /**
     * Start listening for voice input
     */
    fun startListening() {
        _state.value = AssistantState.Listening()

        multilingualManager.startListening(object : MultilingualManager.SpeechListener {
            override fun onSpeechReady() {
                Log.d(TAG, "Speech ready")
            }

            override fun onSpeechStart() {
                Log.d(TAG, "Speech started")
                _state.value = AssistantState.Listening()
            }

            override fun onSpeechResult(result: MultilingualManager.SpeechResult) {
                Log.d(TAG, "Speech result: ${result.translatedToEnglish}")
                processCommand(result.translatedToEnglish, result.detectedLanguage)
            }

            override fun onSpeechError(error: String) {
                Log.e(TAG, "Speech error: $error")
                _state.value = AssistantState.Error(error)

                // Auto-return to idle after 3 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _state.value = AssistantState.Idle
                }
            }

            override fun onPartialResult(text: String) {
                _state.value = AssistantState.Listening(text)
            }
        })
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        multilingualManager.stopListening()
        _state.value = AssistantState.Idle
    }

    /**
     * Process voice command
     */
    private fun processCommand(text: String, detectedLanguage: String) {
        viewModelScope.launch {
            _state.value = AssistantState.Processing

            try {
                // Classify intent
                val intentResult = intentClassifier.classify(text)

                Log.d(TAG, "Intent: ${intentResult.intent}, Confidence: ${intentResult.confidence}")

                // Check confidence threshold
                if (!intentResult.isAboveThreshold) {
                    val message = "I'm not sure I understood that. Could you say it again?"
                    speak(message, detectedLanguage)
                    return@launch
                }

                // Extract entities
                val entities = entityExtractor.extract(text, intentResult.intent)
                Log.d(TAG, "Entities: $entities")

                // Check if critical intent (requires confirmation)
                if (intentClassifier.isCriticalIntent(intentResult.intent)) {
                    _state.value = AssistantState.ConfirmationRequired(
                        message = "This action requires confirmation. Would you like to proceed?",
                        intent = intentResult.intent,
                        entities = entities
                    )
                    pendingAction = PendingAction(intentResult.intent, entities)
                    return@launch
                }

                // Execute action
                executeAction(intentResult.intent, entities, detectedLanguage)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                _state.value = AssistantState.Error("Sorry, something went wrong")

                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _state.value = AssistantState.Idle
                }
            }
        }
    }

    /**
     * Execute action and provide feedback
     */
    private fun executeAction(
        intent: String,
        entities: EntityExtractor.ExtractedEntities,
        language: String
    ) {
        actionExecutor.execute(intent, entities) { result ->
            if (result.requiresPermission != null) {
                // Handle permission request
                val message = "I need permission to do that. Please grant the required permission."
                speak(message, language)
            } else if (result.requiresConfirmation) {
                // Show confirmation
                _state.value = AssistantState.ConfirmationRequired(
                    message = result.message,
                    intent = intent,
                    entities = entities
                )
                pendingAction = PendingAction(intent, entities)
            } else if (result.success) {
                // Success - speak response
                speak(result.message, language)
            } else {
                // Error
                val message = result.message.ifEmpty { "Sorry, I couldn't do that" }
                speak(message, language)
            }
        }
    }

    /**
     * Speak message and return to idle
     */
    private fun speak(message: String, language: String) {
        lastMessage = message
        _state.value = AssistantState.Speaking(message)

        // Speak with TTS
        multilingualManager.speak(message, language)

        // Return to idle after speaking (estimate time based on message length)
        val speakDuration = (message.length * 50L).coerceAtLeast(2000L)
        viewModelScope.launch {
            kotlinx.coroutines.delay(speakDuration)
            _state.value = AssistantState.Idle
        }
    }

    /**
     * Confirm pending action
     */
    fun confirmAction() {
        val action = pendingAction ?: return
        _state.value = AssistantState.Processing

        executeAction(action.intent, action.entities, "en")
        pendingAction = null
    }

    /**
     * Cancel pending action
     */
    fun cancelAction() {
        pendingAction = null
        speak("Action cancelled", "en")
    }

    /**
     * Repeat last message
     */
    fun repeatLast() {
        if (lastMessage.isNotEmpty()) {
            _state.value = AssistantState.Speaking(lastMessage)
            multilingualManager.speak(lastMessage, "en")

            viewModelScope.launch {
                kotlinx.coroutines.delay(lastMessage.length * 50L)
                _state.value = AssistantState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        intentClassifier.close()
        multilingualManager.cleanup()
    }
}