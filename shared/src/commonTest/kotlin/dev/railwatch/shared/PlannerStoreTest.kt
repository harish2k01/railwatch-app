package dev.railwatch.shared

import dev.railwatch.shared.data.PlannerFiles
import dev.railwatch.shared.data.PlannerStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerStoreTest {
    private class Memory(var text: String?) : PlannerFiles {
        val kept = mutableListOf<String>()
        override fun read() = text
        override fun write(text: String) { this.text = text }
        override fun keepUnreadable(text: String) { kept += text }
    }

    @Test fun unreadableFileIsKeptBeforeAnythingReplacesIt() {
        val files = Memory("{ not json")
        val store = PlannerStore(files) { "2026-11-12" }
        assertTrue(store.recoveredFromUnreadable)
        assertEquals(listOf("{ not json"), files.kept)
        store.update { it }
        assertEquals(listOf("{ not json"), files.kept)
    }

    @Test fun firstRunIsNotTreatedAsUnreadable() {
        val files = Memory(null)
        val store = PlannerStore(files) { "2026-11-12" }
        assertTrue(!store.recoveredFromUnreadable && files.kept.isEmpty())
    }
}
