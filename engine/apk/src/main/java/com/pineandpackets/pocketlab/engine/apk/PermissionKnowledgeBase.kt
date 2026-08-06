package com.pineandpackets.pocketlab.engine.apk

data class PermissionKnowledge(
    val name: String,
    val protectionLevel: ProtectionLevel,
    val category: PermissionCategory,
    val description: String,
    val riskLevel: RiskLevel,
    val apiLevel: Int? = null,
    val deprecated: Boolean = false
)

enum class ProtectionLevel {
    NORMAL,
    DANGEROUS,
    SIGNATURE,
    SIGNATURE_OR_SYSTEM,
    INTERNAL,
    UNKNOWN
}

enum class PermissionCategory {
    ACCOUNTS,
    CALENDAR,
    CALL_LOG,
    CAMERA,
    CONTACTS,
    DEVICE_ADMIN,
    LOCATION,
    MICROPHONE,
    NETWORK,
    NOTIFICATIONS,
    PACKAGE_USAGE,
    PHONE,
    SENSORS,
    SMS,
    STORAGE,
    SYSTEM,
    BLUETOOTH,
    NEARBY_DEVICES,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY,
    OVERLAY,
    INSTALL_PACKAGES,
    BOOT_COMPLETED,
    OTHER
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

object PermissionKnowledgeBase {
    
