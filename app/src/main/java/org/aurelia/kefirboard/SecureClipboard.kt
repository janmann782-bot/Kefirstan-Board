package org.aurelia.kefirboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Each clipboard entry is encrypted with AES-GCM in AndroidKeyStore.
 * SQLite has NO artificial maximum-record count; it is limited by device space.
 * No sensitive-clipboard capture in password fields.
 */
class SecureClipboard(context: Context) : SQLiteOpenHelper(context, "clips.db", null, 1) {
    data class Entry(val id: Long, val text: String, val pinned: Boolean)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kefirboard_clip_v1", null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                "kefirboard_clip_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE clips (id INTEGER PRIMARY KEY AUTOINCREMENT, digest TEXT NOT NULL UNIQUE, nonce BLOB NOT NULL, encrypted BLOB NOT NULL, pinned INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized fun add(text: String) {
        if (text.isBlank() || text.length > 1_000_000) return
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val values = android.content.ContentValues().apply {
            put("digest", digest)
            put("nonce", cipher.iv)
            put("encrypted", cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
            put("created", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("clips", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized fun recent(search: String = "", limit: Int = 50): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.rawQuery(
            "SELECT id, nonce, encrypted, pinned FROM clips ORDER BY pinned DESC, created DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, cursor.getBlob(1)))
                    val text = String(cipher.doFinal(cursor.getBlob(2)), Charsets.UTF_8)
                    if (text.contains(search, ignoreCase = true)) {
                        result += Entry(cursor.getLong(0), text, cursor.getInt(3) != 0)
                    }
                } catch (_: Exception) { /* invalid/corrupt entry is skipped */ }
            }
        }
        return result
    }

    @Synchronized fun pin(id: Long, pinned: Boolean) {
        writableDatabase.execSQL("UPDATE clips SET pinned = ? WHERE id = ?", arrayOf(if (pinned) 1 else 0, id))
    }

    @Synchronized fun delete(id: Long) {
        writableDatabase.delete("clips", "id=?", arrayOf(id.toString()))
    }
}
