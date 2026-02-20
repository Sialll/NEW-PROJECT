package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileNameResolver {
    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
        return name ?: uri.lastPathSegment ?: "imported_file"
    }
}
