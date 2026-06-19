package com.wikey.osmossdk

data class AdData(
    val imageUrl: String,
    val destinationUrl: String,
    val impressionTrackingUrl: String,
    val clickTrackingUrl: String,
    val width: Int,
    val height: Int,
    val uclid: String
)

sealed interface AdState {
    object Idle : AdState
    object Loading : AdState
    data class Success(val ad: AdData) : AdState
    data class Error(val message: String) : AdState
    object Empty : AdState
}
