package com.yuriy.diapason

import android.content.Context
import com.yuriy.diapason.analyzer.RecordingGate

/**
 * The prompt shown when Stop is pressed below [RecordingGate.MIN_SAMPLES]: how many more
 * seconds to sing at this session's own rate, or a generic "no clear notes yet" when
 * there's too little data to estimate. Shared by the Analyze and warm-up recording flows.
 */
fun Context.keepSingingMessage(sampleCount: Int, elapsedSeconds: Float): String {
    val seconds = RecordingGate.secondsRemaining(sampleCount, elapsedSeconds)
        ?: return localizedString(R.string.analyze_status_keep_singing_unknown)
    return localizedQuantityString(R.plurals.analyze_status_keep_singing, seconds, seconds)
}

/** The insufficient-data message, showing how close the take came to the gate. */
fun Context.insufficientMessage(sampleCount: Int): String =
    localizedString(R.string.analyze_error_insufficient_progress, sampleCount, RecordingGate.MIN_SAMPLES)
