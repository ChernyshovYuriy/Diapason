package com.yuriy.diapason.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuriy.diapason.MainApp
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ── UI state ──────────────────────────────────────────────────────────────────

sealed interface HistoryUiState {
    /** Initial / loading — show a placeholder while the first DB emission arrives. */
    object Loading : HistoryUiState

    /** No sessions saved yet. */
    object Empty : HistoryUiState

    /** One or more sessions available, newest first. */
    data class Sessions(val items: List<SessionRecord>) : HistoryUiState
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Primary constructor is used by tests, which inject a fake [repository]
 * directly — the [application] is never cast to [MainApp] in that path.
 *
 * The secondary no-repository constructor is the one
 * [ViewModelProvider.AndroidViewModelFactory] calls in production; it
 * resolves the repository from [MainApp] and delegates to the primary.
 * Without this explicit secondary constructor, Kotlin default parameters
 * only generate the two-arg JVM overload and the factory throws a
 * [RuntimeException] at runtime.
 */
class HistoryViewModel(
    application: Application,
    private val repository: SessionRepository,
) : AndroidViewModel(application) {

    /** Called by [ViewModelProvider.AndroidViewModelFactory] in production. */
    @Suppress("unused")
    constructor(application: Application) : this(
        application,
        (application as MainApp).sessionRepository
    )

    val uiState: StateFlow<HistoryUiState> = repository
        .observeAll()
        .map { sessions ->
            if (sessions.isEmpty()) HistoryUiState.Empty
            else HistoryUiState.Sessions(sessions)   // DAO already orders DESC
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )
}
