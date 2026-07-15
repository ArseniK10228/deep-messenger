package online.deepdesign.deep.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.EmailSendRequest
import online.deepdesign.deep.data.EmailVerifyRequest
import online.deepdesign.deep.data.readApiError
import retrofit2.HttpException

data class AuthUiState(
    val email: String = "",
    val code: String = "",
    val step: AuthStep = AuthStep.Email,
    val loading: Boolean = false,
    val error: String? = null,
    val countdown: Int = 0
)

enum class AuthStep { Email, Code }

class AuthViewModel : ViewModel() {
    private val api = DeepApp.instance.api
    private val sessionStore = DeepApp.instance.sessionStore

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var requestId: String? = null

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) verifyCode(digits)
    }

    fun sendCode() {
        val email = normalizeEmail(_state.value.email)
        if (email.isBlank() || !email.contains('@')) {
            _state.update { it.copy(error = "Введите email") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = api.emailSend(EmailSendRequest(email))
                requestId = resp.requestId
                _state.update {
                    it.copy(loading = false, step = AuthStep.Code, countdown = 60)
                }
                startCountdown()
            } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException -> e.readApiError()
                    else -> e.message ?: "Не удалось отправить код"
                }
                _state.update {
                    it.copy(loading = false, error = msg)
                }
            }
        }
    }

    private fun verifyCode(code: String) {
        val id = requestId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val response = api.emailVerify(EmailVerifyRequest(id, code))
                sessionStore.saveSession(response.token, response.user)
                DeepApp.instance.setAuthSession(response.token, response.user.id)
                _state.update { it.copy(loading = false) }
                _authSuccess.value = true
            } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException -> e.readApiError()
                    else -> e.message ?: "Неверный код"
                }
                _state.update {
                    it.copy(loading = false, error = msg)
                }
            }
        }
    }

    private val _authSuccess = MutableStateFlow(false)
    val authSuccess: StateFlow<Boolean> = _authSuccess.asStateFlow()

    private fun startCountdown() {
        viewModelScope.launch {
            var left = 60
            while (left > 0) {
                _state.update { it.copy(countdown = left) }
                kotlinx.coroutines.delay(1000)
                left--
            }
            _state.update { it.copy(countdown = 0) }
        }
    }

    fun backToEmail() {
        requestId = null
        _state.update { it.copy(step = AuthStep.Email, code = "", error = null) }
    }

    private fun normalizeEmail(raw: String): String = raw.trim().lowercase()
}
