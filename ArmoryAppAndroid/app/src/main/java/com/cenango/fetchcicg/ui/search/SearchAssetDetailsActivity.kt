package com.cenango.fetchcicg.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cenango.fetchcicg.R
import com.google.android.material.button.MaterialButton

/**
 * Kotlin/Android equivalent of iOS's `SearchAssetDetailsViewController`
 * (Controllers/Search/SearchAssetDetailsViewController.swift). Reuses
 * Enroll's `activity_asset_summary.xml`/`item_summary_card.xml` like the
 * Checkout/Checkin summaries do; the bottom button is repurposed as "Locate"
 * and hidden entirely (not just disabled) when the asset has no RFID tag —
 * matches iOS's `actionButton.isHidden = asset.tag == nil`.
 */
class SearchAssetDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_ASSET_DESCRIPTION = "extra_asset_description"
        const val EXTRA_ASSET_SERIAL_NUMBER = "extra_asset_serial_number"
        const val EXTRA_ASSET_TAG = "extra_asset_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asset_summary)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.search_details_title)

        val tag = intent.getStringExtra(EXTRA_ASSET_TAG)
        val locateButton = findViewById<MaterialButton>(R.id.saveButton)
        locateButton.setText(R.string.search_details_locate_button)

        if (tag != null) {
            locateButton.visibility = View.VISIBLE
            locateButton.setOnClickListener {
                startActivity(Intent(this, LocateAssetActivity::class.java).putExtra(LocateAssetActivity.EXTRA_TAG, tag))
            }
        } else {
            locateButton.visibility = View.GONE
        }

        addSummaryCards(tag)
    }

    private fun addSummaryCards(tag: String?) {
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
        tag?.let { addCard(getString(R.string.summary_field_epc_tag), it) }
    }
}
