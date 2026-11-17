package com.example.androidwebsiteblocker.data

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class StateStoreTest {
    private fun set(i: Int) = BlockSet("s$i", "Set $i", BlockMode.HARD, setOf(Target.App("com.a")), WeeklySchedule(emptyList()), pinHash = "x")

    @Test fun `many quick updates - memory is immediate and disk ends with the newest`() {
        val file = File(Files.createTempDirectory("store").toFile(), "state.json")
        val store = StateStore(file)
        repeat(200) { i ->
            store.update { it.copy(sets = it.sets + set(i)) }
            assertEquals(i + 1, store.state.value.sets.size) // in-memory state never waits for disk
        }
        store.awaitWrites()
        assertEquals(store.state.value, StateStore(file).state.value)
        assertEquals(200, StateStore(file).state.value.sets.size)
    }

    @Test fun `no write when nothing changed`() {
        val file = File(Files.createTempDirectory("store").toFile(), "state.json")
        val store = StateStore(file)
        store.update { it }
        store.awaitWrites()
        assertEquals(false, file.exists())
    }
}
