package com.wanderai.travelplanner

import com.google.gson.annotations.SerializedName

data class DistanceMatrixResponse(
    @SerializedName("status") val status: String,
    @SerializedName("origin_addresses") val originAddresses: List<String>,
    @SerializedName("destination_addresses") val destinationAddresses: List<String>,
    @SerializedName("rows") val rows: List<DistanceRow>?
)

data class DistanceRow(
    @SerializedName("elements") val elements: List<DistanceElement>
)

data class DistanceElement(
    @SerializedName("status") val status: String,
    @SerializedName("distance") val distance: TextValueObject?,
    @SerializedName("duration") val duration: TextValueObject?
)

data class TextValueObject(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Long
)