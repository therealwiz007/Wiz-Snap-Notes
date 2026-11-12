package com.wizlabs.snapnotes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

data class Note(
    val id: Long,
    val createdMs: Long,
    val header: String,
    val body: String,
    val emails: List<String>,
    /** Stamped full photo, private copy in files/notes/. */
    val file: String,
    /** Small preview in files/thumbs/, used by the grid so it stays light on memory. */
    val thumb: String,
    /** Copy in the phone gallery (Pictures/Wiz Snap Notes), if it was saved there. */
    val galleryUri: String?
)

enum class DateRange(val label: String, val days: Int) {
    ALL("All", 0), TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30)
}

/**
 * Local SQLite store. Notes live in `notes`; `notes_fts` is a full-text index over header, body
 * and recipients so search stays instant with thousands of photos. Everything stays on the phone.
 */
class NoteStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "snapnotes.db", null, 1) {

    val notesDir = File(context.applicationContext.filesDir, "notes").apply { mkdirs() }
    val thumbsDir = File(context.applicationContext.filesDir, "thumbs").apply { mkdirs() }

    /** Bumped on every change so screens reload. */
    var version by mutableIntStateOf(0)
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE notes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created INTEGER NOT NULL,
                header TEXT NOT NULL,
                body TEXT NOT NULL,
                emails TEXT NOT NULL,
                file TEXT NOT NULL,
                thumb TEXT NOT NULL,
                gallery TEXT)"""
        )
        db.execSQL("CREATE INDEX notes_created ON notes(created)")
        // unicode61 folds case for Hindi and other scripts too; fall back to the default tokenizer.
        try {
            db.execSQL("CREATE VIRTUAL TABLE notes_fts USING fts4(header, body, emails, tokenize=unicode61)")
        } catch (e: Exception) {
            db.execSQL("CREATE VIRTUAL TABLE notes_fts USING fts4(header, body, emails)")
        }
        db.execSQL("CREATE TABLE headers(text TEXT PRIMARY KEY, used INTEGER NOT NULL, last INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE recipients(email TEXT PRIMARY KEY, used INTEGER NOT NULL, last INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(n: Note): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("notes", null, ContentValues().apply {
                put("created", n.createdMs)
                put("header", n.header)
                put("body", n.body)
                put("emails", n.emails.joinToString(","))
                put("file", n.file)
                put("thumb", n.thumb)
                put("gallery", n.galleryUri)
            })
            db.insertOrThrow("notes_fts", null, ContentValues().apply {
                put("docid", id)
                put("header", n.header)
                put("body", n.body)
                put("emails", n.emails.joinToString(" "))
            })
            remember(db, "headers", "text", n.header, n.createdMs)
            n.emails.forEach { remember(db, "recipients", "email", it.lowercase(), n.createdMs) }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
            version++
        }
    }

    private fun remember(db: SQLiteDatabase, table: String, col: String, value: String, now: Long) {
        if (value.isBlank()) return
        // Android 10 ships SQLite 3.22, which has no UPSERT, so insert-or-ignore then update.
        db.execSQL("INSERT OR IGNORE INTO $table($col, used, last) VALUES(?, 0, ?)", arrayOf(value, now))
        db.execSQL("UPDATE $table SET used = used + 1, last = ? WHERE $col = ?", arrayOf(now, value))
    }

    fun delete(n: Note) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("notes", "id = ?", arrayOf(n.id.toString()))
            db.delete("notes_fts", "docid = ?", arrayOf(n.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        File(n.file).delete()
        File(n.thumb).delete()
        version++
    }

    fun get(id: Long): Note? = readableDatabase.rawQuery(
        "SELECT id, created, header, body, emails, file, thumb, gallery FROM notes WHERE id = ?",
        arrayOf(id.toString())
    ).use { c -> if (c.moveToFirst()) row(c) else null }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM notes", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    /** Newest first. Blank [query] lists everything in the [range]. */
    fun search(query: String, range: DateRange, now: Long = System.currentTimeMillis()): List<Note> {
        val args = ArrayList<String>()
        val where = ArrayList<String>()
        if (range != DateRange.ALL) {
            where += "n.created >= ?"
            args += SearchText.rangeStart(range, now).toString()
        }
        val terms = SearchText.terms(query)
        val useFts = terms.isNotEmpty() && terms.all { SearchText.isAscii(it) }
        val sql = StringBuilder("SELECT n.id, n.created, n.header, n.body, n.emails, n.file, n.thumb, n.gallery FROM notes n")
        if (useFts) {
            where.add(0, "n.id IN (SELECT docid FROM notes_fts WHERE notes_fts MATCH ?)")
            args.add(0, SearchText.ftsQuery(terms))
        } else if (terms.isNotEmpty()) {
            // Non-Latin scripts: plain substring match so Hindi words are always found.
            terms.forEach {
                where += "(n.header LIKE ? OR n.body LIKE ? OR n.emails LIKE ?)"
                val like = "%$it%"
                args += like; args += like; args += like
            }
        }
        if (where.isNotEmpty()) sql.append(" WHERE ").append(where.joinToString(" AND "))
        sql.append(" ORDER BY n.created DESC LIMIT 2000")
        return try {
            readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
                val out = ArrayList<Note>(c.count)
                while (c.moveToNext()) out += row(c)
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recentHeaders(limit: Int = 12): List<String> = readableDatabase.rawQuery(
        "SELECT text FROM headers ORDER BY last DESC LIMIT $limit", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun recipients(prefix: String, limit: Int = 6): List<String> = readableDatabase.rawQuery(
        "SELECT email FROM recipients WHERE email LIKE ? ORDER BY used DESC, last DESC LIMIT $limit",
        arrayOf(prefix.lowercase() + "%")
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun forgetHeader(text: String) {
        writableDatabase.delete("headers", "text = ?", arrayOf(text))
        version++
    }

    private fun row(c: android.database.Cursor) = Note(
        id = c.getLong(0),
        createdMs = c.getLong(1),
        header = c.getString(2),
        body = c.getString(3),
        emails = c.getString(4).split(',').filter { it.isNotBlank() },
        file = c.getString(5),
        thumb = c.getString(6),
        galleryUri = c.getString(7)
    )

    companion object {
        @Volatile private var instance: NoteStore? = null
        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) { instance ?: NoteStore(context).also { instance = it } }
    }
}
