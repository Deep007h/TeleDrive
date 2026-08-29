package com.teledrive.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.telegram.RecaptchaRequest
import com.teledrive.app.telegram.TdLibAuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { PHONE, CODE, PASSWORD, LOADING, AUTHENTICATED, ERROR }

data class AuthUiState(
    val authStep: AuthStep = AuthStep.PHONE,
    val showApiSetup: Boolean = false,
    val apiIdInput: String = "",
    val apiHashInput: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+91",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val passwordHint: String? = null,
    val hasRecoveryEmail: Boolean = false,
    val recaptchaRequest: RecaptchaRequest? = null
)

class AuthViewModel : ViewModel() {
    private val app = TeleDriveApplication.instance
    private val authRepository = app.authRepository
    private val preferences = app.preferences

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedId = preferences.apiId.first()
            val savedHash = preferences.apiHash.first()
            if (savedId > 0 && savedHash.isNotBlank()) {
                _uiState.update { it.copy(apiIdInput = savedId.toString(), apiHashInput = savedHash) }
                authRepository.restartClient(savedId, savedHash)
            }

            authRepository.authState.collect { tdState ->
                when (tdState) {
                    is TdLibAuthState.WaitPhoneNumber -> {
                        _uiState.update { it.copy(authStep = AuthStep.PHONE, isLoading = false) }
                    }
                    is TdLibAuthState.WaitCode -> {
                        _uiState.update { it.copy(authStep = AuthStep.CODE, isLoading = false, errorMessage = null) }
                    }
                    is TdLibAuthState.WaitPassword -> {
                        _uiState.update {
                            it.copy(
                                authStep = AuthStep.PASSWORD,
                                passwordHint = tdState.hint,
                                hasRecoveryEmail = tdState.hasRecoveryEmail,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
                    is TdLibAuthState.Ready -> {
                        _uiState.update { it.copy(authStep = AuthStep.AUTHENTICATED, isLoading = false, errorMessage = null) }
                    }
                    is TdLibAuthState.Error -> {
                        val isApiError = tdState.message.contains("API_ID", ignoreCase = true)
                        _uiState.update {
                            it.copy(
                                errorMessage = if (isApiError) "Telegram requires your personal API Keys from my.telegram.org" else tdState.message,
                                showApiSetup = isApiError || it.showApiSetup,
                                isLoading = false
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            authRepository.recaptchaRequests.collect { req ->
                _uiState.update { it.copy(recaptchaRequest = req, isLoading = false) }
            }
        }
    }

    fun toggleApiSetup() {
        _uiState.update { it.copy(showApiSetup = !it.showApiSetup) }
    }

    fun updateApiId(id: String) {
        _uiState.update { it.copy(apiIdInput = id, errorMessage = null) }
    }

    fun updateApiHash(hash: String) {
        _uiState.update { it.copy(apiHashInput = hash, errorMessage = null) }
    }

    fun updatePhoneNumber(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, errorMessage = null) }
    }

    fun updateCountryCode(code: String) {
        _uiState.update { it.copy(countryCode = code) }
    }

    fun resetToPhone() {
        _uiState.update { it.copy(authStep = AuthStep.PHONE, isLoading = false, errorMessage = null, recaptchaRequest = null) }
    }

    fun dismissRecaptcha() {
        _uiState.update { it.copy(recaptchaRequest = null, isLoading = false) }
    }

    fun submitRecaptchaToken(token: String) {
        val req = _uiState.value.recaptchaRequest ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, recaptchaRequest = null, errorMessage = null) }
            val result = authRepository.submitRecaptchaToken(req.verificationId, token)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Verification failed"
                    )
                }
            }
        }
    }

    fun submitPhoneNumber() {
        val currentState = _uiState.value
        if (currentState.phoneNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid phone number") }
            return
        }

        val parsedApiId = currentState.apiIdInput.trim().toIntOrNull()
        val parsedApiHash = currentState.apiHashInput.trim()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Save API keys and restart client if provided
            if (parsedApiId != null && parsedApiId > 0 && parsedApiHash.isNotBlank()) {
                try {
                    authRepository.restartClient(parsedApiId, parsedApiHash)
                    // Only save after successful restart
                    preferences.setApiId(parsedApiId)
                    preferences.setApiHash(parsedApiHash)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showApiSetup = true,
                            errorMessage = "Failed to initialize with provided API keys: ${e.message}"
                        )
                    }
                    return@launch
                }
            }

            val fullPhone = "${currentState.countryCode}${currentState.phoneNumber.trimStart('0')}"
            val result = authRepository.submitPhoneNumber(fullPhone)
            
            if (authRepository.authState.value is TdLibAuthState.WaitCode) {
                _uiState.update { it.copy(authStep = AuthStep.CODE, isLoading = false, errorMessage = null) }
                return@launch
            }

            if (result.isFailure) {
                val err = result.exceptionOrNull()?.message ?: "Failed to connect to Telegram"
                if (err.contains("aborted", ignoreCase = true) && authRepository.authState.value is TdLibAuthState.WaitCode) {
                    _uiState.update { it.copy(authStep = AuthStep.CODE, isLoading = false, errorMessage = null) }
                    return@launch
                }
                val isApiError = err.contains("API_ID", ignoreCase = true)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showApiSetup = isApiError || it.showApiSetup,
                        errorMessage = if (isApiError) "Telegram rejected default public keys. Please enter your API ID & Hash from my.telegram.org below." else err
                    )
                }
            } else {
                _uiState.update { it.copy(authStep = AuthStep.CODE, isLoading = false, errorMessage = null) }
            }
        }
    }

    fun submitCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.submitCode(code)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Invalid or expired code"
                    )
                }
            }
        }
    }

    fun submitPassword(password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.submitPassword(password)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Incorrect password"
                    )
                }
            }
        }
    }
}
