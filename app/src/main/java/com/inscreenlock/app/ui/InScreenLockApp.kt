package com.inscreenlock.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inscreenlock.app.UnlockActivity
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetEvaluator
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.ProtectedChange
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.domain.formatUsageDuration
import com.inscreenlock.app.domain.rankUsageByPackage
import com.inscreenlock.app.monitoring.AccessibilityMonitorService
import com.inscreenlock.app.monitoring.AppUsageEntry
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
    var appUsage by remember { mutableStateOf<List<AppUsageEntry>?>(null) }
    var usageAvailable by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var deviceSecure by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(openSettingsInitially) }
    var pendingProtectedChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    val authenticationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) pendingProtectedChange?.invoke()
        pendingProtectedChange = null
    }
    val authenticate: (() -> Unit) -> Unit = { action ->
        pendingProtectedChange = action
        authenticationLauncher.launch(UnlockActivity.intent(context, confirmOnly = true, hasPin = settings.hasPin))
    }

    LaunchedEffect(lifecycleOwner, usageReader, showSettings) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val permissionState = withContext(Dispatchers.IO) {
                    usageReader.hasUsageAccess() to isAccessibilityServiceEnabled(context)
                }
                usageAvailable = permissionState.first
                accessibilityEnabled = permissionState.second
                deviceSecure = (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
                if (!showSettings) {
                    val snapshot = withContext(Dispatchers.IO) { usageReader.todaySnapshot() }
                    usageTotals = snapshot?.totals
                    appUsage = snapshot?.apps
                }
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
                    deviceSecure = deviceSecure,
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
                    onSetPin = { pin ->
                        scope.launch { settingsRepository.setPin(pin) }
                    },
                    onRemovePin = { secure -> scope.launch { settingsRepository.removePin(secure) } },
                    onAuthenticate = authenticate,
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
                        appUsage = if (usageAvailable) appUsage else null,
                        budgetLevel = budget.level,
                        usageAvailable = usageAvailable,
                        accessibilityEnabled = accessibilityEnabled,
                        deviceSecure = deviceSecure,
                        onOpenSettings = { showSettings = true },
                        onMonitoringChanged = { enabled ->
                            if (enabled && usageAvailable && accessibilityEnabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(true) }
                            } else if (!enabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(false) }
                            }
                        },
                        onAuthenticate = authenticate,
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
        modifier = Modifier.fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
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
                "são descartados, e nada disso é guardado. No limite, o bloqueio cobre toda a janela do app, " +
                "inclusive login, e oferece Início e desbloqueio. Desativar Acessibilidade nas configurações do " +
                "Android encerra o bloqueio; isso é intencional pela política do Android.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Com Acesso de uso, o InScreenLock lê o tempo em primeiro plano de todos os apps de hoje " +
                "somente para mostrar este painel neste aparelho. Nada sai do aparelho nem é salvo.",
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
internal fun DashboardScreen(
    settings: UserSettings,
    totals: UsageTotals?,
    appUsage: List<AppUsageEntry>?,
    budgetLevel: BudgetLevel,
    usageAvailable: Boolean,
    accessibilityEnabled: Boolean,
    deviceSecure: Boolean,
    onOpenSettings: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
) {
    val credentialAvailable = deviceSecure || settings.hasPin
    var pendingMonitoringConfirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestMonitoringChange: (Boolean) -> Unit = { enabled ->
        val action = { onMonitoringChanged(enabled) }
        when {
            enabled -> action()
            ProtectedChange.requiresAuthentication(ProtectedChange.DisableMonitoring, credentialAvailable) -> onAuthenticate(action)
            ProtectedChange.requiresConfirmation(ProtectedChange.DisableMonitoring, credentialAvailable) -> {
                pendingMonitoringConfirmation = action
            }
            else -> action()
        }
    }
    val protectionReady = usageAvailable && accessibilityEnabled && settings.monitoringEnabled &&
        (settings.reactionMode == ReactionMode.WARNING || credentialAvailable)
    Column(
        modifier = Modifier.fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("InScreenLock", color = Mint, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        Text("Hoje", color = MainText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        DashboardBudgetCard(settings, totals, budgetLevel)
        Spacer(Modifier.height(14.dp))
        AppBreakdownCard("Instagram", totals?.instagramMs)
        Spacer(Modifier.height(10.dp))
        AppBreakdownCard("TikTok", totals?.tiktokMs)
        Spacer(Modifier.height(14.dp))
        AppUsageCard(appUsage)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    when {
                        protectionReady -> "Monitoramento ativo"
                        settings.reactionMode == ReactionMode.LOCK && !credentialAvailable -> "Bloqueio indisponível: defina um PIN"
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
                        checked = settings.monitoringEnabled && usageAvailable && accessibilityEnabled,
                        onCheckedChange = requestMonitoringChange,
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
    pendingMonitoringConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingMonitoringConfirmation = null },
            title = { Text("Confirmar alteração") },
            text = { Text("Sem credencial configurada, o bloqueio por limite está indisponível. Deseja desativar o monitoramento?") },
            confirmButton = {
                Button(onClick = { pendingMonitoringConfirmation = null; action() }) { Text("Confirmar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingMonitoringConfirmation = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
internal fun AppUsageCard(apps: List<AppUsageEntry>?) {
    var expanded by remember { mutableStateOf(false) }
    val sortedApps = remember(apps) {
        apps?.let { entries ->
            val ranked = rankUsageByPackage(
                entries.associate { it.packageName to it.durationMs },
                limit = entries.size,
            )
            val entriesByPackage = entries.associateBy { it.packageName }
            ranked.mapNotNull { (packageName) -> entriesByPackage[packageName] }
        }
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("app-usage-card")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Uso de hoje por app", color = MainText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            when {
                apps == null -> Text("Indisponível", color = Muted, style = MaterialTheme.typography.bodyMedium)
                sortedApps.isNullOrEmpty() -> Text("Nenhum uso registrado hoje", color = Muted)
                else -> {
                    val visibleApps = if (expanded) sortedApps else sortedApps.take(APP_USAGE_PREVIEW_COUNT)
                    visibleApps.forEach { entry -> AppUsageRow(entry) }
                    if (sortedApps.size > APP_USAGE_PREVIEW_COUNT) {
                        androidx.compose.material3.TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("app-usage-toggle"),
                        ) {
                            Text(if (expanded) "Mostrar menos" else "Ver todos")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(entry: AppUsageEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
            contentDescription = "${entry.label}, ${formatUsageDuration(entry.durationMs)}"
        }.testTag("app-usage-${entry.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.icon != null) {
            Image(
                bitmap = entry.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Surface(modifier = Modifier.size(40.dp), color = SurfaceColor, shape = MaterialTheme.shapes.small) {
                Text("•", color = Mint, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(entry.label, color = MainText, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        Text(formatUsageDuration(entry.durationMs), color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DashboardBudgetCard(settings: UserSettings, totals: UsageTotals?, budgetLevel: BudgetLevel) {
    val progressColor = when (budgetLevel) {
        BudgetLevel.ATTENTION -> Attention
        BudgetLevel.EXCEEDED -> Exceeded
        else -> Mint
    }
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
internal fun SettingsScreen(
    settings: UserSettings,
    usageAvailable: Boolean,
    accessibilityEnabled: Boolean,
    deviceSecure: Boolean,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onSaveBudget: (Int, ReactionMode) -> Unit,
    onClearLocalData: () -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: (Boolean) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
) {
    var limitText by remember(settings.dailyLimitMinutes) { mutableStateOf(settings.dailyLimitMinutes.toString()) }
    var reaction by remember(settings.reactionMode) { mutableStateOf(settings.reactionMode) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pinFirst by remember { mutableStateOf("") }
    var pinSecond by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    val credentialAvailable = deviceSecure || settings.hasPin
    val protectChange: (ProtectedChange, String, () -> Unit) -> Unit = { change, description, action ->
        when {
            ProtectedChange.requiresAuthentication(change, credentialAvailable) -> onAuthenticate(action)
            ProtectedChange.requiresConfirmation(change, credentialAvailable) -> pendingConfirmation = description to action
            else -> action()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
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
                    modifier = Modifier.fillMaxWidth().testTag("daily-limit"),
                )
                Text("Conta o tempo total com Instagram e TikTok abertos e a tela ativa.", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("Ao atingir o limite", color = MainText)
                Column(Modifier.selectableGroup()) {
                    ReactionMode.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = reaction == option,
                                role = Role.RadioButton,
                                onClick = { reaction = option },
                            ).testTag("reaction-${option.name}"),
                        ) {
                            RadioButton(selected = reaction == option, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (option == ReactionMode.WARNING) "Aviso discreto" else "Bloqueio com senha",
                                color = MainText,
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        limitText.toIntOrNull()?.takeIf { it > 0 }?.let { minutes ->
                            val save = { onSaveBudget(minutes, reaction) }
                            when {
                                minutes > settings.dailyLimitMinutes -> protectChange(
                                    ProtectedChange.IncreaseLimit,
                                    "Sem credencial configurada, o bloqueio por limite está indisponível. Deseja aumentar o limite?",
                                    save,
                                )
                                settings.reactionMode == ReactionMode.LOCK && reaction == ReactionMode.WARNING -> protectChange(
                                    ProtectedChange.LockToWarning,
                                    "Sem credencial configurada, o bloqueio por limite está indisponível. Deseja trocar para aviso?",
                                    save,
                                )
                                else -> save()
                            }
                        }
                    },
                    enabled = limitText.toIntOrNull()?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth().testTag("save-budget"),
                ) { Text("Salvar limite") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Credencial de desbloqueio", color = MainText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (settings.hasPin) "PIN do InScreenLock definido" else if (deviceSecure) "Usando o bloqueio de tela seguro do dispositivo." else "Bloqueio indisponível: defina um PIN para ativá-lo. Até lá, aparece apenas um aviso.",
                    color = Muted,
                )
                OutlinedTextField(
                    value = pinFirst,
                    onValueChange = { if (it.length <= 8 && it.all { char -> char in '0'..'9' }) pinFirst = it },
                    label = { Text(if (settings.hasPin) "Novo PIN (4–8 dígitos)" else "Definir PIN (4–8 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pin-first"),
                )
                OutlinedTextField(
                    value = pinSecond,
                    onValueChange = { if (it.length <= 8 && it.all { char -> char in '0'..'9' }) pinSecond = it },
                    label = { Text("Digite o PIN novamente") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pin-second"),
                )
                Button(
                    onClick = {
                        when {
                            pinFirst.length !in 4..8 -> pinError = "Use de 4 a 8 dígitos."
                            pinFirst != pinSecond -> pinError = "Os PINs não coincidem."
                    else -> {
                            val pinToSave = pinFirst
                            val save = { onSetPin(pinToSave) }
                            if (settings.hasPin) protectChange(
                                ProtectedChange.ChangeOrRemovePin,
                                "Deseja alterar o PIN?",
                                save,
                            ) else save()
                            pinFirst = ""; pinSecond = ""; pinError = ""
                        }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pin-save"),
                ) { Text(if (settings.hasPin) "Alterar PIN" else "Definir PIN") }
                if (settings.hasPin) {
                    OutlinedButton(onClick = {
                        protectChange(
                            ProtectedChange.ChangeOrRemovePin,
                            "Deseja remover o PIN?",
                        ) { onRemovePin(deviceSecure) }
                    }, modifier = Modifier.fillMaxWidth().testTag("pin-remove")) { Text("Remover PIN") }
                }
                if (pinError.isNotEmpty()) Text(pinError, color = Exceeded, modifier = Modifier.testTag("pin-error"))
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Privacidade", color = MainText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Com Acesso de uso, o InScreenLock lê o tempo em primeiro plano de todos os apps de hoje " +
                        "somente para mostrar o painel neste aparelho; nada sai do aparelho nem é salvo. " +
                        "Sem conta, rede ou anúncios. O serviço recebe notificações de mudança de janela de qualquer app " +
                        "e lê apenas o nome do pacote para reconhecer entradas e saídas do Instagram/TikTok; " +
                        "descarta os demais e não guarda esses dados. Guardamos preferências e o maior limite já " +
                        "avisado no dia; o tempo vem do histórico de uso do Android. O bloqueio cobre a janela inteira " +
                        "do app, inclusive login, e oferece Início e desbloqueio por credencial. Desativar " +
                        "Acessibilidade nas configurações do Android encerra o bloqueio; isso é intencional pela " +
                        "política do Android. Desinstalar apaga os dados do InScreenLock. O histórico de Acesso de " +
                        "uso é do Android e nunca é copiado pelo app. Este APK privado ainda não passou por " +
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
        OutlinedButton(onClick = {
            protectChange(
                ProtectedChange.DisableMonitoring,
                "Sem credencial configurada, o bloqueio por limite está indisponível. Deseja desativar o monitoramento?",
            ) { onMonitoringChanged(false) }
        }, modifier = Modifier.fillMaxWidth().testTag("disable-monitoring")) {
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
                Button(onClick = {
                    showClearConfirmation = false
                    if (ProtectedChange.requiresAuthentication(ProtectedChange.ClearData, credentialAvailable)) {
                        onAuthenticate(onClearLocalData)
                    } else {
                        onClearLocalData()
                    }
                }) { Text("Apagar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmation = false }) { Text("Cancelar") }
            },
        )
    }

    pendingConfirmation?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Confirmar alteração") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { pendingConfirmation = null; action() }) { Text("Confirmar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingConfirmation = null }) { Text("Cancelar") }
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
internal fun InScreenLockTheme(content: @Composable () -> Unit) {
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

private const val REFRESH_INTERVAL_MS = 10_000L
private const val MINUTE_MS = 60_000L
private const val APP_USAGE_PREVIEW_COUNT = 10

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / MINUTE_MS
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}
