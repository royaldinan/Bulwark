package id.bulwark.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.bulwark.data.ApkGuard
import id.bulwark.files.HashUtil
import id.bulwark.ui.home.HomeRoute
import id.bulwark.ui.onboarding.OnboardingScreen
import id.bulwark.ui.onboarding.PermissionCardState
import id.bulwark.ui.onboarding.PermissionKind
import id.bulwark.ui.onboarding.SystemPermissionStatusReader
import id.bulwark.ui.report.ReportRoute
import id.bulwark.ui.theme.BulwarkBottomBar
import id.bulwark.ui.theme.BulwarkNavItem
import id.bulwark.vpn.BulwarkVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BulwarkTab(val label: String, val icon: ImageVector) {
    BERANDA("Beranda", Icons.Filled.Home),
    LAPOR("Lapor", Icons.Filled.Warning),
    IZIN("Izin", Icons.Filled.Lock),
}

// Public explorer URL fixed by the PRD; transaction hashes are real ones returned by the API.
private const val REGISTRY_URL = "https://amoy.polygonscan.com"

@Composable
fun BulwarkRoot(
    repository: BulwarkRepository,
    apkGuard: ApkGuard?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reader = remember(context) { SystemPermissionStatusReader(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf<BulwarkTab?>(null) }

    // Re-read live permission status every time the activity resumes (e.g. back from Settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = remember(refreshKey) {
        PermissionKind.entries.map { PermissionCardState(it, reader.isGranted(it)) }
    }
    val allGranted = permissions.isNotEmpty() && permissions.all { it.granted }

    LaunchedEffect(allGranted) {
        if (tab == null) tab = if (allGranted) BulwarkTab.BERANDA else BulwarkTab.IZIN
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshKey++ }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }

    val request: (PermissionKind) -> Unit = { kind ->
        when (kind) {
            PermissionKind.LOCAL_FILTER -> {
                val consent = VpnService.prepare(context)
                if (consent != null) vpnLauncher.launch(consent) else refreshKey++
            }

            PermissionKind.APP_OVERLAY ->
                runCatching { context.startActivity(overlaySettingsIntent(context)) }

            PermissionKind.FILE_MONITOR ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { context.startActivity(allFilesSettingsIntent(context)) }
                } else {
                    storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
        }
    }

    // Bring protection online only once the underlying permissions are actually granted.
    LaunchedEffect(permissions) {
        if (permissions.first { it.kind == PermissionKind.LOCAL_FILTER }.granted) {
            BulwarkVpnService.start(context)
        }
        if (permissions.first { it.kind == PermissionKind.FILE_MONITOR }.granted) {
            apkGuard?.start()
        }
    }

    val current = tab ?: BulwarkTab.IZIN
    var pickedApkName by remember { mutableStateOf<String?>(null) }
    var pickedApkHash by remember { mutableStateOf<String?>(null) }
    val pickerScope = rememberCoroutineScope()

    // APK picker: only the file name + SHA-256 leave the device (never the file itself).
    val apkPicker = rememberLauncherForActivityResult(
        OpenApkDocument(),
    ) { uri ->
        if (uri != null) {
            pickerScope.launch {
                val picked = withContext(Dispatchers.IO) { readApk(context, uri) }
                if (picked != null) {
                    pickedApkName = picked.first
                    pickedApkHash = picked.second
                }
            }
        }
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            BulwarkBottomBar(
                items = BulwarkTab.entries.map { BulwarkNavItem(it.label, it.icon) },
                selectedIndex = current.ordinal,
                onSelect = { tab = BulwarkTab.entries[it] },
            )
        },
    ) { padding ->
        when (current) {
            BulwarkTab.IZIN -> OnboardingScreen(
                permissions = permissions,
                onRequest = request,
                onContinue = { tab = BulwarkTab.BERANDA },
                modifier = Modifier.padding(padding),
            )

            BulwarkTab.BERANDA -> HomeRoute(
                repository = repository,
                onReport = { tab = BulwarkTab.LAPOR },
                onOpenRegistry = { openUrl(context, REGISTRY_URL) },
                onManagePermissions = { tab = BulwarkTab.IZIN },
                modifier = Modifier.padding(padding),
            )

            BulwarkTab.LAPOR -> ReportRoute(
                repository = repository,
                onOpenTransaction = { openUrl(context, transactionUrl(it)) },
                modifier = Modifier.padding(padding),
                onPickApk = {
                    runCatching {
                        apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                    }
                },
                pickedApkName = pickedApkName,
                pickedApkHash = pickedApkHash,
            )
        }
    }
}

/**
 * `OpenDocument` that starts in the device Download folder, where APKs actually land. Without the
 * initial URI the picker opens on "Recent", which does not list APK files at all.
 */
private class OpenApkDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"),
        )
}

private fun transactionUrl(txHash: String): String = "$REGISTRY_URL/tx/$txHash"

/**
 * Reads the display name and SHA-256 of the picked document. Only .apk files are accepted; the
 * file content never leaves the device, the report sends the hash.
 */
private fun readApk(context: Context, uri: Uri): Pair<String, String>? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
        ?: return null
    if (!name.lowercase().endsWith(".apk")) return null
    val hash = context.contentResolver.openInputStream(uri)?.use { HashUtil.sha256Stream(it) } ?: return null
    return name to hash
}

private fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

private fun allFilesSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
