package id.bulwark.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bulwark.files.HashUtil
import id.bulwark.ui.BulwarkRepository
import id.bulwark.ui.ReportKind
import id.bulwark.ui.ReportOutcome
import id.bulwark.ui.theme.BulwarkError
import id.bulwark.ui.theme.BulwarkInfoCard
import id.bulwark.ui.theme.BulwarkLine
import id.bulwark.ui.theme.BulwarkNavy
import id.bulwark.ui.theme.BulwarkPrimaryButton
import id.bulwark.ui.theme.BulwarkSegmentedToggle
import id.bulwark.ui.theme.BulwarkTextSecondary
import id.bulwark.ui.theme.BulwarkTopBar
import id.bulwark.ui.theme.BulwarkWhite
import kotlinx.coroutines.launch

data class ReportUiState(
    val canSubmit: Boolean = false,
    val submitting: Boolean = false,
    val txHash: String? = null,
    val error: String? = null,
    val info: String? = null,
) {
    companion object {
        fun fromOutcome(
            outcome: ReportOutcome?,
            canSubmit: Boolean = false,
            submitting: Boolean = false,
        ): ReportUiState = ReportUiState(
            canSubmit = canSubmit,
            submitting = submitting,
            txHash = (outcome as? ReportOutcome.Sent)?.txHash,
            error = (outcome as? ReportOutcome.Failed)?.message,
            info = if (outcome == ReportOutcome.AlreadyListed) "Sudah terdaftar" else null,
        )
    }
}

private val DOMAIN_REGEX = Regex("^([a-z0-9](-?[a-z0-9])*\\.)+[a-z]{2,}$")

fun isValidDomain(value: String): Boolean =
    DOMAIN_REGEX.matches(HashUtil.normalizeDomain(value))

@Composable
fun ReportRoute(
    repository: BulwarkRepository,
    onOpenTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPickApk: () -> Unit = {},
    pickedApkName: String? = null,
    pickedApkHash: String? = null,
) {
    var kind by remember { mutableStateOf(ReportKind.DOMAIN) }
    var domain by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ReportOutcome?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = when (kind) {
        ReportKind.DOMAIN -> isValidDomain(domain)
        ReportKind.APK -> !pickedApkHash.isNullOrBlank()
    }

    ReportScreen(
        kind = kind,
        onKindChange = { selected ->
            kind = selected
            outcome = null
        },
        domainInput = domain,
        onDomainInputChange = { domain = it },
        apkName = pickedApkName,
        apkHash = pickedApkHash,
        onPickApk = onPickApk,
        state = ReportUiState.fromOutcome(
            outcome = outcome,
            canSubmit = canSubmit,
            submitting = submitting,
        ),
        onSubmit = {
            val value = when (kind) {
                ReportKind.DOMAIN -> domain.trim()
                ReportKind.APK -> pickedApkHash.orEmpty()
            }
            scope.launch {
                submitting = true
                outcome = repository.report(kind, value)
                submitting = false
            }
        },
        onOpenTransaction = onOpenTransaction,
        modifier = modifier,
    )
}

@Composable
fun ReportScreen(
    kind: ReportKind,
    onKindChange: (ReportKind) -> Unit,
    domainInput: String,
    onDomainInputChange: (String) -> Unit,
    apkName: String?,
    onPickApk: () -> Unit,
    state: ReportUiState,
    onSubmit: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    apkHash: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BulwarkWhite)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        BulwarkTopBar(title = "Lapor")
        Spacer(Modifier.height(12.dp))
        BulwarkSegmentedToggle(
            options = listOf(ReportKind.DOMAIN, ReportKind.APK),
            selected = kind,
            onSelect = onKindChange,
            label = { if (it == ReportKind.DOMAIN) "Domain" else "APK" },
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = when (kind) {
                ReportKind.DOMAIN ->
                    "Masukkan domain yang kamu tahu berbahaya, tanpa https://. Domain ini akan dicatat permanen di daftar publik dan diblokir di semua perangkat yang memakai Bulwark."

                ReportKind.APK ->
                    "Pilih file APK berbahaya. Yang dikirim hanya sidik jarinya (hash), bukan file-nya."
            },
            color = BulwarkTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        when (kind) {
            ReportKind.DOMAIN -> {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = onDomainInputChange,
                    label = { Text("Alamat domain") },
                    placeholder = { Text("contoh: situs-berbahaya.com") },
                    singleLine = true,
                    isError = domainInput.isNotEmpty() && !isValidDomain(domainInput),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (domainInput.isNotEmpty() && !isValidDomain(domainInput)) {
                    Spacer(Modifier.height(4.dp))
                    Text("Format domain tidak valid.", color = BulwarkError, fontSize = 13.sp)
                }
            }
            ReportKind.APK -> {
                val shape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(BulwarkWhite)
                        .border(1.dp, BulwarkLine, shape)
                        .clickable(onClick = onPickApk)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = apkName ?: "Pilih file APK",
                            color = if (apkName == null) BulwarkTextSecondary else BulwarkNavy,
                            fontSize = 15.sp,
                        )
                        if (apkName != null && !apkHash.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "SHA-256 ${apkHash.take(12)}…${apkHash.takeLast(8)}",
                                color = BulwarkTextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        BulwarkPrimaryButton(
            text = if (state.submitting) "Mengirim..." else "Kirim ke daftar publik",
            onClick = onSubmit,
            enabled = state.canSubmit && !state.submitting,
        )

        state.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = BulwarkError, fontSize = 13.sp)
        }

        state.info?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = BulwarkNavy, fontSize = 13.sp)
        }

        state.txHash?.let { txHash ->
            Spacer(Modifier.height(16.dp))
            BulwarkInfoCard(
                icon = Icons.Default.Info,
                title = "Laporan terkirim",
                subtitle = txHash,
                actionLabel = "Buka",
                onAction = { onOpenTransaction(txHash) },
            )
        }
    }
}
