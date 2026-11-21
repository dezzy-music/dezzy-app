/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class StreamClientType(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val origin: String = "https://www.youtube.com",
    val referer: String = "https://www.youtube.com",
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null
) {
    IOS(
        clientName = "iOS",
        clientVersion = "20.11.6",
        userAgent = "com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)",
        deviceModel = "iPhone10,4",
        osName = "iOS",
        osVersion = "16.7.7.20H330"
    ),
    ANDROID_VR(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        deviceMake = "Oculus",
        deviceModel = "Quest 3"
    ),
    VISIONOS(
        clientName = "VISIONOS",
        clientVersion = "1.02",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        osName = "visionOS",
        osVersion = "26.5.23O471"
    ),
    TV_SIMPLY(
        clientName = "TVHTML5_SIMPLY",
        clientVersion = "1.0",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
    );

    fun buildPlayerPayload(
        videoId: String,
        poToken: String? = null,
        visitorData: String? = null,
        hl: String = "es",
        gl: String = "US"
    ): JsonObject {
        return buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    put("hl", hl)
                    put("gl", gl)
                    if (deviceModel != null) put("deviceModel", deviceModel)
                    if (osName != null) put("osName", osName)
                    if (osVersion != null) put("osVersion", osVersion)
                    if (deviceMake != null) put("deviceMake", deviceMake)
                    if (visitorData != null) put("visitorData", visitorData)
                }
            }
            put("videoId", videoId)
            put("racyCheckOk", true)
            put("contentCheckOk", true)
            if (poToken != null) {
                putJsonObject("serviceIntegrityDimensions") {
                    put("poToken", poToken)
                }
            }
        }
    }
}
