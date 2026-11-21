package id.bulwark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.bulwark.ui.BulwarkRoot
import id.bulwark.ui.theme.BulwarkTheme

/** Hosts the app: onboarding (izin) / Beranda / Lapor wired to the real Room-backed repository. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BulwarkApp
        setContent {
            BulwarkTheme {
                BulwarkRoot(
                    repository = app.repository,
                    apkGuard = app.apkGuard,
                )
            }
        }
    }
}
