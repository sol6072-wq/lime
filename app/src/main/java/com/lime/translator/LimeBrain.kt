package com.lime.translator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 한국어 문장 → 분위기 */
object MoodClassifier {
    private val rules: List<Pair<Mood, List<String>>> = listOf(
        Mood.SNACK to listOf("간식", "밥", "먹", "배고", "씨앗", "사과", "과일", "맛있", "야채", "채소", "물마", "물줄", "냠"),
        Mood.SLEEP to listOf("잘자", "자자", "자러", "졸려", "졸리", "피곤", "굿나잇", "쉬자", "쉬어", "코자", "꿈"),
        Mood.PLAY to listOf("놀자", "놀까", "놀아", "놀이", "장난감", "산책", "나와", "이리와", "날아", "노래", "춤", "공놀"),
        Mood.PRAISE to listOf("예쁘", "이쁘", "착하", "잘했", "귀여", "최고", "멋지", "멋있", "사랑", "똑똑", "훌륭", "대단"),
        Mood.GREETING to listOf("안녕", "하이", "반가", "왔어", "다녀왔", "다녀올", "좋은아침", "굿모닝", "일어났", "보고싶", "헬로")
    )

    fun classify(text: String): Mood {
        val t = text.replace(" ", "")
        for ((mood, words) in rules) {
            if (words.any { t.contains(it) }) return mood
        }
        return Mood.OTHER
    }
}

/** 한국어 → 사랑앵무 지저귐 합성 */
object ChirpSynth {
    const val OUT_SR = 22050

    /** 문장이 길수록 지저귐도 길게 */
    fun targetMs(text: String): Int {
        val syl = text.count { it in '가'..'힣' || it.isLetterOrDigit() }
        return (450 + syl * 170).coerceIn(600, 6000)
    }

    /** 내 목소리에서 소리가 있는 20ms 구간 시작 위치들 */
    private fun voicedFrames(voice: ShortArray): List<Int> {
        val frames = voice.size / FRAME
        if (frames < 5) return emptyList()
        val r = FloatArray(frames) { i -> rms(voice, i * FRAME, (i + 1) * FRAME) }
        val peak = r.maxOrNull() ?: 0f
        if (peak < 800f) return emptyList()
        val thr = max(600f, peak * 0.25f)
        val list = (0 until frames).filter { r[it] > thr }.map { it * FRAME }
        return if (list.size >= 3) list else emptyList()
    }

    fun hasVoice(voice: ShortArray?): Boolean = voice != null && voicedFrames(voice).isNotEmpty()

