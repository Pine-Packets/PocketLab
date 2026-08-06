package com.pineandpackets.pocketlab.engine.dex

/**
 * Maps Android API calls to security-relevant capabilities.
 * Used to identify what an app can do based on its code.
 */
object ApiCapabilityMap {
    
    data class ApiCapability(
        val className: String,
        val methodName: String,
        val capability: String,
        val severity: String,
        val description: String
    )
    
    private val capabilities = listOf(
        // SMS capabilities
        ApiCapability("Landroid/telephony/SmsManager;", "sendTextMessage", "SEND_SMS", "HIGH", "Can send SMS messages"),
        ApiCapability("Landroid/telephony/SmsManager;", "sendDataMessage", "SEND_SMS", "HIGH", "Can send data SMS messages"),
        ApiCapability("Landroid/telephony/SmsManager;", "sendMultipartTextMessage", "SEND_SMS", "HIGH", "Can send multipart SMS"),
        ApiCapability("Landroid/telephony/SmsManager;", "getDefault", "SMS_ACCESS", "MEDIUM", "Accesses SMS manager"),
        
        // Phone capabilities
        ApiCapability("Landroid/telephony/TelephonyManager;", "getDeviceId", "READ_DEVICE_ID", "HIGH", "Can read device IMEI"),
        ApiCapability("Landroid/telephony/TelephonyManager;", "getSubscriberId", "READ_SUBSCRIBER_ID", "HIGH", "Can read IMSI"),
        ApiCapability("Landroid/telephony/TelephonyManager;", "getLine1Number", "READ_PHONE_NUMBER", "HIGH", "Can read phone number"),
        ApiCapability("Landroid/telephony/TelephonyManager;", "getCellLocation", "READ_CELL_LOCATION", "MEDIUM", "Can read cell location"),
        ApiCapability("Landroid/telephony/TelephonyManager;", "getAllCellInfo", "READ_CELL_INFO", "MEDIUM", "Can read cell information"),
        ApiCapability("Landroid/content/Intent;", "ACTION_CALL", "MAKE_CALL", "CRITICAL", "Can make phone calls"),
        ApiCapability("Landroid/net/Uri;", "parse", "URI_PARSE", "LOW", "Parses URIs"),
        
        // Location capabilities
        ApiCapability("Landroid/location/LocationManager;", "requestLocationUpdates", "ACCESS_LOCATION", "HIGH", "Can access location"),
        ApiCapability("Landroid/location/LocationManager;", "getLastKnownLocation", "ACCESS_LOCATION", "HIGH", "Can access last known location"),
        ApiCapability("Landroid/location/LocationManager;", "getBestProvider", "ACCESS_LOCATION", "MEDIUM", "Selects location provider"),
        
        // Contact capabilities
        ApiCapability("Landroid/provider/ContactsContract;", "CONTENT_URI", "ACCESS_CONTACTS", "HIGH", "Can access contacts"),
        ApiCapability("Landroid/content/ContentResolver;", "query", "QUERY_CONTENT", "MEDIUM", "Can query content providers"),
        ApiCapability("Landroid/content/ContentResolver;", "insert", "INSERT_CONTENT", "MEDIUM", "Can insert content"),
        ApiCapability("Landroid/content/ContentResolver;", "update", "UPDATE_CONTENT", "MEDIUM", "Can update content"),
        ApiCapability("Landroid/content/ContentResolver;", "delete", "DELETE_CONTENT", "MEDIUM", "Can delete content"),
        
        // Camera capabilities
        ApiCapability("Landroid/hardware/Camera;", "open", "ACCESS_CAMERA", "MEDIUM", "Can access camera"),
        ApiCapability("Landroid/hardware/Camera;", "takePicture", "TAKE_PICTURE", "MEDIUM", "Can take pictures"),
        ApiCapability("Landroid/media/MediaRecorder;", "start", "RECORD_MEDIA", "HIGH", "Can record audio/video"),
        
        // Network capabilities
        ApiCapability("Ljava/net/URL;", "openConnection", "NETWORK_ACCESS", "MEDIUM", "Can make network connections"),
        ApiCapability("Ljava/net/HttpURLConnection;", "connect", "HTTP_CONNECTION", "MEDIUM", "Can make HTTP connections"),
        ApiCapability("Lorg/apache/http/impl/client/DefaultHttpClient;", "execute", "HTTP_REQUEST", "MEDIUM", "Can execute HTTP requests"),
        ApiCapability("Lokhttp3/OkHttpClient;", "newCall", "HTTP_REQUEST", "MEDIUM", "Can make HTTP requests via OkHttp"),
        ApiCapability("Ljava/net/Socket;", "<init>", "SOCKET_CONNECTION", "MEDIUM", "Can create socket connections"),
        ApiCapability("Ljava/net/ServerSocket;", "<init>", "SERVER_SOCKET", "HIGH", "Can create server sockets"),
        
        // Storage capabilities
        ApiCapability("Landroid/os/Environment;", "getExternalStorageDirectory", "ACCESS_EXTERNAL_STORAGE", "MEDIUM", "Can access external storage"),
        ApiCapability("Ljava/io/File;", "delete", "DELETE_FILE", "MEDIUM", "Can delete files"),
        ApiCapability("Ljava/io/File;", "mkdir", "CREATE_DIRECTORY", "LOW", "Can create directories"),
        ApiCapability("Ljava/io/FileOutputStream;", "<init>", "WRITE_FILE", "MEDIUM", "Can write files"),
        ApiCapability("Ljava/io/FileInputStream;", "<init>", "READ_FILE", "MEDIUM", "Can read files"),
        
        // Crypto capabilities
        ApiCapability("Ljavax/crypto/Cipher;", "getInstance", "CRYPTO_OPERATION", "LOW", "Can perform crypto operations"),
        ApiCapability("Ljavax/crypto/Cipher;", "doFinal", "CRYPTO_ENCRYPT_DECRYPT", "MEDIUM", "Can encrypt/decrypt data"),
        ApiCapability("Ljava/security/MessageDigest;", "getInstance", "HASH_OPERATION", "LOW", "Can compute hashes"),
        ApiCapability("Ljava/security/KeyStore;", "getInstance", "KEYSTORE_ACCESS", "MEDIUM", "Can access keystore"),
        
        // Reflection capabilities
        ApiCapability("Ljava/lang/Class;", "forName", "REFLECTION", "HIGH", "Can use reflection to load classes"),
        ApiCapability("Ljava/lang/reflect/Method;", "invoke", "REFLECTION_INVOKE", "CRITICAL", "Can invoke methods via reflection"),
        ApiCapability("Ljava/lang/reflect/Constructor;", "newInstance", "REFLECTION_INSTANTIATE", "HIGH", "Can instantiate classes via reflection"),
        ApiCapability("Ldalvik/system/DexClassLoader;", "<init>", "DYNAMIC_CODE_LOADING", "CRITICAL", "Can load DEX files dynamically"),
        ApiCapability("Ldalvik/system/PathClassLoader;", "<init>", "DYNAMIC_CODE_LOADING", "CRITICAL", "Can load code from paths"),
        ApiCapability("Ljava/lang/Runtime;", "exec", "EXECUTE_COMMAND", "CRITICAL", "Can execute system commands"),
        ApiCapability("Ljava/lang/ProcessBuilder;", "start", "EXECUTE_COMMAND", "CRITICAL", "Can start processes"),
        
        // Accessibility capabilities
        ApiCapability("Landroid/accessibilityservice/AccessibilityService;", "onAccessibilityEvent", "ACCESSIBILITY_SERVICE", "CRITICAL", "Implements accessibility service"),
        ApiCapability("Landroid/view/accessibility/AccessibilityEvent;", "getEventType", "ACCESSIBILITY_EVENT", "HIGH", "Can receive accessibility events"),
        
        // Overlay capabilities
        ApiCapability("Landroid/view/WindowManager;", "addView", "SYSTEM_OVERLAY", "HIGH", "Can add system overlays"),
        ApiCapability("Landroid/widget/Toast;", "show", "SHOW_TOAST", "LOW", "Can show toast messages"),
        
        // Broadcast capabilities
        ApiCapability("Landroid/content/Context;", "sendBroadcast", "SEND_BROADCAST", "MEDIUM", "Can send broadcasts"),
        ApiCapability("Landroid/content/Context;", "sendOrderedBroadcast", "SEND_ORDERED_BROADCAST", "MEDIUM", "Can send ordered broadcasts"),
        ApiCapability("Landroid/content/Context;", "registerReceiver", "REGISTER_RECEIVER", "MEDIUM", "Can register receivers"),
        
        // Service capabilities
        ApiCapability("Landroid/content/Context;", "startService", "START_SERVICE", "MEDIUM", "Can start services"),
        ApiCapability("Landroid/content/Context;", "bindService", "BIND_SERVICE", "MEDIUM", "Can bind to services"),
        
        // Package manager capabilities
        ApiCapability("Landroid/content/pm/PackageManager;", "getInstalledPackages", "ENUMERATE_PACKAGES", "HIGH", "Can enumerate installed packages"),
        ApiCapability("Landroid/content/pm/PackageManager;", "getInstalledApplications", "ENUMERATE_PACKAGES", "HIGH", "Can enumerate installed applications"),
        ApiCapability("Landroid/content/pm/PackageManager;", "installPackage", "INSTALL_PACKAGES", "CRITICAL", "Can install packages"),
        
        // Clipboard capabilities
        ApiCapability("Landroid/content/ClipboardManager;", "getPrimaryClip", "READ_CLIPBOARD", "HIGH", "Can read clipboard"),
        ApiCapability("Landroid/content/ClipboardManager;", "setPrimaryClip", "WRITE_CLIPBOARD", "MEDIUM", "Can write to clipboard"),
        
        // Notification capabilities
        ApiCapability("Landroid/app/NotificationManager;", "notify", "SHOW_NOTIFICATION", "LOW", "Can show notifications"),
        ApiCapability("Landroid/service/notification/NotificationListenerService;", "onNotificationPosted", "NOTIFICATION_LISTENER", "CRITICAL", "Can read notifications"),
        
        // WebView capabilities
        ApiCapability("Landroid/webkit/WebView;", "loadUrl", "WEBVIEW_LOAD_URL", "MEDIUM", "Can load URLs in WebView"),
        ApiCapability("Landroid/webkit/WebView;", "addJavascriptInterface", "WEBVIEW_JS_INTERFACE", "HIGH", "Can add JavaScript interface to WebView"),
        ApiCapability("Landroid/webkit/WebView;", "setJavaScriptEnabled", "WEBVIEW_JS_ENABLED", "MEDIUM", "Can enable JavaScript in WebView"),
        
        // Account capabilities
        ApiCapability("Landroid/accounts/AccountManager;", "getAccounts", "READ_ACCOUNTS", "HIGH", "Can read accounts"),
        ApiCapability("Landroid/accounts/AccountManager;", "getAuthToken", "GET_AUTH_TOKEN", "HIGH", "Can get auth tokens"),
        
        // Calendar capabilities
        ApiCapability("Landroid/provider/CalendarContract;", "CONTENT_URI", "ACCESS_CALENDAR", "HIGH", "Can access calendar"),
        
        // Call log capabilities
        ApiCapability("Landroid/provider/CallLog;", "CONTENT_URI", "ACCESS_CALL_LOG", "HIGH", "Can access call log"),
        
        // Settings capabilities
        ApiCapability("Landroid/provider/Settings;", "getString", "READ_SETTINGS", "MEDIUM", "Can read system settings"),
        ApiCapability("Landroid/provider/Settings;", "putString", "WRITE_SETTINGS", "HIGH", "Can write system settings")
    )
    
    /**
     * Get all capabilities for a given class and method.
     */
    fun getCapabilities(className: String, methodName: String): List<ApiCapability> {
        return capabilities.filter { 
            it.className == className && it.methodName == methodName 
        }
    }
    
    /**
     * Get all capabilities for a given class.
     */
    fun getCapabilitiesForClass(className: String): List<ApiCapability> {
        return capabilities.filter { it.className == className }
    }
    
    /**
     * Get all capabilities matching a pattern.
     */
    fun getCapabilitiesByPattern(pattern: String): List<ApiCapability> {
        val regex = Regex(pattern)
        return capabilities.filter { 
            regex.matches(it.className) || regex.matches(it.methodName) 
        }
    }
    
    /**
     * Get all capabilities with a specific severity.
     */
    fun getCapabilitiesBySeverity(severity: String): List<ApiCapability> {
        return capabilities.filter { it.severity == severity }
    }
    
    /**
     * Get all defined capabilities.
     */
    fun getAllCapabilities(): List<ApiCapability> {
        return capabilities
    }
    
    /**
     * Check if a class/method combination has any capabilities.
     */
    fun hasCapabilities(className: String, methodName: String): Boolean {
        return capabilities.any { 
            it.className == className && it.methodName == methodName 
        }
    }
}
