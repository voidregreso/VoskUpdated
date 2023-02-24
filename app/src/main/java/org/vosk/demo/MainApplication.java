package org.vosk.demo;

import android.app.Application;
import android.content.Intent;
import android.widget.TextView;

import org.vosk.utils.Recognizer;
import org.vosk.utils.RecognitionListener;

public class MainApplication extends Application {

    private int currentResultCode = 0;
    private Intent resultData;
    private Recognizer recognizer;
    private float sampleRate = 16000.f;
    private RecognitionListener listener;
    private int timeOut = -1;
    private TextView subTextView;

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

    public TextView getSubTextView() {
        return subTextView;
    }

    public void setSubTextView(TextView subTextView) {
        this.subTextView = subTextView;
    }
}
