package com.patusmaximus.snapsmart.imageprocessing.model

import android.graphics.Bitmap

// Data class for scan results
data class FolderScanResult(
    val photoCount: Int,
    val thumbnails: List<Bitmap>,
    val estimatedProcessingTime: Double,
    val totalImages: Int
)
