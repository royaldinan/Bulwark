package id.bulwark.data.model

/** APK decision recorded in `apk_event`. */
enum class ApkAction(val wire: String) {
    QUARANTINED("quarantined"),
    IGNORED("ignored"),
}

/** Result of looking up an APK hash, ready for the quarantine step. */
data class ApkDecision(
    val sha256: String,
    val verdict: Verdict,
    val source: VerdictSource,
    /** true when the verdict was fetched from the remote source this call (cache miss). */
    val fresh: Boolean,
)
