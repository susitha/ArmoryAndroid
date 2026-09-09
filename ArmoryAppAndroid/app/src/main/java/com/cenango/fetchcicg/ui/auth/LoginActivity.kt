package com.cenango.fetchcicg.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.BuildConfig
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.requests.LoginRequest
import com.cenango.fetchcicg.data.network.ApiSettings
import com.cenango.fetchcicg.ui.dashboard.DashboardActivity
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `LoginViewController`
 * (Controllers/Auth/LoginViewController.swift). Same flow: resolve the API
 * host (best-effort), enable the login button once both fields are non-blank,
 * call `/api/auth/signin`, and on success save the session and move on.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var app: FetchCICGApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — installSplashScreen() reads the
        // activity's theme (Theme.FetchCICG.Splash, set in the manifest) to
        // show the splash, then applies postSplashScreenTheme and dismisses
        // it automatically once this activity's first frame is drawn. No
        // separate SplashActivity/manual delay needed.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        app = application as FetchCICGApplication

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val loadingIndicator = findViewById<View>(R.id.loadingIndicator)
        val versionText = findViewById<TextView>(R.id.versionText)

        versionText.text = getString(
            R.string.login_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE.toString()
        )

        val enableButtonWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                loginButton.isEnabled = !usernameInput.text.isNullOrBlank() && !passwordInput.text.isNullOrBlank()
            }
        }
        usernameInput.addTextChangedListener(enableButtonWatcher)
        passwordInput.addTextChangedListener(enableButtonWatcher)

        // Mirrors iOS's UITextFieldDelegate return-key handling: "next" moves
        // focus, "done" submits (if the button is currently enabled).
        passwordInput.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isDoneAction && loginButton.isEnabled) {
                loginButton.performClick()
                true
            } else {
                false
            }
        }

        loginButton.setOnClickListener {
            attemptLogin(
                usernameInput.text.toString().trim(),
                passwordInput.text.toString().trim(),
                loginButton,
                loadingIndicator
            )
        }

        resolveApiDetails()
    }

    /**
     * Mirrors `updateApiDetails()`: best-effort only. Debug builds already
     * talk to the dev API directly regardless of what this returns (see
     * `RetrofitClient`), so a failure here is a harmless no-op rather than
     * something that should block login.
     */
    private fun resolveApiDetails() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getApiDetails(ApiSettings.apiDetailsUrl()) }
            if (result is ApiResult.Success) {
                val details = result.data
                app.sessionManager.serverUrl = details.url
                app.sessionManager.defaultUrl = details.defaultURL
                app.sessionManager.apiVersion = details.version
            }
        }
    }

    private fun attemptLogin(username: String, password: String, loginButton: View, loadingIndicator: View) {
        loginButton.isEnabled = false
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = safeApiCall { app.api.login(LoginRequest(username, password)) }
            loadingIndicator.visibility = View.GONE

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    app.sessionManager.token = response.token
                    app.sessionManager.refreshToken = response.refreshToken
                    app.sessionManager.user = response.user
                    app.sessionManager.isInReview = response.isInReview

                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                    finish()
                }
                is ApiResult.Error -> {
                    loginButton.isEnabled = true
                    Toast.makeText(this@LoginActivity, result.error.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
