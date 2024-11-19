package com.patusmaximus.snapsmart.backend.model

import android.os.Parcelable
import com.patusmaximus.snapsmart.model.blurModelType
import kotlinx.android.parcel.Parcelize

// Data class for scan results
@Parcelize
data class ImageScanResult(
    val imageName: String,
    val score: Int,
    val label: blurModelType,
    val uriString: String,
    val calculatedScore: Int,
    var selected: Boolean = false
) : Parcelable
