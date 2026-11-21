package id.bulwark

import android.app.Application
import id.bulwark.data.ApkGuard
import id.bulwark.data.AppDatabase
import id.bulwark.data.BulwarkRepository
import id.bulwark.data.RoomBulwarkRepository
import id.bulwark.files.Quarantine
import id.bulwark.net.MediationApi
import id.bulwark.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Wires the single app-wide graph: one Room database, one Mediation API client, the core repository,
 * the UI [RoomBulwarkRepository], and the APK guard. Kept here so every screen shares the same real
 * data instead of each building its own.
 */
class BulwarkApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: RoomBulwarkRepository
        private set

    lateinit var apkGuard: ApkGuard
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.get(this)
        val gateway = MediationApi()
        val core = BulwarkRepository(database, gateway)

        repository = RoomBulwarkRepository(database, core)
        apkGuard = ApkGuard(
            repository = core,
            quarantine = Quarantine(this),
            overlay = OverlayController(this),
            scope = appScope,
        )
    }
}
