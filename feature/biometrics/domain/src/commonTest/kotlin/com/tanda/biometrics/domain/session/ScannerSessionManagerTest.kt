package com.tanda.biometrics.domain.session

import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.biometrics.domain.model.Snapshot
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScannerSessionManagerTest {
    @Test
    fun emitsReadyOnlyAfterReadyStatusAndStopsOnce() {
        runBlocking {
            val repository = FakeScannerRepository()
            val manager = ScannerSessionManager(
                dispatcher = TestDispatcher,
                startUsecase = StartUsecase(repository),
                stopUsecase = StopUsecase(repository),
                observeStatusUsecase = ObserveStatusUsecase(repository),
            )

            manager.start()
            manager.start()
            assertEquals(1, repository.startCount)
            assertIs<ScannerSessionState.Starting>(manager.state.value)

            repository.emit(Status.Attached(id = 42))
            yield()
            assertIs<ScannerSessionState.Starting>(manager.state.value)

            repository.emit(Status.Initialize(id = 42, index = 3, progress = 50))
            yield()
            assertEquals(ScannerSessionState.Initializing(50), manager.state.value)

            repository.emit(Status.Ready(id = 42, index = 3))
            yield()
            assertEquals(ScannerSessionState.Ready(42, 3), manager.state.value)

            manager.stop()
            manager.stop()
            assertEquals(2, repository.stopCount)
            assertIs<ScannerSessionState.Stopped>(manager.state.value)
        }
    }

    private object TestDispatcher : Dispatcher {
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }

    private class FakeScannerRepository : ScannerRepository {
        private val statuses = MutableSharedFlow<Status>(replay = 1)

        var startCount = 0
            private set
        var stopCount = 0
            private set

        override val state: Flow<Snapshot> = flowOf(Snapshot.Default)
        override val status: Flow<Status> = statuses
        override val mode: Flow<Mode> = flowOf(Mode.Default)

        init {
            statuses.tryEmit(Status.Default)
        }

        fun emit(status: Status) {
            statuses.tryEmit(status)
        }

        override fun start() {
            startCount++
        }

        override fun hasPermission(id: Int) = true

        override fun requestPermission(id: Int) = Unit

        override suspend fun capture(finger: Finger, index: Int, option: Option) = Unit

        override fun stop() {
            stopCount++
            statuses.tryEmit(Status.Default)
        }
    }
}
