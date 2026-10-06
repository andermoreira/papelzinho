package com.andersonmoreira.papelzinho

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class ProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun repeatedSeekableReadsAndRelease() {
        val app = context.applicationContext as PapelzinhoApplication
        val original = byteArrayOf(1, 2, 3, 4)
        val session = app.sessions.arm(original, "com.whatsapp")
        val uri = Uri.parse("content://${context.packageName}.images/${session.token}.png")
        repeat(2) {
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
                Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
                val read = ByteArray(4)
                assertEquals(4, Os.read(descriptor.fileDescriptor, read, 0, 4))
                assertArrayEquals(original, read)
                assertEquals(2, Os.lseek(descriptor.fileDescriptor, 2, OsConstants.SEEK_SET).toInt())
            }
        }
        context.contentResolver.query(uri, null, null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(4, it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)).toInt())
            assertEquals("${session.token}.png", it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
        }
        app.sessions.release(session.token, SessionState.DONE)
        assertArrayEquals(ByteArray(4), original)
        assertThrows(FileNotFoundException::class.java) { context.contentResolver.openFileDescriptor(uri, "r") }
        context.contentResolver.query(uri, null, null, null, null)!!.use { assertEquals(0, it.count) }
    }

    @Test
    fun rejectsUnknownTokenAndAllWriteModes() {
        val uri = Uri.parse("content://${context.packageName}.images/unknown.png")
        assertThrows(FileNotFoundException::class.java) { context.contentResolver.openFileDescriptor(uri, "r") }
        for (mode in listOf("w", "rw", "rwt", "wa")) {
            assertThrows(SecurityException::class.java) { context.contentResolver.openFileDescriptor(uri, mode) }
        }
    }
}
