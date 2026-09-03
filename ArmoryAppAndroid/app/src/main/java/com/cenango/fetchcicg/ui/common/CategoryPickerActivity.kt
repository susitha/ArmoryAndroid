package com.cenango.fetchcicg.ui.common

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Category
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `CategoryPickerViewController`
 * (Controllers/Common/CategoryPickerViewController.swift): top-level
 * categories act as section headers only — only their subcategories are
 * selectable, matching the iOS table view's grouped-by-section behavior.
 *
 * Categories are normally already cached in [com.cenango.fetchcicg.data.storage.Storage]
 * by Dashboard's `fetchCategories()`. But that fetch is fire-and-forget on
 * Dashboard's `onCreate` and the cache is wiped on every logout — so a user
 * who reaches Enroll/Checkout/Checkin quickly after logging back in can beat
 * it, landing here with an empty cache and nothing to select (reported as
 * "no categories showing"). Rather than only mirror iOS's Dashboard-only
 * fetch (which has the same race, just less often hit), this falls back to
 * fetching directly if the cache turns out to be empty.
 */
class CategoryPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY_ID = "extra_category_id"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker_list)
        app = application as FetchCICGApplication

        findViewById<TextView>(R.id.pickerTitle).text = getString(R.string.pick_category_title)
        findViewById<TextView>(R.id.cancelButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        recyclerView = findViewById(R.id.pickerList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        emptyStateText = findViewById(R.id.emptyStateText)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        val cached = app.storage.categories
        if (cached.any { !it.subCategories.isNullOrEmpty() }) {
            showCategories(cached)
        } else {
            fetchCategories()
        }
    }

    private fun fetchCategories() {
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = safeApiCall { app.api.getCategories() }
            loadingIndicator.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    app.storage.categories = result.data
                    showCategories(result.data)
                }
                is ApiResult.Error -> {
                    emptyStateText.text = getString(R.string.pick_category_load_failed, result.error.message)
                    emptyStateText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showCategories(categories: List<Category>) {
        val rows = mutableListOf<PickerRow>()
        for (category in categories) {
            rows.add(PickerRow.Header(category.name))
            category.subCategories?.forEach { subCategory ->
                rows.add(PickerRow.Item(subCategory.name, subCategory))
            }
        }

        recyclerView.adapter = PickerAdapter(rows) { value ->
            val selected = value as Category
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CATEGORY_ID, selected.id))
            finish()
        }

        // rows only ever holds Headers when there's nothing selectable underneath
        // them (the backend returned categories but none with subcategories) —
        // show that explicitly instead of a blank list, with enough detail
        // (the actual category names) to tell apart "these are genuinely empty
        // parent groups" from "these are already leaf items and shouldn't have
        // been treated as headers at all" — without needing logcat access on a
        // device that can't be debugged remotely.
        if (rows.none { it is PickerRow.Item<*> }) {
            emptyStateText.text = getString(
                R.string.pick_category_empty,
                categories.joinToString(", ") { it.name }.ifEmpty { "(none)" }
            )
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
        }
    }
}
