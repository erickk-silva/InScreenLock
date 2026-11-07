package com.inscreenlock.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.inscreenlock.app.ui.FirstLaunchFlow
import com.inscreenlock.app.ui.OnboardingScreen
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test

class ConsentScreenTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)
    private val composeRule get() = activityRule

    @Test
    fun firstLaunchDisclosesMeasurementAndRequiresAffirmativeConsent() {
        composeRule.onNodeWithTag("onboarding-start").performClick()
        composeRule.onNodeWithText("Seu uso, no seu ritmo").assertIsDisplayed()
        composeRule.onNodeWithText("inclui mensagens e outras áreas", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("tempo em primeiro plano por app", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("nome interno de cada tela (atividade) dos apps", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Esse nome é usado só em memória para não contar o mesmo tempo duas vezes", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("períodos com a tela ligada ou desligada", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("eventos de bloqueio de tela", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("limite diário combinado de Instagram e TikTok", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("painel de hoje e os últimos 7 dias", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("O histórico continua no Android", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("apenas em memória", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("sem enviá-los nem salvá-los no aparelho", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Não lemos nem armazenamos mensagens, legendas ou imagens."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continuar para acessibilidade").assertIsNotEnabled()
    }

    @Test
    fun affirmativeConsentLabelTogglesTheContinueAction() {
        composeRule.onNodeWithTag("onboarding-start").performClick()
        composeRule.onNodeWithTag("accessibility-consent").performScrollTo().performClick()
        composeRule.onNodeWithText("Continuar para acessibilidade").assertIsEnabled()
    }

    @Test
    fun firstLaunchStartOpensConsent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                com.inscreenlock.app.ui.InScreenLockTheme {
                    val accepted = remember { mutableStateOf(false) }
                    FirstLaunchFlow(consentAccepted = accepted.value, onAccept = { accepted.value = true })
                }
            }
        }
        composeRule.onNodeWithTag("onboarding-start").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Seu uso, no seu ritmo").assertIsDisplayed()
        composeRule.activity.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("onboarding-start").assertIsDisplayed()
    }

    @Test
    fun onboardingWithAnimationsDisabledShowsItsFinalContent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                com.inscreenlock.app.ui.InScreenLockTheme {
                    OnboardingScreen(onSkip = {}, onStart = {}, animationsEnabled = false)
                }
            }
        }
        composeRule.onNodeWithText("Controle seu tempo\nnas redes sociais").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-trust-card").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-start").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-skip").assertIsDisplayed()
    }

    @Test
    fun skipOpensConsentAndAcceptedConsentNeverReturnsToOnboarding() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                com.inscreenlock.app.ui.InScreenLockTheme {
                    val accepted = remember { mutableStateOf(false) }
                    FirstLaunchFlow(consentAccepted = accepted.value, onAccept = { accepted.value = true })
                }
            }
        }
        composeRule.onNodeWithText("Seus dados ficam neste aparelho", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Só as permissões necessárias", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Não lemos o que você vê ou digita", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Lemos só o tempo de uso e quais apps e telas estão abertos, sem conteúdo.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-skip").performClick()
        composeRule.onNodeWithText("Seu uso, no seu ritmo").assertIsDisplayed()
        composeRule.onNodeWithTag("accessibility-consent").performScrollTo().performClick()
        composeRule.onNodeWithTag("consent-continue").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("onboarding-start").assertCountEquals(0)
        composeRule.onAllNodesWithText("Seu uso, no seu ritmo").assertCountEquals(0)
    }
}
