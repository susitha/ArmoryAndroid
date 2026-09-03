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
import com.cenango.fetchcicg.data.model.User
import com.cenango.fetchcicg.util.ApiResult
import com.cenango.fetchcicg.util.safeApiCall
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `UserPickerViewController`
 * (Controllers/Common/UserPickerViewController.swift): a flat, selectable
 * list of users (no sections).
 *
 * See [CategoryPickerActivity] for why this falls back to a live fetch when
 * the local cache is empty, rather than only relying on Dashboard's
 * fire-and-forget prefetch (same cache, same logout-wipe/race).
 */
class UserPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker_list)
        app = application as FetchCICGApplication

        findViewById<TextView>(R.id.pickerTitle).text = getString(R.string.pick_user_title)
        findViewById<TextView>(R.id.cancelButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        recyclerView = findViewById(R.id.pickerList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        emptyStateText = findViewById(R.id.emptyStateText)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        val cached = app.storage.users
        if (cached.isNotEmpty()) showUsers(cached) else fetchUsers()
    }

    private fun fetchUsers() {
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = safeApiCall { app.api.getUsers() }
            loadingIndicator.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    app.storage.users = result.data
                    showUsers(result.data)
                }
                is ApiResult.Error -> {
                    emptyStateText.text = getString(R.string.pick_user_load_failed, result.error.message)
                    emptyStateText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showUsers(users: List<User>) {
        val rows = users.map { PickerRow.Item(it.fullName, it) }

        recyclerView.adapter = PickerAdapter(rows) { value ->
            val selected = value as User
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_USER_ID, selected.id))
            finish()
        }

        if (rows.isEmpty()) {
            emptyStateText.text = getString(R.string.pick_user_empty)
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
        }
    }
}
