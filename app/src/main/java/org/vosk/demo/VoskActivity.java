package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.developer.filepicker.model.DialogConfigs;
import com.developer.filepicker.model.DialogProperties;
import com.developer.filepicker.view.FilePickerDialog;
import com.google.gson.Gson;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class VoskActivity extends AppCompatActivity {

    private static final int STATE_START = 0;
    private static final int STATE_READY = 1;
    private static final int STATE_DONE = 2;
    private static final int STATE_FILE = 3;
    private static final int STATE_MIC = 4;
    private static final int STATE_LOOPBACK = 5;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 108;
    private static final int PERMISSIONS_REQUEST_STORAGE = 125;
    private static final int PERMISSIONS_REQUEST_SCREEN_CAPTURE = 0x4f4f4f;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView presView, fresView, statView;
    private int hertz;
    private String sel_lng;

    private MyListener myListener;

    private Intent resultData;
    private int currentResultCode;

    private LoopbackService loopService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            loopService = ((LoopbackService.MyBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    public class MyListener implements RecognitionListener {
        private boolean appendMode = false;

        public void setAppendMode(boolean appendMode) {
            this.appendMode = appendMode;
        }

        private void showResults(String hypothesis) {
            Result res = new Gson().fromJson(hypothesis, Result.class);
            List<Words> ww = res.getResult();
            statView.setText("");
            String texto = res.getText();
            if(!texto.trim().isEmpty()) {
                if(!appendMode) fresView.setText("Text: " + texto + "\n");
                else fresView.append(texto + "\n");
            }
            if(ww != null) {
                for(Words w : ww) {
                    BigDecimal bd = BigDecimal.valueOf(w.getEnd() - w.getStart()), bd0 = BigDecimal.valueOf(w.getConf() * 100.f);
                    Float d = bd.setScale(4, RoundingMode.HALF_UP).floatValue();
                    Float d0 = bd0.setScale(2, RoundingMode.HALF_UP).floatValue();
                    String pw = "Duration of word \"" + w.getWord() + "\" is " + d + "s, and confidence is " + d0 + "%";
                    statView.append(pw + "\n");
                }
            }
        }

        @Override
        public void onResult(String hypothesis) {
            showResults(hypothesis);
        }

        @Override
        public void onFinalResult(String hypothesis) {
            showResults(hypothesis);
            setUiState(STATE_DONE);
            if (speechStreamService != null) {
                speechStreamService = null;
            }
        }

        @Override
        public void onPartialResult(String hypothesis) {
            Result res = new Gson().fromJson(hypothesis, Result.class);
            String pa = res.getPartial();
            if(!pa.isEmpty()) presView.setText(pa + "\n");
            else {
                String bodyData = "<font color='red'><em>No text is recognized yet</em></font>";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    presView.setText(Html.fromHtml(bodyData,Html.FROM_HTML_MODE_LEGACY));
                } else {
                    presView.setText(Html.fromHtml(bodyData));
                }
            }
        }

        @Override
        public void onError(Exception e) {
            setErrorState(e.getMessage());
        }

        @Override
        public void onTimeout() {
            setUiState(STATE_DONE);
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        int[] rates = getResources().getIntArray(R.array.sample_rate);
        hertz = rates[0];
        String[] langs = getResources().getStringArray(R.array.model_name);
        sel_lng = langs[0];

        // Setup layout
        presView = findViewById(R.id.partial_result_text);
        fresView = findViewById(R.id.final_result_text);
        statView = findViewById(R.id.wordstat_text);
        Spinner spinner = findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                hertz = rates[i];
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Spinner spinnerLang = findViewById(R.id.spinnerLang);
        spinnerLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                sel_lng = langs[i];
                setUiState(STATE_START);
                initModel(sel_lng);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findViewById(R.id.recognize_loopback).setOnClickListener(view -> recognizeLoopback());
        }
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel(sel_lng);
        }


        // storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivityForResult(intent, PERMISSIONS_REQUEST_STORAGE);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_STORAGE);
            }
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (currentResultCode != Activity.RESULT_OK || resultData == null) {
                MediaProjectionManager mediaProjectionManager
                        = (MediaProjectionManager) this.getSystemService(MEDIA_PROJECTION_SERVICE);
                Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
                startActivityForResult(screenCaptureIntent, PERMISSIONS_REQUEST_SCREEN_CAPTURE);
            }
        }

        myListener = new MyListener();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSIONS_REQUEST_SCREEN_CAPTURE) {
            currentResultCode = resultCode;
            resultData = data;
            if(data == null) {
                finish();
            }
        }
    }

    private void initModel(String name) {
        StorageService.unpack(this, name, "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel(sel_lng);
            } else {
                finish();
            }
        }
        if (requestCode == PERMISSIONS_REQUEST_STORAGE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                presView.setText(R.string.preparing);
                presView.setMovementMethod(new ScrollingMovementMethod());
                fresView.setMovementMethod(new ScrollingMovementMethod());
                statView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_loopback).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                presView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.recognize_loopback).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                ((Button) findViewById(R.id.recognize_loopback)).setText(R.string.recognize_loopback);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.recognize_loopback).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                presView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_loopback).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                presView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.recognize_loopback).setEnabled(false);
                findViewById(R.id.pause).setEnabled((true));
                break;
            case STATE_LOOPBACK:
                ((Button) findViewById(R.id.recognize_loopback)).setText(R.string.stop_loopback);
                presView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_loopback).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        presView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        ((Button) findViewById(R.id.recognize_loopback)).setText(R.string.recognize_loopback);
        setUiState(STATE_DONE);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            DialogProperties properties = new DialogProperties();
            properties.selection_mode = DialogConfigs.SINGLE_MODE;
            properties.selection_type = DialogConfigs.FILE_SELECT;
            properties.root = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            properties.extensions = new String[]{"wav"};
            properties.show_hidden_files = true;
            FilePickerDialog dialog = new FilePickerDialog(this, properties);
            dialog.setTitle("Select a File");
            dialog.setDialogSelectionListener(files -> {
                setUiState(STATE_FILE);
                MediaExtractor extractor = new MediaExtractor();
                int sampleRate;
                try {
                    extractor.setDataSource(files[0]);
                    int numTracks = extractor.getTrackCount();
                    if(numTracks != 1) {
                        setUiState(STATE_DONE);
                        setErrorState("Audio has " + numTracks + " tracks. Aborted.");
                        return;
                    }
                    MediaFormat format = extractor.getTrackFormat(0);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio")) {
                        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if(channels != 1) {
                            setUiState(STATE_DONE);
                            setErrorState("Audio has " + channels + " channels. Aborted.");
                            return;
                        }
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        myListener.setAppendMode(true);
                        fresView.setText("");
                        Recognizer rec = new Recognizer(model, sampleRate);
                        rec.setWords(true);
                        rec.setMaxAlternatives(0);
                        InputStream ais = new FileInputStream(files[0]);
                        if (ais.skip(44) != 44) throw new IOException("File too short");
                        speechStreamService = new SpeechStreamService(rec, ais, sampleRate);
                        speechStreamService.start(myListener);
                    }
                } catch (Exception e) {
                    setUiState(STATE_DONE);
                    e.printStackTrace();
                    setErrorState(e.getMessage());
                }
            });
            dialog.show();
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, hertz);
                myListener.setAppendMode(false);
                rec.setWords(true);
                rec.setMaxAlternatives(0);
                speechService = new SpeechService(rec, hertz);
                speechService.startListening(myListener);
            } catch (Exception e) {
                setErrorState(e.getMessage());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void recognizeLoopback() {
        if (loopService != null) {
            Intent serviceIntent = new Intent(this, LoopbackService.class);
            unbindService(mServiceConnection);
            stopService(serviceIntent);
            loopService = null;
            setUiState(STATE_DONE);
        } else {
            setUiState(STATE_LOOPBACK);
            try {
                Recognizer rec = new Recognizer(model, hertz);
                myListener.setAppendMode(false);
                rec.setWords(true);
                rec.setMaxAlternatives(0);
                Intent serviceIntent = new Intent(this, LoopbackService.class);
                MainApplication mApp = (MainApplication)getApplication();
                mApp.setCurrentResultCode(currentResultCode);
                mApp.setResultData(resultData);
                mApp.setRecognizer(rec);
                mApp.setSampleRate(hertz);
                mApp.setListener(myListener);
                bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
                startForegroundService(serviceIntent);
            } catch (Exception e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
        if (loopService != null) {
            loopService.setPause(checked);
        }
    }


}
