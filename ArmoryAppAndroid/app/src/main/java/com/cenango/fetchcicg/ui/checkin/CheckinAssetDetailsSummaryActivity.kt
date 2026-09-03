package com.cenango.fetchcicg.ui.checkin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.requests.AssetMoveRequest
import com.cenango.fetchcicg.ui.dashboard.DashboardActivity
import com.cenango.fetchcicg.util.ApiError
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `CheckinAssetDetailsSummaryViewController`
 * (Controllers/Checkin/CheckinAssetDetailsSummaryViewController.swift).
 * Reuses Enroll's `activity_asset_summary.xml`/`item_summary_card.xml` like
 * Checkout's summary does, but with a 3-step API flow iOS's checkout summary
 * doesn't have:
 *
 * 1. `POST .../return` (plain checkin). A 409 conflict means the backend
 *    detected a discrepancy (e.g. a mag/weight mismatch) — prompt for a note
 *    and retry as an *approve*-and-return instead of a plain return.
 * 2. `POST .../approve-and-return` (with that note). A 403 means the
 *    logged-in user isn't authorized to approve it themselves — hand off to
 *    [CheckinAuthActivity] so an admin can authenticate and approve it under
 *    their own token instead.
 * 3. [CheckinAuthActivity] does the actual approve-and-return call itself,
 *    with the admin's token — see `checkinApproveAssetWithSuperUser` on
 *    [com.cenango.fetchcicg.data.network.ArmoryApiService].
 */
class CheckinAssetDetailsSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_ASSET_DESCRIPTION = "extra_asset_description"
        const val EXTRA_ASSET_SERIAL_NUMBER = "extra_asset_serial_number"
        const val EXTRA_ASSET_CATEGORY_NAME = "extra_asset_category_name"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_PASSWORD = "extra_password"

        /** -1 means "not entered" — Intent extras have no nullable Int. */
        const val EXTRA_MAGAZINES_COUNT = "extra_magazines_count"

        /** Empty string means "not entered". */
        const val EXTRA_TOTAL_WEIGHT = "extra_total_weight"
    }

    private lateinit var app: FetchCICGApplication
    private var assetId: Int = -1
    private lateinit var assetMoveRequest: AssetMoveRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asset_summary)
        app = application as FetchCICGApplication

        findViewById<TextView>(R.id.screenTitle).setText(R.string.checkin_title)
        findViewById<MaterialButton>(R.id.saveButton).setText(R.string.checkin_summary_button)

        assetId = intent.getIntExtra(EXTRA_ASSET_ID, -1)
        val magazinesCount = intent.getIntExtra(EXTRA_MAGAZINES_COUNT, -1).takeIf { it != -1 }
        val totalWeight = intent.getStringExtra(EXTRA_TOTAL_WEIGHT)?.toDoubleOrNull()

        assetMoveRequest = AssetMoveRequest(
            userID = intent.getIntExtra(EXTRA_USER_ID, -1),
            password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
            magazinesCount = magazinesCount,
            totalWeight = totalWeight,
            note = null
        )

        addSummaryCards(magazinesCount, totalWeight)
        findViewById<View>(R.id.saveButton).setOnClickListener { checkinAsset() }
    }

    private fun addSummaryCards(magazinesCount: Int?, totalWeight: Double?) {
        val container = findViewById<LinearLayout>(R.id.cardsContainer)
        val inflater = LayoutInflater.from(this)

        fun addCard(title: String, detail: String) {
            val card = inflater.inflate(R.layout.item_summary_card, container, false)
            card.findViewById<TextView>(R.id.cardTitle).text = title
            card.findViewById<TextView>(R.id.cardDetail).text = detail
            if (container.childCount > 0) {
                (card.layoutParams as LinearLayout.LayoutParams).topMargin =
                    resources.getDimensionPixelSize(R.dimen.summary_card_spacing)
            }
            container.addView(card)
        }

        addCard(getString(R.string.summary_field_name), intent.getStringExtra(EXTRA_ASSET_NAME).orEmpty())
        intent.getStringExtra(EXTRA_ASSET_DESCRIPTION)?.let { addCard(getString(R.string.summary_field_description), it) }
        addCard(getString(R.string.summary_field_serial_number), intent.getStringExtra(EXTRA_ASSET_SERIAL_NUMBER).orEmpty())
        magazinesCount?.let { addCard(getString(R.string.checkout_summary_field_mags), it.toString()) }
        totalWeight?.let { addCard(getString(R.string.checkout_summary_field_weight), it.toString()) }
    }

    private fun setSaveButtonEnabled(enabled: Boolean) {
        findViewById<View>(R.id.saveButton).isEnabled = enabled
    }

    private fun checkinAsset() {
        setSaveButtonEnabled(false)

        lifecycleScope.launch {
            when (val result = safeApiCall { app.api.checkinAsset(assetId, assetMoveRequest) }) {
                is ApiResult.Success -> {
                    setSaveButtonEnabled(true)
                    showSuccessDialog()
                }
                is ApiResult.Error -> {
                    setSaveButtonEnabled(true)
                    if (result.error is ApiError.Conflict) {
                        showDiscrepancyDialog(result.error.message)
                    } else {
                        showErrorDialog(result.error.message)
                    }
                }
            }
        }
    }

    /** Mirrors `showdiscrepancyAlert`: prompt for a note, then retry as approve-and-return. */
    private fun showDiscrepancyDialog(message: String) {
        val noteInput = EditText(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        noteInput.setPadding(padding, padding, padding, padding)
        noteInput.hint = getString(R.string.checkin_discrepancy_note_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.summary_error_title)
            .setMessage(message)
            .setView(noteInput)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                assetMoveRequest = assetMoveRequest.copy(note = noteInput.text.toString().ifBlank { null })
                checkinApproveAsset()
            }
            .show()
    }

    private fun checkinApproveAsset() {
        setSaveButtonEnabled(false)

        lifecycleScope.launch {
            when (val result = safeApiCall { app.api.checkinApproveAsset(assetId, assetMoveRequest) }) {
                is ApiResult.Success -> {
                    setSaveButtonEnabled(true)
                    showSuccessDialog()
                }
                is ApiResult.Error -> {
                    setSaveButtonEnabled(true)
                    if (result.error is ApiError.Unauthorized) {
                        goToAdminAuth()
                    } else {
                        showErrorDialog(result.error.message)
                    }
                }
            }
        }
    }

    private fun goToAdminAuth() {
        startActivity(
            Intent(this, CheckinAuthActivity::class.java)
                .putExtra(CheckinAuthActivity.EXTRA_ASSET_ID, assetId)
                .putExtra(CheckinAuthActivity.EXTRA_ASSET_NAME, intent.getStringExtra(EXTRA_ASSET_NAME))
                .putExtra(CheckinAuthActivity.EXTRA_ASSET_CATEGORY_NAME, intent.getStringExtra(EXTRA_ASSET_CATEGORY_NAME))
                .putExtra(CheckinAuthActivity.EXTRA_ASSET_SERIAL_NUMBER, intent.getStringExtra(EXTRA_ASSET_SERIAL_NUMBER))
                .putExtra(CheckinAuthActivity.EXTRA_USER_ID, assetMoveRequest.userID)
                .putExtra(CheckinAuthActivity.EXTRA_PASSWORD, assetMoveRequest.password)
                .putExtra(CheckinAuthActivity.EXTRA_MAGAZINES_COUNT, assetMoveRequest.magazinesCount ?: -1)
                .putExtra(CheckinAuthActivity.EXTRA_TOTAL_WEIGHT, assetMoveRequest.totalWeight?.toString().orEmpty())
                .putExtra(CheckinAuthActivity.EXTRA_NOTE, assetMoveRequest.note)
        )
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.summary_success_title)
            .setMessage(R.string.checkin_summary_success_message)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> goToDashboard() }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
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
