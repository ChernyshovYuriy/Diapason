package com.yuriy.diapason.ui.screens.comparison

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuriy.diapason.R
import com.yuriy.diapason.comparison.ComparisonStage
import com.yuriy.diapason.comparison.WarmUpComparisonViewModel

/**
 * Top-level composable for the warm-up comparison flow.
 * Dispatches to the appropriate sub-screen based on the current [ComparisonStage].
 * All user-visible strings are resolved here via stringResource so the
 * sub-screens receive plain String values and stay context-free.
 */
@Composable
fun WarmUpFlowScreen(
    viewModel: WarmUpComparisonViewModel,
    onExit: () -> Unit,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()

    when (val s = stage) {
        is ComparisonStage.Intro -> CompareIntroScreen(
            onStart = { viewModel.startBaseline() },
            onExit = onExit,
        )

        is ComparisonStage.Baseline -> CompareRecordScreen(
            label = stringResource(R.string.compare_baseline_title),
            instruction = stringResource(R.string.compare_baseline_instruction),
            currentNote = s.currentNote,
            currentHz = s.currentHz,
            sampleCount = s.sampleCount,
            statusMessage = s.statusMessage,
            isRecording = true,
            onStop = { viewModel.stopBaseline() },
            onExit = { viewModel.resetToIntro(); onExit() },
        )

        is ComparisonStage.BaselineInsufficient -> CompareInsufficientScreen(
            reason = s.reason,
            onRetry = { viewModel.retryBaseline() },
            onExit = { viewModel.resetToIntro(); onExit() },
        )

        is ComparisonStage.WarmUp -> WarmUpTimerScreen(
            remainingSeconds = s.remainingSeconds,
            isRunning = s.isRunning,
            onStartTimer = { viewModel.startWarmUpTimer() },
            onSkip = { viewModel.skipWarmUpTimer() },
            onExit = { viewModel.resetToIntro(); onExit() },
        )

        is ComparisonStage.Retest -> CompareRecordScreen(
            label = stringResource(R.string.compare_retest_title),
            instruction = stringResource(R.string.compare_retest_instruction),
            currentNote = s.currentNote,
            currentHz = s.currentHz,
            sampleCount = s.sampleCount,
            statusMessage = s.statusMessage,
            isRecording = s.isRecording,
            onStart = { viewModel.startRetest() },
            onStop = { viewModel.stopRetest() },
            onExit = { viewModel.resetToIntro(); onExit() },
        )

        is ComparisonStage.RetestInsufficient -> CompareInsufficientScreen(
            reason = s.reason,
            onRetry = { viewModel.retryRetest() },
            onExit = { viewModel.resetToIntro(); onExit() },
        )

        is ComparisonStage.Done -> CompareResultScreen(
            result = s.result,
            onDone = { viewModel.resetToIntro(); onExit() },
            onCompareAgain = { viewModel.resetToIntro() },
        )
    }
}
