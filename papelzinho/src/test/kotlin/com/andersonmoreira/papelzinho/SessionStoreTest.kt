package com.andersonmoreira.papelzinho

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionStoreTest {
    private var now = 0L
    private val revoked = mutableListOf<String>()
    private val store = SessionStore({ now }, { revoked.add(it) })

    @Test
    fun `release wipes original bytes and prevents reuse`() {
        val bytes = byteArrayOf(1, 2, 3)
        val session = store.arm(bytes, "com.whatsapp")
        assertArrayEquals(bytes, store.bytesFor(session.token))
        store.release(session.token, SessionState.ABORTED)
        assertArrayEquals(byteArrayOf(0, 0, 0), bytes)
        assertNull(store.bytesFor(session.token))
        assertFalse(store.markInProgress(session.token))
        assertEquals(SessionState.ABORTED, session.state)
    }

    @Test
    fun `armed expires at exact deadline`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        now = 119_999
        assertNotNull(store.bytesFor(session.token))
        now = 120_000
        assertNull(store.bytesFor(session.token))
        assertEquals(SessionState.EXPIRED, session.state)
    }

    @Test
    fun `replacement aborts old session and stale release preserves new session`() {
        val bytes = byteArrayOf(9)
        val old = store.arm(bytes, "com.whatsapp")
        val current = store.arm(byteArrayOf(8), "com.whatsapp.w4b")
        assertEquals(SessionState.ABORTED, old.state)
        assertEquals(0, bytes[0].toInt())
        store.release(old.token, SessionState.DONE)
        assertNotNull(store.bytesFor(current.token))
    }

    @Test
    fun `release is idempotent and send keeps bytes until handoff`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        assertTrue(store.markInProgress(session.token))
        assertTrue(store.markSent(session.token))
        now = 120_001
        assertNotNull(store.bytesFor(session.token))
        store.release(session.token, SessionState.DONE)
        store.release(session.token, SessionState.DONE)
        assertEquals(listOf(session.token), revoked)
    }

    @Test
    fun `cannot send before entering preview`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        assertFalse(store.markSent(session.token))
        assertNull(store.bytesFor("unknown"))
    }
}
