package com.pineandpackets.pocketlab.engine.ioc

import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.core.model.IndicatorType
import timber.log.Timber

class IocExtractor {
    
    fun extractIndicators(text: String, source: IndicatorSource? = null): List<Indicator> {
        val indicators = mutableListOf<Indicator>()
        
        indicators.addAll(extractUrls(text, source))
        indicators.addAll(extractDomains(text, source))
        indicators.addAll(extractIpAddresses(text, source))
        indicators.addAll(extractEmails(text, source))
        
        return indicators.distinctBy { it.canonicalValue }
    }
    
    private fun extractUrls(text: String, source: IndicatorSource?): List<Indicator> {
        val urlPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
        return urlPattern.findAll(text).map { match ->
            val url = match.value
            val domain = extractDomainFromUrl(url)
            
            Indicator(
                type = IndicatorType.URL,
                displayValue = url,
                canonicalValue = url.lowercase(),
                defangedValue = defangUrl(url),
                source = source,
                confidence = Confidence.HIGH,
                context = "URL found in text",
                classification = listOf("NETWORK_DESTINATION")
            )
        }.toList()
    }
    
    private fun extractDomains(text: String, source: IndicatorSource?): List<Indicator> {
        val domainPattern = Regex("""\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}\b""")
        return domainPattern.findAll(text).map { match ->
            val domain = match.value
            
            Indicator(
                type = IndicatorType.DOMAIN,
                displayValue = domain,
                canonicalValue = domain.lowercase(),
                defangedValue = defangDomain(domain),
                source = source,
                confidence = Confidence.MEDIUM,
                context = "Domain found in text",
                classification = listOf("NETWORK_DESTINATION")
            )
        }.toList()
    }
    
    private fun extractIpAddresses(text: String, source: IndicatorSource?): List<Indicator> {
        val ipv4Pattern = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
        return ipv4Pattern.findAll(text).mapNotNull { match ->
            val ip = match.value
            
            if (isValidIpv4(ip)) {
                Indicator(
                    type = IndicatorType.IPV4,
                    displayValue = ip,
                    canonicalValue = ip,
                    defangedValue = defangIp(ip),
                    source = source,
                    confidence = Confidence.HIGH,
                    context = "IPv4 address found in text",
                    classification = classifyIpAddress(ip)
                )
            } else {
                null
            }
        }.toList()
    }
    
    private fun extractEmails(text: String, source: IndicatorSource?): List<Indicator> {
        val emailPattern = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
        return emailPattern.findAll(text).map { match ->
            val email = match.value
            
            Indicator(
                type = IndicatorType.EMAIL,
                displayValue = email,
                canonicalValue = email.lowercase(),
                defangedValue = defangEmail(email),
                source = source,
                confidence = Confidence.MEDIUM,
                context = "Email address found in text",
                classification = listOf("IDENTIFIER")
            )
        }.toList()
    }
    
    private fun extractDomainFromUrl(url: String): String? {
        return try {
            val withoutProtocol = url.removePrefix("http://").removePrefix("https://")
            withoutProtocol.split("/").firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }
    
    private fun classifyIpAddress(ip: String): List<String> {
        val parts = ip.split(".").map { it.toInt() }
        
        return when {
            parts[0] == 10 -> listOf("PRIVATE", "RFC1918")
            parts[0] == 172 && parts[1] in 16..31 -> listOf("PRIVATE", "RFC1918")
            parts[0] == 192 && parts[1] == 168 -> listOf("PRIVATE", "RFC1918")
            parts[0] == 127 -> listOf("LOOPBACK")
            parts[0] == 0 -> listOf("RESERVED")
            else -> listOf("PUBLIC", "NETWORK_DESTINATION")
        }
    }
    
    private fun defangUrl(url: String): String {
        return url.replace(".", "[.]").replace("http://", "hxxp://").replace("https://", "hxxps://")
    }
    
    private fun defangDomain(domain: String): String {
        return domain.replace(".", "[.]")
    }
    
    private fun defangIp(ip: String): String {
        return ip.replace(".", "[.]")
    }
    
    private fun defangEmail(email: String): String {
        return email.replace("@", "[@]").replace(".", "[.]")
    }
}
