package com.example.metricrunble

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.delay

class CalibrationDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        dialog?.window?.setBackgroundDrawableResource(R.drawable.calibrate_background)
        return inflater.inflate(R.layout.fragment_calibration_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textView = view.findViewById<TextView>(R.id.bienvenido)
        val textView2 = view.findViewById<TextView>(R.id.bienvenido2)
        val imageView = view.findViewById<ImageView>(R.id.gears)
        imageView.visibility = View.GONE
        textView2.visibility = View.GONE
        val animation1 = AnimationUtils.loadAnimation(context, R.anim.text_animation)
        val animation2 = AnimationUtils.loadAnimation(context, R.anim.text_animation) // Puedes usar una animación diferente si lo prefieres

        // Listener para la primera animación
        animation1.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // Inicia la segunda animación cuando la primera termina
                textView2.visibility = View.VISIBLE
                textView2.startAnimation(animation2)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        // Listener para la segunda animación
        animation2.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // Utiliza un Handler para cargar el GIF después de un retraso
                Handler(Looper.getMainLooper()).postDelayed({
                    imageView.visibility = View.VISIBLE
                    Glide.with(this@CalibrationDialogFragment).load(R.drawable.gears2).into(imageView)
                    val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
                    valueAnimator.duration = 500 // Duración de la animación en milisegundos
                    valueAnimator.addUpdateListener { animator ->
                        val value = animator.animatedValue as Float
                        imageView.alpha = value // Cambia la propiedad que quieras animar (por ejemplo, alpha para un efecto de desvanecimiento)
                    }
                    valueAnimator.start()
                    }, 500) // 500 milisegundos de retraso
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        // Inicia la primera animación
        textView.startAnimation(animation1)

        val nextButton = view.findViewById<Button>(R.id.next1)
        nextButton.setOnClickListener {
            // Cierra este modal
            dismiss()
            // Abre el siguiente modal
            val nextDialogFragment = NextDialogFragment() // Asegúrate de definir el siguiente fragmento de diálogo
            nextDialogFragment.show(parentFragmentManager, "nextDialog")
        }
    }




    private fun startCalibration() {
        // Lógica de calibración
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
