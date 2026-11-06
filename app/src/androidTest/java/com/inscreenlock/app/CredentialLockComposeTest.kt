package com.inscreenlock.app

import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.ui.DashboardScreen
import com.inscreenlock.app.ui.CredentialRoute
import com.inscreenlock.app.ui.LimitsRoute
import com.inscreenlock.app.ui.MoreScreen
import com.inscreenlock.app.domain.BudgetLevel
import org.junit.Assert.assertEquals
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
        val pinDisplay = compose.onNodeWithTag("pin-display").fetchSemanticsNode().config
        assertTrue(SemanticsProperties.ContentDescription in pinDisplay)
        assertTrue(pinDisplay[SemanticsProperties.ContentDescription].joinToString().contains("2 de 8"))
        compose.onNodeWithTag("pin-digit-4").performClick()
        compose.onNodeWithTag("pin-digit-5").performClick()
        compose.onNodeWithTag("pin-submit").performClick()
        compose.onNodeWithTag("pin-error").assertIsDisplayed().assertTextContains("PIN incorreto")
    }

    @Test fun dashboardDisableWithoutCredentialShowsConfirmationBeforeChangingMonitoring() {
        val disabledCount = AtomicInteger()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    DashboardScreen(
                        settings = UserSettings(monitoringEnabled = true, hasPin = false),
                        totals = null,
                        appUsage = null,
                        budgetLevel = BudgetLevel.UNAVAILABLE,
                        usageAvailable = true,
                        accessibilityEnabled = true,
                        deviceSecure = false,
                        onOpenSettings = {},
                        onMonitoringChanged = { if (!it) disabledCount.incrementAndGet() },
                        onAuthenticate = { it() },
                    )
                }
            }
        }

        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        assertEquals(0, disabledCount.get())
        compose.onNodeWithText("Confirmar").performClick()
        compose.waitForIdle()
        assertEquals(1, disabledCount.get())
    }

    @Test fun dashboardDisableWithCredentialRequiresAuthentication() {
        val authenticationCount = AtomicInteger()
        val disabledCount = AtomicInteger()
        val pending = AtomicReference<(() -> Unit)?>(null)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    DashboardScreen(
                        settings = UserSettings(monitoringEnabled = true, hasPin = false),
                        totals = null,
                        appUsage = null,
                        budgetLevel = BudgetLevel.UNAVAILABLE,
                        usageAvailable = true,
                        accessibilityEnabled = true,
                        deviceSecure = true,
                        onOpenSettings = {},
                        onMonitoringChanged = { if (!it) disabledCount.incrementAndGet() },
                        onAuthenticate = { action -> authenticationCount.incrementAndGet(); pending.set(action) },
                    )
                }
            }
        }

        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        assertEquals(1, authenticationCount.get())
        assertEquals(0, disabledCount.get())
        pending.get()?.invoke()
        compose.waitForIdle()
        assertEquals(1, disabledCount.get())
    }

    @Test fun disablingMonitoringWithoutCredentialUsesConfirmationInsteadOfAuthentication() {
        val authenticationCount = AtomicInteger()
        val disabledCount = AtomicInteger()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    MoreScreen(settings = UserSettings(monitoringEnabled = true), usageAvailable = true,
                        accessibilityEnabled = true, deviceSecure = false,
                        onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onOpenCredential = {},
                        onOpenPrivacy = {}, onOpenAbout = {},
                        onMonitoringChanged = { if (!it) disabledCount.incrementAndGet() },
                        onAuthenticate = { authenticationCount.incrementAndGet(); it() })
                }
            }
        }
        compose.onNodeWithTag("monitoring-switch").performClick()
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        assertEquals(0, authenticationCount.get())
        assertEquals(0, disabledCount.get())
        compose.onNodeWithText("Confirmar").performClick()
        compose.waitForIdle()
        assertEquals(0, authenticationCount.get())
        assertEquals(1, disabledCount.get())
    }

    @Test fun reactionSelectionOffersLockAndWarning() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    LimitsRoute(settings = UserSettings(), deviceSecure = true, onSaveBudget = { _, _ -> },
                        onAuthenticate = { it() }, onDefinePin = {})
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
                    CredentialRoute(settings = UserSettings(), deviceSecure = false, onBack = {},
                        onSetPin = { savedPin.set(it) }, onRemovePin = {}, onAuthenticate = { it() })
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
                        LimitsRoute(settings = UserSettings(), deviceSecure = true, onSaveBudget = { _, _ -> },
                            onAuthenticate = { it() }, onDefinePin = {})
                }
            }
        }
        compose.onNodeWithTag("reaction-LOCK").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("reaction-WARNING").assertHeightIsAtLeast(48.dp)
    }
}
