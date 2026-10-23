package com.yuriy.diapason.analyzer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.logging.AppLogger
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

/**
 * Offline replay of real 2.7.0 sessions through [VoiceProfileAggregator], to measure whether
 * the combined profile is stable where single takes are not. Not a regression test: it is
 * skipped unless `REPLAY_CSV` points at a CSV export (header row kept) of this BigQuery query —
 * one row per completed analysis, all flows (warm-up sessions are saved and combined too), for
 * installs with 2+ results under one voice choice. Set the version to the release being studied
 * (2.7.0+ only: earlier releases don't log the pitch params):
 *
 *     WITH r AS (
 *       SELECT
 *         user_pseudo_id AS uid,
 *         DIV(event_timestamp, 1000) AS ts_ms,
 *         (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'flow') AS flow,
 *         (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'voice_group') AS voice_group,
 *         (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'top_fach_key') AS top_fach_key,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'detected_min_midi') AS detected_min_midi,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'detected_max_midi') AS detected_max_midi,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'comfortable_low_midi') AS comfortable_low_midi,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'comfortable_high_midi') AS comfortable_high_midi,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'passaggio_midi') AS passaggio_midi,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'sample_count') AS sample_count,
 *         (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'duration_seconds') AS duration_seconds
 *       FROM `diapason-app.analytics_525292641.events_*`
 *       WHERE _TABLE_SUFFIX BETWEEN '20261001' AND '20271231'
 *         AND event_name = 'analysis_completed' AND app_info.version = '2.7.0'
 *     )
 *     SELECT * FROM r
 *     WHERE voice_group IS NOT NULL
 *     QUALIFY COUNT(*) OVER (PARTITION BY uid, voice_group) >= 2
 *     ORDER BY uid, voice_group, ts_ms;
 *
 * Then:
 *
 *     REPLAY_CSV=/path/export.csv ./gradlew :app:testDebugUnitTest --tests "*.CombinedProfileReplayTest" --rerun
 *
 * `--rerun` because the environment variable isn't a task input: without it Gradle may call the
 * task up to date and not replay a new CSV. The report is written next to the CSV
 * (`<name>.report.txt`).
 *
 * Analytics logs pitches as whole MIDI notes, so every profile here is quantised to the
 * semitone; the "fidelity" line says how often that still reproduces the Fach the app logged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CombinedProfileReplayTest {

    private data class Run(
        val uid: String,
        val tsMs: Long,
        val flow: String,
        val choice: VoiceGroupChoice,
        val loggedKey: String,
        val profile: VoiceProfile,
    )

    private val resources by lazy { ApplicationProvider.getApplicationContext<Application>().resources }

    private fun key(match: FachMatch?): String? = match?.let { resources.getResourceEntryName(it.fach.nameRes) }

    private fun midiToHz(midi: Int): Float = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    @Test
    fun replay() {
        val path = System.getenv("REPLAY_CSV")
        assumeTrue("REPLAY_CSV not set — replay skipped", !path.isNullOrBlank())
        // The classifier logs every scoring call at debug level and Robolectric keeps every log
        // line in memory, which runs a full replay out of heap.
        AppLogger.setDebug(false)
        val csv = File(path!!)
        val runs = parse(csv)
        val report = buildString { analyse(runs) }
        File(csv.parentFile, csv.nameWithoutExtension + ".report.txt").writeText(report)
        println(report)
    }

    private fun parse(csv: File): List<Run> {
        val lines = csv.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(',').map { it.trim() }
        fun List<String>.col(name: String) = this[header.indexOf(name)].trim().trim('"')
        return lines.drop(1).mapNotNull { line ->
            val c = line.split(',')
            val midis = listOf(
                "detected_min_midi", "detected_max_midi", "comfortable_low_midi",
                "comfortable_high_midi", "passaggio_midi",
            ).map { c.col(it).toIntOrNull() ?: return@mapNotNull null }
            val choice = when (c.col("voice_group")) {
                "male" -> VoiceGroupChoice.MALE
                "female" -> VoiceGroupChoice.FEMALE
                "unsure" -> VoiceGroupChoice.UNSURE
                else -> return@mapNotNull null
            }
            Run(
                uid = c.col("uid"),
                tsMs = c.col("ts_ms").toLong(),
                flow = c.col("flow"),
                choice = choice,
                loggedKey = c.col("top_fach_key"),
                profile = VoiceProfile(
                    detectedMinHz = midiToHz(midis[0]),
                    detectedMaxHz = midiToHz(midis[1]),
                    comfortableLowHz = midiToHz(midis[2]),
                    comfortableHighHz = midiToHz(midis[3]),
                    estimatedPassaggioHz = midiToHz(midis[4]),
                    sampleCount = c.col("sample_count").toIntOrNull() ?: 0,
                    durationSeconds = c.col("duration_seconds").toFloatOrNull() ?: 0f,
                ),
            )
        }
    }

    /** One step of an install's history: the result after its n-th session. */
    private data class Step(
        val tsMs: Long,
        val singleKey: String?,
        val singleFamily: Int?,
        val singleCloseCall: Boolean,
        val combinedKey: String?,
        val combinedFamily: Int?,
        val combinedCloseCall: Boolean,
        val windowSize: Int,
        val consistent: Int,
    )

    private fun closeCall(matches: List<FachMatch>) =
        matches.size >= 2 && matches[0].score - matches[1].score <= 1

    private fun StringBuilder.analyse(runs: List<Run>) {
        val histories = runs.groupBy { it.uid to it.choice }
            .mapValues { (_, r) -> r.sortedBy { it.tsMs } }
            .filterValues { it.size >= 2 }

        line("Combined-profile replay — ${runs.size} sessions, ${histories.size} install×choice histories (2+ sessions)")
        line("Choices: " + histories.keys.groupingBy { it.second }.eachCount())
        line()

        // ── Fidelity: does a semitone-quantised profile reproduce the logged Fach? ──
        val reproduced = runs.count { r -> key(FachClassifier.classify(r.profile, r.choice.group).firstOrNull()) == r.loggedKey }
        line("Fidelity: replayed single-take Fach == logged Fach for ${pct(reproduced, runs.size)} of sessions")
        line("  (below ~90% means MIDI rounding moves results, and everything below is approximate)")
        line()

        val steps = histories.mapValues { (k, r) -> stepsOf(r, k.second.group) }

        // ── Sequential view: what the user sees after each session ──
        line("SEQUENTIAL — what the result screen shows after each new session")
        line("Window = last ${VoiceProfileAggregator.MAX_SESSIONS} sessions within ${VoiceProfileAggregator.WINDOW_DAYS} days; overlapping windows, so some stability is mechanical.")
        sequential(steps.values.filter { it.size >= 3 })
        line()

        line("By window size (combined result vs. the previous step's combined result):")
        val transitions = steps.values.flatMap { s -> s.zipWithNext().filter { (a, b) -> a.combinedKey != null && b.combinedKey != null } }
        transitions.groupBy { it.second.windowSize }.toSortedMap().forEach { (n, t) ->
            line("  n=$n  transitions=${t.size}  Fach changed ${pct(t.count { it.first.combinedKey != it.second.combinedKey }, t.size)}" +
                "  family changed ${pct(t.count { it.first.combinedFamily != it.second.combinedFamily }, t.size)}")
        }
        line()

        line("'Consistent in k of n' as shown to users:")
        steps.values.flatten().filter { it.combinedKey != null }.groupBy { it.windowSize }.toSortedMap().forEach { (n, s) ->
            val dist = s.groupingBy { it.consistent }.eachCount().toSortedMap().entries.joinToString("  ") { "${it.key}/$n: ${pct(it.value, s.size)}" }
            line("  n=$n  $dist")
        }
        line()

        line("Close calls (runner-up within 1 point): single ${pct(steps.values.flatten().count { it.singleCloseCall }, steps.values.flatten().size)}" +
            ", combined ${pct(steps.values.flatten().count { it.combinedKey != null && it.combinedCloseCall }, steps.values.flatten().count { it.combinedKey != null })}")
        line()

        // ── Disjoint blocks: reliability without shared sessions ──
        line("DISJOINT BLOCKS — do two non-overlapping sets of k sessions agree?")
        line("k=1 is plain take-to-take agreement. Adjacent blocks within an install, no 30-day window.")
        for (k in 1..5) disjoint(histories, k)
        line()
        line("Same, only block pairs 12+ h apart (separate sittings):")
        for (k in 1..3) disjoint(histories, k, acrossDaysOnly = true)
        line()

        line("COMPATIBLE SETS — every Fach within d points of the top, compared between disjoint blocks")
        line("any = the sets share a Fach; predicts = block B's winner is in block A's set.")
        line("'other install' pairs block A with a block of a random other install (same voice choice):")
        line("a set is only informative where the same-install figure clearly beats it.")
        for (d in 0..2) {
            for (k in listOf(1, 3, 5)) compatibleSets(histories, k, d)
        }
        line()

        // ── Voice in the other group's range ──
        // Classified against all 19 Fach, does the session's best fit lie in the other half
        // of the table than the singer's Male/Female choice?
        line("VOICE GROUP MISMATCH — the other group's best Fach outscores the chosen group's best by margin m+ points")
        line("(m=1 includes cross-group near-ties, where the halves' ranges overlap; larger m = clearly the other range)")
        for (m in listOf(1, 2, 3, 4)) {
            val line = VoiceGroupChoice.entries.filter { it.group != null }.joinToString("   ") { c ->
                val inChoice = runs.filter { it.choice == c }
                "$c ${pct(inChoice.count { mismatch(it, m) }, inChoice.size)} of ${inChoice.size}"
            }
            line("  m=$m  $line")
        }
        line("Margin used below: m=$MISMATCH_MARGIN")
        val installs = histories.filterKeys { it.second.group != null }
        val mostlyMismatched = installs.count { (_, r) -> r.count { mismatch(it) } * 2 > r.size }
        val someMismatched = installs.count { (_, r) -> r.any { mismatch(it) } && r.any { !mismatch(it) } }
        line("  install×choice histories: mostly mismatched ${pct(mostlyMismatched, installs.size)}, mixed ${pct(someMismatched, installs.size)} of ${installs.size}")
        line()

        val matching = runs.filterNot { mismatch(it) }.groupBy { it.uid to it.choice }
            .mapValues { (_, r) -> r.sortedBy { it.tsMs } }
            .filterValues { it.size >= 2 }
        line("WITHOUT MISMATCHED SESSIONS — ${matching.values.sumOf { it.size }} sessions, ${matching.size} histories")
        for (k in 1..5) disjoint(matching, k)
        for (k in listOf(1, 3, 5)) compatibleSets(matching, k, 1)
        line()

        // ── The voice-type reading the Results screen shows (VoiceTypeReader) ──
        line("VOICE-TYPE READING — family, or 'between' two, by family margin g (app uses g=${VoiceTypeReader.FAMILY_MARGIN})")
        line("same = identical label in both blocks; overlap = labels share a family; other install = same-choice stranger.")
        line("Without mismatched sessions (what the voice-check prompt leaves):")
        for (g in 1..3) for (k in listOf(1, 3, 5)) readings(matching, k, g)
        line()
        line("Sequential: the combined-profile reading after each session (what a returning singer sees change):")
        for (g in 1..3) sequentialReadings(matching, g)
    }

    private fun reading(block: List<Run>, group: VoiceGroup?, g: Int): VoiceTypeReading? =
        VoiceTypeReader.read(
            FachClassifier.classify(VoiceProfileAggregator.medianProfile(block.map { it.profile }), group),
            familyMargin = g,
        )

    private fun StringBuilder.readings(histories: Map<Pair<String, VoiceGroupChoice>, List<Run>>, k: Int, g: Int) {
        data class BlockPair(val choice: VoiceGroupChoice, val a: List<Run>, val b: List<Run>)
        val pairs = histories.flatMap { (key, runs) ->
            runs.chunked(k).filter { it.size == k }.zipWithNext().map { (a, b) -> BlockPair(key.second, a, b) }
        }
        if (pairs.isEmpty()) return
        val pool = pairs.groupBy { it.choice }.mapValues { (_, p) -> p.map { it.b } }
        val random = Random(42)
        var definite = 0; var nonAdjacent = 0; var same = 0; var overlap = 0
        var otherSame = 0; var otherN = 0
        pairs.forEach { p ->
            val group = p.choice.group
            val ra = reading(p.a, group, g)!!
            val rb = reading(p.b, group, g)!!
            if (!ra.isBetween) definite++
            if (ra.isBetween && !adjacentFamilies(ra.familyRes)) nonAdjacent++
            if (ra.familyRes == rb.familyRes) same++
            if (ra.familyRes.intersect(rb.familyRes.toSet()).isNotEmpty()) overlap++
            val strangers = pool.getValue(p.choice).filter { it.first().uid != p.a.first().uid }
            if (strangers.isNotEmpty()) {
                otherN++
                if (reading(strangers[random.nextInt(strangers.size)], group, g)!!.familyRes == ra.familyRes) otherSame++
            }
        }
        val n = pairs.size
        line("  g=$g k=$k  pairs=$n  one family ${pct(definite, n)} (non-adjacent 'between' ${pct(nonAdjacent, n)})  |  " +
            "same ${pct(same, n)}, overlap ${pct(overlap, n)}  |  other install: same ${pct(otherSame, otherN)}")
    }

    private fun StringBuilder.sequentialReadings(histories: Map<Pair<String, VoiceGroupChoice>, List<Run>>, g: Int) {
        var pairs = 0; var changed = 0; var shown = 0; var definite = 0
        histories.forEach { (key, runs) ->
            val group = key.second.group
            val labels = runs.indices.mapNotNull { i ->
                val recent = VoiceProfileAggregator.selectRecent(
                    runs.subList(0, i + 1).map { TimedProfile(it.tsMs, it.profile) }, runs[i].tsMs,
                )
                if (recent.size < VoiceProfileAggregator.MIN_SESSIONS) null
                else VoiceTypeReader.read(FachClassifier.classify(VoiceProfileAggregator.medianProfile(recent), group), g)!!
            }
            shown += labels.size
            definite += labels.count { !it.isBetween }
            labels.zipWithNext().forEach { (a, b) -> pairs++; if (a.familyRes != b.familyRes) changed++ }
        }
        line("  g=$g  readings=$shown  one family ${pct(definite, shown)}  |  label changed ${pct(changed, pairs)} of $pairs consecutive")
    }

    /** True when the two families are neighbours in the table (soprano–mezzo, tenor–baritone, …). */
    private fun adjacentFamilies(familyRes: List<Int>): Boolean {
        val order = ALL_FACH.map { it.categoryRes }.distinct()
        return kotlin.math.abs(order.indexOf(familyRes[0]) - order.indexOf(familyRes[1])) == 1
    }

    /**
     * True when a Male/Female session's best Fach in the other group outscores its best in
     * the chosen group by at least [margin] points.
     */
    private fun mismatch(run: Run, margin: Int = MISMATCH_MARGIN): Boolean {
        val group = run.choice.group ?: return false
        val other = if (group == VoiceGroup.MALE) VoiceGroup.FEMALE else VoiceGroup.MALE
        val own = FachClassifier.classify(run.profile, group).firstOrNull()?.score ?: return false
        val best = FachClassifier.classify(run.profile, other).firstOrNull()?.score ?: return false
        return best - own >= margin
    }

    private fun compatibleSet(profiles: List<Run>, group: VoiceGroup?, d: Int): Set<String> {
        val matches = FachClassifier.classify(VoiceProfileAggregator.medianProfile(profiles.map { it.profile }), group)
        val top = matches.firstOrNull()?.score ?: return emptySet()
        return matches.filter { it.score >= top - d }.mapNotNull { key(it) }.toSet()
    }

    private fun StringBuilder.compatibleSets(histories: Map<Pair<String, VoiceGroupChoice>, List<Run>>, k: Int, d: Int) {
        data class BlockPair(val choice: VoiceGroupChoice, val a: List<Run>, val b: List<Run>)
        val pairs = histories.flatMap { (key, runs) ->
            runs.chunked(k).filter { it.size == k }.zipWithNext().map { (a, b) -> BlockPair(key.second, a, b) }
        }
        if (pairs.isEmpty()) return
        // Blocks grouped by voice choice, for the other-install baseline.
        val pool = pairs.groupBy { it.choice }.mapValues { (_, p) -> p.map { it.b } }
        val random = Random(42)

        var size = 0.0; var any = 0; var predicts = 0; var jaccard = 0.0
        var otherAny = 0; var otherPredicts = 0; var otherJaccard = 0.0; var otherN = 0
        pairs.forEach { p ->
            val group = p.choice.group
            val setA = compatibleSet(p.a, group, d)
            val setB = compatibleSet(p.b, group, d)
            val winnerB = compatibleSet(p.b, group, 0)
            size += setA.size
            if ((setA intersect setB).isNotEmpty()) any++
            if ((setA intersect winnerB).isNotEmpty()) predicts++
            jaccard += jaccard(setA, setB)

            val strangers = pool.getValue(p.choice).filter { it.first().uid != p.a.first().uid }
            if (strangers.isNotEmpty()) {
                val other = strangers[random.nextInt(strangers.size)]
                val setO = compatibleSet(other, group, d)
                val winnerO = compatibleSet(other, group, 0)
                otherN++
                if ((setA intersect setO).isNotEmpty()) otherAny++
                if ((setA intersect winnerO).isNotEmpty()) otherPredicts++
                otherJaccard += jaccard(setA, setO)
            }
        }
        val n = pairs.size
        line("  d=$d k=$k  pairs=$n  avg set size ${fmt(size / n)}  |  same install: any ${pct(any, n)}, predicts ${pct(predicts, n)}, " +
            "Jaccard ${fmt(jaccard / n)}  |  other install: any ${pct(otherAny, otherN)}, predicts ${pct(otherPredicts, otherN)}, " +
            "Jaccard ${fmt(otherJaccard / otherN.coerceAtLeast(1))}")
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        val union = (a union b).size
        return if (union == 0) 0.0 else (a intersect b).size.toDouble() / union
    }

    private fun stepsOf(runs: List<Run>, group: VoiceGroup?): List<Step> = runs.indices.map { i ->
        val single = FachClassifier.classify(runs[i].profile, group)
        val combined = VoiceProfileAggregator.combine(
            sessions = runs.subList(0, i + 1).map { TimedProfile(it.tsMs, it.profile) },
            nowMs = runs[i].tsMs,
            group = group,
        )
        Step(
            tsMs = runs[i].tsMs,
            singleKey = key(single.firstOrNull()),
            singleFamily = single.firstOrNull()?.fach?.categoryRes,
            singleCloseCall = closeCall(single),
            combinedKey = key(combined?.matches?.firstOrNull()),
            combinedFamily = combined?.matches?.firstOrNull()?.fach?.categoryRes,
            combinedCloseCall = combined != null && closeCall(combined.matches),
            windowSize = combined?.sessionCount ?: 1,
            consistent = combined?.consistentCount ?: 0,
        )
    }

    private fun StringBuilder.sequential(histories: List<List<Step>>) {
        line("Installs with 3+ sessions: ${histories.size}")
        var sPairs = 0; var sFach = 0; var sFam = 0
        var cPairs = 0; var cFach = 0; var cFam = 0
        var sDistinct = 0.0; var cDistinct = 0.0
        histories.forEach { h ->
            h.zipWithNext().forEach { (a, b) ->
                sPairs++; if (a.singleKey != b.singleKey) sFach++; if (a.singleFamily != b.singleFamily) sFam++
                if (a.combinedKey != null && b.combinedKey != null) {
                    cPairs++; if (a.combinedKey != b.combinedKey) cFach++; if (a.combinedFamily != b.combinedFamily) cFam++
                }
            }
            sDistinct += h.mapNotNull { it.singleKey }.distinct().size
            cDistinct += h.mapNotNull { it.combinedKey }.distinct().size
        }
        line("  single take:  Fach changed ${pct(sFach, sPairs)}, family changed ${pct(sFam, sPairs)} of $sPairs consecutive pairs; " +
            "avg distinct Fach ${fmt(sDistinct / histories.size)}")
        line("  combined:     Fach changed ${pct(cFach, cPairs)}, family changed ${pct(cFam, cPairs)} of $cPairs consecutive pairs; " +
            "avg distinct Fach ${fmt(cDistinct / histories.size)}")
        val settled = histories.filter { it.size >= 6 }
        line("  installs with 6+ sessions whose last 3 combined results are the same Fach: " +
            "${pct(settled.count { h -> h.takeLast(3).map { it.combinedKey }.distinct().size == 1 }, settled.size)} of ${settled.size}")
    }

    private fun StringBuilder.disjoint(histories: Map<Pair<String, VoiceGroupChoice>, List<Run>>, k: Int, acrossDaysOnly: Boolean = false) {
        var pairs = 0; var sameFach = 0; var sameFamily = 0
        val installs = mutableSetOf<Pair<String, VoiceGroupChoice>>()
        histories.forEach { (key, runs) ->
            val group = key.second.group
            val blocks = runs.chunked(k).filter { it.size == k }
            blocks.zipWithNext().forEach { (a, b) ->
                if (acrossDaysOnly && b.first().tsMs - a.last().tsMs < DAY_MS / 2) return@forEach
                installs += key
                val ma = FachClassifier.classify(VoiceProfileAggregator.medianProfile(a.map { it.profile }), group).firstOrNull()
                val mb = FachClassifier.classify(VoiceProfileAggregator.medianProfile(b.map { it.profile }), group).firstOrNull()
                pairs++
                if (key(ma) == key(mb)) sameFach++
                if (ma?.fach?.categoryRes == mb?.fach?.categoryRes) sameFamily++
            }
        }
        line("  k=$k  installs=${installs.size}  block pairs=$pairs  same Fach ${pct(sameFach, pairs)}  same family ${pct(sameFamily, pairs)}")
    }

    private fun StringBuilder.line(s: String = "") = append(s).append('\n')
    private fun pct(n: Int, d: Int) = if (d == 0) "—" else String.format(Locale.ROOT, "%.1f%%", 100.0 * n / d)
    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MISMATCH_MARGIN = VoiceGroupCheck.MARGIN
    }
}
