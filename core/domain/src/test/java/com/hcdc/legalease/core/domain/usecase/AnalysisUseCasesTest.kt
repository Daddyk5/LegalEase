package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalysisScheduler
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisUseCasesTest {

    private val repository = FakeAnalysisRepository()
    private val scheduler = FakeAnalysisScheduler()
    private val sync = FakeSyncScheduler()
    private val analytics = FakeAnalytics()

    @Test
    fun `start records the analysis and schedules it`() = runTest {
        val start = StartAnalysisUseCase(repository, scheduler, analytics)
        val result = start("lease.pdf", SourceKind.PDF, listOf("content://x"))

        val id = (result as AppResult.Success).value
        assertEquals(listOf(id), scheduler.enqueued)
        assertEquals(AnalysisStatus.QUEUED, repository.get(id)?.status)
        assertEquals(listOf("scan_started"), analytics.events.map { it.name })
    }

    @Test
    fun `start does not schedule when the copy fails`() = runTest {
        repository.createResult = AppResult.Failure(AppError.StorageFull)

        val start = StartAnalysisUseCase(repository, scheduler, analytics)
        val result = start("lease.pdf", SourceKind.PDF, listOf("content://x"))

        assertEquals(AppResult.Failure(AppError.StorageFull), result)
        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test
    fun `cancel stops the work and marks the analysis cancelled`() = runTest {
        val id = (repository.create("a", SourceKind.IMAGES, listOf("x")) as AppResult.Success).value

        CancelAnalysisUseCase(repository, scheduler)(id)

        assertEquals(listOf(id), scheduler.cancelled)
        assertEquals(AnalysisStatus.CANCELLED, repository.get(id)?.status)
    }

    @Test
    fun `history trims the query`() = runTest {
        val id = (repository.create("Lease Contract", SourceKind.PDF, listOf("x")) as AppResult.Success).value
        repository.fail(id, AppError.NoTextFound)

        GetHistoryUseCase(repository)("  lease ").first()

        assertEquals(listOf(id), repository.historySnapshot("lease").map { it.id })
    }

    @Test
    fun `delete requests a sync so the deletion reaches the cloud copy`() = runTest {
        val id = (repository.create("a", SourceKind.PDF, listOf("x")) as AppResult.Success).value

        DeleteAnalysisUseCase(repository, sync, analytics)(id)

        assertEquals(null, repository.get(id))
        assertEquals(1, sync.requests)
    }
}
