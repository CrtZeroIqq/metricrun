package com.example.metricrunble

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.metricrunble.R
import com.google.android.material.textfield.TextInputEditText
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import android.content.Intent


class SignUpActivity : AppCompatActivity() {

    // Define your views from layout
    lateinit var name: TextInputEditText
    lateinit var email: TextInputEditText
    lateinit var password: TextInputEditText
    lateinit var passwordConfirmation: TextInputEditText
    lateinit var phone: TextInputEditText
    lateinit var equipment_serial: TextInputEditText
    lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up)

        // Initialize your views
        name = findViewById(R.id.name)
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        passwordConfirmation = findViewById(R.id.password_confirmation)
        phone = findViewById(R.id.phone)
        equipment_serial = findViewById(R.id.equipment_serial)
        registerButton = findViewById(R.id.signup)

        registerButton.setOnClickListener {
            if (password.text.toString() == passwordConfirmation.text.toString()) {
                // Prepare for the API call
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://54.221.216.132/metricrun/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(ApiService::class.java)

                val user = User(
                    name.text.toString(),
                    email.text.toString(),
                    password.text.toString(),
                    phone.text.toString(),
                    equipment_serial.text.toString()
                )

                // Send the user data to the server
                service.registerUser(user).enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            // Registration was successful, log the success and notify the user
                            Log.d("Response", "Data uploaded successfully")
                            Toast.makeText(this@SignUpActivity, "Registro Exitoso!", Toast.LENGTH_SHORT).show()

                            // After successful registration, navigate back to the login activity
                            val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finish() // This will close the SignUpActivity
                        } else {
                            // Handle the error
                            Log.d("Response", "Data upload failed")
                            Toast.makeText(this@SignUpActivity, "Registro Falló :(", Toast.LENGTH_SHORT).show()
                        }
                    }


                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        // Handle the failure
                        Log.d("Failure", t.message.toString())
                        Toast.makeText(this@SignUpActivity, "Registration failed: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                Toast.makeText(this@SignUpActivity, "Los passwords no coinciden...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    interface ApiService {
        @POST("save_user.php")
        fun registerUser(@Body user: User): Call<ResponseBody>
    }

    data class User(
        val name: String,
        val email: String,
        val password: String,
        val phone: String,
        val equipment_serial: String
    )
}

