package online.deepdesign.deep.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.FirebaseAuthRequest
import java.lang.ref.WeakReference

data class AuthUiState(
    val phone: String = "+7",
    val code: String = "",
    val step: AuthStep = AuthStep.Phone,
    val loading: Boolean = false,
    val error: String? = null,
    val countdown: Int = 0
)

enum class AuthStep { Phone, Code }

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val api = DeepApp.instance.api
    private val sessionStore = DeepApp.instance.sessionStore

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var activityRef: WeakReference<Activity>? = null

    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phone = value, error = null) }
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) verifyCode(digits)
    }

    fun sendCode() {
        val activity = activityRef?.get() ?: return
        val phone = normalizePhone(_state.value.phone)
        if (phone.length < 11) {
            _state.update { it.copy(error = "Введите номер телефона") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _state.update {
                    it.copy(loading = false, error = e.localizedMessage ?: "Ошибка SMS")
                }
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                resendToken = token
                _state.update {
                    it.copy(loading = false, step = AuthStep.Code, countdown = 60)
                }
                startCountdown()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val id = verificationId ?: return
        _state.update { it.copy(loading = true, error = null) }
        signInWithCredential(PhoneAuthProvider.getCredential(id, code))
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                viewModelScope.launch {
                    try {
                        val idToken = result.user?.getIdToken(true)?.await()?.token
                            ?: throw IllegalStateException("No Firebase token")
                        val response = api.authFirebase(FirebaseAuthRequest(idToken))
                        sessionStore.saveSession(response.token, response.user)
                        DeepApp.instance.setAuthSession(response.token, response.user.id)
                        _state.update { it.copy(loading = false) }
                        _authSuccess.value = true
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(loading = false, error = e.message ?: "Ошибка сервера")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                _state.update {
                    it.copy(loading = false, error = e.localizedMessage ?: "Неверный код")
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

    fun backToPhone() {
        _state.update { it.copy(step = AuthStep.Phone, code = "", error = null) }
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.startsWith("+") -> "+" + digits
            digits.startsWith("8") && digits.length == 11 -> "+7" + digits.drop(1)
            digits.startsWith("7") -> "+$digits"
            else -> "+7$digits"
        }
    }
}
