package com.classcue.kexing.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.TranscriptRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoryStore(context: Context) : SQLiteOpenHelper(context, "classcue_history.db", null, 2) {
    private val _events = MutableStateFlow<List<HistoryEvent>>(emptyList())
    val events: StateFlow<List<HistoryEvent>> = _events
    private val _transcripts = MutableStateFlow<List<TranscriptRecord>>(emptyList())
    val transcripts: StateFlow<List<TranscriptRecord>> = _transcripts

    init { reload() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                type TEXT NOT NULL,
                risk TEXT NOT NULL,
                transcript TEXT NOT NULL,
                confidence REAL NOT NULL,
                reason TEXT NOT NULL,
                answer TEXT,
                recording_name TEXT,
                recording_offset_ms INTEGER
            )""".trimIndent()
        )
        createTranscriptTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTranscriptTable(db)
    }

    private fun createTranscriptTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS transcripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                text TEXT NOT NULL,
                recording_name TEXT
            )""".trimIndent()
        )
    }

    @Synchronized
    fun insert(event: HistoryEvent): Long {
        val values = ContentValues().apply {
            put("created_at", event.createdAt)
            put("type", event.type.name)
            put("risk", event.risk.name)
            put("transcript", event.transcript)
            put("confidence", event.confidence)
            put("reason", event.reason)
            put("answer", event.answer)
            put("recording_name", event.recordingName)
            put("recording_offset_ms", event.recordingOffsetMs)
        }
        val id = writableDatabase.insertOrThrow("events", null, values)
        reload()
        return id
    }

    @Synchronized
    fun updateAnswer(id: Long, answer: String) {
        writableDatabase.update(
            "events", ContentValues().apply { put("answer", answer) }, "id = ?", arrayOf(id.toString())
        )
        reload()
    }

    @Synchronized
    fun getEvent(id: Long): HistoryEvent? =
        _events.value.firstOrNull { it.id == id }

    @Synchronized
    fun insertTranscript(record: TranscriptRecord): Long {
        val id = writableDatabase.insertOrThrow(
            "transcripts",
            null,
            ContentValues().apply {
                put("created_at", record.createdAt)
                put("text", record.text)
                put("recording_name", record.recordingName)
            }
        )
        _transcripts.value = (listOf(record.copy(id = id)) + _transcripts.value).take(2000)
        return id
    }

    @Synchronized
    fun deleteAll() {
        writableDatabase.delete("events", null, null)
        writableDatabase.delete("transcripts", null, null)
        reload()
    }

    @Synchronized
    fun reload() {
        val result = mutableListOf<HistoryEvent>()
        readableDatabase.query("events", null, null, null, null, null, "created_at DESC", "300").use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val created = c.getColumnIndexOrThrow("created_at")
            val type = c.getColumnIndexOrThrow("type")
            val risk = c.getColumnIndexOrThrow("risk")
            val transcript = c.getColumnIndexOrThrow("transcript")
            val confidence = c.getColumnIndexOrThrow("confidence")
            val reason = c.getColumnIndexOrThrow("reason")
            val answer = c.getColumnIndexOrThrow("answer")
            val recordingName = c.getColumnIndexOrThrow("recording_name")
            val recordingOffset = c.getColumnIndexOrThrow("recording_offset_ms")
            while (c.moveToNext()) {
                result += HistoryEvent(
                    id = c.getLong(id),
                    createdAt = c.getLong(created),
                    type = runCatching { CueType.valueOf(c.getString(type)) }.getOrDefault(CueType.NONE),
                    risk = runCatching { RiskLevel.valueOf(c.getString(risk)) }.getOrDefault(RiskLevel.LOW),
                    transcript = c.getString(transcript),
                    confidence = c.getDouble(confidence),
                    reason = c.getString(reason),
                    answer = if (c.isNull(answer)) null else c.getString(answer),
                    recordingName = if (c.isNull(recordingName)) null else c.getString(recordingName),
                    recordingOffsetMs = if (c.isNull(recordingOffset)) null else c.getLong(recordingOffset)
                )
            }
        }
        _events.value = result
        reloadTranscripts()
    }

    @Synchronized
    private fun reloadTranscripts() {
        val result = mutableListOf<TranscriptRecord>()
        readableDatabase.query("transcripts", null, null, null, null, null, "created_at DESC", "2000").use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val created = c.getColumnIndexOrThrow("created_at")
            val text = c.getColumnIndexOrThrow("text")
            val recordingName = c.getColumnIndexOrThrow("recording_name")
            while (c.moveToNext()) {
                result += TranscriptRecord(
                    id = c.getLong(id),
                    createdAt = c.getLong(created),
                    text = c.getString(text),
                    recordingName = if (c.isNull(recordingName)) null else c.getString(recordingName)
                )
            }
        }
        _transcripts.value = result
    }
}
