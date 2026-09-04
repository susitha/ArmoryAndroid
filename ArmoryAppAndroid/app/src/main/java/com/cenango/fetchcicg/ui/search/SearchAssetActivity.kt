package com.cenango.fetchcicg.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Asset
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `SearchAssetViewController`
 * (Controllers/Search/SearchAssetViewController.swift): a search box over a
 * paginated, incrementally-loaded results list. iOS debounces via a
 * `SearchTextField` library callback (`userDidStopTyping`); this uses a
 * simple cancel-and-relaunch coroutine delay for the same effect.
 */
class SearchAssetActivity : AppCompatActivity() {

    private companion object {
        const val RESULTS_PER_PAGE = 10
        const val SEARCH_DEBOUNCE_MS = 400L
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var adapter: AssetListAdapter
    private lateinit var emptyStateText: TextView

    private var searchText: String = ""
    private var pageNumber = 1
    private var totalResults = 0
    private var fetchedResults = 0
    private var isLoadingPage = false
    private var searchDebounceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_asset)
        app = application as FetchCICGApplication

        emptyStateText = findViewById(R.id.emptyStateText)
        adapter = AssetListAdapter { asset -> openDetails(asset) }

        val resultsList = findViewById<RecyclerView>(R.id.resultsList)
        val layoutManager = LinearLayoutManager(this)
        resultsList.layoutManager = layoutManager
        resultsList.adapter = adapter
        resultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoadingPage) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 1 && fetchedResults < totalResults) {
                    pageNumber++
                    fetchAssets(append = true)
                }
            }
        })

        findViewById<android.widget.EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString().orEmpty()
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    resetAndFetch()
                }
            }
        })

        fetchAssets(append = false)
    }

    private fun resetAndFetch() {
        pageNumber = 1
        totalResults = 0
        fetchedResults = 0
        fetchAssets(append = false)
    }

    private fun fetchAssets(append: Boolean) {
        isLoadingPage = true

        lifecycleScope.launch {
            val result = safeApiCall {
                app.api.searchAssets(searchText, emptyList<Int>().toString(), pageNumber, RESULTS_PER_PAGE)
            }
            isLoadingPage = false

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    totalResults = response.total
                    fetchedResults += response.assets.size
                    if (append) adapter.appendList(response.assets) else adapter.submitList(response.assets)
                    if (adapter.itemCount == 0) {
                        showEmptyState(getString(R.string.search_empty_no_results))
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

    private fun openDetails(asset: Asset) {
        startActivity(
            Intent(this, SearchAssetDetailsActivity::class.java)
                .putExtra(SearchAssetDetailsActivity.EXTRA_ASSET_NAME, asset.name)
                .putExtra(SearchAssetDetailsActivity.EXTRA_ASSET_DESCRIPTION, asset.description)
                .putExtra(SearchAssetDetailsActivity.EXTRA_ASSET_SERIAL_NUMBER, asset.serialNumber)
                .putExtra(SearchAssetDetailsActivity.EXTRA_ASSET_CATEGORY_NAME, asset.category.name)
                .putExtra(SearchAssetDetailsActivity.EXTRA_ASSET_TAG, asset.tag)
        )
    }
}
