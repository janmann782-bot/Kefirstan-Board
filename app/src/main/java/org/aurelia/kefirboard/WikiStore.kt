package org.aurelia.kefirboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** Offline MediaWiki XML asset -> searchable SQLite index, populated on a worker thread. */
class WikiStore(private val ctx: Context) : SQLiteOpenHelper(ctx, "aurelia.db", null, 1) {
    data class Article(val title: String, val extract: String)
    @Volatile private var importing = false
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE articles(title TEXT PRIMARY KEY, lookup TEXT NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE INDEX articles_lookup_idx ON articles(lookup)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized fun startImportIfNeeded() {
        val settings = ctx.getSharedPreferences("wiki", Context.MODE_PRIVATE)
        if (importing || settings.getBoolean("indexed", false)) return
        importing = true
        Thread {
            try {
                val db = writableDatabase
                ctx.assets.open("aurelia_wiki.xml").use { stream ->
                    val xml = Xml.newPullParser()
                    xml.setInput(stream, "UTF-8")
                    var event = xml.eventType
                    var title = ""
                    var body = ""
                    var ns = "0"
                    db.beginTransaction()
                    try {
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (xml.name) {
                                    "page" -> { title = ""; body = ""; ns = "0" }
                                    "title" -> title = xml.nextText()
                                    "ns" -> ns = xml.nextText()
                                    "text" -> body = xml.nextText()
                                }
                            } else if (event == XmlPullParser.END_TAG && xml.name == "page") {
                                if (ns == "0" && title.isNotBlank()) {
                                    val row = android.content.ContentValues().apply {
                                        put("title", title)
                                        put("lookup", title.lowercase(Locale.ROOT))
                                        put("body", body)
                                    }
                                    db.insertWithOnConflict("articles", null, row, SQLiteDatabase.CONFLICT_REPLACE)
                                }
                            }
                            event = xml.next()
                        }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
                settings.edit().putBoolean("indexed", true).apply()
            } catch (e: Exception) {
                android.util.Log.e("KefirBoard", "Wiki import failed", e)
            } finally { importing = false }
        }.start()
    }

    fun isReady(): Boolean = ctx.getSharedPreferences("wiki", Context.MODE_PRIVATE)
        .getBoolean("indexed", false)

    fun suggest(prefix: String, limit: Int = 4): List<String> {
        if (!isReady() || prefix.length < 2) return emptyList()
        val titles = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT title FROM articles WHERE lookup LIKE ? ORDER BY LENGTH(title), title LIMIT ?",
            arrayOf(prefix.lowercase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%", limit.toString())
        ).use { c -> while (c.moveToNext()) titles += c.getString(0) }
        return titles
    }

    fun search(query: String, limit: Int = 30): List<Article> {
        if (!isReady() || query.isBlank()) return emptyList()
        val result = mutableListOf<Article>()
        val needle = "%" + query.lowercase(Locale.ROOT) + "%"
        readableDatabase.rawQuery(
            "SELECT title, SUBSTR(body,1,550) FROM articles WHERE lookup LIKE ? OR body LIKE ? ORDER BY CASE WHEN lookup LIKE ? THEN 0 ELSE 1 END, LENGTH(title) LIMIT ?",
            arrayOf(needle, needle, needle, limit.toString())
        ).use { c ->
            while (c.moveToNext()) result += Article(c.getString(0), c.getString(1) ?: "")
        }
        return result
    }
}
