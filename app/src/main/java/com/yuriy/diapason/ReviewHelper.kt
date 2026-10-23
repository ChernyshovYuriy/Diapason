package com.yuriy.diapason

import android.content.Context
import androidx.core.content.edit

class ReviewHelper(context: Context) {

    private val prefs = context.getSharedPreferences("diapason_review", Context.MODE_PRIVATE)

    // Returns true on the 3rd successful analysis and every 10th one after that.
    fun recordAnalysisAndCheckShouldPrompt(): Boolean {
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit { putInt(KEY_COUNT, count) }
        return count == 3 || (count > 3 && count % 10 == 0)
    }

    companion object {
        private const val KEY_COUNT = "analysis_count"
    }
}
