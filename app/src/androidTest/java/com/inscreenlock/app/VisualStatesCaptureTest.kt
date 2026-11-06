package com.inscreenlock.app

import android.view.View
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.inscreenlock.app.ui.AnalysisScreen
import com.inscreenlock.app.ui.CredentialRoute
import com.inscreenlock.app.ui.DashboardScreen
import com.inscreenlock.app.ui.InScreenLockTheme
import com.inscreenlock.app.ui.LimitsRoute
import com.inscreenlock.app.ui.MoreScreen
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
        captureDashboard("home-normal-synthetic", BudgetLevel.NORMAL, UsageTotals(12 * MINUTE_MS))
        activityRule.onNodeWithText("Dentro do combinado").assertIsDisplayed()
        captureDashboard("home-attention-synthetic", BudgetLevel.ATTENTION, UsageTotals(25 * MINUTE_MS + 30_000))
        activityRule.onNodeWithText("Atenção: perto do limite").assertIsDisplayed()
        captureDashboard("home-exceeded-synthetic", BudgetLevel.EXCEEDED, UsageTotals(39 * MINUTE_MS))
        activityRule.onNodeWithText("Limite diário alcançado").assertIsDisplayed()
        captureDashboard("home-unavailable-synthetic", BudgetLevel.UNAVAILABLE, null, available = false)
        activityRule.onNodeWithText("Indisponível").assertIsDisplayed()

        val now = LocalDate.now()
        val withData = WeekUsage((6 downTo 0).map { daysAgo ->
            val available = daysAgo % 3 != 0
            DayUsage(now.minusDays(daysAgo.toLong()), available, if (available) (daysAgo + 1) * MINUTE_MS else 0L,
                if (available) mapOf("com.synthetic.fixture" to (daysAgo + 1) * MINUTE_MS) else emptyMap())
        })
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                AnalysisScreen(todayTotals = null, todayAvailable = false, week = withData, loading = false, failed = false, onRetry = {})
            } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithText("sem dados", substring = true).assertIsDisplayed()
        capture("analysis-week-synthetic")

        val unavailable = WeekUsage(withData.days.map { it.copy(available = false, totalMs = 0L, byPackage = emptyMap()) })
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                AnalysisScreen(todayTotals = null, todayAvailable = false, week = unavailable, loading = false, failed = false, onRetry = {})
            } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Sem dados semanais").assertIsDisplayed()
        capture("analysis-week-no-data-synthetic")

        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                LimitsRoute(settings = UserSettings(dailyLimitMinutes = 30, reactionMode = ReactionMode.LOCK), deviceSecure = true,
                    onSaveBudget = { _, _ -> }, onAuthenticate = { it() }, onDefinePin = {})
            } }
        }
        activityRule.waitForIdle()
        capture("limits-synthetic")

        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                MoreScreen(settings = UserSettings(), usageAvailable = false, accessibilityEnabled = false, deviceSecure = false,
                    onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onOpenCredential = {}, onOpenPrivacy = {},
                    onOpenAbout = {}, onMonitoringChanged = {}, onAuthenticate = { it() })
            } }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Não concedido").assertIsDisplayed()
        capture("more-permissions-denied-synthetic")

        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                CredentialRoute(settings = UserSettings(), deviceSecure = false, onBack = {}, onSetPin = {}, onRemovePin = {}, onAuthenticate = { it() })
            } }
        }
        activityRule.waitForIdle()
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
        activityRule.onNodeWithText("PIN incorreto").assertIsDisplayed()
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
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme {
                DashboardScreen(settings = UserSettings(dailyLimitMinutes = 30), totals = totals, appUsage = emptyList(),
                    budgetLevel = level, usageAvailable = available, accessibilityEnabled = available, deviceSecure = false,
                    onOpenSettings = {}, onMonitoringChanged = {}, onAuthenticate = { it() })
            } }
        }
        activityRule.waitForIdle()
        capture(name)
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
