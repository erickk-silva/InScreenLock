package com.inscreenlock.app

import android.view.View
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.DayUsage
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.domain.WeekUsage
import com.inscreenlock.app.monitoring.LockOverlayContent
import com.inscreenlock.app.monitoring.AppUsageEntry
import com.inscreenlock.app.ui.AnalysisScreen
import com.inscreenlock.app.ui.CredentialRoute
import com.inscreenlock.app.ui.DashboardScreen
import com.inscreenlock.app.ui.InScreenLockTheme
import com.inscreenlock.app.ui.OnboardingScreen
import com.inscreenlock.app.ui.SplashLoadingScreen
import com.inscreenlock.app.ui.LimitsRoute
import com.inscreenlock.app.ui.MoreScreen
import com.inscreenlock.app.ui.components.AppBarItem
import com.inscreenlock.app.ui.components.AppScaffold
import com.inscreenlock.app.UnlockScreen
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualStatesCaptureTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)

    @Test fun capturesCompleteRedesignScreensUsingClearlySyntheticFixtureData() {
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme { SplashLoadingScreen(Modifier.fillMaxSize()) } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Mais foco, menos rolagem.", substring = false).assertIsDisplayed()
        capture("splash-loading")
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme { OnboardingScreen(onSkip = {}, onStart = {}) } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("onboarding-start").assertIsDisplayed()
        capture("onboarding")

        captureDashboard("home-normal-synthetic", BudgetLevel.NORMAL, UsageTotals(12 * MINUTE_MS))
        activityRule.onNodeWithText("Dentro do combinado", substring = false).assertIsDisplayed()
        captureDashboard("home-attention-synthetic", BudgetLevel.ATTENTION, UsageTotals(25 * MINUTE_MS + 30_000))
        activityRule.onNodeWithText("Atenção: perto do limite", substring = false).assertIsDisplayed()
        captureDashboard("home-exceeded-synthetic", BudgetLevel.EXCEEDED, UsageTotals(39 * MINUTE_MS))
        activityRule.onNodeWithText("Limite diário alcançado", substring = false).assertIsDisplayed()
        captureDashboard("home-unavailable-synthetic", BudgetLevel.UNAVAILABLE, null, available = false)
        activityRule.onAllNodesWithText("Indisponível", substring = false).assertCountEquals(2)
        activityRule.onAllNodesWithText("Indisponível", substring = false)[0].assertIsDisplayed()

        val now = LocalDate.now()
        val withData = WeekUsage((6 downTo 0).map { daysAgo ->
            val available = daysAgo % 3 != 0
            DayUsage(now.minusDays(daysAgo.toLong()), available, if (available) (daysAgo + 1) * MINUTE_MS else 0L,
                if (available) mapOf("com.synthetic.focus" to (daysAgo + 1) * MINUTE_MS) else emptyMap())
        })
        showOnScaffold("ANALYSIS") { insets ->
            AnalysisScreen(modifier = Modifier.padding(insets), todayTotals = null, todayAvailable = false, week = withData, loading = false, failed = false, onRetry = {})
        }
        activityRule.onNodeWithText("Tempo por dia", substring = false).assertIsDisplayed()
        activityRule.onAllNodesWithText("sem dados", substring = false).assertCountEquals(3)
        activityRule.onNodeWithText("Distribuição por app na semana", substring = false).assertIsDisplayed()
        activityRule.onNodeWithText("Focus", substring = false).assertIsDisplayed()
        activityRule.onAllNodesWithText("sem dados", substring = false)[0].assertIsDisplayed()
        capture("analysis-week-synthetic")

        val unavailable = WeekUsage(withData.days.map { it.copy(available = false, totalMs = 0L, byPackage = emptyMap()) })
        showOnScaffold("ANALYSIS") { insets ->
            AnalysisScreen(modifier = Modifier.padding(insets), todayTotals = null, todayAvailable = false, week = unavailable, loading = false, failed = false, onRetry = {})
        }
        activityRule.onNodeWithText("Sem dados semanais", substring = false).assertIsDisplayed()
        capture("analysis-week-no-data-synthetic")

        showOnScaffold("LIMITS") { insets ->
                LimitsRoute(modifier = Modifier.padding(insets), settings = UserSettings(dailyLimitMinutes = 30, reactionMode = ReactionMode.LOCK), deviceSecure = true,
                    onSaveBudget = { _, _ -> }, onAuthenticate = { it() }, onDefinePin = {})
        }
        activityRule.onNodeWithText("Limite combinado diário", substring = false).assertIsDisplayed()
        activityRule.onNodeWithText("Apenas aviso discreto", substring = false).assertIsDisplayed()
        capture("limits-synthetic")

        showOnScaffold("MORE") { insets ->
                MoreScreen(modifier = Modifier.padding(insets), settings = UserSettings(), usageAvailable = false, accessibilityEnabled = false, deviceSecure = false,
                    onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onOpenCredential = {}, onOpenPrivacy = {},
                    onOpenAbout = {}, onMonitoringChanged = {}, onAuthenticate = { it() })
        }
        activityRule.onNodeWithText("Não concedido", substring = false).assertIsDisplayed()
        capture("more-permissions-denied-synthetic")

        showOnScaffold("MORE") { insets ->
            CredentialRoute(modifier = Modifier.padding(insets), settings = UserSettings(), deviceSecure = false, onBack = {}, onSetPin = {}, onRemovePin = {}, onAuthenticate = { it() })
        }
        activityRule.onNodeWithText("Defina um PIN para habilitar bloqueio com senha.", substring = false).assertIsDisplayed()
        capture("pin-setup-synthetic")

        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                UnlockScreen(hasPin = true, canUseDeviceCredential = true, confirmingChange = false,
                    onDigit = { value, digit -> value + digit }, onDelete = { it.dropLast(1) },
                    onPinSubmit = { _, showError -> showError("PIN incorreto") }, onDeviceCredential = {}, onCancel = {})
            } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("pin-digit-1").performClick()
        activityRule.onNodeWithTag("pin-digit-2").performClick()
        activityRule.onNodeWithTag("pin-digit-3").performClick()
        activityRule.onNodeWithTag("pin-digit-4").performClick()
        activityRule.onNodeWithTag("pin-submit").performClick()
        activityRule.onNodeWithText("PIN incorreto", substring = false).assertIsDisplayed()
        capture("unlock-pin-error-synthetic")

        lateinit var lockPanel: View
        activityRule.activity.runOnUiThread {
            lockPanel = LockOverlayContent.create(activityRule.activity, "Limite atingido", "39 min usados de 30 min hoje.",
                "Ir para o início", "Desbloquear", {}, {})
            activityRule.activity.setContent { InScreenLockTheme {
                AndroidView(factory = { lockPanel }, modifier = Modifier.fillMaxSize().testTag("visual-lock"))
            } }
        }
        activityRule.waitForIdle()
        assertTrue(lockPanel.texts().containsAll(listOf("Limite atingido", "39 min usados de 30 min hoje.", "Ir para o início", "Desbloquear")))
        activityRule.onNodeWithTag("visual-lock").assertIsDisplayed()
        capture("lock-overlay-synthetic")
    }

    private fun captureDashboard(name: String, level: BudgetLevel, totals: UsageTotals?, available: Boolean = true) {
        val appRows: List<AppUsageEntry>? = if (available) buildList<AppUsageEntry> {
            totals?.instagramMs?.takeIf { it > 0 }?.let { add(AppUsageEntry("com.instagram.android", "Instagram (sintético)", it, null)) }
            totals?.tiktokMs?.takeIf { it > 0 }?.let { add(AppUsageEntry("com.zhiliaoapp.musically", "TikTok (sintético)", it, null)) }
        } else null
        showOnScaffold("HOME") { insets ->
                DashboardScreen(modifier = Modifier.padding(insets), settings = UserSettings(dailyLimitMinutes = 30), totals = totals, appUsage = appRows,
                    budgetLevel = level, usageAvailable = available, accessibilityEnabled = available, deviceSecure = false,
                    onOpenSettings = {}, onMonitoringChanged = {}, onAuthenticate = { it() })
        }
        capture(name)
    }

    private fun showOnScaffold(route: String, screen: @Composable (PaddingValues) -> Unit) {
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                InScreenLockTheme {
                    val items = listOf(
                        AppBarItem("Início", "Início", route == "HOME", {}, { Icon(painterResource(R.drawable.ic_nav_home), null) }),
                        AppBarItem("Análises", "Análises", route == "ANALYSIS", {}, { Icon(painterResource(R.drawable.ic_nav_analysis), null) }),
                        AppBarItem("Limites", "Limites", route == "LIMITS", {}, { Icon(painterResource(R.drawable.ic_nav_limits), null) }),
                        AppBarItem("Mais", "Mais", route == "MORE", {}, { Icon(painterResource(R.drawable.ic_nav_more), null) }),
                    )
                    AppScaffold(bottomBarItems = items, modifier = Modifier.fillMaxSize(), content = screen)
                }
            }
        }
        activityRule.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual"))
        directory.mkdirs()
        val file = File(directory, "$name.png")
        FileOutputStream(file).use { stream ->
            activityRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue(file.length() > 0)
    }

    private fun View.texts(): List<String> = buildList {
        if (this@texts is TextView) add(this@texts.text.toString())
        if (this@texts is android.view.ViewGroup) for (index in 0 until this@texts.childCount) addAll(this@texts.getChildAt(index).texts())
    }

    private companion object { const val MINUTE_MS = 60_000L }
}
