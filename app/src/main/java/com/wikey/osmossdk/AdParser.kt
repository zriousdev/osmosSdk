package com.wikey.osmossdk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AdParser {
    private val gson = Gson()

    fun parseAdResponse(response: Any?): AdData? {
        if (response == null) return null
        return try {
            val jsonString = gson.toJson(response)
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val responseMap: Map<String, Any> = gson.fromJson(jsonString, mapType)

            val ads = responseMap["ads"] as? Map<*, *> ?: return null
            val bannerAds = ads["banner_ads"] as? List<*> ?: return null
            val firstAd = bannerAds.firstOrNull() as? Map<*, *> ?: return null

            val uclid = firstAd["uclid"] as? String ?: ""
            val impressionTrackingUrl = firstAd["impression_tracking_url"] as? String ?: ""
            val clickTrackingUrl = firstAd["click_tracking_url"] as? String ?: ""

            val rawElements = firstAd["elements"]
            val elementsMap = when (rawElements) {
                is Map<*, *> -> rawElements
                is List<*> -> rawElements.firstOrNull() as? Map<*, *>
                else -> null
            } ?: return null

            val imageUrl = elementsMap["value"] as? String ?: return null
            val destinationUrl = elementsMap["destination_url"] as? String ?: ""

            val width = (elementsMap["width"] as? Number)?.toInt() ?: 320
            val height = (elementsMap["height"] as? Number)?.toInt() ?: 50

            AdData(
                imageUrl = imageUrl,
                destinationUrl = destinationUrl,
                impressionTrackingUrl = impressionTrackingUrl,
                clickTrackingUrl = clickTrackingUrl,
                width = width,
                height = height,
                uclid = uclid
            )
        } catch (e: Exception) {
            null
        }
    }
}
