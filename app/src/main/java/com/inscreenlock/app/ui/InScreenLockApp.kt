package com.inscreenlock.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inscreenlock.app.UnlockActivity
import com.inscreenlock.app.R
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetEvaluator
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.ProtectedChange
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.domain.WeekUsage
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
import com.inscreenlock.app.ui.components.AppBarItem
import com.inscreenlock.app.ui.components.AppScaffold
import com.inscreenlock.app.ui.components.BannerLevel
import com.inscreenlock.app.ui.components.LimitSelector
import com.inscreenlock.app.ui.components.MetricCard
import com.inscreenlock.app.ui.components.PermissionRow as StyledPermissionRow
import com.inscreenlock.app.ui.components.PrimaryAction
import com.inscreenlock.app.ui.components.ProgressLevel
import com.inscreenlock.app.ui.components.ProgressRing
import com.inscreenlock.app.ui.components.ScreenHeader
import com.inscreenlock.app.ui.components.StatusBanner
import com.inscreenlock.app.ui.components.SurfaceCard
import com.inscreenlock.app.ui.theme.InScreenLockColors
import com.inscreenlock.app.ui.theme.InScreenLockTheme as DesignTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val Background = InScreenLockColors.Background
private val Border = InScreenLockColors.Outline
private val Mint = InScreenLockColors.Primary
private val MainText = InScreenLockColors.TextPrimary
private val Muted = InScreenLockColors.TextSecondary
private val Attention = Color(0xFFFFC857)
private val Exceeded = InScreenLockColors.Alert

private enum class MainRoute { HOME, ANALYSIS, LIMITS, MORE }
private enum class MoreRoute { ROOT, CREDENTIAL, PRIVACY, ABOUT }