    private val permissions = mapOf(
        // Dangerous permissions (runtime permissions)
        "android.permission.READ_CALENDAR" to PermissionKnowledge(
            name = "android.permission.READ_CALENDAR",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CALENDAR,
            description = "Read calendar events and details",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.WRITE_CALENDAR" to PermissionKnowledge(
            name = "android.permission.WRITE_CALENDAR",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CALENDAR,
            description = "Add or modify calendar events and send emails to guests",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.READ_CALL_LOG" to PermissionKnowledge(
            name = "android.permission.READ_CALL_LOG",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CALL_LOG,
            description = "Read phone call log",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.WRITE_CALL_LOG" to PermissionKnowledge(
            name = "android.permission.WRITE_CALL_LOG",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CALL_LOG,
            description = "Write to phone call log",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.CAMERA" to PermissionKnowledge(
            name = "android.permission.CAMERA",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CAMERA,
            description = "Take pictures and record video",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.READ_CONTACTS" to PermissionKnowledge(
            name = "android.permission.READ_CONTACTS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CONTACTS,
            description = "Read contacts data",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.WRITE_CONTACTS" to PermissionKnowledge(
            name = "android.permission.WRITE_CONTACTS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.CONTACTS,
            description = "Modify contacts data",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.ACCESS_FINE_LOCATION" to PermissionKnowledge(
            name = "android.permission.ACCESS_FINE_LOCATION",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.LOCATION,
            description = "Access precise location using GPS",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionKnowledge(
            name = "android.permission.ACCESS_COARSE_LOCATION",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.LOCATION,
            description = "Access approximate location using network",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionKnowledge(
            name = "android.permission.ACCESS_BACKGROUND_LOCATION",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.LOCATION,
            description = "Access location in background",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.RECORD_AUDIO" to PermissionKnowledge(
            name = "android.permission.RECORD_AUDIO",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.MICROPHONE,
            description = "Record audio using microphone",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.READ_PHONE_STATE" to PermissionKnowledge(
            name = "android.permission.READ_PHONE_STATE",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.PHONE,
            description = "Read phone state and identity",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.CALL_PHONE" to PermissionKnowledge(
            name = "android.permission.CALL_PHONE",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.PHONE,
            description = "Make phone calls without user intervention",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.READ_PHONE_NUMBERS" to PermissionKnowledge(
            name = "android.permission.READ_PHONE_NUMBERS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.PHONE,
            description = "Read phone numbers",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.ANSWER_PHONE_CALLS" to PermissionKnowledge(
            name = "android.permission.ANSWER_PHONE_CALLS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.PHONE,
            description = "Answer incoming phone calls",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.BODY_SENSORS" to PermissionKnowledge(
            name = "android.permission.BODY_SENSORS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SENSORS,
            description = "Access body sensor data (heart rate, etc.)",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.SEND_SMS" to PermissionKnowledge(
            name = "android.permission.SEND_SMS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Send SMS messages without user intervention",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.RECEIVE_SMS" to PermissionKnowledge(
            name = "android.permission.RECEIVE_SMS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Receive and read SMS messages",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.READ_SMS" to PermissionKnowledge(
            name = "android.permission.READ_SMS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Read SMS messages",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.WRITE_SMS" to PermissionKnowledge(
            name = "android.permission.WRITE_SMS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Write to SMS storage",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.RECEIVE_MMS" to PermissionKnowledge(
            name = "android.permission.RECEIVE_MMS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Receive and monitor MMS messages",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.RECEIVE_WAP_PUSH" to PermissionKnowledge(
            name = "android.permission.RECEIVE_WAP_PUSH",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.SMS,
            description = "Receive WAP push messages",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.READ_EXTERNAL_STORAGE" to PermissionKnowledge(
            name = "android.permission.READ_EXTERNAL_STORAGE",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.STORAGE,
            description = "Read external storage contents",
            riskLevel = RiskLevel.HIGH,
            deprecated = true
        ),
        "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionKnowledge(
            name = "android.permission.WRITE_EXTERNAL_STORAGE",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.STORAGE,
            description = "Write to external storage",
            riskLevel = RiskLevel.HIGH,
            deprecated = true
        ),
        "android.permission.READ_MEDIA_IMAGES" to PermissionKnowledge(
            name = "android.permission.READ_MEDIA_IMAGES",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.STORAGE,
            description = "Read image files from storage",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 33
        ),
        "android.permission.READ_MEDIA_VIDEO" to PermissionKnowledge(
            name = "android.permission.READ_MEDIA_VIDEO",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.STORAGE,
            description = "Read video files from storage",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 33
        ),
        "android.permission.READ_MEDIA_AUDIO" to PermissionKnowledge(
            name = "android.permission.READ_MEDIA_AUDIO",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.STORAGE,
            description = "Read audio files from storage",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 33
        ),
        
        // Normal permissions (automatically granted)
        "android.permission.INTERNET" to PermissionKnowledge(
            name = "android.permission.INTERNET",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.NETWORK,
            description = "Access network (internet)",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionKnowledge(
            name = "android.permission.ACCESS_NETWORK_STATE",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.NETWORK,
            description = "Check network connection status",
            riskLevel = RiskLevel.LOW
        ),
        "android.permission.ACCESS_WIFI_STATE" to PermissionKnowledge(
            name = "android.permission.ACCESS_WIFI_STATE",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.NETWORK,
            description = "Check WiFi connection status",
            riskLevel = RiskLevel.LOW
        ),
        "android.permission.BLUETOOTH" to PermissionKnowledge(
            name = "android.permission.BLUETOOTH",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.BLUETOOTH,
            description = "Connect to Bluetooth devices",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.BLUETOOTH_ADMIN" to PermissionKnowledge(
            name = "android.permission.BLUETOOTH_ADMIN",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.BLUETOOTH,
            description = "Discover and pair Bluetooth devices",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.BLUETOOTH_SCAN" to PermissionKnowledge(
            name = "android.permission.BLUETOOTH_SCAN",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.NEARBY_DEVICES,
            description = "Scan for nearby Bluetooth devices",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 31
        ),
        "android.permission.BLUETOOTH_CONNECT" to PermissionKnowledge(
            name = "android.permission.BLUETOOTH_CONNECT",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.NEARBY_DEVICES,
            description = "Connect to paired Bluetooth devices",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 31
        ),
        "android.permission.NFC" to PermissionKnowledge(
            name = "android.permission.NFC",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.NEARBY_DEVICES,
            description = "Use NFC for contactless communication",
            riskLevel = RiskLevel.LOW
        ),
        "android.permission.VIBRATE" to PermissionKnowledge(
            name = "android.permission.VIBRATE",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.SYSTEM,
            description = "Control vibration",
            riskLevel = RiskLevel.LOW
        ),
        "android.permission.WAKE_LOCK" to PermissionKnowledge(
            name = "android.permission.WAKE_LOCK",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.SYSTEM,
            description = "Prevent device from sleeping",
            riskLevel = RiskLevel.LOW
        ),
        "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionKnowledge(
            name = "android.permission.RECEIVE_BOOT_COMPLETED",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.BOOT_COMPLETED,
            description = "Start automatically when device boots",
            riskLevel = RiskLevel.MEDIUM
        ),
        "android.permission.FOREGROUND_SERVICE" to PermissionKnowledge(
            name = "android.permission.FOREGROUND_SERVICE",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.SYSTEM,
            description = "Run foreground services",
            riskLevel = RiskLevel.LOW,
            apiLevel = 28
        ),
        "android.permission.POST_NOTIFICATIONS" to PermissionKnowledge(
            name = "android.permission.POST_NOTIFICATIONS",
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = PermissionCategory.NOTIFICATIONS,
            description = "Post notifications",
            riskLevel = RiskLevel.LOW,
            apiLevel = 33
        ),
        
        // Signature permissions
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionKnowledge(
            name = "android.permission.BIND_ACCESSIBILITY_SERVICE",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.ACCESSIBILITY,
            description = "Bind to accessibility service (can read/modify all screen content)",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to PermissionKnowledge(
            name = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.NOTIFICATION_LISTENER,
            description = "Read all notifications (may contain sensitive data)",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionKnowledge(
            name = "android.permission.SYSTEM_ALERT_WINDOW",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.OVERLAY,
            description = "Draw over other apps (can be used for clickjacking)",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionKnowledge(
            name = "android.permission.REQUEST_INSTALL_PACKAGES",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.INSTALL_PACKAGES,
            description = "Request to install packages",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.INSTALL_PACKAGES" to PermissionKnowledge(
            name = "android.permission.INSTALL_PACKAGES",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.INSTALL_PACKAGES,
            description = "Install packages (system apps only)",
            riskLevel = RiskLevel.CRITICAL
        ),
        "android.permission.PACKAGE_USAGE_STATS" to PermissionKnowledge(
            name = "android.permission.PACKAGE_USAGE_STATS",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.PACKAGE_USAGE,
            description = "Collect usage statistics (what apps are used)",
            riskLevel = RiskLevel.HIGH
        ),
        "android.permission.QUERY_ALL_PACKAGES" to PermissionKnowledge(
            name = "android.permission.QUERY_ALL_PACKAGES",
            protectionLevel = ProtectionLevel.NORMAL,
            category = PermissionCategory.PACKAGE_USAGE,
            description = "Query information about all installed apps",
            riskLevel = RiskLevel.MEDIUM,
            apiLevel = 30
        ),
        
        // Device admin
        "android.permission.BIND_DEVICE_ADMIN" to PermissionKnowledge(
            name = "android.permission.BIND_DEVICE_ADMIN",
            protectionLevel = ProtectionLevel.SIGNATURE,
            category = PermissionCategory.DEVICE_ADMIN,
            description = "Device administrator (can lock/wipe device)",
            riskLevel = RiskLevel.CRITICAL
        )
    )
    
    fun getPermissionKnowledge(permissionName: String): PermissionKnowledge {
        return permissions[permissionName] ?: PermissionKnowledge(
            name = permissionName,
            protectionLevel = ProtectionLevel.UNKNOWN,
            category = PermissionCategory.OTHER,
            description = "Unknown permission",
            riskLevel = RiskLevel.MEDIUM
        )
    }
    
    fun getProtectionLevel(permissionName: String): ProtectionLevel {
        return getPermissionKnowledge(permissionName).protectionLevel
    }
    
    fun isDangerousPermission(permissionName: String): Boolean {
        return getProtectionLevel(permissionName) == ProtectionLevel.DANGEROUS
    }
    
    fun getAllDangerousPermissions(): List<PermissionKnowledge> {
        return permissions.values.filter { it.protectionLevel == ProtectionLevel.DANGEROUS }
    }
    
    fun getPermissionsByCategory(category: PermissionCategory): List<PermissionKnowledge> {
        return permissions.values.filter { it.category == category }
    }
    
    fun getHighRiskPermissions(): List<PermissionKnowledge> {
        return permissions.values.filter { 
            it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL 
        }
    }
}
