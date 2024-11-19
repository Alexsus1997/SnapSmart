package com.patusmaximus.snapsmart.backend.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UserBucketPreferences(
    val sourceFolder : Uri?,
    var destinationFolder : Uri? = null,
    var keepOriginalFiles : Boolean? = true,
    var granularity : BucketGranularity? = BucketGranularity.Month
): Parcelable


public enum class BucketGranularity {
    Day,
    Month,
    Year
}