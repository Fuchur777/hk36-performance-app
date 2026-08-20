package nl.schellenberg.hk36ttc.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class LoadGuardTest {

    @Test
    fun `runIfLoaded does not run before markLoaded`() {
        val guard = LoadGuard()
        var calls = 0

        guard.runIfLoaded { calls++ }

        assertEquals(0, calls)
    }

    @Test
    fun `runIfLoaded runs after markLoaded`() {
        val guard = LoadGuard()
        var calls = 0

        guard.markLoaded()
        guard.runIfLoaded { calls++ }
        guard.runIfLoaded { calls++ }

        assertEquals(2, calls)
    }
}
