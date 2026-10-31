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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import kotlin.math.max

enum class Mode { IDLE, HEARING_ME, LISTENING_LIME, LIME_TALKING }

private const val IDLE_STATUS = "버튼을 눌러 라임이와 이야기해 보세요"

class LimeController(private val ctx: Context) : TextToSpeech.OnInitListener {
    val store = Store(ctx)
    private val main = Handler(Looper.getMainLooper())

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

    var avatar by mutableStateOf<ImageBitmap?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val voiceCap = PcmRecorder()
    private val limeRec = PcmRecorder()
    private val player = SoundPlayer()
    private var capturing = false
    private var nextId: Long

    private val levelPoll = object : Runnable {
        override fun run() {
            val l = max(voiceCap.level, limeRec.level)
            if (capturing || limeRec.isRunning) level = l
            if (mode == Mode.HEARING_ME || mode == Mode.LISTENING_LIME) {
                main.postDelayed(this, 80)
            } else {
                level = 0f
            }
        }
    }

    init {
        chat.addAll(store.loadChat())
        nextId = (chat.maxOfOrNull { it.id } ?: 0L) + 1
        avatar = loadAvatarFile()
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
            val hadCapture = capturing
            val voice = stopVoiceCapture()
            destroyRecognizer()
            partial = ""
            mode = Mode.IDLE
            if (text.isBlank() && hadCapture) {
                fallBackWithoutCapture()
                return
            }
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
            if (hadCapture && p.isBlank() && error in listOf(
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
            ) {
                // 내 목소리 녹음이 마이크를 가져가서 음성인식이 아무것도 못 들은 경우
                fallBackWithoutCapture()
                return
            }
            if (p.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                translateToParrot(p, null)
                return
            }
            status = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "잘 못 들었어요. 다시 말해 볼까요? (코드 $error)"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "인터넷 연결을 확인해 주세요 (음성인식에 필요할 수 있어요)"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 허용해 주세요"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성인식이 바빠요. 잠시 후 다시 눌러 주세요"
                else -> "음성인식 오류($error). 다시 눌러 주세요"
            }
        }
    }

    /** 이 폰은 음성인식과 내 목소리 녹음을 동시에 못 함 → 녹음 끄고 바로 다시 듣기 */
    private fun fallBackWithoutCapture() {
        voiceConflict = true
        store.voiceConflict = true
        status = "다시 한 번 말해 주세요 (이 폰은 합성 지저귐으로 들려줄게요)"
        main.postDelayed({
            if (mode == Mode.IDLE) {
                startTalk()
                status = "다시 한 번 말해 주세요 (이 폰은 합성 지저귐으로 들려줄게요)"
            }
        }, 300)
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
        val ms = ChirpSynth.targetMs(text)
        val usedVoice = ChirpSynth.hasVoice(voice)
        val pcm = ChirpSynth.synth(if (usedVoice) voice else null, ms, System.nanoTime())
        addChat(Sender.ME, text)
        mode = Mode.LIME_TALKING
        status = "라임이 말로 바꾸는 중…"
        player.playPcm(pcm, ChirpSynth.OUT_SR) { finishTalking() }
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
        addChat(Sender.LIME, line)
        speakAsLime(line)
    }

    // ---------------- 라임이 사진 ----------------
    private val avatarFile: File get() = File(ctx.filesDir, "avatar.jpg")

    private fun loadAvatarFile(): ImageBitmap? = try {
        if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.absolutePath)?.asImageBitmap() else null
    } catch (e: Exception) {
        null
    }

    fun setAvatar(uri: Uri) {
        status = "사진을 바꾸는 중…"
        Thread {
            val bmp = decodeScaled(uri, 600)
            if (bmp != null) {
                try {
                    avatarFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                } catch (_: Exception) {}
            }
            main.post {
                if (bmp != null) {
                    avatar = bmp.asImageBitmap()
                    status = IDLE_STATUS
                } else {
                    status = "사진을 불러오지 못했어요"
                }
            }
        }.start()
    }

    fun resetAvatar() {
        try { avatarFile.delete() } catch (_: Exception) {}
        avatar = null
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, maxSide.toFloat() / minOf(w, h).coerceAtLeast(1))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: Exception) {
        null
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
        player.stop()
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
