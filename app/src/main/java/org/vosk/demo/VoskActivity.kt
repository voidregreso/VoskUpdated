package org.vosk.demo

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog
import com.google.gson.Gson
import org.vosk.service.*
import org.vosk.service.LoopbackService.MyBinder
import org.vosk.utils.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode

class VoskActivity : AppCompatActivity() {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechStreamService: SpeechStreamService? = null
    private var presView: TextView? = null
    private var fresView: TextView? = null
    private var statView: TextView? = null
    private var hoverBox: CheckBox? = null
    private var hertz = 0
    private var shouldShowOnHover = false
    private var sel_lng: String? = null
    private var myListener: MyListener? = null
    private var resultData: Intent? = null
    private var currentResultCode = 0
    private var loopService: LoopbackService? = null
    private var hoverService: Intent? = null
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            loopService = (service as MyBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    inner class MyListener : RecognitionListener {
        private var appendMode = false
        fun setAppendMode(appendMode: Boolean) {
            this.appendMode = appendMode
        }

        private fun cutOff(passage: String): String {
            return if (passage.split(" ").size > 20) {
                val words = passage.split(" ")
                val startIndex = words.size - 20
                words.subList(startIndex, words.size).joinToString(" ")
            } else {
                passage
            }
        }

        private fun showResults(hypothesis: String) {
            val mApp = application as MainApplication
            val res = Gson().fromJson(hypothesis, Result::class.java)
            val texto = res.text.trim { it <= ' ' }
            // Show in hover subtitle box
            if(shouldShowOnHover) mApp.subTextView.text = cutOff(texto)
            // Append to app console
            if (!texto.isEmpty()) {
                if (!appendMode) {
                    fresView!!.text = "Text: $texto\n"
                } else {
                    fresView!!.append(
                        """
    $texto
    
    """.trimIndent()
                    )
                }
            }
            val ww = res.result
            if (ww != null) {
                for (w in ww) {
                    val bd = BigDecimal.valueOf((w.end - w.start).toDouble())
                    val d = bd.setScale(4, RoundingMode.HALF_UP).toFloat()
                    val d0 = BigDecimal.valueOf((w.conf * 100f).toDouble())
                        .setScale(2, RoundingMode.HALF_UP).toFloat()
                    val pw =
                        "Duration of word \"" + w.word + "\" is " + d + "s, and confidence is " + d0 + "%"
                    statView!!.append(
                        """
    $pw
    
    """.trimIndent()
                    )
                }
            }
        }

        override fun onResult(hypothesis: String) {
            showResults(hypothesis)
        }

        override fun onFinalResult(hypothesis: String) {
            showResults(hypothesis)
            setUiState(STATE_DONE)
            speechStreamService = null
        }

        override fun onPartialResult(hypothesis: String) {
            val mApp = application as MainApplication
            val res = Gson().fromJson(hypothesis, Result::class.java)
            val pa = res.partial
            // Show in hover subtitle box
            if(shouldShowOnHover) mApp.subTextView.text = cutOff(pa)
            // Append to app console
            if (!pa.isEmpty()) {
                presView!!.text = """
                    $pa
                    
                    """.trimIndent()
            } else {
                val bodyData = "<font color='red'><em>No text is recognized yet</em></font>"
                presView!!.text = Html.fromHtml(bodyData, Html.FROM_HTML_MODE_LEGACY)
            }
        }

        override fun onError(e: Exception) {
            setErrorState(e.message)
        }

        override fun onTimeout() {
            setUiState(STATE_DONE)
        }
    }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)

        // Initialize variables
        hertz = resources.getIntArray(R.array.sample_rate)[0]
        sel_lng = resources.getStringArray(R.array.model_name)[0]
        presView = findViewById(R.id.partial_result_text)
        fresView = findViewById(R.id.final_result_text)
        statView = findViewById(R.id.wordstat_text)
        hoverBox = findViewById(R.id.cbHoverLrc)

