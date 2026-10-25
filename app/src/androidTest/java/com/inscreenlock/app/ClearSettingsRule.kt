package com.inscreenlock.app

import androidx.test.platform.app.InstrumentationRegistry
import com.inscreenlock.app.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ClearSettingsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            runBlocking {
                SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
                    .clearLocalSettings()
            }
            base.evaluate()
        }
    }
}
