package com.inscreenlock.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.ui.InScreenLockApp
import com.inscreenlock.app.ui.InScreenLockTheme
import com.inscreenlock.app.ui.LimitsRoute
import com.inscreenlock.app.ui.MoreScreen
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedesignNavigationComposeTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)

    @Test fun tabsNavigateAndSystemBackReturnsToHome() {
        val repository = SettingsRepository(activityRule.activity.applicationContext)
        runBlocking { repository.acceptAccessibilityDisclosure() }
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockApp(repository) }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("screen-title").assertTextEquals("Hoje")

        activityRule.onAllNodesWithText("Análises").get(0).performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Uso real no aparelho").assertIsDisplayed()

        activityRule.onAllNodesWithText("Limites").get(0).performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Limite combinado diário").assertIsDisplayed()

        activityRule.onAllNodesWithText("Mais").get(0).performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Permissões do Android").assertIsDisplayed()
        activityRule.onNodeWithText("Acesso de uso").assertIsDisplayed()
        activityRule.onNodeWithText("Acessibilidade").assertIsDisplayed()

        activityRule.activity.onBackPressedDispatcher.onBackPressed()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("screen-title").assertTextEquals("Hoje")
    }

    @Test fun detailRoutesReturnUsingTopBackButtonAndSystemBack() {
        val repository = SettingsRepository(activityRule.activity.applicationContext)
        runBlocking { repository.acceptAccessibilityDisclosure() }
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent { InScreenLockApp(repository) }
        }
        activityRule.waitForIdle()
        activityRule.onAllNodesWithText("Mais").get(0).performClick()
        activityRule.onNodeWithText("Privacidade e dados").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Uso medido").assertIsDisplayed()
        activityRule.onNodeWithText("tempo em primeiro plano por app", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("nome interno de cada tela (atividade) dos apps", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("Esse nome é usado só em memória para não contar o mesmo tempo duas vezes", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("períodos com a tela ligada ou desligada", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("eventos de bloqueio de tela", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("limite diário combinado de Instagram e TikTok", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("painel de hoje e os últimos 7 dias", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("O histórico continua no Android", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("apenas em memória", substring = true).assertIsDisplayed()
        activityRule.onNodeWithText("sem enviá-los nem salvá-los no aparelho", substring = true).assertIsDisplayed()
        activityRule.onNodeWithContentDescription("Voltar").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Permissões do Android").assertIsDisplayed()

        activityRule.onNodeWithText("Credencial de desbloqueio").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("detail-back").assertIsDisplayed()
        activityRule.activity.onBackPressedDispatcher.onBackPressed()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Permissões do Android").assertIsDisplayed()
    }

    @Test fun limitIncreaseStillWaitsForAuthenticationBeforeSaving() {
        val authCalls = AtomicInteger()
        val saved = AtomicInteger(30)
        val pending = AtomicReference<(() -> Unit)?>(null)
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                InScreenLockTheme {
                    LimitsRoute(settings = UserSettings(dailyLimitMinutes = 30, reactionMode = ReactionMode.LOCK), deviceSecure = true,
                        onSaveBudget = { minutes, _ -> saved.set(minutes) },
                        onAuthenticate = { action -> authCalls.incrementAndGet(); pending.set(action) }, onDefinePin = {})
                }
            }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("daily-limit").performScrollTo().performTextReplacement("45")
        activityRule.onNodeWithText("Salvar limite").performScrollTo().performClick()
        activityRule.waitForIdle()
        assertEquals(1, authCalls.get())
        assertEquals(30, saved.get())
        assertNotNull(pending.get())
        pending.get()?.invoke()
        assertEquals(45, saved.get())
    }

    @Test fun permissionRowsCommunicateRealGrantedAndMissingStates() {
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                InScreenLockTheme {
                    MoreScreen(settings = UserSettings(), usageAvailable = false, accessibilityEnabled = true, deviceSecure = false,
                        onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onOpenCredential = {}, onOpenPrivacy = {},
                        onOpenAbout = {}, onMonitoringChanged = {}, onAuthenticate = { it() })
                }
            }
        }
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Não concedido").assertIsDisplayed()
        activityRule.onNodeWithText("Ativa").assertIsDisplayed()
        activityRule.onNodeWithText("Permissões do Android").assertIsDisplayed()
    }

    @Test fun disablingMonitoringFromMoreStillRequiresAuthenticationWhenCredentialExists() {
        val authCalls = AtomicInteger()
        val disabled = AtomicInteger()
        val pending = AtomicReference<(() -> Unit)?>(null)
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                InScreenLockTheme {
                    MoreScreen(settings = UserSettings(monitoringEnabled = true), usageAvailable = true,
                        accessibilityEnabled = true, deviceSecure = true,
                        onOpenUsageSettings = {}, onOpenAccessibilitySettings = {}, onOpenCredential = {},
                        onOpenPrivacy = {}, onOpenAbout = {}, onMonitoringChanged = { if (!it) disabled.incrementAndGet() },
                        onAuthenticate = { action -> authCalls.incrementAndGet(); pending.set(action) })
                }
            }
        }
        activityRule.onNodeWithTag("monitoring-switch").performClick()
        activityRule.waitForIdle()
        assertEquals(1, authCalls.get())
        assertEquals(0, disabled.get())
        pending.get()?.invoke()
        assertEquals(1, disabled.get())
    }
}
