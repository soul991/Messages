package com.messages.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring Cold and Warm startup timings (Phase 6.3 / Part 6).
 * Evaluates Time-To-Initial-Display and Time-To-Fully-Drawn across
 * CompilationMode.Partial (baseline profile) and CompilationMode.None.
 * 10 iterations each.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun setUp() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val output = device.executeShellCommand(
            "am broadcast -n $TARGET_PACKAGE/com.messages.app.debug.SeedDataReceiver -a com.messages.app.debug.SEED --ez wipe true"
        )
        check(output.contains("result=-1")) {
            "Database seeding failed! Output was: $output"
        }
    }

    @Test
    fun startupColdCompilationNone() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupColdCompilationPartial() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarmCompilationNone() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 10,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarmCompilationPartial() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        startActivityAndWait()
    }

    companion object {
        const val TARGET_PACKAGE = "com.messages.app.benchmark"
    }
}
