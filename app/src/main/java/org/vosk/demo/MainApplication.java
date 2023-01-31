package org.vosk.demo;

import android.app.Application;
import android.content.Intent;

import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;

public class MainApplication extends Application {

    private int currentResultCode = 0;
    private Intent resultData;
    private Recognizer recognizer;
    private float sampleRate = 16000.f;
    private RecognitionListener listener;
    private int timeOut = -1;

    public void onCreate() {
        super.onCreate();
    }

    public int getCurrentResultCode() {
        return currentResultCode;
    }

    public void setCurrentResultCode(int currentResultCode) {
        this.currentResultCode = currentResultCode;
    }

    public Intent getResultData() {
        return resultData;
    }

    public void setResultData(Intent resultData) {
        this.resultData = resultData;
    }

    public Recognizer getRecognizer() {
        return recognizer;
    }

    public void setRecognizer(Recognizer recognizer) {
        this.recognizer = recognizer;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public RecognitionListener getListener() {
        return listener;
    }

    public void setListener(RecognitionListener listener) {
        this.listener = listener;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }
}
