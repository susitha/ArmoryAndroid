package com.cenango.fetchcicg.ui.checkout

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.requests.AssetMoveRequest
import com.cenango.fetchcicg.ui.dashboard.DashboardActivity
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `CheckoutAssetDetailsSummaryViewController`
 * (Controllers/Checkout/CheckoutAssetDetailsSummaryViewController.swift):
 * review the entered details, then `POST /api/assets/{id}/checkout` on
 * Checkout. Reuses the same layout as the Enroll summary screen
 * (`activity_asset_summary.xml`, `item_summary_card.xml`) — only the title,
 * button label, and card contents differ, so the title/button text is set
 * here in code rather than duplicating the layout.
 */
class CheckoutAssetDetailsSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "extra_asset_id"
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_ASSET_DESCRIPTION = "extra_asset_description"
        const val EXTRA_ASSET_SERIAL_NUMBER = "extra_asset_serial_number"
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

        findViewById<TextView>(R.id.screenTitle).setText(R.string.checkout_title)
        findViewById<MaterialButton>(R.id.saveButton).setText(R.string.checkout_summary_button)

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
        findViewById<View>(R.id.saveButton).setOnClickListener { checkoutAsset() }
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

    private fun checkoutAsset() {
        findViewById<View>(R.id.saveButton).isEnabled = false

        lifecycleScope.launch {
            val result = safeApiCall { app.api.checkoutAsset(assetId, assetMoveRequest) }
            findViewById<View>(R.id.saveButton).isEnabled = true

            when (result) {
                is ApiResult.Success -> {
                    AlertDialog.Builder(this@CheckoutAssetDetailsSummaryActivity)
                        .setTitle(R.string.summary_success_title)
                        .setMessage(R.string.checkout_summary_success_message)
                        .setPositiveButton(R.string.dialog_ok) { _, _ -> goToDashboard() }
                        .setCancelable(false)
                        .show()
                }
                is ApiResult.Error -> {
                    AlertDialog.Builder(this@CheckoutAssetDetailsSummaryActivity)
                        .setTitle(R.string.summary_error_title)
                        .setMessage(result.error.message)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()
                }
            }
        }
    }

    private fun goToDashboard() {
        startActivity(
            Intent(this, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
