package com.inscreenlock.app.data

import android.content.Context
import android.content.ContextWrapper
import com.inscreenlock.app.domain.ReactionMode
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryWarningTest {
    @Test
    fun warningRearmsOnlyForAHigherLimitOrNextDayAndTracksTheDailyMaximum() = runBlocking {
        val directory = Files.createTempDirectory("inscreenlock-settings-test").toFile()
        val repository = SettingsRepository(TestContext(directory))
        val day = LocalDate.of(2026, 10, 25)

        assertEquals(true, repository.claimThresholdWarning(day, 30))
        repository.updateBudget(30, ReactionMode.PAUSE)
        assertEquals(false, repository.claimThresholdWarning(day, 30))
        assertEquals(false, repository.claimThresholdWarning(day, 20))
        assertEquals(true, repository.claimThresholdWarning(day, 45))
        assertEquals(false, repository.claimThresholdWarning(day, 40))
        assertEquals(false, repository.claimThresholdWarning(day, 45))
        assertEquals(true, repository.claimThresholdWarning(day.plusDays(1), 20))
    }

    private class TestContext(directory: File) : ContextWrapper(null) {
        private val filesDirectory = directory

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesDirectory
    }
}
