package com.andersonmoreira.papelzinho

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutomationTest {
    @Test
    fun `keyboard clipboard read needs nonpreview handoff and verified new editor`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp", SendRoute.CLIPBOARD)
        store.markKeyboardRead(session.token)
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertTrue(clicks.isEmpty())
        engine.onSnapshot("com.whatsapp", preview.copy(preview = false, active = true))
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertTrue(clicks.isEmpty())
        engine.onSnapshot("com.whatsapp.w4b", PreviewSnapshot())
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertTrue(clicks.isEmpty())
        engine.onSnapshot("com.whatsapp", PreviewSnapshot())
        engine.onSnapshot("com.whatsapp", preview)
        assertEquals(listOf(Action.TOGGLE), clicks)
        engine.onSnapshot("com.whatsapp", preview)
        assertFalse(clicks.contains(Action.SEND))
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertEquals(listOf(Action.TOGGLE, Action.SEND), clicks)
        assertNotNull(store.bytesFor(session.token))
        engine.onSnapshot("com.whatsapp", PreviewSnapshot())
        assertEquals(SessionState.DONE, session.state)
    }

    @Test
    fun `clipboard automation waits for target image read and still verifies before send`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp", SendRoute.CLIPBOARD)
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertTrue(clicks.isEmpty())
        assertEquals(SessionState.ARMED, session.state)
        store.markTargetRead("unknown")
        engine.onSnapshot("com.whatsapp", preview)
        assertTrue(clicks.isEmpty())
        store.markTargetRead(session.token)
        engine.onSnapshot("com.whatsapp", preview)
        assertEquals(listOf(Action.TOGGLE), clicks)
        engine.onSnapshot("com.whatsapp", preview)
        assertFalse(clicks.contains(Action.SEND))
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertEquals(listOf(Action.TOGGLE, Action.SEND), clicks)
        assertNotNull(store.bytesFor(session.token))
        engine.onSnapshot("com.whatsapp", PreviewSnapshot())
        assertEquals(SessionState.DONE, session.state)
        assertNull(store.bytesFor(session.token))
    }

    private var now = 0L
    private val store = SessionStore({ now }, {})
    private val clicks = mutableListOf<Action>()
    private val engine =
        ViewOnceAutomation(store, { now }, {
            clicks.add(it)
            true
        })
    private val preview = PreviewSnapshot(preview = true, toggle = true, send = true)

    @Test
    fun `unarmed and foreign package events cause no actions`() {
        engine.onSnapshot("com.whatsapp", preview)
        store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp.w4b", preview)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun `click alone never authorizes send`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview)
        assertEquals(listOf(Action.TOGGLE), clicks)
        now = 500
        engine.onSnapshot("com.whatsapp", preview)
        assertFalse(clicks.contains(Action.SEND))
        now = 10_000
        engine.tick()
        assertEquals(SessionState.ABORTED, session.state)
        assertNull(store.bytesFor(session.token))
    }

    @Test
    fun `verified send keeps bytes until preview exits`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview)
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertEquals(listOf(Action.TOGGLE, Action.SEND), clicks)
        assertEquals(SessionState.SENT, session.state)
        assertNotNull(store.bytesFor(session.token))
        engine.onSnapshot("com.whatsapp", PreviewSnapshot())
        assertEquals(SessionState.DONE, session.state)
        assertNull(store.bytesFor(session.token))
    }

    @Test
    fun `already active toggle is not switched off`() {
        store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertEquals(listOf(Action.SEND), clicks)
    }

    @Test
    fun `unknown dialog aborts without send`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview)
        engine.onSnapshot("com.whatsapp", preview.copy(unknownDialog = true))
        assertEquals(SessionState.ABORTED, session.state)
        assertFalse(clicks.contains(Action.SEND))
    }

    @Test
    fun `missing toggle times out without events`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview.copy(toggle = false))
        now = 5_000
        engine.tick()
        assertEquals(SessionState.ABORTED, session.state)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun `known dialog confirmation is followed by fresh verification`() {
        store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview)
        engine.onSnapshot("com.whatsapp", preview.copy(preview = false, knownDialog = true))
        assertEquals(listOf(Action.TOGGLE, Action.CONFIRM), clicks)
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        assertEquals(Action.SEND, clicks.last())
    }

    @Test
    fun `handoff timeout releases after eight seconds`() {
        val session = store.arm(byteArrayOf(1), "com.whatsapp")
        engine.onSnapshot("com.whatsapp", preview.copy(active = true))
        now = 7_999
        engine.tick()
        assertNotNull(store.bytesFor(session.token))
        now = 8_000
        engine.tick()
        assertEquals(SessionState.DONE, session.state)
    }
}
