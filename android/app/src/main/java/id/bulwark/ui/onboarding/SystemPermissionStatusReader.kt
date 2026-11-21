package id.bulwark.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Reads the three onboarding permissions live from the system (never stored as an assumption).
 *
 * - `LOCAL_FILTER`: granted when `VpnService.prepare` no longer needs user consent.
 * - `FILE_MONITOR`: All-files access on Android 11+ (PRD V2), READ_EXTERNAL_STORAGE below that.
 * - `APP_OVERLAY`: `Settings.canDrawOverlays`.
 */
class SystemPermissionStatusReader(private val context: Context) : PermissionStatusReader {

    override fun isGranted(kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.LOCAL_FILTER -> VpnService.prepare(context) == null
        PermissionKind.FILE_MONITOR -> fileAccessGranted()
        PermissionKind.APP_OVERLAY -> Settings.canDrawOverlays(context)
    }

    private fun fileAccessGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
}
