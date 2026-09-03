package com.cenango.fetchcicg.ui.enroll

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Category
import com.cenango.fetchcicg.data.model.User
import com.cenango.fetchcicg.ui.common.CategoryPickerActivity
import com.cenango.fetchcicg.ui.common.UserPickerActivity
import com.google.android.material.button.MaterialButton

/**
 * Kotlin/Android equivalent of iOS's `EnterAssetDetailsViewController`
 * (Controllers/Enroll/EnterAssetDetailsViewController.swift): asset name,
 * description, serial number, category (required) and, only for
 * personal-weapon categories, an assigned user.
 */
class EnterAssetDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAG = "extra_tag"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var tag: String

    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var serialNumberInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var userLabel: View
    private lateinit var userInput: EditText
    private lateinit var nextButton: MaterialButton

    private var selectedCategory: Category? = null
    private var selectedUser: User? = null

    private val pickCategory = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val categoryId = result.data?.getIntExtra(CategoryPickerActivity.EXTRA_CATEGORY_ID, -1) ?: -1
        val category = app.storage.categories.flatMap { it.subCategories.orEmpty() }.firstOrNull { it.id == categoryId }
        if (category != null) onCategorySelected(category)
    }

    private val pickUser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val userId = result.data?.getIntExtra(UserPickerActivity.EXTRA_USER_ID, -1) ?: -1
        val user = app.storage.users.firstOrNull { it.id == userId }
        if (user != null) onUserSelected(user)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_asset_details)
        app = application as FetchCICGApplication
        tag = intent.getStringExtra(EXTRA_TAG).orEmpty()

        nameInput = findViewById(R.id.nameInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        serialNumberInput = findViewById(R.id.serialNumberInput)
        categoryInput = findViewById(R.id.categoryInput)
        userLabel = findViewById(R.id.userLabel)
        userInput = findViewById(R.id.userInput)
        nextButton = findViewById(R.id.nextButton)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateNextButtonEnabled()
        }
        nameInput.addTextChangedListener(watcher)
        serialNumberInput.addTextChangedListener(watcher)

        categoryInput.setOnClickListener {
            pickCategory.launch(Intent(this, CategoryPickerActivity::class.java))
        }
        userInput.setOnClickListener {
            pickUser.launch(Intent(this, UserPickerActivity::class.java))
        }

        nextButton.setOnClickListener { onNext() }
    }

    private fun onCategorySelected(category: Category) {
        selectedCategory = category
        categoryInput.setText(category.name)
        updateNextButtonEnabled()

        if (category.isPersonalWeapon) {
            userLabel.visibility = View.VISIBLE
            userInput.visibility = View.VISIBLE
        } else {
            userLabel.visibility = View.GONE
            userInput.visibility = View.GONE
            userInput.setText("")
            selectedUser = null
        }
    }

    private fun onUserSelected(user: User) {
        selectedUser = user
        userInput.setText(user.fullName)
    }

    private fun updateNextButtonEnabled() {
        nextButton.isEnabled = nameInput.text.isNotBlank() &&
            serialNumberInput.text.isNotBlank() &&
            selectedCategory != null
    }

    private fun onNext() {
        val category = selectedCategory ?: return
        val assignedUserId = if (category.isPersonalWeapon) selectedUser?.id else null

        startActivity(
            Intent(this, AssetSummaryActivity::class.java)
                .putExtra(AssetSummaryActivity.EXTRA_NAME, nameInput.text.toString().trim())
                .putExtra(AssetSummaryActivity.EXTRA_DESCRIPTION, descriptionInput.text.toString().trim().ifEmpty { null })
                .putExtra(AssetSummaryActivity.EXTRA_CATEGORY_ID, category.id)
                .putExtra(AssetSummaryActivity.EXTRA_TAG, tag)
                .putExtra(AssetSummaryActivity.EXTRA_SERIAL_NUMBER, serialNumberInput.text.toString().trim())
                .putExtra(AssetSummaryActivity.EXTRA_ASSIGNED_USER_ID, assignedUserId ?: -1)
        )
    }
}
