package com.jiangdg.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jiangdg.demo.databinding.ActivityGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private val adapter = MediaGalleryAdapter(::openMedia, ::showCommentDialog)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(MainActivity.resolveAppearanceTheme(this))
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.mediaList.layoutManager = GridLayoutManager(this, 2)
        binding.mediaList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshMedia()
    }

    private fun refreshMedia() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                MediaRepository.importSelectedFolder(this@GalleryActivity)
                MediaRepository.list(this@GalleryActivity)
            }
            adapter.submitList(records)
            binding.emptyTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openMedia(record: MediaRecord) {
        startActivity(
            Intent(this, MediaViewerActivity::class.java).apply {
                putExtra(MediaViewerActivity.EXTRA_MEDIA_URI, record.uri)
            }
        )
    }

    private fun showCommentDialog(record: MediaRecord) {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            setText(record.comment)
            setSelection(text.length)
            hint = getString(R.string.media_comment_hint)
            minLines = 2
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
            setPadding((24 * density).toInt(), (10 * density).toInt(),
                (24 * density).toInt(), (10 * density).toInt())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.media_comment_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.clear_comment) { _, _ ->
                MediaRepository.updateComment(this, record.uri, "")
                refreshMedia()
            }
            .setPositiveButton(R.string.save_comment) { _, _ ->
                MediaRepository.updateComment(this, record.uri, input.text.toString())
                refreshMedia()
            }
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }
}
