package com.example.metricrunble


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

class NextDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(R.drawable.calibrate_background)
        return inflater.inflate(R.layout.fragment_next_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Configura los elementos de la vista según tus necesidades
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Establece el ancho y alto fijos del diálogo
            val width = resources.getDimensionPixelSize(R.dimen.dialog_width)
            val height = resources.getDimensionPixelSize(R.dimen.dialog_height)
            setLayout(width, height)

            // Opcional: Establece los bordes redondos
            setBackgroundDrawableResource(R.drawable.calibrate_background)
        }
    }
}
