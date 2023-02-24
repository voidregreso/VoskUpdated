package org.vosk.service

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.lifecycle.LifecycleService
import org.vosk.demo.MainApplication
import org.vosk.utils.ItemViewTouchListener
import org.vosk.utils.Utils
import org.vosk.utils.ViewModelMain
import org.vosk.demo.R

class HoverBoxService : LifecycleService() {

    // Declarar una variable de WindowManager y una vista para el cuadro de suspensión.
    private lateinit var windowManager: WindowManager
    private var floatRootView: View? = null

    // Crea el servicio y llama a initObserve().
    override fun onCreate() {
        super.onCreate()
        initObserve()
    }

    // Inicializa ViewModelMain y observa sus variables de LiveData.
    private fun initObserve() {
        ViewModelMain.apply {
            isVisible.observe(this@HoverBoxService) {
                floatRootView?.visibility = if (it) View.VISIBLE else View.GONE
            }
            isShowSuspendWindow.observe(this@HoverBoxService) {
                if (it) {
                    showWindow()
                } else {
                    if (!Utils.isNull(floatRootView)) {
                        if (!Utils.isNull(floatRootView?.windowToken)) {
                            if (!Utils.isNull(windowManager)) {
                                windowManager?.removeView(floatRootView)
                            }
                        }
                    }
                }
            }
        }
    }

    // Muestra el cuadro de suspensión en la pantalla del usuario.
    @SuppressLint("ClickableViewAccessibility")
    private fun showWindow() {
        val mApp = application as MainApplication
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(outMetrics)
        var layoutParam = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.RGBA_8888
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            // Posición y tamaño del cuadro de suspensión
            width = WRAP_CONTENT
            height = WRAP_CONTENT
            gravity = Gravity.CENTER or Gravity.TOP
            // Ubicar en la pantalla
            x = 0
            y = (outMetrics.heightPixels - height) / 6 * 5;
        }
        // Crear una nueva vista para el cuadro de suspensión
        floatRootView = LayoutInflater.from(this).inflate(R.layout.activity_float_item, null)
        floatRootView?.setOnTouchListener(ItemViewTouchListener(layoutParam, windowManager))
        // Agregar la vista del cuadro de suspensión al WindowManager
        windowManager.addView(floatRootView, layoutParam)
        // Establecer texto inicial
        mApp.subTextView = floatRootView?.findViewById(R.id.subtitle_caption)
        mApp.subTextView.text = getString(R.string.ready_hover_tip)
    }

}