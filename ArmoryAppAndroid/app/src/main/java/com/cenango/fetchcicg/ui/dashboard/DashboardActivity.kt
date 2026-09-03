package com.cenango.fetchcicg.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.ui.auth.LoginActivity
import com.cenango.fetchcicg.ui.common.ScanActivity
import com.cenango.fetchcicg.ui.common.ScanFlow
import com.cenango.fetchcicg.ui.inventory.TakeInventoryActivity
import com.cenango.fetchcicg.ui.search.SearchAssetActivity
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `DashboardViewController`
 * (Controllers/DashboardViewController.swift). Same structure: a title bar
 * with a logout action, a 2x2 grid of summary counts, and a menu of the five
 * main flows — all five are wired up for real now (Enroll, Checkout, Checkin,
 * Search, Inventory).
 */
class DashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DashboardActivity"
    }

    private lateinit var app: FetchCICGApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        app = application as FetchCICGApplication

        findViewById<View>(R.id.logoutButton).setOnClickListener { confirmLogout() }

        findViewById<View>(R.id.checkinButton).setOnClickListener {
            startActivity(
                Intent(this, ScanActivity::class.java)
                    .putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.dashboard_checkin))
                    .putExtra(ScanActivity.EXTRA_FLOW, ScanFlow.CHECKIN.name)
            )
        }
        findViewById<View>(R.id.checkoutButton).setOnClickListener {
            startActivity(
                Intent(this, ScanActivity::class.java)
                    .putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.dashboard_checkout))
                    .putExtra(ScanActivity.EXTRA_FLOW, ScanFlow.CHECKOUT.name)
            )
        }
        findViewById<View>(R.id.enrollButton).setOnClickListener {
            startActivity(
                Intent(this, ScanActivity::class.java)
                    .putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.dashboard_enroll))
                    .putExtra(ScanActivity.EXTRA_FLOW, ScanFlow.ENROLL.name)
            )
        }
        findViewById<View>(R.id.searchButton).setOnClickListener {
            startActivity(Intent(this, SearchAssetActivity::class.java))
        }
        findViewById<View>(R.id.inventoryButton).setOnClickListener {
            startActivity(Intent(this, TakeInventoryActivity::class.java))
        }

        bindInfoItem(R.id.totalAssetsItem, R.drawable.dashboard_total_assets, null, getString(R.string.dashboard_total_assets))
        bindInfoItem(R.id.totalCheckoutsItem, R.drawable.dashboard_total_checkout, null, getString(R.string.dashboard_total_checkouts))
        bindInfoItem(R.id.todayCheckoutsItem, R.drawable.dashboard_today_checkout, null, getString(R.string.dashboard_today_checkouts))
        bindInfoItem(R.id.missingAssetsItem, R.drawable.dashboard_missing_assets, null, getString(R.string.dashboard_missing_assets))

        // Mirrors viewDidLoad: fetch reference data once and cache it locally.
        fetchTagFormatData()
        fetchCategories()
        fetchUsers()
    }

    override fun onResume() {
        super.onResume()
        // Mirrors viewDidAppear: refresh the summary counts every time this screen is shown.
        updateDashboardInfoViews()
    }

    private fun updateDashboardInfoViews() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getDashboardData() }
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    bindInfoItem(R.id.totalAssetsItem, R.drawable.dashboard_total_assets, data.totalAssets, getString(R.string.dashboard_total_assets))
                    bindInfoItem(R.id.totalCheckoutsItem, R.drawable.dashboard_total_checkout, data.totalCheckouts, getString(R.string.dashboard_total_checkouts))
                    bindInfoItem(R.id.todayCheckoutsItem, R.drawable.dashboard_today_checkout, data.todayCheckouts, getString(R.string.dashboard_today_checkouts))
                    bindInfoItem(R.id.missingAssetsItem, R.drawable.dashboard_missing_assets, data.totalMissing, getString(R.string.dashboard_missing_assets))
                }
                is ApiResult.Error -> {
                    // iOS just logs this and leaves the cards blank; surfaced here
                    // (temporarily, as a Toast) since a silently-blank screen is
                    // exactly what's being debugged right now.
                    Log.e(TAG, "getDashboardData failed: ${result.error.message}")
                    Toast.makeText(this@DashboardActivity, "Dashboard data failed: ${result.error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchTagFormatData() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getTagFormatData() }
            if (result is ApiResult.Success) app.storage.tagFormat = result.data
        }
    }

    private fun fetchCategories() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getCategories() }
            when (result) {
                is ApiResult.Success -> app.storage.categories = result.data
                // iOS silently logs this and leaves the cache stale; surfaced
                // here (temporarily, as a Toast) since it's the likely cause
                // of "can't select a category" on Enroll/Checkout/Checkin —
                // an empty/stale cache means the picker has nothing to show.
                is ApiResult.Error -> {
                    Log.e(TAG, "getCategories failed: ${result.error.message}")
                    Toast.makeText(this@DashboardActivity, "Failed to load categories: ${result.error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchUsers() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getUsers() }
            when (result) {
                is ApiResult.Success -> app.storage.users = result.data
                is ApiResult.Error -> {
                    Log.e(TAG, "getUsers failed: ${result.error.message}")
                    Toast.makeText(this@DashboardActivity, "Failed to load users: ${result.error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindInfoItem(containerId: Int, iconRes: Int, count: Int?, title: String) {
        val container = findViewById<View>(containerId)
        container.findViewById<ImageView>(R.id.itemIcon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.itemCount).text = count?.toString() ?: getString(R.string.dashboard_count_na)
        container.findViewById<TextView>(R.id.itemTitle).text = title
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_logout_title)
            .setMessage(R.string.dashboard_logout_message)
            .setPositiveButton(R.string.dashboard_logout_confirm) { _, _ ->
                app.sessionManager.clearSession()
                app.storage.clear()
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
            .setNegativeButton(R.string.dashboard_logout_cancel, null)
            .show()
    }
}
