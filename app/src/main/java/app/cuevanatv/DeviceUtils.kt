package app.cuevanatv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

object DeviceUtils {
    /**
     * Detecta si el dispositivo es un Android TV (Leanback UI).
     */
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Detecta si el dispositivo es móvil (Celular o Tablet).
     */
    fun isMobile(context: Context): Boolean = !isTv(context)
}
