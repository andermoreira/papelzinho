package com.andersonmoreira.papelzinho

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.FileNotFoundException

class MemoryImageProvider : ContentProvider() {
    private val app get() = context!!.applicationContext as PapelzinhoApplication

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        val token = token(uri) ?: throw FileNotFoundException()
        return app.withImageBytes(token) { bytes ->
            val fd = Os.memfd_create("papelzinho", OsConstants.MFD_CLOEXEC)
            try {
                var offset = 0
                while (offset < bytes.size) {
                    val written = Os.write(fd, bytes, offset, bytes.size - offset)
                    check(written > 0)
                    offset += written
                }
                Os.lseek(fd, 0, OsConstants.SEEK_SET)
                ParcelFileDescriptor.dup(fd).also { recordTargetRead(token) }
            } finally {
                Os.close(fd)
            }
        } ?: throw FileNotFoundException()
    }

    private fun recordTargetRead(token: String) {
        val session = app.sessions.active()?.takeIf { it.token == token } ?: return
        val targetUid =
            try {
                app.packageManager.getApplicationInfo(session.targetPackage, 0).uid
            } catch (_: PackageManager.NameNotFoundException) {
                return
            }
        if (Binder.getCallingUid() == targetUid) app.sessions.markTargetRead(token)
        val caller = callerRole(Binder.getCallingUid(), targetUid)
        if (caller == "keyboard") app.sessions.markKeyboardRead(token)
        Log.i("Papelzinho", "provider=open route=${session.route} caller=$caller targetRead=${session.targetHasRead}")
    }

    private fun callerRole(
        callerUid: Int,
        targetUid: Int,
    ): String {
        val keyboardPackage =
            Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val packages = app.packageManager.getPackagesForUid(callerUid).orEmpty()
        return when {
            callerUid == targetUid -> "target"
            callerUid == Process.myUid() -> "self"
            "com.android.systemui" in packages -> "system_ui"
            keyboardPackage != null && keyboardPackage in packages -> "keyboard"
            else -> "other"
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val token = token(uri) ?: return cursor
        app.withImageBytes(token) { bytes ->
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> "$token.png"
                        OpenableColumns.SIZE -> bytes.size
                        else -> null
                    }
                },
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri = throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    private fun token(uri: Uri): String? {
        if (uri.scheme != "content" ||
            uri.authority != "${app.packageName}.images" ||
            uri.pathSegments.size != 1
        ) {
            return null
        }
        if (uri.query != null || uri.fragment != null) return null
        val path = uri.lastPathSegment ?: return null
        if (!path.endsWith(".png")) return null
        return path.removeSuffix(".png").takeIf {
            it.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
        }
    }
}
