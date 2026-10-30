package com.lime.translator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 말·녹음의 분위기 */
enum class Mood(val label: String, val emoji: String) {
    GREETING("인사", "👋"),
    PRAISE("칭찬", "💚"),
    SNACK("간식", "🌻"),
    PLAY("놀자", "🎵"),
    SLEEP("잘자", "🌙"),
    OTHER("기타", "🪶");

    companion object {
        fun from(name: String?): Mood = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class Sender { ME, LIME, SYSTEM }

data class ChatMessage(
    val id: Long,
    val sender: Sender,
    val text: String,
    val sub: String? = null,
    val time: Long = System.currentTimeMillis()
)

data class SoundClip(
    val id: Long,
    val name: String,
    val mood: Mood,
    val fileName: String,
    val createdAt: Long,
    val durationMs: Long
)

/** 폰 안에만 저장 (서버 없음) */
class Store(context: Context) {
    private val dir: File = context.filesDir
    val soundDir: File = File(dir, "sounds").apply { mkdirs() }
    private val soundsJson = File(dir, "sounds.json")
    private val chatJson = File(dir, "chat.json")
    private val prefs = context.getSharedPreferences("lime", Context.MODE_PRIVATE)

    fun soundFile(clip: SoundClip): File = File(soundDir, clip.fileName)

    fun loadSounds(): List<SoundClip> = try {
        if (!soundsJson.exists()) emptyList() else {
            val arr = JSONArray(soundsJson.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SoundClip(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    mood = Mood.from(o.optString("mood")),
                    fileName = o.getString("file"),
                    createdAt = o.optLong("created"),
                    durationMs = o.optLong("dur")
                )
            }.filter { File(soundDir, it.fileName).exists() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveSounds(list: List<SoundClip>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("mood", c.mood.name)
                put("file", c.fileName)
                put("created", c.createdAt)
                put("dur", c.durationMs)
            })
        }
        try { soundsJson.writeText(arr.toString()) } catch (_: Exception) {}
    }

    fun loadChat(): List<ChatMessage> = try {
        if (!chatJson.exists()) emptyList() else {
            val arr = JSONArray(chatJson.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    sender = Sender.entries.firstOrNull { it.name == o.optString("sender") } ?: Sender.SYSTEM,
                    text = o.getString("text"),
                    sub = if (o.has("sub")) o.getString("sub") else null,
                    time = o.optLong("time")
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveChat(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.takeLast(300).forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("sender", m.sender.name)
                put("text", m.text)
                if (m.sub != null) put("sub", m.sub)
                put("time", m.time)
            })
        }
        try { chatJson.writeText(arr.toString()) } catch (_: Exception) {}
    }

    var useMyVoice: Boolean
        get() = prefs.getBoolean("use_my_voice", true)
        set(v) { prefs.edit().putBoolean("use_my_voice", v).apply() }

    /** 음성인식과 동시 녹음이 안 되는 폰이면 true */
    var voiceConflict: Boolean
        get() = prefs.getBoolean("voice_conflict", false)
        set(v) { prefs.edit().putBoolean("voice_conflict", v).apply() }
}