@Composable
fun InScreenLockApp(
    settingsRepository: SettingsRepository,
    openSettingsInitially: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsEmission by settingsRepository.settings.collectAsState(initial = null)
    val settings = settingsEmission ?: UserSettings()
    val usageReader = remember { UsageStatsReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var usageTotals by remember { mutableStateOf<UsageTotals?>(null) }
    var appUsage by remember { mutableStateOf<List<AppUsageEntry>?>(null) }
    var usageAvailable by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var deviceSecure by remember { mutableStateOf(false) }
    var selectedRouteName by rememberSaveable { mutableStateOf(if (openSettingsInitially) MainRoute.MORE.name else MainRoute.HOME.name) }
    val selectedRoute = runCatching { MainRoute.valueOf(selectedRouteName) }.getOrDefault(MainRoute.HOME)
    var moreRouteName by rememberSaveable { mutableStateOf(MoreRoute.ROOT.name) }
    val moreRoute = runCatching { MoreRoute.valueOf(moreRouteName) }.getOrDefault(MoreRoute.ROOT)
    var weekUsage by remember { mutableStateOf<WeekUsage?>(null) }
    var weekLoading by remember { mutableStateOf(false) }
    var weekError by remember { mutableStateOf(false) }
    var pendingProtectedChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    val authenticationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) pendingProtectedChange?.invoke()
        pendingProtectedChange = null
    }
    val authenticate: (() -> Unit) -> Unit = { action ->
        pendingProtectedChange = action
        authenticationLauncher.launch(UnlockActivity.intent(context, confirmOnly = true, hasPin = settings.hasPin))
    }

    LaunchedEffect(lifecycleOwner, usageReader, selectedRoute) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val permissionState = withContext(Dispatchers.IO) {
                    usageReader.hasUsageAccess() to isAccessibilityServiceEnabled(context)
                }
                usageAvailable = permissionState.first
                accessibilityEnabled = permissionState.second
                deviceSecure = (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
                if (selectedRoute == MainRoute.HOME || selectedRoute == MainRoute.ANALYSIS) {
                    val snapshot = withContext(Dispatchers.IO) { usageReader.todaySnapshot() }
                    usageTotals = snapshot?.totals
                    appUsage = snapshot?.apps
                }
                if (selectedRoute == MainRoute.ANALYSIS && weekUsage == null && !weekLoading) {
                    weekLoading = true
                    weekError = false
                    val result = runCatching { withContext(Dispatchers.IO) { usageReader.weekUsage() } }
                    weekUsage = result.getOrNull()
                    weekError = result.isFailure
                    weekLoading = false
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    DesignTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            AnimatedContent(
                targetState = settingsEmission == null,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .985f)).togetherWith(fadeOut(tween(220))) },
                label = "settings-startup",
            ) { loading ->
            when {
                loading -> SplashLoadingScreen(Modifier.testTag("splash-loading"))
                !settings.accessibilityDisclosureAccepted -> FirstLaunchFlow(
                    consentAccepted = false,
                    onAccept = {
                        scope.launch {
                            settingsRepository.acceptAccessibilityDisclosure()
                            openAccessibilitySettings(context)
                        }
                    },
                )
                else -> {
                    val budget = BudgetEvaluator.evaluate(
                        usedMs = usageTotals?.totalMs ?: 0L,
                        limitMs = settings.dailyLimitMinutes * MINUTE_MS,
                        dataAvailable = usageAvailable && usageTotals != null,
                    )
                    val monitoringChange: (Boolean) -> Unit = { enabled ->
                            if (enabled && usageAvailable && accessibilityEnabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(true) }
                            } else if (!enabled) {
                                scope.launch { settingsRepository.setMonitoringEnabled(false) }
                            }
                        }
                    BackHandler(selectedRoute != MainRoute.HOME) {
                        if (selectedRoute == MainRoute.MORE && moreRoute != MoreRoute.ROOT) moreRouteName = MoreRoute.ROOT.name
                        else selectedRouteName = MainRoute.HOME.name
                    }
                    val tabItems = listOf(
                        navItem("Início", "HOME", selectedRoute, R.drawable.ic_nav_home) { selectedRouteName = MainRoute.HOME.name },
                        navItem("Análises", "ANALYSIS", selectedRoute, R.drawable.ic_nav_analysis) { selectedRouteName = MainRoute.ANALYSIS.name },
                        navItem("Limites", "LIMITS", selectedRoute, R.drawable.ic_nav_limits) { selectedRouteName = MainRoute.LIMITS.name },
                        navItem("Mais", "MORE", selectedRoute, R.drawable.ic_nav_more) { selectedRouteName = MainRoute.MORE.name; moreRouteName = MoreRoute.ROOT.name },
                    )
                    AppScaffold(bottomBarItems = tabItems) { insets ->
                        when (selectedRoute) {
                            MainRoute.HOME -> DashboardScreen(
                                modifier = Modifier.padding(insets), settings = settings,
                                totals = usageTotals, appUsage = if (usageAvailable) appUsage else null,
                                budgetLevel = budget.level, usageAvailable = usageAvailable,
                                accessibilityEnabled = accessibilityEnabled, deviceSecure = deviceSecure,
                                onOpenSettings = { selectedRouteName = MainRoute.MORE.name; moreRouteName = MoreRoute.ROOT.name },
                                onMonitoringChanged = monitoringChange, onAuthenticate = authenticate,
                            )
                            MainRoute.ANALYSIS -> AnalysisScreen(
                                modifier = Modifier.padding(insets), todayTotals = usageTotals,
                                todayAvailable = usageAvailable && usageTotals != null,
                                usageAccessGranted = usageAvailable,
                                week = weekUsage, loading = weekLoading, failed = weekError,
                                onRetry = {
                                    weekUsage = null
                                    weekLoading = true
                                    scope.launch {
                                        val result = runCatching { withContext(Dispatchers.IO) { usageReader.weekUsage() } }
                                        weekUsage = result.getOrNull(); weekError = result.isFailure; weekLoading = false
                                    }
                                },
                            )
                            MainRoute.LIMITS -> LimitsRoute(
                                modifier = Modifier.padding(insets), settings = settings,
                                deviceSecure = deviceSecure, onSaveBudget = { minutes, reaction -> scope.launch { settingsRepository.updateBudget(minutes, reaction) } },
                                onAuthenticate = authenticate, onDefinePin = { selectedRouteName = MainRoute.MORE.name; moreRouteName = MoreRoute.CREDENTIAL.name },
                            )
                            MainRoute.MORE -> when (moreRoute) {
                                MoreRoute.ROOT -> MoreScreen(
                                    modifier = Modifier.padding(insets), settings = settings,
                                    usageAvailable = usageAvailable, accessibilityEnabled = accessibilityEnabled,
                                    deviceSecure = deviceSecure, onOpenUsageSettings = { openUsageSettings(context) },
                                    onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
                                    onOpenCredential = { moreRouteName = MoreRoute.CREDENTIAL.name },
                                    onOpenPrivacy = { moreRouteName = MoreRoute.PRIVACY.name },
                                    onOpenAbout = { moreRouteName = MoreRoute.ABOUT.name },
                                    onMonitoringChanged = monitoringChange,
                                    onAuthenticate = authenticate,
                                )
                                MoreRoute.CREDENTIAL -> CredentialRoute(
                                    modifier = Modifier.padding(insets), settings = settings, deviceSecure = deviceSecure,
                                    onBack = { moreRouteName = MoreRoute.ROOT.name },
                                    onSetPin = { scope.launch { settingsRepository.setPin(it) } },
                                    onRemovePin = { scope.launch { settingsRepository.removePin(deviceSecure) } },
                                    onAuthenticate = authenticate,
                                )
                                MoreRoute.PRIVACY -> PrivacyRoute(
                                    modifier = Modifier.padding(insets), settings = settings, deviceSecure = deviceSecure,
                                    onBack = { moreRouteName = MoreRoute.ROOT.name },
                                    onClear = { scope.launch { settingsRepository.clearLocalSettings() } },
                                    onAuthenticate = authenticate,
                                )
                                MoreRoute.ABOUT -> SimpleDetailRoute("Sobre", Modifier.padding(insets), { moreRouteName = MoreRoute.ROOT.name }) {
                                    Text("InScreenLock", style = MaterialTheme.typography.headlineSmall)
                                    Text("Versão ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "desconhecida"}", color = Muted)
                                    Text("Controle de tempo e foco com privacidade. Os dados de uso permanecem no aparelho.", color = Muted)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun FirstLaunchFlow(consentAccepted: Boolean, onAccept: () -> Unit) {
    var showingConsent by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showingConsent && !consentAccepted) { showingConsent = false }
    when {
        consentAccepted -> Unit
        showingConsent -> ConsentScreen(onContinue = onAccept)
        else -> OnboardingScreen(onSkip = { showingConsent = true }, onStart = { showingConsent = true })
    }
}

@Composable
internal fun SplashLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x3322E3B0), Background, Background), center = Offset.Zero, radius = 760f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandBadge(Modifier.size(104.dp))
            Spacer(Modifier.height(22.dp))
            Text("InScreenLock", color = MainText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Mais foco, menos rolagem.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        LinearProgressIndicator(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp).padding(bottom = 42.dp).height(3.dp).testTag("splash-progress"),
            color = Mint,
            trackColor = InScreenLockColors.Surface,
        )
    }
}

@Composable
private fun BrandBadge(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFF101C22)).border(1.dp, Color(0x5536E5BB), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.fillMaxSize(.72f))
    }
}

