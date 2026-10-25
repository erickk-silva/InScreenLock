package com.inscreenlock.app

import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CredentialLockComposeTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)
    private val compose get() = activityRule

    @Test fun pinKeypadAcceptsDigitsErasesAndShowsRejectedPin() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    UnlockScreen(
                        hasPin = true,
                        canUseDeviceCredential = false,
                        confirmingChange = false,
                        onDigit = { value, digit -> if (value.length < 8) value + digit else value },
                        onDelete = { it.dropLast(1) },
                        onPinSubmit = { _, showError -> showError("PIN incorreto") },
                        onDeviceCredential = {},
                        onCancel = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("pin-digit-1").performClick()
        compose.onNodeWithTag("pin-digit-2").performClick()
        compose.onNodeWithTag("pin-delete").performClick()
        compose.onNodeWithTag("pin-digit-3").performClick()
        compose.onNodeWithTag("pin-display").assertTextContains("••")
        compose.onNodeWithTag("pin-digit-4").performClick()
        compose.onNodeWithTag("pin-digit-5").performClick()
        compose.onNodeWithTag("pin-submit").performClick()
        compose.onNodeWithTag("pin-error").assertIsDisplayed().assertTextContains("PIN incorreto")
    }

    @Test fun disablingMonitoringAndIncreasingLimitRequireAuthentication() {
        val authenticationCount = AtomicInteger()
        val pending = AtomicReference<(() -> Unit)?>(null)
        val savedLimit = AtomicInteger(30)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    SettingsScreen(
                        settings = UserSettings(monitoringEnabled = true, dailyLimitMinutes = 30, reactionMode = ReactionMode.LOCK),
                        usageAvailable = true,
                        accessibilityEnabled = true,
                        deviceSecure = true,
                        onBack = {}, onOpenUsageSettings = {}, onOpenAccessibilitySettings = {},
                        onMonitoringChanged = {},
                        onSaveBudget = { minutes, _ -> savedLimit.set(minutes) },
                        onClearLocalData = {}, onSetPin = {}, onRemovePin = { _ -> },
                        onAuthenticate = { action -> authenticationCount.incrementAndGet(); pending.set(action) },
                    )
                }
            }
        }
        compose.waitForIdle()
        val disableMonitoring = compose.onNodeWithTag("disable-monitoring")
        disableMonitoring.performScrollTo()
        compose.waitForIdle()
        disableMonitoring.performClick()
        compose.waitForIdle()
        assertEquals(1, authenticationCount.get())
        val dailyLimit = compose.onNodeWithTag("daily-limit")
        dailyLimit.performScrollTo()
        compose.waitForIdle()
        dailyLimit.performTextReplacement("45")
        compose.waitForIdle()
        dailyLimit.assertTextContains("45")
        val saveBudget = compose.onNodeWithTag("save-budget")
        saveBudget.performScrollTo()
        compose.waitForIdle()
        saveBudget.assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(30, savedLimit.get())
        assertEquals(2, authenticationCount.get())
        assertNotNull(pending.get())
        pending.get()?.invoke()
        compose.waitForIdle()
        assertEquals(45, savedLimit.get())
    }

    @Test fun reactionSelectionOffersLockAndWarning() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    SettingsScreen(
                        settings = UserSettings(), usageAvailable = true, accessibilityEnabled = true, deviceSecure = true,
                        onBack = {}, onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onMonitoringChanged = {},
                        onSaveBudget = { _, _ -> }, onClearLocalData = {}, onSetPin = {}, onRemovePin = { },
                        onAuthenticate = { it() },
                    )
                }
            }
        }
        compose.onNodeWithTag("reaction-LOCK").assertIsDisplayed().performClick()
        compose.onNodeWithTag("reaction-WARNING").assertIsDisplayed().performClick()
        compose.onNodeWithText("Aviso discreto").assertIsDisplayed()
        compose.onNodeWithText("Bloqueio com senha").assertIsDisplayed()
    }

    @Test fun settingsPinFieldsMaskDigitsAndLimitInput() {
        val savedPin = AtomicReference("")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    SettingsScreen(
                        settings = UserSettings(), usageAvailable = true, accessibilityEnabled = true, deviceSecure = false,
                        onBack = {}, onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onMonitoringChanged = {},
                        onSaveBudget = { _, _ -> }, onClearLocalData = {}, onSetPin = { savedPin.set(it) }, onRemovePin = {},
                        onAuthenticate = { it() },
                    )
                }
            }
        }
        val pin = compose.onNodeWithTag("pin-first")
        pin.performScrollTo().performTextInput("12345678")
        val firstSemantics = pin.fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Password in firstSemantics)
        assertEquals("••••••••", firstSemantics[SemanticsProperties.EditableText].text)
        pin.performTextInput("9")
        compose.onNodeWithTag("pin-second").performScrollTo().performTextInput("12345678")
        val secondSemantics = compose.onNodeWithTag("pin-second").fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Password in secondSemantics)
        assertEquals("••••••••", secondSemantics[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("pin-save").performScrollTo().performClick()
        assertEquals("12345678", savedPin.get())
    }

    @Test fun reactionRowsHaveAtLeastFortyEightDpTouchTargets() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    SettingsScreen(
                        settings = UserSettings(), usageAvailable = true, accessibilityEnabled = true, deviceSecure = true,
                        onBack = {}, onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onMonitoringChanged = {},
                        onSaveBudget = { _, _ -> }, onClearLocalData = {}, onSetPin = {}, onRemovePin = {},
                        onAuthenticate = { it() },
                    )
                }
            }
        }
        compose.onNodeWithTag("reaction-LOCK").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("reaction-WARNING").assertHeightIsAtLeast(48.dp)
    }
}
