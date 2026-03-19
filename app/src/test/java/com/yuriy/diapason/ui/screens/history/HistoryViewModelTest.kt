package com.yuriy.diapason.ui.screens.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [HistoryViewModel].
 *
 * Key test-harness decisions:
 *
 * 1. [Dispatchers.setMain] is set to [UnconfinedTestDispatcher] so that
 *    [viewModelScope] — which uses Main — executes coroutines eagerly.
 *
 * 2. Every [runTest] block also uses [UnconfinedTestDispatcher].  This means
 *    the `stateIn` coroutine inside the ViewModel starts immediately when
 *    its [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed] condition
 *    is met (i.e. as soon as we subscribe via [backgroundScope.launch]).
 *
 * 3. Each test subscribes to [HistoryViewModel.uiState] with a
 *    [backgroundScope] collector *before* asserting.  Without a subscriber,
 *    [WhileSubscribed] would never trigger the upstream [Flow] and
 *    [uiState.value] would remain [HistoryUiState.Loading].
 *
 * Coverage:
 *  1. Empty repository → [HistoryUiState.Empty]
 *  2. Single-session repository → [HistoryUiState.Sessions] with that item
 *  3. Multi-session repository → all items passed through in order
 *  4. ViewModel does NOT re-sort the list (ordering is the DAO's contract)
 *  5. New emission after save → state updates reactively
 *  6. Delete-all → state reverts to [HistoryUiState.Empty]
 *  7. passaggioHz == 0f is included without error
 *  8. null topFachKey / score is included without error
 *  9. Cold value before subscription is [HistoryUiState.Loading]
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpDispatcher() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDownDispatcher() = Dispatchers.resetMain()

    // ── Fake repository ───────────────────────────────────────────────────────

    private class FakeRepository(
        initial: List<SessionRecord> = emptyList(),
    ) : SessionRepository {
        val flow = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<SessionRecord>> = flow

        override suspend fun save(session: SessionRecord) {
            flow.value = listOf(session) + flow.value
        }

        override suspend fun getAll(): List<SessionRecord> = flow.value
        override suspend fun getById(id: String): SessionRecord? = flow.value.find { it.id == id }
        override suspend fun deleteById(id: String) {
            flow.value = flow.value.filter { it.id != id }
        }

        override suspend fun count(): Int = flow.value.size
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private fun record(
        id: String,
        timestampMs: Long = 1_000L,
        passaggioHz: Float = 392f,
        topFachKey: String? = "Lyric Soprano",
        topFachScore: Int? = 10,
        topFachMaxScore: Int? = 14,
    ) = SessionRecord(
        id = id,
        timestampMs = timestampMs,
        durationSeconds = 35f,
        detectedMinHz = 196f,
        detectedMaxHz = 880f,
        comfortableLowHz = 247f,
        comfortableHighHz = 659f,
        passaggioHz = passaggioHz,
        sampleCount = 700,
        topFachKey = topFachKey,
        topFachScore = topFachScore,
        topFachMaxScore = topFachMaxScore,
        isPartial = false,
    )

    // ── ViewModel builder ─────────────────────────────────────────────────────

    private fun buildViewModel(repo: FakeRepository): HistoryViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return HistoryViewModel(application = app, repository = repo)
    }

    /**
     * Subscribes to [uiState] (triggering [WhileSubscribed]) and suspends until
     * the first non-Loading value is emitted.
     */
    private suspend fun HistoryViewModel.awaitState(): HistoryUiState =
        uiState.first { it !is HistoryUiState.Loading }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `empty repository emits Empty state`() = runTest(testDispatcher) {
        val repo = FakeRepository(emptyList())
        val vm = buildViewModel(repo)

        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        val state = vm.awaitState()

        assertEquals(HistoryUiState.Empty, state)
    }

    @Test
    fun `repository with one session emits Sessions state`() = runTest(testDispatcher) {
        val session = record("s1")
        val repo = FakeRepository(listOf(session))
        val vm = buildViewModel(repo)

        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        val state = vm.awaitState()

        assertTrue("Expected Sessions but got $state", state is HistoryUiState.Sessions)
        assertEquals(listOf(session), (state as HistoryUiState.Sessions).items)
    }

    @Test
    fun `repository with multiple sessions passes all items through unchanged`() =
        runTest(testDispatcher) {
            val sessions = listOf(
                record("newest", timestampMs = 3_000L),
                record("middle", timestampMs = 2_000L),
                record("oldest", timestampMs = 1_000L),
            )
            val repo = FakeRepository(sessions)
            val vm = buildViewModel(repo)

            backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
            val state = vm.awaitState() as HistoryUiState.Sessions

            assertEquals(listOf("newest", "middle", "oldest"), state.items.map { it.id })
        }

    @Test
    fun `ViewModel does not re-sort the list emitted by the repository`() =
        runTest(testDispatcher) {
            // If DAO returned ascending order (a bug), ViewModel must not silently fix it.
            val ascendingOrder = listOf(
                record("oldest", timestampMs = 1_000L),
                record("newest", timestampMs = 3_000L),
            )
            val repo = FakeRepository(ascendingOrder)
            val vm = buildViewModel(repo)

            backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
            val state = vm.awaitState() as HistoryUiState.Sessions

            assertEquals(listOf("oldest", "newest"), state.items.map { it.id })
        }

    @Test
    fun `state updates when repository emits a new list`() = runTest(testDispatcher) {
        val repo = FakeRepository(emptyList())
        val vm = buildViewModel(repo)

        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        assertEquals(HistoryUiState.Empty, vm.awaitState())

        repo.flow.value = listOf(record("new"))
        val updated = vm.uiState.first { it is HistoryUiState.Sessions }

        assertEquals("new", (updated as HistoryUiState.Sessions).items.single().id)
    }

    @Test
    fun `state reverts to Empty when all sessions are deleted`() = runTest(testDispatcher) {
        val repo = FakeRepository(listOf(record("lone")))
        val vm = buildViewModel(repo)

        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        assertTrue(vm.awaitState() is HistoryUiState.Sessions)

        repo.flow.value = emptyList()
        val afterDelete = vm.uiState.first { it is HistoryUiState.Empty }

        assertEquals(HistoryUiState.Empty, afterDelete)
    }

    @Test
    fun `session with passaggioHz zero is included without error`() = runTest(testDispatcher) {
        // passaggioHz == 0f means unavailable; ViewModel must pass it through —
        // hiding it is the HistoryScreen's responsibility.
        val session = record("no-passaggio", passaggioHz = 0f)
        val repo = FakeRepository(listOf(session))
        val vm = buildViewModel(repo)

        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        val state = vm.awaitState() as HistoryUiState.Sessions

        assertEquals(1, state.items.size)
        assertEquals(0f, state.items.first().passaggioHz)
    }

    @Test
    fun `session with null topFachKey and scores is included without error`() =
        runTest(testDispatcher) {
            val session =
                record("no-fach", topFachKey = null, topFachScore = null, topFachMaxScore = null)
            val repo = FakeRepository(listOf(session))
            val vm = buildViewModel(repo)

            backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
            val state = vm.awaitState() as HistoryUiState.Sessions

            assertEquals(1, state.items.size)
            val item = state.items.first()
            assertEquals(null, item.topFachKey)
            assertEquals(null, item.topFachScore)
            assertEquals(null, item.topFachMaxScore)
        }

    @Test
    fun `initial StateFlow value before any subscription is Loading`() {
        // Read the cold value without subscribing — Loading is the initialValue
        // passed to stateIn and must remain stable until WhileSubscribed fires.
        val repo = FakeRepository(emptyList())
        val vm = buildViewModel(repo)

        assertEquals(HistoryUiState.Loading, vm.uiState.value)
    }
}
