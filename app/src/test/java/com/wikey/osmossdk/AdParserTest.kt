package com.wikey.osmossdk

import org.junit.Assert.*
import org.junit.Test

class AdParserTest {

    @Test
    fun testParseValidAdResponse() {
        val mockResponse = mapOf(
            "ads" to mapOf(
                "banner_ads" to listOf(
                    mapOf(
                        "uclid" to "mock-uclid-123",
                        "impression_tracking_url" to "https://demo.o-s.io/impression",
                        "click_tracking_url" to "https://demo.o-s.io/click",
                        "elements" to mapOf(
                            "value" to "https://demo.o-s.io/ad.png",
                            "destination_url" to "https://demo.o-s.io/landing",
                            "width" to 320,
                            "height" to 50
                        )
                    )
                )
            )
        )

        val adData = AdParser.parseAdResponse(mockResponse)
        assertNotNull(adData)
        assertEquals("https://demo.o-s.io/ad.png", adData?.imageUrl)
        assertEquals("https://demo.o-s.io/landing", adData?.destinationUrl)
        assertEquals("https://demo.o-s.io/impression", adData?.impressionTrackingUrl)
        assertEquals("https://demo.o-s.io/click", adData?.clickTrackingUrl)
        assertEquals(320, adData?.width)
        assertEquals(50, adData?.height)
        assertEquals("mock-uclid-123", adData?.uclid)
    }

    @Test
    fun testParseElementsAsList() {
        val mockResponse = mapOf(
            "ads" to mapOf(
                "banner_ads" to listOf(
                    mapOf(
                        "uclid" to "mock-uclid-456",
                        "impression_tracking_url" to "https://demo.o-s.io/impression",
                        "click_tracking_url" to "https://demo.o-s.io/click",
                        "elements" to listOf(
                            mapOf(
                                "value" to "https://demo.o-s.io/ad_list.png",
                                "destination_url" to "https://demo.o-s.io/landing_list",
                                "width" to 728,
                                "height" to 90
                            )
                        )
                    )
                )
            )
        )

        val adData = AdParser.parseAdResponse(mockResponse)
        assertNotNull(adData)
        assertEquals("https://demo.o-s.io/ad_list.png", adData?.imageUrl)
        assertEquals("https://demo.o-s.io/landing_list", adData?.destinationUrl)
        assertEquals(728, adData?.width)
        assertEquals(90, adData?.height)
        assertEquals("mock-uclid-456", adData?.uclid)
    }

    @Test
    fun testParseInvalidResponse() {
        val invalidResponse = mapOf(
            "ads" to mapOf(
                "banner_ads" to listOf(
                    mapOf(
                        "uclid" to "mock-uclid"
                        // Missing elements and tracking urls
                    )
                )
            )
        )

        val adData = AdParser.parseAdResponse(invalidResponse)
        assertNull(adData)
    }

    @Test
    fun testParseNullResponse() {
        val adData = AdParser.parseAdResponse(null)
        assertNull(adData)
    }
}
