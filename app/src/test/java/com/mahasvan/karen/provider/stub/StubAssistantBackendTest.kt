package com.mahasvan.karen.provider.stub

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The stateful contract every provider must keep: retry replays, and never before a turn. */
class StubAssistantBackendTest {

    @Test
    fun `retry is null before any turn and replays the last prompt after one`() = runBlocking {
        val backend = StubAssistantBackend()
        assertNull(backend.retry())

        val first = backend.respond("set a timer for 5 minutes").toList()
        val replay = backend.retry()!!.toList()

        assertEquals(first, replay)
    }
}
