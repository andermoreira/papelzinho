package com.andersonmoreira.papelzinho

import java.util.UUID

enum class SessionState { ARMED, IN_PROGRESS, SENT, DONE, ABORTED, EXPIRED }

enum class SendRoute { SHARE, CLIPBOARD }

class SendSession internal constructor(
    val token: String,
    val targetPackage: String,
    val createdAt: Long,
    internal var bytes: ByteArray?,
    val route: SendRoute,
) {
    var targetHasRead = false
        internal set
    var keyboardHasRead = false
        internal set
    var state = SessionState.ARMED
        internal set
}

class SessionStore(
    private val clock: () -> Long,
    private val revoke: (String) -> Unit,
) {
    private var session: SendSession? = null

    @Synchronized
    fun arm(
        bytes: ByteArray,
        targetPackage: String,
        route: SendRoute = SendRoute.SHARE,
    ): SendSession {
        require(targetPackage in setOf("com.whatsapp", "com.whatsapp.w4b"))
        require(bytes.isNotEmpty())
        session?.let { release(it.token, SessionState.ABORTED) }
        return SendSession(UUID.randomUUID().toString(), targetPackage, clock(), bytes, route).also { session = it }
    }

    @Synchronized
    fun active(): SendSession? {
        val current = session ?: return null
        if (current.state == SessionState.ARMED && clock() - current.createdAt >= SessionTiming.ARMED_TTL_MS) {
            release(current.token, SessionState.EXPIRED)
            return null
        }
        return current
    }

    @Synchronized
    fun bytesFor(token: String): ByteArray? = active()?.takeIf { it.token == token }?.bytes

    // Serializes descriptor creation with release, without making a plaintext copy.
    @Synchronized
    fun <T> withBytes(
        token: String,
        block: (ByteArray) -> T,
    ): T? = bytesFor(token)?.let(block)

    @Synchronized
    fun markInProgress(token: String): Boolean = transition(token, SessionState.ARMED, SessionState.IN_PROGRESS)

    @Synchronized
    fun markSent(token: String): Boolean = transition(token, SessionState.IN_PROGRESS, SessionState.SENT)

    @Synchronized
    fun markTargetRead(token: String) {
        active()?.takeIf { it.token == token }?.targetHasRead = true
    }

    @Synchronized
    fun markKeyboardRead(token: String) {
        active()?.takeIf { it.token == token && it.route == SendRoute.CLIPBOARD }?.keyboardHasRead = true
    }

    private fun transition(
        token: String,
        expected: SessionState,
        next: SessionState,
    ): Boolean {
        val current = active() ?: return false
        if (current.token != token || current.state != expected) return false
        current.state = next
        return true
    }

    @Synchronized
    fun release(
        token: String,
        terminal: SessionState,
    ) {
        require(terminal in setOf(SessionState.DONE, SessionState.ABORTED, SessionState.EXPIRED))
        val current = session?.takeIf { it.token == token } ?: return
        current.bytes?.fill(0)
        current.bytes = null
        current.state = terminal
        session = null
        revoke(token)
    }
}
