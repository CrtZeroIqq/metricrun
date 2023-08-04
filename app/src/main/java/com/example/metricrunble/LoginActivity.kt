package com.example.metricrunble // Aquí debes poner el nombre de tu paquete

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import okhttp3.ResponseBody
import org.mindrot.jbcrypt.BCrypt
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class LoginActivity : AppCompatActivity() {

    private lateinit var signUpTextView: TextView
    private lateinit var loginButton: Button
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        signUpTextView = findViewById(R.id.signup)
        signUpTextView.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        loginButton = findViewById(R.id.login)
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)

        loginButton.setOnClickListener {
            val retrofit = Retrofit.Builder()
                .baseUrl("http://54.221.216.132/metricrun/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val userEmail = email.text.toString()
            val userPassword = password.text.toString()
            val service = retrofit.create(ApiService::class.java)

            val user = User(
                email.text.toString(),
                password.text.toString()
            )
            Log.d("LoginActivity", "Sending POST request: email=$userEmail, password=$userPassword")
            service.loginUser(user).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        Log.d("Response", "Logged in successfully")
                        Toast.makeText(this@LoginActivity, "Logged in successfully!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("userEmail", userEmail)
                        startActivity(intent)
                    } else {
                        Log.d("Response", "Login failed")
                        Log.d("email", userEmail)
                        Log.d("password", userPassword)
                        Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.d("Failure", t.message.toString())
                    Toast.makeText(this@LoginActivity, "Login failed: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    interface ApiService {
        @POST("login_user.php")
        fun loginUser(@Body user: User): Call<ResponseBody>
    }

    data class User(
        val email: String,
        val password: String
    )
}
