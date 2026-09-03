package com.cenango.fetchcicg.ui.enroll

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
import com.cenango.fetchcicg.data.model.requests.AssetRequest
import com.cenango.fetchcicg.ui.dashboard.DashboardActivity
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `AssetDetailSummaryViewController`
 * (Controllers/Enroll/AssetDetailSummaryViewController.swift): review the
 * entered details, then `POST /api/assets` on Save.
 */
class AssetSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_TAG = "extra_tag"
        const val EXTRA_SERIAL_NUMBER = "extra_serial_number"

        /** -1 means "no user assigned" — Intent extras have no nullable Int. */
        const val EXTRA_ASSIGNED_USER_ID = "extra_assigned_user_id"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var assetRequest: AssetRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asset_summary)
        app = application as FetchCICGApplication

        val assignedUserId = intent.getIntExtra(EXTRA_ASSIGNED_USER_ID, -1).takeIf { it != -1 }
        assetRequest = AssetRequest(
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            description = intent.getStringExtra(EXTRA_DESCRIPTION),
            categoryID = intent.getIntExtra(EXTRA_CATEGORY_ID, -1),
            tag = intent.getStringExtra(EXTRA_TAG).orEmpty(),
            serialNumber = intent.getStringExtra(EXTRA_SERIAL_NUMBER).orEmpty(),
            assignedUserID = assignedUserId
        )

        addSummaryCards(assignedUserId)
        findViewById<View>(R.id.saveButton).setOnClickListener { enrollAsset() }
    }

    private fun addSummaryCards(assignedUserId: Int?) {
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

        addCard(getString(R.string.summary_field_name), assetRequest.name)
        assetRequest.description?.let { addCard(getString(R.string.summary_field_description), it) }
        addCard(getString(R.string.summary_field_serial_number), assetRequest.serialNumber)
        addCard(getString(R.string.summary_field_epc_tag), assetRequest.tag)
        assignedUserId?.let { userId ->
            app.storage.users.firstOrNull { it.id == userId }?.let { user ->
                addCard(getString(R.string.summary_field_assigned_user), user.fullName)
            }
        }
    }

    private fun enrollAsset() {
        findViewById<View>(R.id.saveButton).isEnabled = false

        lifecycleScope.launch {
            val result = safeApiCall { app.api.enrollAsset(assetRequest) }
            findViewById<View>(R.id.saveButton).isEnabled = true

            when (result) {
                is ApiResult.Success -> {
                    AlertDialog.Builder(this@AssetSummaryActivity)
                        .setTitle(R.string.summary_success_title)
                        .setMessage(R.string.summary_success_message)
                        .setPositiveButton(R.string.dialog_ok) { _, _ -> goToDashboard() }
                        .setCancelable(false)
                        .show()
                }
                is ApiResult.Error -> {
                    AlertDialog.Builder(this@AssetSummaryActivity)
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
