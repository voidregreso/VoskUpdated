package org.vosk.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import org.vosk.utils.Recognizer
import org.vosk.demo.MainApplication
import org.vosk.utils.RecognitionListener
import java.io.IOException
import java.util.*

class LoopbackService : Service() {
    private var recognizer: Recognizer? = null
    private var sampleRate = 0
    private var bufferSize = 0
    private var recorder: AudioRecord? = null
    private var recThread: RecThread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mBinder = MyBinder()
    private var isRecording = false

    private val NO_TIMEOUT = -1

    inner class MyBinder : Binder() {
        val service: LoopbackService
            get() = this@LoopbackService
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        val builder: Notification.Builder
        val CHANNEL_ID = "InnerRecordService"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val Channel = NotificationChannel(
            CHANNEL_ID,
            "Recording service", NotificationManager.IMPORTANCE_HIGH
        )
        Channel.enableLights(true)
        Channel.lightColor = Color.RED
        Channel.setShowBadge(true)
        Channel.description = "InnerRecord Notification"
        Channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager?.createNotificationChannel(Channel)
        builder = Notification.Builder(this, CHANNEL_ID)
        val notification = builder.setAutoCancel(false)
            .setContentTitle("Audio Foreground Service")
            .setContentText("The service is recording loopback audio")
            .setWhen(System.currentTimeMillis())
            .build()
        startForeground(1, notification)
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val mApp = application as MainApplication
        val currentResultCode = mApp.currentResultCode
        val resultData = mApp.resultData
        val recognizer = mApp.recognizer
        val sampleRate = mApp.sampleRate
        val listener = mApp.listener
        val timeOut = mApp.timeOut
        this.recognizer = recognizer
        this.sampleRate = sampleRate.toInt()
        bufferSize = AudioRecord.getMinBufferSize(
            this.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Initialize recorder
        val mediaProjectionManager = applicationContext
            .getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(
            currentResultCode,
            Objects.requireNonNull(resultData)
        )
        val builder = AudioRecord.Builder()
        builder.setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
            .setBufferSizeInBytes(bufferSize)
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        builder.setAudioPlaybackCaptureConfig(config)
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            recorder = builder.build()
            if (recorder?.getState() == AudioRecord.STATE_UNINITIALIZED) {
                recorder?.release()
                val ioe = IOException(
                    "Failed to initialize recorder. Loopback device might be already in use."
                )
                mainHandler.post { listener.onError(ioe) }
            }
        }
        if (timeOut != -1) startListening(listener, timeOut) else startListening(listener)
        return super.onStartCommand(intent, flags, startId)
    }

    fun startListening(listener: RecognitionListener): Boolean {
        if (isRecording) return false
        if (recThread != null) return false
        recThread = RecThread(listener)
        isRecording = true
        recorder!!.startRecording()
        recThread!!.start()
        return true
    }

    fun startListening(listener: RecognitionListener, timeout: Int): Boolean {
        if (isRecording) return false
        if (recThread != null) return false
        recThread = RecThread(listener, timeout)
        isRecording = true
        recorder!!.startRecording()
        recThread!!.start()
        return true
    }

    fun setPause(paused: Boolean) {
        if (recThread != null) {
            recThread!!.setPause(paused)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        if (recThread != null) {
            recorder!!.stop()
            recorder!!.release()
            recorder = null
            recThread = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        return super.onUnbind(intent)
    }

    private inner class RecThread @JvmOverloads constructor(
        private val listener: RecognitionListener,
        timeout: Int = NO_TIMEOUT
    ) : Thread() {
        private val timeoutSamples: Int
        private var remainingSamples: Int

        @Volatile
        private var paused = false

        @Volatile
        private var reset = false

        init {
            if (timeout != NO_TIMEOUT) timeoutSamples =
                timeout * sampleRate / 1000 else timeoutSamples = NO_TIMEOUT
            remainingSamples = timeoutSamples
        }

        fun setPause(paused: Boolean) {
            this.paused = paused
        }

        fun reset() {
            reset = true
        }

        override fun run() {
            if (recorder!!.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                recorder!!.stop()
                val ioe = IOException(
                    "Failed to start recording. Loopback device might be already in use."
                )
                mainHandler.post { listener.onError(ioe) }
            }
            val buf = ShortArray(bufferSize)
            while (isRecording && (timeoutSamples == NO_TIMEOUT || remainingSamples > 0)) {
                val nread = recorder!!.read(buf, 0, buf.size)
                if (paused) continue
                if (reset) {
                    recognizer!!.reset()
                    reset = false
                }
                if (nread < 0) throw RuntimeException("Error reading audio buffer")
                if (recognizer!!.acceptWaveForm(buf, nread)) {
                    val result = recognizer!!.result
                    mainHandler.post { listener.onResult(result) }
                } else {
                    val partialResult = recognizer!!.partialResult
                    mainHandler.post { listener.onPartialResult(partialResult) }
                }
                if (timeoutSamples != NO_TIMEOUT) {
                    remainingSamples = remainingSamples - nread
                }
            }
            if (!paused) {
                if (timeoutSamples != NO_TIMEOUT && remainingSamples <= 0) {
                    mainHandler.post { listener.onTimeout() }
                } else {
                    val finalResult = recognizer!!.finalResult
                    mainHandler.post { listener.onFinalResult(finalResult) }
                }
            }
        }
    }
}