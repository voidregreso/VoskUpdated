package org.vosk.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;

import java.io.IOException;
import java.util.Objects;

public class LoopbackService extends Service {
    private Recognizer recognizer;
    private int sampleRate;
    private int bufferSize;
    private AudioRecord recorder;
    private RecThread recThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MyBinder mBinder = new MyBinder();
    private boolean isRecording = false;

    public class MyBinder extends Binder {
        public LoopbackService getService() {
            return LoopbackService.this;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();

        Notification.Builder builder;
        String CHANNEL_ID = "InnerRecordService";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel Channel = new NotificationChannel(CHANNEL_ID,
                "Recording service", NotificationManager.IMPORTANCE_HIGH);
        Channel.enableLights(true);
        Channel.setLightColor(Color.RED);
        Channel.setShowBadge(true);
        Channel.setDescription("InnerRecord Notification");
        Channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (manager != null) {
            manager.createNotificationChannel(Channel);
        }
        builder = new Notification.Builder(this, CHANNEL_ID);
        Notification notification = builder.setAutoCancel(false)
                .setContentTitle("Audio Foreground Service")
                .setContentText("The service is recording loopback audio")
                .setWhen(System.currentTimeMillis())
                .build();
        startForeground(1, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MainApplication mApp = (MainApplication) getApplication();
        int currentResultCode = mApp.getCurrentResultCode();
        Intent resultData = mApp.getResultData();
        Recognizer recognizer = mApp.getRecognizer();
        float sampleRate = mApp.getSampleRate();
        RecognitionListener listener = mApp.getListener();
        int timeOut = mApp.getTimeOut();

        this.recognizer = recognizer;
        this.sampleRate = (int) sampleRate;
        this.bufferSize = AudioRecord.getMinBufferSize(this.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // Initialize recorder
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getApplicationContext()
                .getSystemService(MEDIA_PROJECTION_SERVICE);
        MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(currentResultCode,
                Objects.requireNonNull(resultData));
        AudioRecord.Builder builder = new AudioRecord.Builder();
        builder.setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(this.bufferSize);
        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();
        builder.setAudioPlaybackCaptureConfig(config);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recorder = builder.build();
            if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
                recorder.release();
                IOException ioe = new IOException(
                        "Failed to initialize recorder. Loopback device might be already in use.");
                mainHandler.post(() -> listener.onError(ioe));
            }
        }

        if(timeOut != -1) startListening(listener, timeOut);
        else startListening(listener);

        return super.onStartCommand(intent, flags, startId);
    }

    public boolean startListening(RecognitionListener listener) {
        if (isRecording) return false;
        if(recThread != null) return false;
        recThread = new RecThread(listener);
        isRecording = true;
        recorder.startRecording();
        recThread.start();
        return true;
    }

    public boolean startListening(RecognitionListener listener, int timeout) {
        if (isRecording) return false;
        if(recThread != null) return false;
        recThread = new RecThread(listener, timeout);
        isRecording = true;
        recorder.startRecording();
        recThread.start();
        return true;
    }

    public void setPause(boolean paused) {
        if (recThread != null) {
            recThread.setPause(paused);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRecording = false;

        if(recThread != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            recThread = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }


    private final class RecThread extends Thread {
        private final static int NO_TIMEOUT = -1;
        private RecognitionListener listener;
        private final int timeoutSamples;
        private int remainingSamples;
        private volatile boolean paused = false;
        private volatile boolean reset = false;

        public RecThread(RecognitionListener listener, int timeout) {
            this.listener = listener;
            if(timeout != NO_TIMEOUT) this.timeoutSamples = timeout * sampleRate / 1000;
            else this.timeoutSamples = NO_TIMEOUT;
            this.remainingSamples = this.timeoutSamples;
        }

        public RecThread(RecognitionListener listener) {
            this(listener, NO_TIMEOUT);
        }

        public void setPause(boolean paused) {
            this.paused = paused;
        }

        public void reset() {
            this.reset = true;
        }

        @Override
        public void run() {
            if(recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
                recorder.stop();
                IOException ioe = new IOException(
                        "Failed to start recording. Loopback device might be already in use.");
                mainHandler.post(() -> listener.onError(ioe));
            }
            short[] buf = new short[bufferSize];
            while(isRecording && ((timeoutSamples == NO_TIMEOUT) || (remainingSamples > 0))) {
                int nread = recorder.read(buf, 0, buf.length);
                if(paused) continue;
                if(reset) {
                    recognizer.reset();
                    reset = false;
                }
                if(nread < 0) throw new RuntimeException("Error reading audio buffer");
                if(recognizer.acceptWaveForm(buf, nread)) {
                    String result = recognizer.getResult();
                    mainHandler.post(() -> listener.onResult(result));
                } else {
                    String partialResult = recognizer.getPartialResult();
                    mainHandler.post(() -> listener.onPartialResult(partialResult));
                }

                if (timeoutSamples != NO_TIMEOUT) {
                    remainingSamples = remainingSamples - nread;
                }
            }

            if(!paused) {
                if (timeoutSamples != NO_TIMEOUT && remainingSamples <= 0) {
                    mainHandler.post(() -> listener.onTimeout());
                } else {
                    final String finalResult = recognizer.getFinalResult();
                    mainHandler.post(() -> listener.onFinalResult(finalResult));
                }
            }

        }
    }
}
