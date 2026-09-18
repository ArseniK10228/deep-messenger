package online.deepdesign.deep.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.R
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: AuthViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val success by vm.authSuccess.collectAsState()

    LaunchedEffect(success) {
        if (success) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.deep_logo),
            contentDescription = "Deep",
            modifier = Modifier
                .size(88.dp)
                .deepAppear()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            modifier = Modifier.deepAppear(delayMillis = 80),
            text = if (state.step == AuthStep.Email) {
                "Войди по email — пришлём код"
            } else {
                "Введи код из письма"
            },
            color = DeepMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        if (state.step == AuthStep.Email) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "Проверь папку «Спам», если письма нет",
                color = DeepMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(32.dp))

        if (state.step == AuthStep.Email) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .deepAppear(delayMillis = 120),
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors()
            )
        } else {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .deepAppear(),
                value = state.code,
                onValueChange = vm::onCodeChange,
                label = { Text("Код из письма") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors()
            )
            if (state.countdown > 0) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "Повтор через ${state.countdown} с",
                    color = DeepMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            } else {
                TextButton(onClick = vm::sendCode) {
                    Text("Отправить снова", color = DeepAccent)
                }
            }
            TextButton(onClick = vm::backToEmail) {
                Text("Изменить email", color = DeepMuted)
            }
        }

        state.error?.let {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = it,
                color = DeepError,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(24.dp))

        if (state.step == AuthStep.Email) {
            Button(
                onClick = vm::sendCode,
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .deepAppear(delayMillis = 180),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepAccent)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.height(22.dp)
                    )
                } else {
                    Text("Получить код")
                }
            }
        } else if (state.loading) {
            CircularProgressIndicator(color = DeepAccent)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DeepAccent,
    unfocusedBorderColor = DeepSurfaceHigh,
    focusedContainerColor = DeepSurfaceHigh,
    unfocusedContainerColor = DeepSurfaceHigh,
    cursorColor = DeepAccent
)
