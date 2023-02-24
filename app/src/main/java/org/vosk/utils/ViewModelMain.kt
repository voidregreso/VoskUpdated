package org.vosk.utils

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

object ViewModelMain : ViewModel() {

    // Creación de ventanas Hover: suprimir (normal)
    var isShowSuspendWindow = MutableLiveData<Boolean>()

    // Visualización de la ventana Hover: ocultar
    var isVisible = MutableLiveData<Boolean>()

}