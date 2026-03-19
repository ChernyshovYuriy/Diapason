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
 * [repository] has a default value that reads from [MainApp] so that the
 * standard [ViewModelProvider] factory works in production without any extra
 * setup. Tests pass a fake by supplying the parameter explicitly — the
 * Application passed in tests is never cast to [MainApp].
 */
class HistoryViewModel(
    application: Application,
    private val repository: SessionRepository =
        (application as MainApp).sessionRepository,
) : AndroidViewModel(application) {

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
