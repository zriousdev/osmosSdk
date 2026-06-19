package com.wikey.osmossdk

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AdParser {
    private val gson = Gson()

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.e(tag, msg, tr)
        } catch (e: Throwable) {
            println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }

    fun parseAdResponse(response: Any?): AdData? {
        if (response == null) {
            logD("AdParser", "Response is null")
            return null
        }
        logD("AdParser", "Parsing response of type: ${response.javaClass.name}")
        return try {
            val jsonString = gson.toJson(response)
            logD("AdParser", "Serialized JSON: $jsonString")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val responseMap: Map<String, Any> = gson.fromJson(jsonString, mapType)

            // Resiliently check if "ads" is at the root or wrapped in SDK response.data
            val ads = if (responseMap.containsKey("ads")) {
                responseMap["ads"] as? Map<*, *>
            } else {
                val sdkResponse = responseMap["response"] as? Map<*, *>
                val dataString = sdkResponse?.get("data") as? String
                if (dataString != null) {
                    logD("AdParser", "Found wrapped response data string, parsing it...")
                    val dataMap: Map<String, Any> = gson.fromJson(dataString, mapType)
                    dataMap["ads"] as? Map<*, *>
                } else {
                    null
                }
            }

            if (ads == null) {
                logD("AdParser", "Failed to find or cast 'ads' field to Map")
                return null
            }

            val bannerAds = ads["banner_ads"] as? List<*> ?: run {
                logD("AdParser", "Failed to cast 'banner_ads' field to List")
                return null
            }
            val firstAd = bannerAds.firstOrNull() as? Map<*, *> ?: run {
                logD("AdParser", "No ads in 'banner_ads' list or unable to cast to Map")
                return null
            }

            val uclid = firstAd["uclid"] as? String ?: ""
            val impressionTrackingUrl = firstAd["impression_tracking_url"] as? String ?: ""
            val clickTrackingUrl = firstAd["click_tracking_url"] as? String ?: ""

            val rawElements = firstAd["elements"]
            val elementsMap = when (rawElements) {
                is Map<*, *> -> rawElements
                is List<*> -> rawElements.firstOrNull() as? Map<*, *>
                else -> null
            } ?: run {
                logD("AdParser", "elements field is missing or not a Map/List")
                return null
            }

            val imageUrl = elementsMap["value"] as? String ?: run {
                logD("AdParser", "imageUrl (value) field is missing in elements")
                return null
            }
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
            logE("AdParser", "Exception during parsing", e)
            null
        }
    }
}
