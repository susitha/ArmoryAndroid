package com.cenango.fetchcicg.ui.checkin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.requests.AssetMoveRequest
import com.cenango.fetchcicg.data.model.requests.LoginRequest
import com.cenango.fetchcicg.ui.common.bindAssetHeader
import com.cenango.fetchcicg.ui.dashboard.DashboardActivity
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `CheckinAuthView`
 * (Controllers/Checkin/CheckinAuthView.swift): shown when the logged-in
 * user's own approve-and-return attempt came back 403. An admin logs in
 * here with their own credentials — that login is *not* saved to
 * [com.cenango.fetchcicg.data.session.SessionManager] (it doesn't replace
 * the current user's session) — and the resulting token is used for a single
 * `checkinApproveAssetWithSuperUser` call.
 */
class CheckinAuthActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_ASSET_CATEGORY_NAME = "extra_asset_category_name"
        const val EXTRA_ASSET_SERIAL_NUMBER = "extra_asset_serial_number"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_MAGAZINES_COUNT = "extra_magazines_count"
        const val EXTRA_TOTAL_WEIGHT = "extra_total_weight"
        const val EXTRA_NOTE = "extra_note"
    }

    private lateinit var app: FetchCICGApplication
    private var assetId: Int = -1
    private lateinit var assetMoveRequest: AssetMoveRequest

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var proceedButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin_auth)
        app = application as FetchCICGApplication

        assetId = intent.getIntExtra(EXTRA_ASSET_ID, -1)
        assetMoveRequest = AssetMoveRequest(
            userID = intent.getIntExtra(EXTRA_USER_ID, -1),
            password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
            magazinesCount = intent.getIntExtra(EXTRA_MAGAZINES_COUNT, -1).takeIf { it != -1 },
            totalWeight = intent.getStringExtra(EXTRA_TOTAL_WEIGHT)?.toDoubleOrNull(),
            note = intent.getStringExtra(EXTRA_NOTE)
        )

        bindAssetHeader(
            findViewById(R.id.assetHeader),
            name = intent.getStringExtra(EXTRA_ASSET_NAME).orEmpty(),
            categoryName = intent.getStringExtra(EXTRA_ASSET_CATEGORY_NAME).orEmpty(),
            serialNumber = intent.getStringExtra(EXTRA_ASSET_SERIAL_NUMBER).orEmpty()
        )

        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        proceedButton = findViewById(R.id.proceedButton)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                proceedButton.isEnabled = usernameInput.text.isNotBlank() && passwordInput.text.isNotBlank()
            }
        }
        usernameInput.addTextChangedListener(watcher)
        passwordInput.addTextChangedListener(watcher)

        proceedButton.setOnClickListener { login() }
    }

    private fun login() {
        proceedButton.isEnabled = false
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        lifecycleScope.launch {
            when (val result = safeApiCall { app.api.login(LoginRequest(username, password)) }) {
                is ApiResult.Success -> checkinApproveAssetWithSuperUser(result.data.token)
                is ApiResult.Error -> {
                    proceedButton.isEnabled = true
                    showError(result.error.message)
                }
            }
        }
    }

    private fun checkinApproveAssetWithSuperUser(adminToken: String) {
        lifecycleScope.launch {
            val result = safeApiCall {
                app.api.checkinApproveAssetWithSuperUser(assetId, assetMoveRequest, adminToken)
            }
            proceedButton.isEnabled = true

            when (result) {
                is ApiResult.Success -> {
                    AlertDialog.Builder(this@CheckinAuthActivity)
                        .setTitle(R.string.summary_success_title)
                        .setMessage(R.string.checkin_summary_success_message)
                        .setPositiveButton(R.string.dialog_ok) { _, _ -> goToDashboard() }
                        .setCancelable(false)
                        .show()
                }
                is ApiResult.Error -> showError(result.error.message)
            }
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.summary_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun goToDashboard() {
        startActivity(
            Intent(this, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
