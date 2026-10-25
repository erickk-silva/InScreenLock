package com.inscreenlock.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.inscreenlock.app.data.PinAttempt
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.domain.UnlockWindow
import kotlinx.coroutines.launch
import java.time.LocalDate

class UnlockActivity : ComponentActivity() {
    private lateinit var repository: SettingsRepository
    private var deviceSecure = false
    private var confirmResult: (() -> Unit)? = null
    private val credentialResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) success()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(applicationContext)
        deviceSecure = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        setContent {
            val palette = darkColorScheme(
                primary = Color(0xFF69DDBB), background = Color(0xFF101319),
                surface = Color(0xFF1B2029), onSurface = Color(0xFFF2F5F7),
            )
            MaterialTheme(colorScheme = palette) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101319)) {
                    UnlockScreen(
                        hasPin = intent.getBooleanExtra(EXTRA_HAS_PIN, false),
                        canUseDeviceCredential = deviceSecure,
                        confirmingChange = intent.getBooleanExtra(EXTRA_CONFIRM_ONLY, false),
                        onDigit = { current, digit -> if (current.length < 8) current + digit else current },
                        onDelete = { it.dropLast(1) },
                        onPinSubmit = { pin, showError ->
                            lifecycleScope.launch {
                                when (val attempt = repository.verifyPin(pin)) {
                                    PinAttempt.Accepted -> success()
                                    PinAttempt.Rejected -> showError("PIN incorreto")
                                    is PinAttempt.Locked -> showError("Tente novamente em ${((attempt.retryAtMs - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(1)} s")
                                }
                            }
                        },
                        onDeviceCredential = ::requestDeviceCredential,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun requestDeviceCredential() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent("Confirmar identidade", "Desbloqueie o InScreenLock")
        if (intent != null) credentialResult.launch(intent)
    }

    private fun success() {
        if (!intent.getBooleanExtra(EXTRA_CONFIRM_ONLY, false)) {
            lifecycleScope.launch {
                repository.grantUnlock(UnlockWindow.expiresAt(System.currentTimeMillis()), LocalDate.now())
                setResult(RESULT_OK)
                finish()
            }
        } else {
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_CONFIRM_ONLY = "confirm_only"
        const val EXTRA_HAS_PIN = "has_pin"
        fun intent(context: android.content.Context, confirmOnly: Boolean, hasPin: Boolean) =
            Intent(context, UnlockActivity::class.java)
                .putExtra(EXTRA_CONFIRM_ONLY, confirmOnly)
                .putExtra(EXTRA_HAS_PIN, hasPin)
    }
}

@Composable
internal fun UnlockScreen(
    hasPin: Boolean,
    canUseDeviceCredential: Boolean,
    confirmingChange: Boolean,
    onDigit: (String, String) -> String,
    onDelete: (String) -> String,
    onPinSubmit: (String, (String) -> Unit) -> Unit,
    onDeviceCredential: () -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (confirmingChange) "Confirme para continuar" else "Desbloquear apps", color = Color(0xFFF2F5F7), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (confirmingChange) "Confirme para autorizar este ajuste."
            else "O acesso será liberado por até 15 minutos.",
            color = Color(0xFFA5AFBC),
        )
        if (canUseDeviceCredential) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDeviceCredential, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Usar senha, padrão ou biometria do dispositivo")
            }
        }
        if (hasPin) {
            Spacer(Modifier.height(20.dp))
            Text("PIN do InScreenLock", color = Color(0xFFA5AFBC))
            Spacer(Modifier.height(8.dp))
            Text("•".repeat(pin.length).ifEmpty { "—" }, modifier = Modifier.testTag("pin-display"), color = Color.White, fontSize = 28.sp)
            (0..8 step 3).forEach { start ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (start..start + 2).forEach { number ->
                        OutlinedButton(onClick = { pin = onDigit(pin, (number + 1).toString()); error = "" }, modifier = Modifier.weight(1f).height(56.dp).testTag("pin-digit-${number + 1}")) { Text("${number + 1}", fontSize = 20.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pin = onDelete(pin); error = "" }, modifier = Modifier.weight(1f).height(56.dp).testTag("pin-delete")) { Text("Apagar") }
                OutlinedButton(onClick = { pin = onDigit(pin, "0"); error = "" }, modifier = Modifier.weight(1f).height(56.dp).testTag("pin-digit-0")) { Text("0", fontSize = 20.sp) }
                Button(onClick = { onPinSubmit(pin) { error = it } }, enabled = pin.length in 4..8, modifier = Modifier.weight(1f).height(56.dp).testTag("pin-submit")) { Text("OK") }
            }
            if (error.isNotEmpty()) Text(error, color = Color(0xFFEE8791), modifier = Modifier.testTag("pin-error"))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