@Composable
internal fun OnboardingScreen(onSkip: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x3322E3B0), Background, Background), center = Offset.Zero, radius = 760f))) {
        androidx.compose.animation.AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 24 },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                OnboardingIllustration(context)
                Spacer(Modifier.height(22.dp))
                Text("Controle seu tempo\nnas redes sociais", color = MainText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Defina limites, acompanhe o uso e evite o excesso de rolagem.", color = Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OnboardingTrustCard()
                Spacer(Modifier.height(20.dp))
                PrimaryAction("Começar  →", onStart, Modifier.fillMaxWidth().testTag("onboarding-start"))
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 30 },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 20.dp),
        ) {
            Text("Pular", color = Muted, modifier = Modifier.clickable(onClick = onSkip).heightIn(min = 48.dp).padding(12.dp).testTag("onboarding-skip"))
        }
    }
}

@Composable
private fun OnboardingIllustration(context: Context) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 168.dp, height = 192.dp).rotate(12f).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF22303C), Color(0xFF101820), Color(0xFF17242B)))).border(1.dp, Color(0x8853CDB8), RoundedCornerShape(28.dp))) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                OnboardingAppIcon(context, "com.zhiliaoapp.musically", "TikTok")
                Spacer(Modifier.height(16.dp))
                OnboardingAppIcon(context, "com.instagram.android", "Instagram")
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).offset(x = 75.dp, y = (-2).dp).size(62.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF102C2B)).border(1.dp, Mint, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_lock_symbol), contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun OnboardingTrustCard() {
    SurfaceCard(Modifier.fillMaxWidth().testTag("onboarding-trust-card")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrustRow(R.drawable.ic_trust_device, "Seus dados ficam neste aparelho", "Sem conta, internet ou anúncios.")
            TrustRow(R.drawable.ic_trust_permissions, "Só as permissões necessárias", "Acesso de uso e Acessibilidade: você ativa e pode desligar nas Configurações.")
            TrustRow(R.drawable.ic_trust_visibility, "Não lemos o que você vê ou digita", "Lemos só o tempo de uso e o nome do app aberto.")
        }
    }
}

