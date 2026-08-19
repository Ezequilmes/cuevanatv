package app.cuevanatv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : RowsSupportFragment() {
    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        verticalGridView.setPadding(0, 40, 56, 40)
        
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter(4))
        adapter = rowsAdapter
        
        loadSettings()

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is SettingsItem) {
                when (item.action) {
                    "action://refresh" -> {
                        val parent = parentFragment as? MainBrowseFragment
                        parent?.refreshData()
                        Toast.makeText(context, "Sincronizando...", Toast.LENGTH_SHORT).show()
                    }
                    "action://premium" -> {
                        val email = Auth.getEmail(requireContext())
                        ejecutarCobroMercadoPago(email)
                    }
                    "action://logout" -> {
                        Auth.clear(requireContext())
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finish()
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        val listRowAdapter = ArrayObjectAdapter(ModernSettingsCardPresenter())
        listRowAdapter.add(SettingsItem("Sincronizar", "Actualizar contenido", "action://refresh"))
        listRowAdapter.add(SettingsItem("Premium", "Activar suscripción", "action://premium"))
        listRowAdapter.add(SettingsItem("Cerrar Sesión", "Salir de la cuenta", "action://logout"))
        
        rowsAdapter?.add(ListRow(HeaderItem(1, "Ajustes"), listRowAdapter))
    }

    private fun ejecutarCobroMercadoPago(email: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val initPoint = ApiClient(requireContext()).createMercadoPagoPreference(email)
            withContext(Dispatchers.Main) {
                if (!initPoint.isNullOrEmpty()) {
                    val uiModeManager = requireContext().getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                        mostrarQrEnDialogo(initPoint)
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(initPoint)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Navegador no disponible.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Error al conectar con MercadoPago", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarQrEnDialogo(url: String) {
        try {
            val bitmap = BarcodeEncoder().encodeBitmap(url, BarcodeFormat.QR_CODE, 500, 500)
            val imageView = ImageView(requireContext())
            imageView.setImageBitmap(bitmap)
            imageView.setPadding(40, 40, 40, 40)
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Escanea para Pagar Premium")
                .setView(imageView)
                .setPositiveButton("CERRAR", null)
                .show()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error QR: ${e.message}")
        }
    }
}

data class SettingsItem(val title: String, val description: String, val action: String)

class ModernSettingsCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setMainImageDimensions(200, 200)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val setting = item as SettingsItem
        val card = viewHolder.view as ImageCardView
        card.titleText = setting.title
        card.contentText = setting.description
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
