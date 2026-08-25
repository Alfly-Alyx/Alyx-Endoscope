package com.jiangdg.demo

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.MediaController
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.jiangdg.demo.databinding.ActivityMediaViewerBinding

class MediaViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var record: MediaRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(MainActivity.resolveAppearanceTheme(this))
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_MEDIA_URI)
        record = uri?.let { MediaRepository.find(this, it) } ?: run {
            finish()
            return
        }
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.commentBtn.setOnClickListener { showCommentDialog() }
        showMedia()
    }

    override fun onStop() {
        if (::binding.isInitialized) binding.videoView.stopPlayback()
        super.onStop()
    }

    private fun showMedia() = with(binding) {
        timestampTv.text = MediaRepository.displayDate(record.capturedAt)
        timestampTv.visibility = if (record.hasEmbeddedStamp) View.GONE else View.VISIBLE
        commentTv.text = record.comment
        updateCommentVisibility()
        val uri = Uri.parse(record.uri)
        if (record.isVideo) {
            imageView.visibility = View.GONE
            videoView.visibility = View.VISIBLE
            videoView.setMediaController(MediaController(this@MediaViewerActivity).apply {
                setAnchorView(videoView)
            })
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { player ->
                player.isLooping = true
                videoView.start()
            }
        } else {
            videoView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            Glide.with(imageView).load(uri).fitCenter().into(imageView)
        }
    }

    private fun showCommentDialog() {
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
            .setNeutralButton(R.string.clear_comment) { _, _ -> saveComment("") }
            .setPositiveButton(R.string.save_comment) { _, _ -> saveComment(input.text.toString()) }
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun saveComment(comment: String) {
        MediaRepository.updateComment(this, record.uri, comment)
        record = MediaRepository.find(this, record.uri) ?: record
        binding.commentTv.text = record.comment
        updateCommentVisibility()
    }

    private fun updateCommentVisibility() {
        binding.commentTv.visibility = if (
            record.comment.isBlank() ||
            (record.hasEmbeddedStamp && record.comment == record.embeddedComment)
        ) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_MEDIA_URI = "media_uri"
    }
}
