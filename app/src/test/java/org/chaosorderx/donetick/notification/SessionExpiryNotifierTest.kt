package org.chaosorderx.donetick.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionExpiryNotifierTest {

    @Test
    fun `warning fires exactly 5 days before expiry`() {
        val expiry = 1_800_000_000_000L
        assertEquals(expiry - TimeUnit.DAYS.toMillis(5), SessionExpiryNotifier.warnAt(expiry))
    }
}
