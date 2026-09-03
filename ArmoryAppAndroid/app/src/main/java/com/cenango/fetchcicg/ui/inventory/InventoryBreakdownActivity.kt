package com.cenango.fetchcicg.ui.inventory

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.InventoryGroup
import com.cenango.fetchcicg.ui.search.AssetListAdapter
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `InventoryBreakdownViewController`
 * (Controllers/Inventory/InventoryBreakdownViewController.swift): given the
 * set of tags found during a Take Inventory scan, shows how many fall into
 * each of Available/Missing/Checkedout, and a paginated list for whichever
 * group is selected. Rows aren't tappable here — iOS's table view has no
 * `didSelectRowAt`, just pagination on scroll — so [AssetListAdapter]'s
 * click callback is a deliberate no-op, unlike its use in `SearchAssetActivity`.
 */
class InventoryBreakdownActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAGS = "extra_tags"
        private const val RESULTS_PER_PAGE = 10
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var adapter: AssetListAdapter
    private lateinit var emptyStateText: TextView
    private lateinit var availableButton: MaterialButton
    private lateinit var missingButton: MaterialButton
    private lateinit var checkedoutButton: MaterialButton

    private lateinit var tags: String
    private var selectedGroup = InventoryGroup.AVAILABLE
    private var pageNumber = 1
    private var totalResults = 0
    private var fetchedResults = 0
    private var isLoadingPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory_breakdown)
        app = application as FetchCICGApplication
        tags = intent.getStringExtra(EXTRA_TAGS).orEmpty()

        emptyStateText = findViewById(R.id.emptyStateText)
        availableButton = findViewById(R.id.availableButton)
        missingButton = findViewById(R.id.missingButton)
        checkedoutButton = findViewById(R.id.checkedoutButton)
        // Available starts selected (matches iOS's `availableButton.isSelected = true`
        // at init) — set here rather than via an `android:selected` XML attribute,
        // which AAPT doesn't recognize on this element.
        availableButton.isSelected = true

        availableButton.setOnClickListener { selectGroup(InventoryGroup.AVAILABLE) }
        missingButton.setOnClickListener { selectGroup(InventoryGroup.MISSING) }
        checkedoutButton.setOnClickListener { selectGroup(InventoryGroup.CHECKED_OUT) }

        adapter = AssetListAdapter { /* no-op: iOS's table isn't tappable here either */ }
        val resultsList = findViewById<RecyclerView>(R.id.resultsList)
        val layoutManager = LinearLayoutManager(this)
        resultsList.layoutManager = layoutManager
        resultsList.adapter = adapter
        resultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoadingPage) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 1 && fetchedResults < totalResults) {
                    pageNumber++
                    fetchInventoryList(append = true)
                }
            }
        })

        findViewById<View>(R.id.actionButton).setOnClickListener { finish() }

        fetchInventorySummary()
        fetchInventoryList(append = false)
    }

    private fun selectGroup(group: InventoryGroup) {
        if (group == selectedGroup) return
        selectedGroup = group
        availableButton.isSelected = group == InventoryGroup.AVAILABLE
        missingButton.isSelected = group == InventoryGroup.MISSING
        checkedoutButton.isSelected = group == InventoryGroup.CHECKED_OUT

        pageNumber = 1
        totalResults = 0
        fetchedResults = 0
        fetchInventoryList(append = false)
    }

    private fun fetchInventorySummary() {
        lifecycleScope.launch {
            val result = safeApiCall { app.api.getInventorySummary(tags) }
            if (result is ApiResult.Success) {
                val summary = result.data
                availableButton.text = getString(R.string.inventory_group_available_format, summary.available)
                missingButton.text = getString(R.string.inventory_group_missing_format, summary.missing)
                checkedoutButton.text = getString(R.string.inventory_group_checkedout_format, summary.checkedOut)
            }
        }
    }

    private fun fetchInventoryList(append: Boolean) {
        isLoadingPage = true

        lifecycleScope.launch {
            val result = safeApiCall {
                app.api.getInventoryList(tags, selectedGroup.value, pageNumber, RESULTS_PER_PAGE)
            }
            isLoadingPage = false

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    totalResults = response.total
                    fetchedResults += response.assets.size
                    if (append) adapter.appendList(response.assets) else adapter.submitList(response.assets)
                    if (adapter.itemCount == 0) {
                        showEmptyState(getString(R.string.inventory_empty_no_results))
                    } else {
                        hideEmptyState()
                    }
                }
                is ApiResult.Error -> {
                    if (!append) {
                        adapter.submitList(emptyList())
                        totalResults = 0
                        fetchedResults = 0
                    }
                    showEmptyState(result.error.message)
                }
            }
        }
    }

    private fun showEmptyState(message: String) {
        emptyStateText.text = message
        emptyStateText.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        emptyStateText.visibility = View.GONE
    }
}
