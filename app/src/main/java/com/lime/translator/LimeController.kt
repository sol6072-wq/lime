package com.lime.translator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import kotlin.math.max

enum class Screen { MAIN, SOUNDS }
enum class Mode { IDLE, HEARING_ME, LISTENING_LIME, LIME_TALKING }

private const val IDLE_STATUS = "버튼을 눌러 라임이와 이야기해 보세요"

class LimeController(private val ctx: Context) : TextToSpeech.OnInitListener {
    val store = Store(ctx)
    private val main = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.MAIN)
    var mode by mutableStateOf(Mode.IDLE)
        private set
    var status by mutableStateOf(IDLE_STATUS)
    var partial by mutableStateOf("")
        private set
    var level by mutableStateOf(0f)
        private set
    var useMyVoice by mutableStateOf(store.useMyVoice)
        private set
    var voiceConflict by mutableStateOf(store.voiceConflict)
        private set

    val chat = mutableStateListOf<ChatMessage>()
    val sounds = mutableStateListOf<SoundClip>()

    // 라임이 소리 녹음 (소리 모음 화면)
    var clipRecording by mutableStateOf(false)
        private set
    var pendingClip by mutableStateOf<ShortArray?>(null)
        private set
    var playingClipId by mutableStateOf<Long?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val voiceCap = PcmRecorder()
    private val limeRec = PcmRecorder()
    private val clipRec = PcmRecorder()
    private val player = SoundPlayer()
    private var capturing = false
    private var nextId: Long

    private val levelPoll = object : Runnable {
        override fun run() {
            val l = max(max(voiceCap.level, limeRec.level), clipRec.level)
            if (capturing || limeRec.isRunning || clipRec.isRunning) level = l
            if (mode == Mode.HEARING_ME || mode == Mode.LISTENING_LIME || clipRecording) {
                main.postDelayed(this, 80)
            } else {
                level = 0f
            }
        }
    }

    init {
        chat.addAll(store.loadChat())
        sounds.addAll(store.loadSounds())
        nextId = (chat.maxOfOrNull { it.id } ?: 0L) + 1
        tts = TextToSpeech(ctx, this)
    }

    // ---------------- TTS ----------------
    override fun onInit(result: Int) {
        val t = tts ?: return
        if (result != TextToSpeech.SUCCESS) return
        val r = t.setLanguage(Locale.KOREAN)
        ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        t.setPitch(1.7f)      // 아기 같은 높은 목소리
        t.setSpeechRate(1.05f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { if (mode == Mode.LIME_TALKING) finishTalking() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { if (mode == Mode.LIME_TALKING) finishTalking() }
            }
        })
    }

    private fun speakAsLime(text: String) {
        val t = tts
        if (t == null || !ttsReady) {
            status = "한국어 음성(TTS)이 준비되지 않았어요. 설정 > 텍스트 음성 변환을 확인해 주세요"
            return
        }
        mode = Mode.LIME_TALKING
        status = "라임이가 말하는 중…"
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lime-${System.currentTimeMillis()}")
    }

    private fun finishTalking() {
        mode = Mode.IDLE
        status = IDLE_STATUS
    }

    // ---------------- 대화 기록 ----------------
    private fun addChat(sender: Sender, text: String, sub: String? = null) {
        chat.add(ChatMessage(nextId++, sender, text, sub))
        store.saveChat(chat)
    }

    fun clearChat() {
        chat.clear()
        store.saveChat(chat)
    }

    // ---------------- 기능 1: 한국어 → 앵무새 소리 ----------------
    private val captureVoice: Boolean get() = useMyVoice && !voiceConflict

    fun onTalkButton() {
        when (mode) {
            Mode.HEARING_ME -> { recognizer?.stopListening(); return } // 다시 누르면 말 끝내기
            Mode.IDLE -> {}
            else -> cancelAll()
        }
        startTalk()
    }

    private fun startTalk() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            status = "이 폰에서 음성인식을 쓸 수 없어요 (Google 앱 설치·업데이트를 확인해 주세요)"
            return
        }
        player.stop(); tts?.stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        partial = ""
        mode = Mode.HEARING_ME
        status = "듣고 있어요… 라임이한테 할 말을 해 주세요"
        startLevelPoll()
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            destroyRecognizer()
            mode = Mode.IDLE
            status = "음성인식을 시작하지 못했어요"
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (mode != Mode.HEARING_ME) return
            // 음성인식과 동시에 내 목소리도 녹음 (지저귐 재료)
            if (captureVoice && !capturing) capturing = voiceCap.start(maxMs = 15000)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (!capturing) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (mode == Mode.HEARING_ME) status = "알아듣는 중…"
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) partial = p
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (mode != Mode.HEARING_ME) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.trim().orEmpty().ifBlank { partial }
            val voice = stopVoiceCapture()
            destroyRecognizer()
            partial = ""
            mode = Mode.IDLE
            if (text.isBlank()) {
                status = "잘 못 들었어요. 다시 말해 볼까요?"
            } else {
                translateToParrot(text, voice)
            }
        }

        override fun onError(error: Int) {
            if (mode != Mode.HEARING_ME) return
            val hadCapture = capturing
            stopVoiceCapture()
            destroyRecognizer()
            mode = Mode.IDLE
            val p = partial
            partial = ""
            if (hadCapture && (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
                // 이 폰은 음성인식 중 동시 녹음이 안 됨 → 녹음 없이 다시 시도
                voiceConflict = true
                store.voiceConflict = true
                status = "이 폰은 동시 녹음이 안 돼서 합성 소리로 바꿨어요. 다시 말해 주세요"
                main.postDelayed({ if (mode == Mode.IDLE) startTalk() }, 300)
                return
            }
            if (p.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                translateToParrot(p, null)
                return
            }
            status = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "잘 못 들었어요. 다시 말해 볼까요?"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "인터넷 연결을 확인해 주세요 (음성인식에 필요할 수 있어요)"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 허용해 주세요"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성인식이 바빠요. 잠시 후 다시 눌러 주세요"
                else -> "음성인식 오류($error). 다시 눌러 주세요"
            }
        }
    }

    private fun stopVoiceCapture(): ShortArray? {
        if (!capturing) return null
        capturing = false
        return voiceCap.stop()
    }

    private fun destroyRecognizer() {
        recognizer?.let {
            try { it.cancel() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        recognizer = null
    }

    private fun translateToParrot(text: String, voice: ShortArray?) {
        addChat(Sender.ME, text)
        val mood = MoodClassifier.classify(text)
        val seed = System.nanoTime()
        val clips = sounds.filter { it.mood == mood }
        if (clips.isNotEmpty()) {
            val clip = clips.random()
            addChat(
                Sender.LIME,
                ChirpSynth.words(clip.durationMs.toInt().coerceAtLeast(600), seed),
                "${mood.emoji} ${mood.label} · 라임이 진짜 목소리 '${clip.name}'"
            )
            mode = Mode.LIME_TALKING
            status = "라임이 말로 바꾸는 중…"
            player.playFile(store.soundFile(clip)) { finishTalking() }
        } else {
            val ms = ChirpSynth.targetMs(text)
            val usedVoice = ChirpSynth.hasVoice(voice)
            val pcm = ChirpSynth.synth(if (usedVoice) voice else null, ms, seed)
            addChat(
                Sender.LIME,
                ChirpSynth.words(ms, seed),
                "${mood.emoji} ${mood.label} · ${if (usedVoice) "내 목소리로 만든 지저귐" else "합성 지저귐"}"
            )
            mode = Mode.LIME_TALKING
            status = "라임이 말로 바꾸는 중…"
            player.playPcm(pcm, ChirpSynth.OUT_SR) { finishTalking() }
        }
    }

    // ---------------- 기능 2: 앵무새 소리 → 한국어 ----------------
    fun onListenButton() {
        when (mode) {
            Mode.LISTENING_LIME -> { finishListen(); return } // 다시 누르면 바로 통역
            Mode.IDLE -> {}
            else -> cancelAll()
        }
        player.stop(); tts?.stop()
        val ok = limeRec.start(maxMs = 6000, silenceStopMs = 1200) {
            main.post { if (mode == Mode.LISTENING_LIME) finishListen() }
        }
        if (!ok) {
            status = "마이크를 열 수 없어요"
            return
        }
        mode = Mode.LISTENING_LIME
        status = "라임이 소리를 듣는 중… (다시 누르면 바로 통역)"
        startLevelPoll()
    }

    private fun finishListen() {
        val pcm = limeRec.stop()
        mode = Mode.IDLE
        val a = SoundAnalyzer.analyze(pcm)
        if (a.state == LimeState.SILENT) {
            addChat(Sender.SYSTEM, "라임이가 아무 소리도 안 냈어요. 라임이 가까이에서 다시 해 볼까요?")
            status = IDLE_STATUS
            return
        }
        val line = Phrases.pick(a.state)
        addChat(Sender.LIME, line, a.describe())
        speakAsLime(line)
    }

    // ---------------- 라임이 소리 모음 ----------------
    fun setUseMyVoiceOption(v: Boolean) {
        useMyVoice = v
        store.useMyVoice = v
        if (v && voiceConflict) {
            // 다시 켜면 한 번 더 시도해 봄
            voiceConflict = false
            store.voiceConflict = false
        }
    }

    fun toggleClipRecording() {
        if (clipRecording) { stopClip(); return }
        if (mode != Mode.IDLE) cancelAll()
        stopClipPlayback()
        val ok = clipRec.start(maxMs = 10000) { main.post { if (clipRecording) stopClip() } }
        if (!ok) {
            status = "마이크를 열 수 없어요"
            return
        }
        clipRecording = true
        startLevelPoll()
    }

    private fun stopClip() {
        val pcm = Wav.trimSilence(clipRec.stop())
        clipRecording = false
        if (pcm.size < SAMPLE_RATE / 5) {
            status = "소리가 너무 작거나 짧았어요. 라임이 가까이에서 다시 녹음해 주세요"
            pendingClip = null
        } else {
            pendingClip = pcm
        }
    }

    fun previewPending() {
        val p = pendingClip ?: return
        player.playPcm(p, SAMPLE_RATE) {}
    }

    fun discardPending() {
        player.stop()
        pendingClip = null
    }

    fun savePending(name: String, mood: Mood) {
        val p = pendingClip ?: return
        player.stop()
        val id = System.currentTimeMillis()
        val fileName = "clip_$id.wav"
        try {
            Wav.write(File(store.soundDir, fileName), p)
        } catch (e: Exception) {
            status = "저장하지 못했어요"
            return
        }
        val clip = SoundClip(
            id = id,
            name = name.ifBlank { "라임이 소리 ${sounds.size + 1}" },
            mood = mood,
            fileName = fileName,
            createdAt = id,
            durationMs = p.size * 1000L / SAMPLE_RATE
        )
        sounds.add(0, clip)
        store.saveSounds(sounds)
        pendingClip = null
    }

    fun playClip(c: SoundClip) {
        if (playingClipId == c.id) { stopClipPlayback(); return }
        if (mode != Mode.IDLE) cancelAll()
        playingClipId = c.id
        player.playFile(store.soundFile(c)) { if (playingClipId == c.id) playingClipId = null }
    }

    private fun stopClipPlayback() {
        player.stop()
        playingClipId = null
    }

    fun updateClip(c: SoundClip, name: String, mood: Mood) {
        val i = sounds.indexOfFirst { it.id == c.id }
        if (i < 0) return
        sounds[i] = c.copy(name = name.ifBlank { c.name }, mood = mood)
        store.saveSounds(sounds)
    }

    fun deleteClip(c: SoundClip) {
        if (playingClipId == c.id) stopClipPlayback()
        try { store.soundFile(c).delete() } catch (_: Exception) {}
        sounds.removeAll { it.id == c.id }
        store.saveSounds(sounds)
    }

    // ---------------- 공통 ----------------
    private fun startLevelPoll() {
        main.removeCallbacks(levelPoll)
        main.post(levelPoll)
    }

    fun cancelAll() {
        destroyRecognizer()
        stopVoiceCapture()
        limeRec.stop()
        clipRec.stop()
        clipRecording = false
        player.stop()
        playingClipId = null
        tts?.stop()
        partial = ""
        mode = Mode.IDLE
        status = IDLE_STATUS
    }

    fun release() {
        cancelAll()
        tts?.shutdown()
        tts = null
    }
}
