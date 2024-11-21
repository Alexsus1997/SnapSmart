package com.patusmaximus.snapsmart.backend

import android.net.Uri
import android.provider.DocumentsContract

class Utilities
{
    fun decodeAndFormatUri(uri: String): String {
        return try {
            // Extract the document ID from the URI
            val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
            val split = documentId.split(":")
            val type = split[0]
            val path = split.getOrNull(1)

            // Format the URI into a readable file path
            if (type.equals("raw", ignoreCase = true) && path != null) {
                path // Raw path
            } else if (type.equals("primary", ignoreCase = true) && path != null) {
                "/storage/emulated/0/$path" // Primary storage path
            } else {
                uri // Fallback to original URI if not recognized
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri // Return the original URI in case of any error
        }
    }

}