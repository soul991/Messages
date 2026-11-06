package com.messages.protection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class MemoryMeasurementHarnessTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun measureCandidateMemory() {
        // Look for model file to measure, fallback to current candidate or bundled
        val candidatePath = System.getProperty("candidate.model.path") 
            ?: "ml/candidates/9a4ddf6934c0bab5/model.json"
        
        var modelFile = File(candidatePath)
        if (!modelFile.exists()) {
            modelFile = File("../" + candidatePath)
        }
        if (!modelFile.exists()) {
            // Try relative to workspace root
            val root = File("D:/Istak/Messages/Messages App/01_active_codebase/messages")
            modelFile = File(root, candidatePath)
        }

        println("Measuring memory for model: ${modelFile.absolutePath} (exists: ${modelFile.exists()})")
        
        val rt = Runtime.getRuntime()
        System.gc()
        Thread.sleep(100)
        System.gc()
        
        val heapBeforeBytes = rt.totalMemory() - rt.freeMemory()

        // Read and parse model
        val jsonText = if (modelFile.exists()) {
            modelFile.readText()
        } else {
            javaClass.getResourceAsStream("/ngram_model.json")!!.bufferedReader().readText()
        }
        
        val rootObj = json.parseToJsonElement(jsonText).jsonObject
        val modelType = rootObj["modelType"]?.jsonPrimitive?.content ?: "NGramScorer"

        // Store loaded model in memory
        val loadedExperts = mutableMapOf<String, Any>()
        if (rootObj.containsKey("experts")) {
            val expertsObj = rootObj["experts"]!!.jsonObject
            for ((script, expertElem) in expertsObj) {
                val expObj = expertElem.jsonObject
                val intercept = expObj["intercept"]?.jsonPrimitive?.double ?: 0.0
                val weights = mutableMapOf<String, Double>()
                expObj["weights"]?.jsonObject?.forEach { (k, v) ->
                    weights[k] = v.jsonPrimitive.double
                }
                val idf = mutableMapOf<String, Double>()
                expObj["idf"]?.jsonObject?.forEach { (k, v) ->
                    idf[k] = v.jsonPrimitive.double
                }
                loadedExperts[script] = Triple(intercept, weights, idf)
            }
        }

        System.gc()
        Thread.sleep(50)
        val heapAfterLoadBytes = rt.totalMemory() - rt.freeMemory()
        val heapDeltaBytes = maxOf(0L, heapAfterLoadBytes - heapBeforeBytes)

        // 1,000 scores benchmark
        val testMessages = listOf(
            "Your HDFC Bank account 4829 has been debited by Rs 1500.00 on 20-Oct-26. Call 18002586161 if not done by you.",
            "Free lottery prize Rs 50,00,000 won in KBC lucky draw! Send processing fee immediately to claim.",
            "Electricity bill unpaid: Power will be disconnected tonight at 9:30 PM. Call electricity officer at 9876543210.",
            "प्रिय ग्राहक, आपका बैंक खाता ब्लॉक कर दिया गया है। तुरंत पैन अपडेट करें: http://bit.ly/bank-update",
            "আপোনাৰ বিদ্যুৎ বিল পৰিশোধ কৰা হোৱা নাই। যোগাযোগ কৰক নম্বৰত。",
            "Job offer: Earn Rs 3000 to 5000 daily by liking YouTube videos. Join our Telegram channel now.",
            "Dear customer, never share your OTP, UPI PIN, or password with anyone. Bank never asks for sensitive details."
        )

        var dummySum = 0.0
        val sampleSize = 1000
        for (i in 0 until sampleSize) {
            val msg = testMessages[i % testMessages.size]
            // Simple scoring pass over message
            val latinExpert = loadedExperts["latin"] as? Triple<Double, Map<String, Double>, Map<String, Double>>
            if (latinExpert != null) {
                val (intercept, weights, idf) = latinExpert
                var score = intercept
                val L = msg.length
                for (n in 2..5) {
                    for (j in 0..L - n) {
                        val g = msg.substring(j, j + n).lowercase()
                        val w = weights[g]
                        if (w != null) {
                            score += w
                        }
                    }
                }
                dummySum += score
            }
        }

        val heapDuringScoringBytes = rt.totalMemory() - rt.freeMemory()

        // Resident set size (RSS) via ManagementFactory or ProcessHandle
        val pid = ProcessHandle.current().pid()
        var residentBytes = 0L
        try {
            // Query OS working set size via powershell or wmic on windows
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", 
                "(Get-Process -Id $pid).WorkingSet64").start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            residentBytes = out.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            residentBytes = heapDuringScoringBytes
        }

        val heapDeltaMb = round4(heapDeltaBytes.toDouble() / (1024.0 * 1024.0))
        val heapDuringMb = round4(heapDuringScoringBytes.toDouble() / (1024.0 * 1024.0))
        val residentMb = round4(residentBytes.toDouble() / (1024.0 * 1024.0))

        val resultJson = """
        {
            "environment": "host JVM",
            "jvm_version": "${System.getProperty("java.version")}",
            "pid": $pid,
            "heap_before_bytes": $heapBeforeBytes,
            "heap_after_load_bytes": $heapAfterLoadBytes,
            "heap_delta_bytes": $heapDeltaBytes,
            "heap_delta_mb": $heapDeltaMb,
            "heap_during_1000_scores_bytes": $heapDuringScoringBytes,
            "heap_during_1000_scores_mb": $heapDuringMb,
            "resident_bytes": $residentBytes,
            "resident_mb": $residentMb,
            "scores_evaluated": $sampleSize,
            "checksum_sink": $dummySum,
            "device": "NOT MEASURED"
        }
        """.trimIndent()

        println("\n=== MEMORY MEASUREMENT RESULTS ===")
        println(resultJson)

        val outDir = File("build")
        if (!outDir.exists()) outDir.mkdirs()
        File(outDir, "memory_measurement.json").writeText(resultJson)
        
        // Also write to parent build if running inside module
        val parentOutDir = File("../build")
        if (parentOutDir.exists()) {
            File(parentOutDir, "memory_measurement.json").writeText(resultJson)
        }
    }

    private fun round4(value: Double): Double =
        kotlin.math.round(value * 10000.0) / 10000.0
}