@Composable
private fun TrustRow(icon: Int, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = Mint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = MainText, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OnboardingAppIcon(context: Context, packageName: String, fallback: String) {
    val icon = remember(packageName) { runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() }
    Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF202833)).border(1.dp, Color(0x553FFFFFFF), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        if (icon != null) AndroidView(factory = { ImageView(it).apply { setImageDrawable(icon); contentDescription = fallback } }, modifier = Modifier.fillMaxSize().padding(7.dp))
        else Text("•", color = Muted, style = MaterialTheme.typography.headlineMedium)
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
      SurfaceCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp)) {
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
        PrimaryAction("Continuar para acessibilidade", onContinue, Modifier.fillMaxWidth().testTag("consent-continue"), enabled = consentChecked)
       }
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
    modifier: Modifier = Modifier,
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
    Column(
        modifier = modifier.fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Hoje", subtitle = localTodayLabel())
        Spacer(Modifier.height(14.dp))
        DashboardBudgetCard(settings, totals, budgetLevel, usageAccessGranted = usageAvailable)
        Spacer(Modifier.height(10.dp))
        val remainingMs = totals?.let { settings.dailyLimitMinutes * MINUTE_MS - it.totalMs }
        StatusBanner(
            title = when (budgetLevel) {
                BudgetLevel.NORMAL -> "Dentro do combinado"
                BudgetLevel.ATTENTION -> "Atenção: perto do limite"
                BudgetLevel.EXCEEDED -> "Limite diário alcançado"
                BudgetLevel.UNAVAILABLE -> "Indisponível"
            },
            message = when (budgetLevel) {
                BudgetLevel.NORMAL, BudgetLevel.ATTENTION -> remainingMs?.let { "Restam ${formatDuration(it)} da meta de hoje." } ?: "O tempo de hoje não está disponível."
                BudgetLevel.EXCEEDED -> "${formatDuration(-(remainingMs ?: 0L))} acima da meta diária."
                BudgetLevel.UNAVAILABLE -> if (usageAvailable) "O Android não disponibilizou dados de uso agora." else "Conceda Acesso de uso nas configurações do Android."
            },
            level = when (budgetLevel) {
                BudgetLevel.NORMAL -> BannerLevel.INFO
                BudgetLevel.ATTENTION -> BannerLevel.WARNING
                BudgetLevel.EXCEEDED, BudgetLevel.UNAVAILABLE -> BannerLevel.ERROR
            },
            actionLabel = if (budgetLevel == BudgetLevel.UNAVAILABLE && !usageAvailable) "Permissões" else null,
            onAction = if (budgetLevel == BudgetLevel.UNAVAILABLE && !usageAvailable) onOpenSettings else null,
        )
        Spacer(Modifier.height(14.dp))
        AppUsageCard(appUsage, totals?.totalMs)
        Spacer(Modifier.height(12.dp))
        val monitoringChecked = settings.monitoringEnabled && usageAvailable && accessibilityEnabled
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp).toggleable(
                        value = monitoringChecked,
                        enabled = usageAvailable && accessibilityEnabled,
                        role = Role.Switch,
                        onValueChange = requestMonitoringChange,
                    ).testTag("dashboard-monitoring-switch"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Monitoramento", color = MainText, style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                !usageAvailable -> "Acesso de uso não concedido"
                                !accessibilityEnabled -> "Acessibilidade não ativada"
                                monitoringChecked -> "Ativo"
                                else -> "Desligado"
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = monitoringChecked,
                        onCheckedChange = null,
                        enabled = usageAvailable && accessibilityEnabled,
                    )
                }
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
internal fun AppUsageCard(apps: List<AppUsageEntry>?, combinedTotalMs: Long? = null) {
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
    SurfaceCard(modifier = Modifier.fillMaxWidth().testTag("app-usage-card")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Uso por app", color = MainText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            when {
                apps == null -> Text("Indisponível", color = Muted, style = MaterialTheme.typography.bodyMedium)
                sortedApps.isNullOrEmpty() -> Text(
                    if ((combinedTotalMs ?: 0L) > 0L) "Detalhamento por app indisponível; total combinado preservado acima."
                    else "Nenhum uso registrado hoje",
                    color = Muted,
                )
                else -> {
                    val visibleApps = if (expanded) sortedApps else sortedApps.take(APP_USAGE_PREVIEW_COUNT)
                    visibleApps.forEachIndexed { index, entry ->
                        AppUsageRow(entry)
                        if (index != visibleApps.lastIndex) HorizontalDivider(color = Border.copy(alpha = 0.35f), thickness = 0.5.dp)
                    }
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
            Surface(modifier = Modifier.size(40.dp), color = InScreenLockColors.PrimaryDark, shape = MaterialTheme.shapes.small) {
                Box(contentAlignment = Alignment.Center) { Text(entry.label.firstOrNull()?.uppercase() ?: "?", color = InScreenLockColors.Background, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            }
        }
        Text(entry.label, color = MainText, modifier = Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        Text(formatUsageDuration(entry.durationMs), color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DashboardBudgetCard(settings: UserSettings, totals: UsageTotals?, budgetLevel: BudgetLevel, usageAccessGranted: Boolean = true) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tempo combinado · Instagram + TikTok", color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            ProgressRing(
                progress = if (budgetLevel == BudgetLevel.UNAVAILABLE || totals == null) 0f else totals.totalMs.toFloat() / (settings.dailyLimitMinutes * MINUTE_MS),
                valueLabel = totals?.let { formatDuration(it.totalMs) } ?: "—",
                level = when (budgetLevel) {
                    BudgetLevel.ATTENTION -> ProgressLevel.ATTENTION
                    BudgetLevel.EXCEEDED -> ProgressLevel.EXCEEDED
                    else -> ProgressLevel.NORMAL
                },
                contentDescription = "${totals?.let { formatDuration(it.totalMs) } ?: "Indisponível"} usados de ${settings.dailyLimitMinutes} minutos",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(totals?.let { formatDuration(it.totalMs) } ?: "—", style = MaterialTheme.typography.displaySmall, color = MainText, textAlign = TextAlign.Center)
                    Text("de ${settings.dailyLimitMinutes} min", style = MaterialTheme.typography.titleSmall, color = Muted)
                }
            }
        }
    }
}

private fun localTodayLabel(): String {
    val date = java.time.LocalDate.now()
    val locale = Locale.forLanguageTag("pt-BR")
    val weekday = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).replace(".", "").replaceFirstChar { it.uppercase(locale) }
    val month = date.month.getDisplayName(DateTextStyle.SHORT, locale).replace(".", "")
    return "$weekday, ${date.dayOfMonth} de $month."
}

