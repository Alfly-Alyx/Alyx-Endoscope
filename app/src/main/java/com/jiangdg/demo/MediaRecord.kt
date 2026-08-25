package com.jiangdg.demo

data class MediaRecord(
    val uri: String,
    val mimeType: String,
    val capturedAt: Long,
    val displayName: String,
    val comment: String = "",
    val hasEmbeddedStamp: Boolean = false,
    val embeddedComment: String = ""
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/")
}
