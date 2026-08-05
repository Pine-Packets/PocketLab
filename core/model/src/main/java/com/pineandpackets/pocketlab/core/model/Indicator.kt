package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Indicator(
    val type: IndicatorType,
    val displayValue: String,
    val canonicalValue: String,
    val defangedValue: String,
    val source: IndicatorSource,
    val confidence: Confidence,
    val context: String? = null,
    val classification: List<String> = emptyList()
)

@Serializable
enum class IndicatorType {
    DOMAIN,
    URL,
    IPV4,
    IPV6,
    EMAIL,
    FILE_HASH,
    CERTIFICATE_FINGERPRINT,
    PACKAGE_NAME,
    ANDROID_COMPONENT,
    FILE_PATH,
    USER_AGENT,
    BOT_TOKEN,
    WEBHOOK
}

@Serializable
data class IndicatorSource(
    val container: String? = null,
    val entry: String? = null,
    val className: String? = null,
    val method: String? = null,
    val offset: Long? = null
)
