package com.example.metricrunble

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import com.polidea.rxandroidble2.RxBleDevice

interface CalibrationCallback {
    fun onCalibrationStart()
    fun getDevice(): RxBleDevice?
}

class NextDialogFragment : DialogFragment() {
    private var callback: CalibrationCallback? = null
    private var calibrationStarted = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? CalibrationCallback
    }

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
        val circularProgressBar = view.findViewById<ProgressBar>(R.id.circularProgressBar)
        circularProgressBar.max = 100

        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 20000
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            circularProgressBar.progress = progress

            if (progress == 0 && !calibrationStarted) {
                calibrationStarted = true
                Log.d("NextDialogFragment", "Starting calibration...")

                val device = callback?.getDevice()
                device?.let {
                    (activity as? DeviceActivity)?.readAdcValuesForCalibration(it, object : DeviceActivity.ServerResponseCallback {
                        override fun onResponse(response: String) {
                            Log.d("NextDialogFragment", "Server Response: $response")
                        }

                        override fun onError(error: String) {
                            Log.e("NextDialogFragment", "Error during calibration: $error")
                        }
                    })
                }
            }
        }

        animator.start()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Establece el ancho y alto fijos del diálogo
            val width = resources.getDimensionPixelSize(R.dimen.dialog_width)
            val height = resources.getDimensionPixelSize(R.dimen.dialog_height)
            setLayout(width, height)
        }
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }
}
