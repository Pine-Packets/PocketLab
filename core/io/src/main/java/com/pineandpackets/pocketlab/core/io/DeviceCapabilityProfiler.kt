package com.pineandpackets.pocketlab.core.io

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import timber.log.Timber

enum class DeviceProfile {
    LOW_MEMORY,
    STANDARD,
    HIGH_MEMORY
}

data class DeviceCapabilities(
    val profile: DeviceProfile,
    val totalMemoryMb: Int,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val availableProcessors: Int,
    val availableStorageBytes: Long,
    val totalStorageBytes: Long,
    val supportedAbis: List<String>
) {
    val maxInputSizeBytes: Long
        get() = when (profile) {
            DeviceProfile.LOW_MEMORY -> 256L * 1024 * 1024
            DeviceProfile.STANDARD -> 512L * 1024 * 1024
            DeviceProfile.HIGH_MEMORY -> 512L * 1024 * 1024
        }

    val maxArchiveExpandedBytes: Long
        get() = when (profile) {
            DeviceProfile.LOW_MEMORY -> 512L * 1024 * 1024
            DeviceProfile.STANDARD -> 1024L * 1024 * 1024
            DeviceProfile.HIGH_MEMORY -> 1024L * 1024 * 1024
        }

    val recommendedWorkerCount: Int
        get() = when (profile) {
            DeviceProfile.LOW_MEMORY -> 1
            DeviceProfile.STANDARD -> minOf(availableProcessors, 2)
            DeviceProfile.HIGH_MEMORY -> minOf(availableProcessors, 4)
        }

    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        val safetyMargin = 100L * 1024 * 1024
        return availableStorageBytes > requiredBytes + safetyMargin
    }
}

class DeviceCapabilityProfiler(private val context: Context) {

    fun profile(): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass ?: 128
        val largeMemoryClass = activityManager?.largeMemoryClass ?: memoryClass
        val isLowRam = activityManager?.isLowRamDevice ?: false
        val totalMemoryMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()

        val profile = determineProfile(memoryClass, isLowRam)
        val storageInfo = getStorageInfo()
        val abis = getSupportedAbis()

        Timber.i("Device profile: $profile, memory class: ${memoryClass}MB, " +
            "available storage: ${storageInfo.first / (1024 * 1024)}MB")

        return DeviceCapabilities(
            profile = profile,
            totalMemoryMb = totalMemoryMb,
            memoryClassMb = memoryClass,
            largeMemoryClassMb = largeMemoryClass,
            isLowRamDevice = isLowRam,
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            availableStorageBytes = storageInfo.first,
            totalStorageBytes = storageInfo.second,
            supportedAbis = abis
        )
    }

    private fun determineProfile(memoryClass: Int, isLowRam: Boolean): DeviceProfile {
        return when {
            isLowRam || memoryClass < 128 -> DeviceProfile.LOW_MEMORY
            memoryClass >= 256 -> DeviceProfile.HIGH_MEMORY
            else -> DeviceProfile.STANDARD
        }
    }

    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val statFs = StatFs(context.noBackupFilesDir.absolutePath)
            val available = statFs.availableBlocksLong * statFs.blockSizeLong
            val total = statFs.blockCountLong * statFs.blockSizeLong
            Pair(available, total)
        } catch (e: Exception) {
            Timber.w(e, "Failed to query storage stats")
            Pair(0L, 0L)
        }
    }

    private fun getSupportedAbis(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOf(Build.CPU_ABI, Build.CPU_ABI2)
        }
    }
}
