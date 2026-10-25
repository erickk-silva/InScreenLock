package com.inscreenlock.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inscreenlock.app.MainActivity
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetEvaluator
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.monitoring.AccessibilityMonitorService
import com.inscreenlock.app.monitoring.UsageStatsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF101319)
private val SurfaceColor = Color(0xFF1B2029)
private val Border = Color(0xFF303845)
private val Mint = Color(0xFF69DDBB)
private val MainText = Color(0xFFF2F5F7)
private val Muted = Color(0xFFA5AFBC)
private val Attention = Color(0xFFFFBD75)
private val Exceeded = Color(0xFFEE8791)

@Composable
fun InScreenLockApp(
    settingsRepository: SettingsRepository,
    openSettingsInitially: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsRepository.settings.collectAsState(initial = UserSettings())
    val usageReader = remember { UsageStatsReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var usageTotals by remember { mutableStateOf<UsageTotals?>(null) }
    var usageAvailable by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(openSettingsInitially) }

    LaunchedEffect(lifecycleOwner, usageReader) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val permissionState = withContext(Dispatchers.IO) {
                    usageReader.hasUsageAccess() to isAccessibilityServiceEnabled(context)
                }
                usageAvailable = permissionState.first
                accessibilityEnabled = permissionState.second
                usageTotals = withContext(Dispatchers.IO) { usageReader.todayTotals() }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    InScreenLockTheme {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = Background,
        ) {
            when {
                !settings.accessibilityDisclosureAccepted -> ConsentScreen(
                    onContinue = {
                        scope.launch {
                            settingsRepository.acceptAccessibilityDisclosure()
                            openAccessibilitySettings(context)
                        }
                    },
                )
                showSettings -> SettingsScreen(
                    settings = settings,
                    usageAvailable = usageAvailable,
                    accessibilityEnabled = accessibilityEnabled,
                    onBack = { showSettings = false },
                    onOpenUsageSettings = { openUsageSettings(context) },
                    onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
                    onMonitoringChanged = { enabled ->
                        if (enabled && usageAvailable && accessibilityEnabled) {
                            scope.launch { settingsRepository.setMonitoringEnabled(true) }
                        } else if (!enabled) {
                            scope.launch { settingsRepository.setMonitoringEnabled(false) }
                        }
                    },
                    onSaveBudget = { minutes, reaction ->
                        scope.launch { settingsRepository.updateBudget(minutes, reaction) }
                    },
                    onClearLocalData = { scope.launch { settingsRepository.clearLocalSettings() } },
                )
                else -> {
                    val budget = BudgetEvaluator.evaluate(
                        usedMs = usageTotals?.totalMs ?: 0L,
                        limitMs = settings.dailyLimitMinutes * MINUTE_MS,
                        dataAvailable = usageAvailable && usageTotals != null,
                    )
                    DashboardScreen(
                        settings = settings,
                        totals = usageTotals,
                        budgetLevel = budget.level,
                        usageAvailable = usageAvailable,
                        accessibilityEnabled = accessibilityEnabled,
                        onOpenSettings = { showSettings = true },
                        onMonitoringChanged = { enabled ->
                            if (enabled && usageAvailable && accessibilityEnabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(true) }
                            } else if (!enabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(false) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentScreen(onContinue: () -> Unit) {
    var consentChecked by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("InScreenLock", color = Mint, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(16.dp))
        Text("Seu uso, no seu ritmo", color = MainText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text(
            "O modo de tempo mede por quanto tempo Instagram e TikTok ficam abertos com a tela ativa — " +
                "inclui mensagens e outras áreas, não apenas vídeos.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "O serviço recebe notificações de mudança de janela de qualquer app e lê somente o nome do pacote " +
                "para identificar quando Instagram ou TikTok entram ou saem do primeiro plano. Os demais pacotes " +
                "são descartados, e nada disso é guardado. A pausa cobre toda a janela do app, inclusive telas " +
                "internas como login, e oferece Início e Configurações. Home, Voltar e notificações do sistema " +
                "ainda não foram validados em aparelho físico.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Não lemos nem armazenamos mensagens, legendas ou imagens.",
            color = MainText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().testTag("accessibility-consent").toggleable(
                value = consentChecked,
                role = Role.Checkbox,
                onValueChange = { consentChecked = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = consentChecked, onCheckedChange = null)
            Text("Entendo e quero continuar", color = MainText, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onContinue, enabled = consentChecked, modifier = Modifier.fillMaxWidth()) {
            Text("Continuar para acessibilidade")
        }
    }
}

@Composable
private fun DashboardScreen(
    settings: UserSettings,
    totals: UsageTotals?,
    budgetLevel: BudgetLevel,
    usageAvailable: Boolean,
    accessibilityEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
) {
    val protectionReady = usageAvailable && accessibilityEnabled && settings.monitoringEnabled
    val progressColor = when (budgetLevel) {
        BudgetLevel.ATTENTION -> Attention
        BudgetLevel.EXCEEDED -> Exceeded
        else -> Mint
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("InScreenLock", color = Mint, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        Text("Hoje", color = MainText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Tempo total nos apps", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    totals?.let { formatDuration(it.totalMs) } ?: "Indisponível",
                    color = MainText,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text("Meta diária · ${settings.dailyLimitMinutes} min", color = Muted)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = {
                        if (budgetLevel == BudgetLevel.UNAVAILABLE || totals == null) 0f
                        else (totals.totalMs.toDouble() / (settings.dailyLimitMinutes * MINUTE_MS))
                            .coerceAtMost(1.0).toFloat()
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                        contentDescription = "Progresso do limite diário"
                    },
                    color = progressColor,
                )
                Spacer(Modifier.height(8.dp))
                val status = when (budgetLevel) {
                    BudgetLevel.NORMAL -> "Dentro do combinado"
                    BudgetLevel.ATTENTION -> "Atenção: perto do limite"
                    BudgetLevel.EXCEEDED -> "Limite diário alcançado"
                    BudgetLevel.UNAVAILABLE -> "Libere Acesso de uso para consultar o total"
                }
                Text(status, color = progressColor, style = MaterialTheme.typography.bodyMedium)
                if (totals != null && budgetLevel != BudgetLevel.UNAVAILABLE) {
                    val remaining = settings.dailyLimitMinutes * MINUTE_MS - totals.totalMs
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (remaining >= 0) "Restam ${formatDuration(remaining)}"
                        else "${formatDuration(-remaining)} acima da meta",
                        color = Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AppBreakdownCard("Instagram", totals?.instagramMs)
        Spacer(Modifier.height(10.dp))
        AppBreakdownCard("TikTok", totals?.tiktokMs)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    when {
                        protectionReady -> "Monitoramento ativo"
                        !usageAvailable -> "Acesso de uso necessário"
                        !accessibilityEnabled -> "Contador sobre os apps indisponível"
                        else -> "Monitoramento desativado"
                    },
                    color = MainText,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (accessibilityEnabled) "Acessibilidade: ativa" else "Acessibilidade: não ativada",
                    color = Muted,
                )
                Text(
                    if (usageAvailable) "Acesso de uso: ativo" else "Acesso de uso: não concedido",
                    color = Muted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Monitorar apps", color = MainText)
                    Switch(
                        checked = protectionReady,
                        onCheckedChange = onMonitoringChanged,
                        enabled = usageAvailable && accessibilityEnabled,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Configurações e privacidade")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppBreakdownCard(name: String, usedMs: Long?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, color = MainText, style = MaterialTheme.typography.titleMedium)
            Text(usedMs?.let(::formatDuration) ?: "—", color = Muted)
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: UserSettings,
    usageAvailable: Boolean,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onSaveBudget: (Int, ReactionMode) -> Unit,
    onClearLocalData: () -> Unit,
) {
    var limitText by remember(settings.dailyLimitMinutes) { mutableStateOf(settings.dailyLimitMinutes.toString()) }
    var reaction by remember(settings.reactionMode) { mutableStateOf(settings.reactionMode) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Configurações", color = MainText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Permissões do Android", color = MainText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                PermissionRow("Acesso de uso", usageAvailable)
                OutlinedButton(onClick = onOpenUsageSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Acesso de uso")
                }
                HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                PermissionRow("Acessibilidade", accessibilityEnabled)
                Text(
                    "O Android controla essa permissão. O InScreenLock não consegue ativá-la por você.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir configurações de Acessibilidade")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Limite combinado por dia", color = MainText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { candidate -> if (candidate.all(Char::isDigit)) limitText = candidate },
                    label = { Text("Minutos (mínimo 1)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Conta o tempo total com Instagram e TikTok abertos e a tela ativa.", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("Ao atingir o limite", color = MainText)
                ReactionMode.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reaction == option, onClick = { reaction = option })
                        Text(
                            if (option == ReactionMode.WARNING) "Aviso discreto" else "Pausa reversível",
                            color = MainText,
                        )
                    }
                }
                Button(
                    onClick = { limitText.toIntOrNull()?.takeIf { it > 0 }?.let { onSaveBudget(it, reaction) } },
                    enabled = limitText.toIntOrNull()?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar limite") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Privacidade", color = MainText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sem conta, rede ou anúncios. O serviço recebe notificações de mudança de janela de qualquer app " +
                        "e lê apenas o nome do pacote para reconhecer entradas e saídas do Instagram/TikTok; " +
                        "descarta os demais e não guarda esses dados. Guardamos preferências e o maior limite já " +
                        "avisado no dia; o tempo vem do histórico de uso do Android. A pausa cobre a janela inteira " +
                        "do app, inclusive login, e oferece Início e Configurações. Home, Voltar e notificações do " +
                        "sistema não foram validados em aparelho físico. Este APK privado ainda não passou por " +
                        "revisão de acessibilidade da Google Play.",
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showClearConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apagar preferências locais")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { onMonitoringChanged(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Desativar monitoramento")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        Spacer(Modifier.height(24.dp))
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Apagar preferências?") },
            text = { Text("O consentimento e as configurações locais serão removidos.") },
            confirmButton = {
                Button(onClick = { showClearConfirmation = false; onClearLocalData() }) { Text("Apagar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MainText)
        Text(if (granted) "Ativo" else "Não concedido", color = if (granted) Mint else Attention)
    }
}

@Composable
private fun InScreenLockTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = darkColorScheme(
        primary = Mint,
        onPrimary = Background,
        background = Background,
        onBackground = MainText,
        surface = SurfaceColor,
        onSurface = MainText,
        outline = Border,
        secondary = Attention,
        error = Exceeded,
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        ?: return false
    return manager.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    ).any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo
        serviceInfo?.packageName == context.packageName &&
            serviceInfo.name == AccessibilityMonitorService::class.java.name
    }
}

private fun openUsageSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0) / MINUTE_MS
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private const val REFRESH_INTERVAL_MS = 10_000L
private const val MINUTE_MS = 60_000L
