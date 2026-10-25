package com.inscreenlock.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscreenlock.app.monitoring.AppUsageEntry
import com.inscreenlock.app.ui.AppUsageCard
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUsageCardTest {
    private val activityRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(ClearSettingsRule()).around(activityRule)

    @Test
    fun ordersAppsShowsTopTenAndExpandsInPlaceWithAccessibleDurations() {
        val apps = (1..11).map { index ->
            AppUsageEntry(
                packageName = "com.example.app$index",
                label = "App $index",
                durationMs = index * 60_000L,
                icon = null,
            )
        }
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                MaterialTheme { AppUsageCard(apps.reversed()) }
            }
        }

        activityRule.onNodeWithContentDescription("App 11, 11 min").assertIsDisplayed()
        activityRule.onAllNodesWithTag("app-usage-com.example.app1").assertCountEquals(0)
        activityRule.onNodeWithText("Ver todos").assertIsDisplayed().performClick()
        activityRule.onNodeWithContentDescription("App 1, 1 min").assertIsDisplayed()
        activityRule.onNodeWithText("Mostrar menos").assertIsDisplayed()
    }

    @Test
    fun unavailableUsageIsNotPresentedAsAnEmptyList() {
        activityRule.activity.runOnUiThread {
            activityRule.activity.setContent {
                MaterialTheme { AppUsageCard(apps = null) }
            }
        }

        activityRule.onNodeWithText("Indisponível").assertIsDisplayed()
        activityRule.onAllNodesWithTag("app-usage-toggle").assertCountEquals(0)
    }
}
