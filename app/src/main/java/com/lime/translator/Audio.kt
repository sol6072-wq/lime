package com.lime.translator

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val SAMPLE_RATE = 16000
const val FRAME = SAMPLE_RATE / 50 // 20ms

fun rms(a: ShortArray, from: Int = 0, to: Int = a.size): Float {
    if (to <= from) return 0f
    var sum = 0.0
    for (i in from until to) {
        val v = a[i].toDouble()
        sum += v * v
    }
    return sqrt(sum / (to - from)).toFloat()
}

/** 마이크에서 PCM(16kHz, 모노)을 받아 모아두는 녹음기 */
class PcmRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val chunks = ArrayList<ShortArray>()

    /** 0~1, 화면 표시용 소리 크기 */
    @Volatile var level: Float = 0f
        private set

    val isRunning: Boolean get() = running

    /**
     * @param maxMs 최대 녹음 길이
     * @param silenceStopMs 0보다 크면, 소리가 난 뒤 이 시간만큼 조용하면 자동 정지
     * @param onAutoStop 자동 정지됐을 때 (녹음 스레드에서 호출됨)
     */
    @SuppressLint("MissingPermission")
    fun start(maxMs: Long, silenceStopMs: Long = 0, onAutoStop: (() -> Unit)? = null): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, SAMPLE_RATE)
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release(); return false
        }
        synchronized(chunks) { chunks.clear() }
        record = rec
        running = true
        thread = Thread {
            val buf = ShortArray(FRAME)
            var total = 0L
            var heard = false
            var silentMs = 0L
            while (running) {
                val n = try { rec.read(buf, 0, buf.size) } catch (e: Exception) { -1 }
                if (n < 0) break
                if (n == 0) continue
                val copy = buf.copyOf(n)
                synchronized(chunks) { chunks.add(copy) }
                val r = rms(copy)
                level = (r / 5000f).coerceIn(0f, 1f)
                val ms = n * 1000L / SAMPLE_RATE
                total += ms
                if (silenceStopMs > 0) {
                    if (r > 700f) { heard = true; silentMs = 0 } else if (heard) silentMs += ms
                    if (heard && silentMs >= silenceStopMs) {
                        running = false; onAutoStop?.invoke(); break
                    }
                }
                if (total >= maxMs) {
                    running = false; onAutoStop?.invoke(); break
                }
            }
        }.also { it.start() }
        return true
    }

    /** 녹음을 멈추고 모은 소리를 돌려줌 */
    fun stop(): ShortArray {
        running = false
        val t = thread
        if (t != null && t != Thread.currentThread()) {
            try { t.join(600) } catch (_: InterruptedException) {}
        }
        record?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        record = null
        thread = null
        level = 0f
        return synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val out = ShortArray(total)
            var p = 0
            for (c in chunks) { c.copyInto(out, p); p += c.size }
            chunks.clear()
            out
        }
    }
}

object Wav {
    fun write(file: File, pcm: ShortArray, sr: Int = SAMPLE_RATE) {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1.toShort())       // PCM
        bb.putShort(1.toShort())       // mono
        bb.putInt(sr)
        bb.putInt(sr * 2)
        bb.putShort(2.toShort())
        bb.putShort(16.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLen)
        for (s in pcm) bb.putShort(s)
        file.writeBytes(bb.array())
    }

    /** 앞뒤 무음 잘라내기 (앞뒤로 0.1초 여유) */
    fun trimSilence(pcm: ShortArray): ShortArray {
        val frames = pcm.size / FRAME
        if (frames == 0) return pcm
        var peak = 0f
        val r = FloatArray(frames) { i -> rms(pcm, i * FRAME, (i + 1) * FRAME).also { peak = max(peak, it) } }
        val thr = max(400f, peak * 0.08f)
        val first = r.indexOfFirst { it > thr }
        val last = r.indexOfLast { it > thr }
        if (first < 0) return ShortArray(0)
        val pad = 5
        val s = max(0, first - pad) * FRAME
        val e = min(frames, last + 1 + pad) * FRAME
        return pcm.copyOfRange(s, e)
    }
}

/** 녹음 파일 / 합성 소리 재생 */
class SoundPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var endRunnable: Runnable? = null

    fun playFile(file: File, onDone: () -> Unit) {
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { stop(); onDone() }
            p.setOnErrorListener { _, _, _ -> stop(); onDone(); true }
            p.prepare()
            p.start()
            mp = p
        } catch (e: Exception) {
            stop(); onDone()
        }
    }

    fun playPcm(pcm: ShortArray, sr: Int, onDone: () -> Unit) {
        stop()
        if (pcm.isEmpty()) { onDone(); return }
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t.play()
            track = t
            val r = Runnable { stop(); onDone() }
            endRunnable = r
            main.postDelayed(r, pcm.size * 1000L / sr + 150)
        } catch (e: Exception) {
            stop(); onDone()
        }
    }

    fun stop() {
        endRunnable?.let { main.removeCallbacks(it) }
        endRunnable = null
        mp?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        mp = null
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
    }
}
