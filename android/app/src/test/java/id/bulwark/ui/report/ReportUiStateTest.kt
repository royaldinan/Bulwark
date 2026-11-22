package id.bulwark.ui.report

import id.bulwark.ui.ReportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportUiStateTest {

    @Test
    fun alreadyListedOutcomeBecomesInformationInsteadOfAnError() {
        val state = ReportUiState.fromOutcome(ReportOutcome.AlreadyListed)

        assertEquals("Sudah terdaftar", state.info)
        assertNull(state.error)
        assertNull(state.txHash)
    }

    @Test
    fun failedOutcomeKeepsItsErrorWithoutDuplicateInformation() {
        val state = ReportUiState.fromOutcome(ReportOutcome.Failed("Laporan gagal dikirim."))

        assertEquals("Laporan gagal dikirim.", state.error)
        assertNull(state.info)
        assertNull(state.txHash)
    }
}
