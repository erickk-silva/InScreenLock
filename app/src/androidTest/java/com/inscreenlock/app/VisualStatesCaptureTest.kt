package com.inscreenlock.app

import android.view.View
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.monitoring.AppUsageEntry
import com.inscreenlock.app.monitoring.LockOverlayContent
import com.inscreenlock.app.ui.AppUsageCard
import com.inscreenlock.app.ui.DashboardBudgetCard
import com.inscreenlock.app.ui.InScreenLockTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualStatesCaptureTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)

    @Test fun capturesSyntheticDashboardAppListAndLockStates() {
        val cases = listOf(
            Triple("dashboard-normal-40", UsageTotals(12 * MINUTE_MS), BudgetLevel.NORMAL to "Dentro do combinado"),
            Triple("dashboard-attention-85", UsageTotals(25 * MINUTE_MS + 30_000), BudgetLevel.ATTENTION to "Atenção: perto do limite"),
            Triple("dashboard-exceeded-130", UsageTotals(39 * MINUTE_MS), BudgetLevel.EXCEEDED to "Limite diário alcançado"),
        )
        cases.forEach { (filename, totals, expected) ->
            activityRule.activity.runOnUiThread {
                activityRule.activity.setContent {
                    InScreenLockTheme {
                        DashboardBudgetCard(UserSettings(dailyLimitMinutes = 30), totals, expected.first)
                    }
                }
            }
            activityRule.waitForIdle()
            activityRule.onNodeWithText(expected.second).assertIsDisplayed()
            if (expected.first == BudgetLevel.EXCEEDED) {
                activityRule.onNodeWithText("9 min acima da meta").assertIsDisplayed()
            }
            capture(filename)
        }

        val apps = listOf(
            AppUsageEntry("synthetic.one", "App sintético 1", 12 * MINUTE_MS, null),
            AppUsageEntry("synthetic.two", "App sintético 2", 8 * MINUTE_MS, null),
            AppUsageEntry("synthetic.three", "App sintético 3", 5 * MINUTE_MS, null),
        )
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockTheme { AppUsageCard(apps) } }
        }
        activityRule.waitForIdle()
        apps.forEach { activityRule.onNodeWithText(it.label).assertIsDisplayed() }
        capture("apps-three-synthetic")

        lateinit var lockPanel: View
        activityRule.activity.runOnUiThread {
            lockPanel = LockOverlayContent.create(
                activityRule.activity,
                title = "Limite diário alcançado",
                summary = "39 min usados de 30 min",
                homeLabel = "Ir para início",
                unlockLabel = "Desbloquear",
                onHome = {},
                onUnlock = {},
            )
            activityRule.activity.setContent {
                InScreenLockTheme {
                    AndroidView(
                        factory = { lockPanel },
                        modifier = Modifier.fillMaxWidth().testTag("visual-lock"),
                    )
                }
            }
        }
        activityRule.waitForIdle()
        assertTrue(lockPanel.texts().containsAll(listOf("Limite diário alcançado", "39 min usados de 30 min", "Ir para início", "Desbloquear")))
        activityRule.onNodeWithTag("visual-lock").assertIsDisplayed()
        capture("lock-overlay-synthetic")
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
        if (this@texts is android.view.ViewGroup) {
            for (index in 0 until this@texts.childCount) addAll(this@texts.getChildAt(index).texts())
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
