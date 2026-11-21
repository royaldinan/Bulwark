package id.bulwark.data

import id.bulwark.data.model.ApkAction
import id.bulwark.data.model.Verdict
import id.bulwark.files.ApkWatcher
import id.bulwark.files.HashUtil
import id.bulwark.files.Quarantine
import id.bulwark.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owned by `data/`: watches folders for finished APKs, hashes them, checks the verdict, and moves
 * malicious files into quarantine while writing a real `apk_event` (PRD Fase 2 / the NOTES request).
 * `files/` only promises the callback + [Quarantine], so the verdict logic stays here.
 */
class ApkGuard(
    private val repository: BulwarkRepository,
    private val quarantine: Quarantine,
    private val overlay: OverlayController,
    private val scope: CoroutineScope,
    directories: List<File> = ApkWatcher.defaultDirectories(),
) {

    private val watcher = ApkWatcher(directories) { file ->
        scope.launch { handle(file) }
    }

    fun start() = watcher.start()

    fun stop() = watcher.stop()

    suspend fun handle(file: File) {
        if (!file.isFile) return

        val sha256 = HashUtil.sha256File(file)
        val decision = repository.checkApk(sha256)
        if (decision.verdict != Verdict.MALICIOUS) return

        val target = quarantine.move(file) ?: return
        repository.recordApkEvent(
            sha256 = sha256,
            fileName = file.name,
            originalPath = file.absolutePath,
            quarantinePath = target.absolutePath,
            action = ApkAction.QUARANTINED,
        )
        overlay.showFileBlocked(file.name)
    }
}
