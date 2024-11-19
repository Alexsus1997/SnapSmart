package com.patusmaximus.snapsmart.backend.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UserScanPreferences(
    val sourceFolder : Uri?,
    var destinationFolder : Uri? = null,
    val moveImages : Boolean = false,
    var deleteNotRecommendedPhotos : Boolean = false
): Parcelable