    /**
     * @param voice 내 목소리(16kHz). null이거나 무음이면 순수 합성 지저귐
     */
    fun synth(voice: ShortArray?, targetMs: Int, seed: Long): ShortArray {
        val rnd = Random(seed)
        val total = OUT_SR * targetMs / 1000
        val out = FloatArray(total + OUT_SR)
        val grains = voice?.let { voicedFrames(it) } ?: emptyList()
        var voiceGain = 0f
        if (voice != null && grains.isNotEmpty()) {
            var peak = 1
            for (s in voice) peak = max(peak, abs(s.toInt()))
            voiceGain = 0.9f / peak
        }

        var pos = OUT_SR * 30 / 1000
        var syllable = 0
        while (pos < total) {
            val type = rnd.nextInt(10)
            val lenMs = when {
                type < 5 -> 50 + rnd.nextInt(70)    // 짹! 짧은 소리
                type < 8 -> 120 + rnd.nextInt(120)  // 삐리~ 휘는 소리
                else -> 180 + rnd.nextInt(160)      // 또르르 떨리는 소리
            }
            val len = OUT_SR * lenMs / 1000
            val f0 = 2600f + rnd.nextFloat() * 1800f
            val f1 = (f0 + (rnd.nextFloat() - 0.4f) * 2200f).coerceIn(1800f, 6500f)
            val trill = type >= 8
            val vibHz = if (trill) 28f + rnd.nextFloat() * 14f else 8f + rnd.nextFloat() * 10f
            val vibDepth = if (trill) 500f else 250f
            val amp = 0.45f + rnd.nextFloat() * 0.35f
            val grainStart = if (grains.isNotEmpty()) grains[(syllable * 3 + rnd.nextInt(3)) % grains.size] else -1
            val pitchUp = 2.4 + rnd.nextDouble() * 0.8
            var phase = 0.0

            for (i in 0 until len) {
                val o = pos + i
                if (o >= out.size) break
                val t = i.toFloat() / len
                val env = envelope(t, type)
                val vib = sin(2.0 * PI * vibHz * i / OUT_SR).toFloat()
                val am = if (trill) 0.55f + 0.45f * vib else 1f
                val f = f0 + (f1 - f0) * t + vibDepth * vib
                phase += 2.0 * PI * f / OUT_SR
                var s = sin(phase).toFloat() * 0.8f + sin(2.0 * phase).toFloat() * 0.15f
                if (grainStart >= 0 && voice != null) {
                    // 내 목소리를 빠르게 읽어서 음높이를 확 올림
                    val src = grainStart + i * pitchUp * SAMPLE_RATE / OUT_SR
                    val idx = src.toInt()
                    val fr = (src - idx).toFloat()
                    val a = voice[idx % voice.size].toFloat()
                    val b = voice[(idx + 1) % voice.size].toFloat()
                    val v = (a * (1 - fr) + b * fr) * voiceGain
                    s = s * 0.55f + v * 0.75f
                }
                out[o] += s * env * amp * am
            }
            pos += len + OUT_SR * (15 + rnd.nextInt(if (type < 5) 45 else 80)) / 1000
            if (rnd.nextInt(7) == 0) pos += OUT_SR * (80 + rnd.nextInt(120)) / 1000
            syllable++
        }

        val end = min(out.size, pos + OUT_SR / 20)
        var peak = 0.0001f
        for (i in 0 until end) peak = max(peak, abs(out[i]))
        val g = 0.85f * 32767f / peak
        return ShortArray(end) { i -> (out[i] * g).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun envelope(t: Float, type: Int): Float {
        val attack = if (type < 5) 0.08f else 0.15f
        return when {
            t < attack -> t / attack
            t > 0.75f -> max(0f, (1f - t) / 0.25f)
            else -> 1f
        }
    }

    private val syll = listOf("삐", "삑", "짹", "찌", "삐리", "리릭", "뾰로롱", "치리리", "쮸", "삐요")

    /** 화면에 보여줄 앵무새 소리 글자 */
    fun words(ms: Int, seed: Long): String {
        val rnd = Random(seed)
        val n = (ms / 380).coerceIn(2, 12)
        val sb = StringBuilder()
        for (i in 0 until n) {
            sb.append(syll[rnd.nextInt(syll.size)])
            if (i < n - 1) sb.append(if (rnd.nextInt(3) == 0) " " else "")
        }
        sb.append(if (rnd.nextBoolean()) "~" else "!")
        return sb.toString()
    }
}

enum class LimeState(val desc: String) {
    CALL("짧고 높은 소리"),
    SONG("길게 지저귀는 소리"),
    SNACK("반복해서 빽빽대는 소리"),
    SLEEPY("작고 낮은 소리"),
    CHAT("재잘재잘 수다"),
    SILENT("조용")
}

data class Analysis(
    val state: LimeState,
    val activeMs: Int,
    val bursts: Int,
    val freqHz: Int,
    val peakDb: Float
) {
    fun describe(): String =
        "${state.desc} · ${"%.1f".format(activeMs / 1000f)}초 · ${bursts}번 · 약 ${freqHz}Hz"
}

/** 라임이 소리 → 길이·높낮이·크기·반복 분석 */
object SoundAnalyzer {
    fun analyze(pcm: ShortArray): Analysis {
        val n = pcm.size / FRAME
        if (n < 5) return Analysis(LimeState.SILENT, 0, 0, 0, -90f)
        val r = FloatArray(n)
        val zc = FloatArray(n)
        for (i in 0 until n) {
            val s = i * FRAME
            val e = s + FRAME
            r[i] = rms(pcm, s, e)
            var c = 0
            for (j in s + 1 until e) {
                if ((pcm[j - 1] >= 0) != (pcm[j] >= 0)) c++
            }
            zc[i] = c.toFloat() / FRAME
        }
        val sorted = r.sorted()
        val noise = sorted[(n * 0.2f).toInt().coerceIn(0, n - 1)]
        val thr = max(250f, noise * 3f)

        var active = 0
        var bursts = 0
        var gap = 99
        var freqSum = 0f
        var peak = 0f
        for (i in 0 until n) {
            peak = max(peak, r[i])
            if (r[i] > thr) {
                if (gap >= 3) bursts++
                gap = 0
                active++
                freqSum += zc[i] * SAMPLE_RATE / 2f
            } else {
                gap++
            }
        }
        val peakDb = 20f * log10(max(peak, 1f) / 32768f)
        if (active < 3) return Analysis(LimeState.SILENT, active * 20, bursts, 0, peakDb)
        val activeMs = active * 20
        val freq = (freqSum / active).toInt()

        val state = when {
            peakDb < -32f || freq < 1300 -> LimeState.SLEEPY
            bursts >= 4 -> LimeState.SNACK
            activeMs >= 1400 -> LimeState.SONG
            activeMs <= 700 && freq >= 2200 -> LimeState.CALL
            else -> LimeState.CHAT
        }
        return Analysis(state, activeMs, bursts, freq, peakDb)
    }
}

/** 라임이 말투 문장 (반말, 아기 말투) */
object Phrases {
    private val map: Map<LimeState, List<String>> = mapOf(
        LimeState.CALL to listOf(
            "엄마! 나 여기 있어!",
            "여기 봐봐! 라임이 여기!",
            "어디 갔어? 빨리 와~",
            "나 불렀지? 나도 불렀어!",
            "히히, 나 보여?",
            "라임이 심심해! 이리 와!",
            "안녕! 나 깼어!"
        ),
        LimeState.SONG to listOf(
            "오늘 기분 최고야~ 노래 들어볼래?",
            "라임이 노래 잘하지? 박수 쳐줘!",
            "랄랄라~ 오늘 날씨 좋다~",
            "나 지금 콘서트 중이야! 조용히 들어~",
            "기분 좋아서 노래가 막 나와~",
            "이 노래 엄마한테 주는 선물이야!"
        ),
        LimeState.SNACK to listOf(
            "간식 줘! 지금 당장!",
            "라임이 배고파! 배고파!",
            "씨앗! 씨앗! 씨앗 줘!",
            "밥 언제 줘? 지금? 지금?",
            "맛있는 거 숨겼지? 다 알아!",
            "사과 한 조각만~ 제발~"
        ),
        LimeState.SLEEPY to listOf(
            "졸려… 이제 잘래",
            "라임이 눈이 감겨…",
            "조용히 해줘… 쿨쿨…",
            "오늘 많이 놀았어… 피곤해…",
            "불 꺼줘… 잘 자…",
            "음냐음냐… 꿈에서 만나…"
        ),
        LimeState.CHAT to listOf(
            "있잖아, 오늘 창밖에 새 봤어!",
            "라임이 할 말 엄청 많아!",
            "내 깃털 예쁘지? 맞지?",
            "거울 속에 친구가 있어!",
            "나 똑똑하지? 칭찬해줘!",
            "같이 놀자~ 응? 응?",
            "라임이는 오늘도 귀여워!"
        )
    )

    private var last: String? = null

    fun pick(state: LimeState): String {
        val list = map[state] ?: map.getValue(LimeState.CHAT)
        var p = list.random()
        if (p == last && list.size > 1) p = (list - p).random()
        last = p
        return p
    }
}
