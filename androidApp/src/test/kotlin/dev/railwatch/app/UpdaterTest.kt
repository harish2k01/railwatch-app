package dev.railwatch.app

import dev.railwatch.app.data.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesVersions() {
        assertTrue(Updater.newer("0.7.0", "0.6.0"))
        assertTrue(Updater.newer("v1.0.0", "0.9.9"))
        assertTrue(Updater.newer("0.6.10", "0.6.9"))
        assertTrue(Updater.newer("0.6.0", "0.0.0-dev"))
        assertFalse(Updater.newer("0.6.0", "0.6.0"))
        assertFalse(Updater.newer("0.5.1", "0.6.0"))
    }
}