        // Setup spinners
        val spinner = findViewById<Spinner>(R.id.spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                hertz = resources.getIntArray(R.array.sample_rate)[i]
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        val spinnerLang = findViewById<Spinner>(R.id.spinnerLang)
        spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                sel_lng = resources.getStringArray(R.array.model_name)[i]
                setUiState(STATE_START)
                initModel(sel_lng)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        // Set up UI
        setUiState(STATE_START)
        findViewById<View>(R.id.recognize_file).setOnClickListener { recognizeFile() }
        findViewById<View>(R.id.recognize_mic).setOnClickListener { recognizeMicrophone() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findViewById<View>(R.id.recognize_loopback).setOnClickListener { recognizeLoopback() }
        }
        (findViewById<View>(R.id.pause) as ToggleButton).setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            pause(
                isChecked
            )
        }
        hoverBox?.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                if(hoverService == null)
                    hoverService = Intent(this, HoverBoxService::class.java)
                startService(hoverService)
                Utils.checkSuspendedWindowPermission(this) {
                    shouldShowOnHover = true
                    ViewModelMain.isShowSuspendWindow.postValue(true)
                }
            } else {
                ViewModelMain.isShowSuspendWindow.postValue(false)
                shouldShowOnHover = false
                stopService(hoverService)
            }
        }
        LibVosk.setLogLevel(LogLevel.INFO)

        // Request permissions
        val permissionCheck =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            initModel(sel_lng)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:" + this.packageName)
            startActivityForResult(intent, PERMISSIONS_REQUEST_STORAGE)
        } else if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSIONS_REQUEST_STORAGE
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (currentResultCode != RESULT_OK || resultData == null)) {
            val mediaProjectionManager =
                this.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(screenCaptureIntent, PERMISSIONS_REQUEST_SCREEN_CAPTURE)
        }
        myListener = MyListener()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSIONS_REQUEST_SCREEN_CAPTURE) {
            currentResultCode = resultCode
            resultData = data
            if (data == null) {
                finish()
            }
        }
    }

    private fun initModel(name: String?) {
        if (name != null) {
            StorageService.unpack(this, name, "model",
                { model: Model? ->
                    this.model = model
                    setUiState(STATE_READY)
                }
            ) { exception: IOException -> setErrorState("Failed to unpack the model" + exception.message) }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel(sel_lng)
            } else {
                finish()
            }
        }
        if (requestCode == PERMISSIONS_REQUEST_STORAGE) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                finish()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (speechService != null) {
            speechService!!.stop()
            speechService!!.shutdown()
        }
        if (speechStreamService != null) {
            speechStreamService!!.stop()
        }
    }

    private fun setUiState(state: Int) {
        when (state) {
            STATE_START -> {
                presView!!.setText(R.string.preparing)
                presView!!.movementMethod = ScrollingMovementMethod()
                fresView!!.movementMethod = ScrollingMovementMethod()
                statView!!.movementMethod = ScrollingMovementMethod()
                findViewById<View>(R.id.recognize_file).isEnabled = false
                findViewById<View>(R.id.recognize_mic).isEnabled = false
                findViewById<View>(R.id.recognize_loopback).isEnabled = false
                findViewById<View>(R.id.pause).isEnabled = false
            }
            STATE_READY -> {
                presView!!.setText(R.string.ready)
                (findViewById<View>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
                findViewById<View>(R.id.recognize_file).isEnabled = true
                findViewById<View>(R.id.recognize_mic).isEnabled = true
                findViewById<View>(R.id.recognize_loopback).isEnabled = true
                findViewById<View>(R.id.pause).isEnabled = false
            }
            STATE_DONE -> {
                (findViewById<View>(R.id.recognize_file) as Button).setText(R.string.recognize_file)
                (findViewById<View>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
                (findViewById<View>(R.id.recognize_loopback) as Button).setText(R.string.recognize_loopback)
                findViewById<View>(R.id.recognize_file).isEnabled = true
                findViewById<View>(R.id.recognize_mic).isEnabled = true
                findViewById<View>(R.id.recognize_loopback).isEnabled = true
                findViewById<View>(R.id.pause).isEnabled = false
                (findViewById<View>(R.id.pause) as ToggleButton).isChecked =
                    false
            }
            STATE_FILE -> {
                (findViewById<View>(R.id.recognize_file) as Button).setText(R.string.stop_file)
                presView!!.text = getString(R.string.starting)
                findViewById<View>(R.id.recognize_mic).isEnabled = false
                findViewById<View>(R.id.recognize_loopback).isEnabled = false
                findViewById<View>(R.id.recognize_file).isEnabled = true
                findViewById<View>(R.id.pause).isEnabled = false
            }
            STATE_MIC -> {
                (findViewById<View>(R.id.recognize_mic) as Button).setText(R.string.stop_microphone)
                presView!!.text = getString(R.string.say_something)
                findViewById<View>(R.id.recognize_file).isEnabled = false
                findViewById<View>(R.id.recognize_mic).isEnabled = true
                findViewById<View>(R.id.recognize_loopback).isEnabled = false
                findViewById<View>(R.id.pause).isEnabled = true
            }
            STATE_LOOPBACK -> {
                (findViewById<View>(R.id.recognize_loopback) as Button).setText(R.string.stop_loopback)
                presView!!.text = getString(R.string.say_something)
                findViewById<View>(R.id.recognize_file).isEnabled = false
                findViewById<View>(R.id.recognize_mic).isEnabled = false
                findViewById<View>(R.id.recognize_loopback).isEnabled = true
                findViewById<View>(R.id.pause).isEnabled = true
            }
            else -> throw IllegalStateException("Unexpected value: $state")
        }
    }

    private fun setErrorState(message: String?) {
        presView!!.text = message
        (findViewById<View>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
        (findViewById<View>(R.id.recognize_loopback) as Button).setText(R.string.recognize_loopback)
        setUiState(STATE_DONE)
    }

    private fun recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE)
            speechStreamService!!.stop()
            speechStreamService = null
        } else {
            val properties = DialogProperties()
            properties.selection_mode = DialogConfigs.SINGLE_MODE
            properties.selection_type = DialogConfigs.FILE_SELECT
            properties.root = File(Environment.getExternalStorageDirectory().toString())
            properties.extensions = arrayOf("wav")
            properties.show_hidden_files = true
            val dialog = FilePickerDialog(this, properties)
            dialog.setTitle("Select a File")
            dialog.setDialogSelectionListener { files: Array<String?> ->
                setUiState(STATE_FILE)
                val extractor = MediaExtractor()
                val sampleRate: Int
                try {
                    extractor.setDataSource(files[0]!!)
                    val numTracks = extractor.trackCount
                    if (numTracks != 1) {
                        setUiState(STATE_DONE)
                        setErrorState("Audio has $numTracks tracks. Aborted.")
                        return@setDialogSelectionListener
                    }
                    val format = extractor.getTrackFormat(0)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime!!.startsWith("audio")) {
                        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (channels != 1) {
                            setUiState(STATE_DONE)
                            setErrorState("Audio has $channels channels. Aborted.")
                            return@setDialogSelectionListener
                        }
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        myListener!!.setAppendMode(true)
                        fresView!!.text = ""
                        val rec = Recognizer(
                            model,
                            sampleRate.toFloat()
                        )
                        rec.setWords(true)
                        rec.setMaxAlternatives(0)
                        val ais: InputStream = FileInputStream(files[0])
                        if (ais.skip(44) != 44L) throw IOException("File too short")
                        speechStreamService =
                            SpeechStreamService(
                                rec,
                                ais,
                                sampleRate.toFloat()
                            )
                        speechStreamService!!.start(myListener)
                    }
                } catch (e: Exception) {
                    setUiState(STATE_DONE)
                    e.printStackTrace()
                    setErrorState(e.message)
                }
            }
            dialog.show()
        }
    }

    private fun recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE)
            speechService!!.stop()
            speechService = null
        } else {
            setUiState(STATE_MIC)
            try {
                val rec = Recognizer(model, hertz.toFloat())
                myListener!!.setAppendMode(false)
                rec.setWords(true)
                rec.setMaxAlternatives(0)
                speechService =
                    SpeechService(rec, hertz.toFloat())
                speechService!!.startListening(myListener)
            } catch (e: Exception) {
                setErrorState(e.message)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun recognizeLoopback() {
        if (loopService != null) {
            val serviceIntent = Intent(this, LoopbackService::class.java)
            unbindService(mServiceConnection)
            stopService(serviceIntent)
            loopService = null
            setUiState(STATE_DONE)
        } else {
            setUiState(STATE_LOOPBACK)
            try {
                val rec = Recognizer(model, hertz.toFloat())
                myListener!!.setAppendMode(false)
                rec.setWords(true)
                rec.setMaxAlternatives(0)
                val serviceIntent = Intent(this, LoopbackService::class.java)
                val mApp = application as MainApplication
                mApp.currentResultCode = currentResultCode
                mApp.resultData = resultData
                mApp.recognizer = rec
                mApp.sampleRate = hertz.toFloat()
                mApp.listener = myListener
                bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE)
                startForegroundService(serviceIntent)
            } catch (e: Exception) {
                setErrorState(e.message)
            }
        }
    }

    private fun pause(checked: Boolean) {
        if (speechService != null) {
            speechService!!.setPause(checked)
        }
        if (loopService != null) {
            loopService!!.setPause(checked)
        }
    }

    companion object {
        private const val STATE_START = 0
        private const val STATE_READY = 1
        private const val STATE_DONE = 2
        private const val STATE_FILE = 3
        private const val STATE_MIC = 4
        private const val STATE_LOOPBACK = 5

        /* Used to handle permission request */
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 108
        private const val PERMISSIONS_REQUEST_STORAGE = 125
        private const val PERMISSIONS_REQUEST_SCREEN_CAPTURE = 0x4f4f4f
    }
}