private fun navItem(label: String, route: String, selected: MainRoute, iconRes: Int, onClick: () -> Unit) = AppBarItem(
    label = label,
    contentDescription = label,
    selected = selected.name == route,
    onClick = onClick,
    icon = { androidx.compose.foundation.Image(painterResource(iconRes), contentDescription = null, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (selected.name == route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) },
)

@Composable
internal fun AnalysisScreen(
    modifier: Modifier = Modifier,
    todayTotals: UsageTotals?,
    todayAvailable: Boolean,
    usageAccessGranted: Boolean = true,
    week: WeekUsage?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    var weekSelected by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        ScreenHeader("Análises", subtitle = "Uso real no aparelho")
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Dia", true to "Semana").forEach { (weekTab, label) ->
                val selected = weekSelected == weekTab
                OutlinedButton(onClick = { weekSelected = weekTab }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(if (selected) "●  $label" else label, color = if (selected) Mint else MainText)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!weekSelected) {
            MetricCard(
                label = "Tempo combinado de hoje",
                value = if (todayAvailable) formatDuration(todayTotals!!.totalMs) else "Indisponível",
                detail = "Instagram + TikTok · dados do Android",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            AppBreakdownCard("Instagram", if (todayAvailable) todayTotals?.instagramMs else null)
            Spacer(Modifier.height(10.dp))
            AppBreakdownCard("TikTok", if (todayAvailable) todayTotals?.tiktokMs else null)
            if (!todayAvailable) StatusBanner(if (usageAccessGranted) "O Android não disponibilizou o histórico de hoje agora." else "Conceda Acesso de uso nas configurações do Android e tente novamente.", BannerLevel.WARNING, title = "Indisponível")
        } else when {
            loading -> StatusBanner("Consultando os últimos sete dias no Android…", BannerLevel.INFO, title = "Carregando")
            failed || week == null -> StatusBanner("Não foi possível consultar o histórico semanal.", BannerLevel.WARNING, title = "Erro ao carregar", actionLabel = "Tentar novamente", onAction = onRetry)
            week.days.none { it.available } && !usageAccessGranted -> StatusBanner("Conceda Acesso de uso nas configurações do Android para consultar a semana.", BannerLevel.WARNING, title = "Acesso de uso necessário")
            week.days.none { it.available } -> StatusBanner("O Android não forneceu eventos para os últimos sete dias.", BannerLevel.WARNING, title = "Sem dados semanais")
            else -> {
                WeeklyBars(week)
                Spacer(Modifier.height(14.dp))
                WeeklyAppDistribution(week)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeeklyBars(week: WeekUsage) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tempo por dia", style = MaterialTheme.typography.titleMedium)
            Text("Últimos 7 dias locais · dias parciais podem variar", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                val maxMs = week.days.filter { it.available }.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
                week.days.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(if (day.available) formatDuration(day.totalMs) else "sem dados", color = if (day.available) Muted else Attention, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                        Spacer(Modifier.height(5.dp))
                        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
                            val heightPx = if (day.available) (day.totalMs.toFloat() / maxMs * size.height).coerceAtLeast(7.dp.toPx()) else 7.dp.toPx()
                            val barWidth = size.width * 0.72f
                            drawRoundRect(
                                color = if (day.available) Mint else Border,
                                topLeft = Offset((size.width - barWidth) / 2f, size.height - heightPx),
                                size = Size(barWidth, heightPx),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.forLanguageTag("pt-BR")).replaceFirstChar { it.uppercase() }, color = MainText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyAppDistribution(week: WeekUsage) {
    val context = LocalContext.current
    val totals = week.days.filter { it.available }.flatMap { it.byPackage.entries }
        .groupBy({ it.key }, { it.value }).mapValues { (_, durations) -> durations.sum() }
        .toList().sortedByDescending { it.second }
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Distribuição por app na semana", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (totals.isEmpty()) Text("Nenhum uso positivo identificado nos dias com dados.", color = Muted)
            else {
                val total = totals.sumOf { it.second }.coerceAtLeast(1L)
                totals.take(10).forEach { (packageName, duration) ->
                        val label = remember(packageName, context) { runCatching {
                        val info = context.packageManager.getApplicationInfo(packageName, 0)
                        context.packageManager.getApplicationLabel(info).toString()
                        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != packageName }
                            ?: packageName.substringAfterLast('.').split(Regex("[_-]+")).filter { it.isNotBlank() }
                                .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } } }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = MainText, maxLines = 1)
                        Text("${(duration * 100 / total)}% · ${formatDuration(duration)}", color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { duration.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Mint)
                }
            }
        }
    }
}

@Composable
internal fun LimitsRoute(
    modifier: Modifier = Modifier,
    settings: UserSettings,
    deviceSecure: Boolean,
    onSaveBudget: (Int, ReactionMode) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
    onDefinePin: () -> Unit,
) {
    var limitText by remember(settings.dailyLimitMinutes) { mutableStateOf(settings.dailyLimitMinutes.toString()) }
    var reaction by remember(settings.reactionMode) { mutableStateOf(settings.reactionMode) }
    var pendingConfirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val credentialAvailable = deviceSecure || settings.hasPin
    val limit = limitText.toIntOrNull()?.takeIf { it > 0 }
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Limites", subtitle = "Instagram + TikTok")
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Limite combinado diário", style = MaterialTheme.typography.titleMedium)
                Text("Conta o tempo com Instagram ou TikTok em primeiro plano e a tela ativa.", color = Muted)
                LimitSelector(listOf(15, 20, 30, 45, 60), limit ?: settings.dailyLimitMinutes, { limitText = it.toString() }, label = "Atalhos de limite")
                OutlinedTextField(
                    value = limitText, onValueChange = { if (it.all(Char::isDigit)) limitText = it },
                    label = { Text("Minutos (mínimo 1)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("daily-limit"),
                )
                if (limit == null) Text("Informe um limite maior que zero.", color = Exceeded)
                Text("Ao atingir o limite", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    ReactionMode.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected = reaction == option, role = Role.RadioButton, onClick = { reaction = option }).testTag("reaction-${option.name}"), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(reaction == option, onClick = null)
                            Column {
                                Text(if (option == ReactionMode.WARNING) "Apenas aviso discreto" else "Bloqueio com senha", color = MainText)
                                if (option == ReactionMode.LOCK) Text("Exige PIN ou credencial segura do aparelho.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (reaction == ReactionMode.LOCK && !credentialAvailable) {
                    StatusBanner("Defina um PIN ou credencial segura antes de ativar o bloqueio.", BannerLevel.WARNING, title = "Bloqueio indisponível", actionLabel = "Definir PIN", onAction = onDefinePin)
                }
                PrimaryAction(
                    label = "Salvar limite", modifier = Modifier.fillMaxWidth(), enabled = limit != null,
                    onClick = {
                        val minutes = limit ?: return@PrimaryAction
                        val save = { onSaveBudget(minutes, reaction) }
                        when {
                            minutes > settings.dailyLimitMinutes -> if (ProtectedChange.requiresAuthentication(ProtectedChange.IncreaseLimit, credentialAvailable)) onAuthenticate(save) else if (ProtectedChange.requiresConfirmation(ProtectedChange.IncreaseLimit, credentialAvailable)) pendingConfirmation = "Sem credencial configurada, o bloqueio por limite está indisponível. Deseja aumentar o limite?" to save else save()
                            settings.reactionMode == ReactionMode.LOCK && reaction == ReactionMode.WARNING -> if (ProtectedChange.requiresAuthentication(ProtectedChange.LockToWarning, credentialAvailable)) onAuthenticate(save) else if (ProtectedChange.requiresConfirmation(ProtectedChange.LockToWarning, credentialAvailable)) pendingConfirmation = "Sem credencial configurada, o bloqueio por limite está indisponível. Deseja trocar para aviso?" to save else save()
                            else -> save()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    pendingConfirmation?.let { (message, action) -> ConfirmationDialog(message, { pendingConfirmation = null }, { pendingConfirmation = null; action() }) }
}

@Composable
internal fun MoreScreen(
    modifier: Modifier = Modifier, settings: UserSettings, usageAvailable: Boolean, accessibilityEnabled: Boolean, deviceSecure: Boolean,
    onOpenUsageSettings: () -> Unit, onOpenAccessibilitySettings: () -> Unit, onOpenCredential: () -> Unit,
    onOpenPrivacy: () -> Unit, onOpenAbout: () -> Unit, onMonitoringChanged: (Boolean) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
) {
    var confirmation by remember { mutableStateOf(false) }
    val credentialAvailable = deviceSecure || settings.hasPin
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        ScreenHeader("Mais", subtitle = "Permissões, credencial e privacidade")
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Permissões do Android", style = MaterialTheme.typography.titleMedium)
                StyledPermissionRow("Acesso de uso", usageAvailable, onOpenUsageSettings, activeLabel = "Ativo", inactiveLabel = "Não concedido", actionLabel = "Abrir")
                StyledPermissionRow("Acessibilidade", accessibilityEnabled, onOpenAccessibilitySettings, activeLabel = "Ativa", inactiveLabel = "Não concedida", actionLabel = "Abrir")
                Text("O Android controla a Acessibilidade. O InScreenLock não pode concedê-la por você.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Monitoramento", style = MaterialTheme.typography.titleMedium)
                Text(if (settings.monitoringEnabled && usageAvailable && accessibilityEnabled) "Ativo" else "Desativado ou sem permissões", color = Muted)
                Switch(checked = settings.monitoringEnabled && usageAvailable && accessibilityEnabled, enabled = usageAvailable && accessibilityEnabled, onCheckedChange = { enabled ->
                    if (enabled) onMonitoringChanged(true)
                    else if (ProtectedChange.requiresAuthentication(ProtectedChange.DisableMonitoring, credentialAvailable)) onAuthenticate { onMonitoringChanged(false) }
                    else confirmation = true
                }, modifier = Modifier.testTag("monitoring-switch"))
            }
        }
        Spacer(Modifier.height(12.dp))
        MoreActionCard("Credencial de desbloqueio", if (settings.hasPin) "PIN configurado" else "PIN ou credencial do aparelho", onOpenCredential)
        MoreActionCard("Privacidade e dados", "Dados locais e controles", onOpenPrivacy)
        MoreActionCard("Sobre", "Versão instalada", onOpenAbout)
    }
    if (confirmation) ConfirmationDialog("Sem credencial configurada, o bloqueio por limite está indisponível. Deseja desativar o monitoramento?", { confirmation = false }, { confirmation = false; onMonitoringChanged(false) })
}

@Composable
private fun MoreActionCard(title: String, description: String, onClick: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = MainText, style = MaterialTheme.typography.titleSmall); Text(description, color = Muted, style = MaterialTheme.typography.bodySmall) }
            Text("›", color = Mint, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun CredentialRoute(modifier: Modifier = Modifier, settings: UserSettings, deviceSecure: Boolean, onBack: () -> Unit, onSetPin: (String) -> Unit, onRemovePin: () -> Unit, onAuthenticate: (() -> Unit) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val credentialAvailable = deviceSecure || settings.hasPin
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Credencial de desbloqueio", subtitle = "PIN local de 4 a 8 dígitos", trailingContent = {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Voltar") }
        })
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (settings.hasPin) "Altere o PIN" else if (deviceSecure) "A credencial segura do aparelho também pode desbloquear." else "Defina um PIN para habilitar bloqueio com senha.", color = Muted)
                OutlinedTextField(first, { if (it.length <= 8 && it.all(Char::isDigit)) first = it }, label = { Text(if (settings.hasPin) "Novo PIN (4–8 dígitos)" else "Definir PIN (4–8 dígitos)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("pin-first"))
                OutlinedTextField(second, { if (it.length <= 8 && it.all(Char::isDigit)) second = it }, label = { Text("Digite o PIN novamente") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("pin-second"))
                if (error.isNotBlank()) Text(error, color = Exceeded, modifier = Modifier.testTag("pin-error"))
                PrimaryAction("${if (settings.hasPin) "Alterar" else "Definir"} PIN", onClick = {
                    if (first.length !in 4..8) error = "Use de 4 a 8 dígitos."
                    else if (first != second) error = "Os PINs não coincidem."
                    else {
                        val pinToSave = first
                        val action = { onSetPin(pinToSave) }
                        if (settings.hasPin) onAuthenticate(action) else action()
                        first = ""; second = ""; error = ""
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("pin-save"), enabled = first.isNotEmpty())
                if (settings.hasPin) androidx.compose.material3.TextButton(onClick = {
                    val action = { onRemovePin() }
                    if (ProtectedChange.requiresAuthentication(ProtectedChange.ChangeOrRemovePin, credentialAvailable)) onAuthenticate(action)
                    else if (ProtectedChange.requiresConfirmation(ProtectedChange.ChangeOrRemovePin, credentialAvailable)) confirmation = "Deseja remover o PIN?" to action
                    else action()
                }, modifier = Modifier.fillMaxWidth().testTag("pin-remove")) { Text("Remover PIN") }
                PrimaryAction("Voltar", onBack, modifier = Modifier.fillMaxWidth(), secondary = true)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    confirmation?.let { (message, action) -> ConfirmationDialog(message, { confirmation = null }, { confirmation = null; action() }) }
}

@Composable
private fun PrivacyRoute(modifier: Modifier, settings: UserSettings, deviceSecure: Boolean, onBack: () -> Unit, onClear: () -> Unit, onAuthenticate: (() -> Unit) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val privacyText = "Com Acesso de uso, o InScreenLock lê o tempo em primeiro plano de todos os apps de hoje somente para mostrar o painel neste aparelho; nada sai do aparelho nem é salvo. Sem conta, rede ou anúncios. O serviço recebe notificações de mudança de janela de qualquer app e lê apenas o nome do pacote para reconhecer entradas e saídas do Instagram/TikTok; descarta os demais e não guarda esses dados. Guardamos preferências e o maior limite já avisado no dia; o tempo vem do histórico de uso do Android. O bloqueio cobre a janela inteira do app, inclusive login, e oferece Início e desbloqueio por credencial. Desativar Acessibilidade nas configurações do Android encerra o bloqueio; isso é intencional pela política do Android. Desinstalar apaga os dados do InScreenLock. O histórico de Acesso de uso é do Android e nunca é copiado pelo app. Este APK privado ainda não passou por revisão de acessibilidade da Google Play."
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Privacidade e dados", trailingContent = {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Voltar") }
        })
        SurfaceCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(privacyText, color = Muted); PrimaryAction("Apagar preferências locais", { showDialog = true }, Modifier.fillMaxWidth(), secondary = true); PrimaryAction("Voltar", onBack, Modifier.fillMaxWidth(), secondary = true) } }
        Spacer(Modifier.height(24.dp))
    }
    if (showDialog) AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Apagar preferências?") }, text = { Text("O consentimento e as configurações locais serão removidos.") }, confirmButton = { Button(onClick = { showDialog = false; val action = { onClear() }; if (ProtectedChange.requiresAuthentication(ProtectedChange.ClearData, deviceSecure || settings.hasPin)) onAuthenticate(action) else action() }) { Text("Apagar") } }, dismissButton = { OutlinedButton(onClick = { showDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun SimpleDetailRoute(title: String, modifier: Modifier, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(title, trailingContent = {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Voltar") }
        })
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        PrimaryAction("Voltar", onBack, Modifier.fillMaxWidth(), secondary = true)
    }
}

@Composable
private fun ConfirmationDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Confirmar alteração") }, text = { Text(message) }, confirmButton = { Button(onClick = onConfirm) { Text("Confirmar") } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun AppBreakdownCard(name: String, usedMs: Long?) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
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
internal fun InScreenLockTheme(content: @Composable () -> Unit) {
    DesignTheme(content)
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
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
