package com.cenango.fetchcicg.ui.checkout

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Asset
import com.cenango.fetchcicg.data.model.User
import com.cenango.fetchcicg.ui.common.UserPickerActivity
import com.cenango.fetchcicg.ui.common.bindAssetHeader
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `EnterCheckoutAssetDetailsViewController`
 * (Controllers/Checkout/EnterCheckoutAssetDetailsViewController.swift):
 * fetches the scanned asset, shows its header, and collects who's checking
 * it out (+ password, + mag count/weight if the category requires it).
 */
class EnterCheckoutAssetDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAG = "extra_tag"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var tag: String
    private var asset: Asset? = null
    private var selectedUser: User? = null

    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var magsInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var magsRequiredMarker: View
    private lateinit var weightRequiredMarker: View
    private lateinit var nextButton: MaterialButton

    private val pickUser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val userId = result.data?.getIntExtra(UserPickerActivity.EXTRA_USER_ID, -1) ?: -1
        app.storage.users.firstOrNull { it.id == userId }?.let { user ->
            selectedUser = user
            userInput.setText(user.fullName)
            updateNextButtonEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_checkout_asset_details)
        app = application as FetchCICGApplication
        tag = intent.getStringExtra(EXTRA_TAG).orEmpty()

        userInput = findViewById(R.id.userInput)
        passwordInput = findViewById(R.id.passwordInput)
        magsInput = findViewById(R.id.magsInput)
        weightInput = findViewById(R.id.weightInput)
        magsRequiredMarker = findViewById(R.id.magsRequiredMarker)
        weightRequiredMarker = findViewById(R.id.weightRequiredMarker)
        nextButton = findViewById(R.id.nextButton)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateNextButtonEnabled()
        }
        passwordInput.addTextChangedListener(watcher)
        magsInput.addTextChangedListener(watcher)
        weightInput.addTextChangedListener(watcher)

        userInput.setOnClickListener {
            pickUser.launch(Intent(this, UserPickerActivity::class.java))
        }
        nextButton.setOnClickListener { onNext() }

        fetchAsset()
    }

    private fun fetchAsset() {
        lifecycleScope.launch {
            when (val result = safeApiCall { app.api.getAsset(tag) }) {
                is ApiResult.Success -> onAssetLoaded(result.data)
                is ApiResult.Error -> {
                    AlertDialog.Builder(this@EnterCheckoutAssetDetailsActivity)
                        .setTitle(R.string.asset_fetch_error_title)
                        .setMessage(result.error.message)
                        .setPositiveButton(R.string.dialog_ok) { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun onAssetLoaded(loadedAsset: Asset) {
        asset = loadedAsset
        bindAssetHeader(findViewById(R.id.assetHeader), loadedAsset)

        val weightRequired = loadedAsset.category.isWeightRequired
        magsRequiredMarker.visibility = if (weightRequired) View.VISIBLE else View.GONE
        weightRequiredMarker.visibility = if (weightRequired) View.VISIBLE else View.GONE

        loadedAsset.assignedUserID?.let { assignedUserId ->
            app.storage.users.firstOrNull { it.id == assignedUserId }?.let { user ->
                selectedUser = user
                userInput.setText(user.fullName)
            }
        }

        updateNextButtonEnabled()
        checkAccessLevel(loadedAsset)
    }

    /**
     * Mirrors `checkForAccessLevel()`: if the category requires admin
     * privilege to check out and the logged-in user isn't in that group
     * (id 1, per iOS's hardcoded check), block and go back.
     */
    private fun checkAccessLevel(loadedAsset: Asset) {
        if (!loadedAsset.category.isAdminRequiredToCheckout) return
        val currentUser = app.sessionManager.user ?: return

        if (currentUser.userGroup.id != 1) {
            AlertDialog.Builder(this)
                .setTitle(R.string.checkout_access_denied_title)
                .setMessage(R.string.checkout_access_denied_message)
                .setPositiveButton(R.string.dialog_ok) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun updateNextButtonEnabled() {
        val currentAsset = asset ?: run { nextButton.isEnabled = false; return }
        val baseValid = selectedUser != null && passwordInput.text.isNotBlank()
        nextButton.isEnabled = if (currentAsset.category.isWeightRequired) {
            baseValid && magsInput.text.isNotBlank() && weightInput.text.isNotBlank()
        } else {
            baseValid
        }
    }

    private fun onNext() {
        val currentAsset = asset ?: return
        val user = selectedUser ?: return

        startActivity(
            Intent(this, CheckoutAssetDetailsSummaryActivity::class.java)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_ASSET_ID, currentAsset.id)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_ASSET_NAME, currentAsset.name)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_ASSET_DESCRIPTION, currentAsset.description)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_ASSET_SERIAL_NUMBER, currentAsset.serialNumber)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_USER_ID, user.id)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_PASSWORD, passwordInput.text.toString())
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_MAGAZINES_COUNT, magsInput.text.toString().toIntOrNull() ?: -1)
                .putExtra(CheckoutAssetDetailsSummaryActivity.EXTRA_TOTAL_WEIGHT, weightInput.text.toString())
        )
    }
}
