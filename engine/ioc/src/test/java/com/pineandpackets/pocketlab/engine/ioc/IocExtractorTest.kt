package com.pineandpackets.pocketlab.engine.ioc

import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.IndicatorType
import org.junit.Assert.*
import org.junit.Test

class IocExtractorTest {
    
    private val extractor = IocExtractor()
    
    @Test
    fun `extract HTTP URLs`() {
        val text = "Visit http://example.com for more info"
        val indicators = extractor.extractIndicators(text)
        
        assertTrue(indicators.any { it.type == IndicatorType.URL && it.displayValue.contains("http://example.com") })
    }
    
    @Test
    fun `extract HTTPS URLs`() {
        val text = "Secure site: https://secure.example.com/path?param=value"
        val indicators = extractor.extractIndicators(text)
        
        assertTrue(indicators.any { it.type == IndicatorType.URL && it.displayValue.contains("https://secure.example.com") })
    }
    
    @Test
    fun `extract domains`() {
        val text = "Connect to api.example.com or www.test.org"
        val indicators = extractor.extractIndicators(text)
        
        assertTrue(indicators.any { it.type == IndicatorType.DOMAIN && it.displayValue == "api.example.com" })
        assertTrue(indicators.any { it.type == IndicatorType.DOMAIN && it.displayValue == "www.test.org" })
    }
    
    @Test
    fun `extract IPv4 addresses`() {
        val text = "Server at 192.168.1.1 and public IP 8.8.8.8"
        val indicators = extractor.extractIndicators(text)
        
        val ips = indicators.filter { it.type == IndicatorType.IPV4 }
        assertEquals(2, ips.size)
        assertTrue(ips.any { it.displayValue == "192.168.1.1" })
        assertTrue(ips.any { it.displayValue == "8.8.8.8" })
    }
    
    @Test
    fun `classify private IP addresses`() {
        val text = "Private IPs: 10.0.0.1, 172.16.0.1, 192.168.0.1"
        val indicators = extractor.extractIndicators(text)
        
        val privateIps = indicators.filter { 
            it.type == IndicatorType.IPV4 && it.classification.contains("PRIVATE") 
        }
        assertEquals(3, privateIps.size)
    }
    
    @Test
    fun `classify public IP addresses`() {
        val text = "Public IP: 8.8.8.8"
        val indicators = extractor.extractIndicators(text)
        
        val publicIp = indicators.find { it.displayValue == "8.8.8.8" }
        assertNotNull(publicIp)
        assertTrue(publicIp!!.classification.contains("PUBLIC"))
    }
    
    @Test
    fun `extract email addresses`() {
        val text = "Contact us at user@example.com or admin@test.org"
        val indicators = extractor.extractIndicators(text)
        
        assertTrue(indicators.any { it.type == IndicatorType.EMAIL && it.displayValue == "user@example.com" })
        assertTrue(indicators.any { it.type == IndicatorType.EMAIL && it.displayValue == "admin@test.org" })
    }
    
    @Test
    fun `defang URLs`() {
        val text = "URL: http://malware.example.com/bad"
        val indicators = extractor.extractIndicators(text)
        
        val url = indicators.find { it.type == IndicatorType.URL }
        assertNotNull(url)
        assertTrue(url!!.defangedValue.contains("hxxp://"))
        assertTrue(url.defangedValue.contains("[.]"))
    }
    
    @Test
    fun `defang domains`() {
        val text = "Domain: evil.example.com"
        val indicators = extractor.extractIndicators(text)
        
        val domain = indicators.find { it.type == IndicatorType.DOMAIN && it.displayValue == "evil.example.com" }
        assertNotNull(domain)
        assertEquals("evil[.]example[.]com", domain!!.defangedValue)
    }
    
    @Test
    fun `defang IP addresses`() {
        val text = "IP: 192.168.1.1"
        val indicators = extractor.extractIndicators(text)
        
        val ip = indicators.find { it.type == IndicatorType.IPV4 }
        assertNotNull(ip)
        assertEquals("192[.]168[.]1[.]1", ip!!.defangedValue)
    }
    
    @Test
    fun `defang email addresses`() {
        val text = "Email: user@example.com"
        val indicators = extractor.extractIndicators(text)
        
        val email = indicators.find { it.type == IndicatorType.EMAIL }
        assertNotNull(email)
        assertEquals("user[@]example[.]com", email!!.defangedValue)
    }
    
    @Test
    fun `reject invalid IP addresses`() {
        val text = "Invalid IPs: 256.256.256.256, 999.999.999.999"
        val indicators = extractor.extractIndicators(text)
        
        val ips = indicators.filter { it.type == IndicatorType.IPV4 }
        assertTrue(ips.isEmpty())
    }
    
    @Test
    fun `deduplicate indicators`() {
        val text = "Same URL twice: http://example.com and http://example.com"
        val indicators = extractor.extractIndicators(text)
        
        val urls = indicators.filter { it.type == IndicatorType.URL }
        assertEquals(1, urls.size)
    }
    
    @Test
    fun `extract multiple indicator types`() {
        val text = """
            URL: https://api.example.com
            Domain: cdn.example.com
            IP: 10.0.0.1
            Email: admin@example.com
        """.trimIndent()
        
        val indicators = extractor.extractIndicators(text)
        
        assertTrue(indicators.any { it.type == IndicatorType.URL })
        assertTrue(indicators.any { it.type == IndicatorType.DOMAIN })
        assertTrue(indicators.any { it.type == IndicatorType.IPV4 })
        assertTrue(indicators.any { it.type == IndicatorType.EMAIL })
    }
}
