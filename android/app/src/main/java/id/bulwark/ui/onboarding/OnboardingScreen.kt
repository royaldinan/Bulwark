package id.bulwark.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bulwark.ui.theme.BulwarkNavy
import id.bulwark.ui.theme.BulwarkOptionCard
import id.bulwark.ui.theme.BulwarkPrimaryButton
import id.bulwark.ui.theme.BulwarkTopBar
import id.bulwark.ui.theme.BulwarkWhite

enum class PermissionKind { LOCAL_FILTER, FILE_MONITOR, APP_OVERLAY }

data class PermissionCardState(
    val kind: PermissionKind,
    val granted: Boolean,
)

interface PermissionStatusReader {
    fun isGranted(kind: PermissionKind): Boolean
}

@Composable
fun OnboardingScreen(
    permissions: List<PermissionCardState>,
    onRequest: (PermissionKind) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allGranted = permissions.isNotEmpty() && permissions.all { it.granted }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BulwarkWhite)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        BulwarkTopBar(title = "Penyiapan izin")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tiga langkah singkat agar Bulwark bisa melindungi perangkat.",
            color = BulwarkNavy,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(20.dp))
        permissions.forEach { permission ->
            BulwarkOptionCard(
                title = titleFor(permission.kind),
                subtitle = descriptionFor(permission.kind),
                selected = permission.granted,
                value = if (permission.granted) "Diizinkan" else "Belum",
                badge = if (permission.granted) "Aktif" else null,
                onClick = { onRequest(permission.kind) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        BulwarkPrimaryButton(
            text = "Lanjutkan",
            onClick = onContinue,
            enabled = allGranted,
        )
    }
}

private fun titleFor(kind: PermissionKind): String = when (kind) {
    PermissionKind.LOCAL_FILTER -> "Filter koneksi lokal"
    PermissionKind.FILE_MONITOR -> "Pantau file"
    PermissionKind.APP_OVERLAY -> "Peringatan di atas aplikasi"
}

private fun descriptionFor(kind: PermissionKind): String = when (kind) {
    PermissionKind.LOCAL_FILTER -> "Menyaring link berbahaya langsung di perangkat."
    PermissionKind.FILE_MONITOR -> "Memeriksa file APK yang baru masuk."
    PermissionKind.APP_OVERLAY -> "Menampilkan peringatan saat ada ancaman."
}
