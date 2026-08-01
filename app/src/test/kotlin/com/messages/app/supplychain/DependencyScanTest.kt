package com.messages.app.supplychain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-18 guard.
 *
 * Dependency verification proves we got the bytes upstream published; it has
 * never had an opinion on whether those bytes are known-vulnerable. That
 * question is now asked by `scripts/scan-dependencies.py`, wired into CI, with
 * a reviewed baseline in `gradle/accepted-advisories.json`.
 *
 * All of which is deletable in one line. Drop the CI step and the scan simply
 * stops happening — no build fails, no badge turns red, and the repository goes
 * back to the state the review flagged while looking exactly as it does today.
 * So this test holds the wiring in place, and holds the shape of the baseline,
 * which is the part most likely to rot:
 *
 *  - a wildcard advisory id would accept advisories nobody has read, including
 *    ones published after the entry was written;
 *  - a `"scope": "shipped"` entry would be a decision to knowingly publish
 *    vulnerable code to users, and belongs in a conversation, not a JSON file.
 *
 * What this deliberately does NOT check is whether the entries have expired, or
 * whether OSV currently returns anything. The scanner owns both. A unit test
 * that reached the network would be flaky, and one that compared against the
 * clock would turn `./gradlew test` red on a date nobody chose.
 *
 * Paths are relative to the `:app` module directory, which is the working
 * directory for its unit tests, so the repository root is `../`.
 */
class DependencyScanTest {

    private val repoRoot = File("..")

    private val scanner = File(repoRoot, "scripts/scan-dependencies.py")
    private val sbom = File(repoRoot, "scripts/generate-sbom.py")
    private val baselineFile = File(repoRoot, "gradle/accepted-advisories.json")
    private val workflow = File(repoRoot, ".github/workflows/ci.yml")
    private val dependabot = File(repoRoot, ".github/dependabot.yml")
    private val appBuildFile = File("build.gradle.kts")

    @Test
    fun `every piece of the scan is present`() {
        // Each assertion below is vacuous against a missing file.
        for (file in listOf(scanner, sbom, baselineFile, workflow, dependabot, appBuildFile)) {
            assertTrue("expected ${file.canonicalPath}", file.isFile)
        }
    }

    @Test
    fun `the build can still say what ships`() {
        // Everything downstream depends on the shipped-vs-build-time split, and
        // that split comes from one Gradle task reading releaseRuntimeClasspath.
        val text = appBuildFile.readText()
        assertTrue(
            "the shippedDependencies task is gone; scan-dependencies.py has no input without it",
            text.contains("""tasks.register("shippedDependencies")"""),
        )
        assertTrue(
            "shippedDependencies must resolve releaseRuntimeClasspath — any other " +
                "configuration includes code that never reaches the APK",
            text.contains("releaseRuntimeClasspath"),
        )
    }

    @Test
    fun `CI runs the scan and publishes a bill of materials`() {
        val text = workflow.readText()
        assertTrue(
            "ci.yml no longer invokes scripts/scan-dependencies.py",
            text.contains("scripts/scan-dependencies.py"),
        )
        assertTrue(
            "ci.yml no longer invokes scripts/generate-sbom.py",
            text.contains("scripts/generate-sbom.py"),
        )
        assertTrue(
            "ci.yml must run :app:shippedDependencies before scanning",
            text.contains(":app:shippedDependencies"),
        )
    }

    @Test
    fun `CI scans on a schedule and not only on changes`() {
        // The failure mode this covers: an advisory is published against code we
        // already have. The repository does not move, so nothing on push or
        // pull_request ever fires again, and a scan that only runs when someone
        // commits cannot find it.
        val text = workflow.readText()
        assertTrue(
            "ci.yml has no schedule trigger; without one the scan only sees " +
                "advisories that happen to coincide with a commit",
            Regex("""(?m)^\s{2}schedule:""").containsMatchIn(text),
        )
        assertTrue(
            "the schedule trigger has no cron expression",
            Regex("""(?m)^\s+- cron:\s*["']?\S""").containsMatchIn(text),
        )
    }

    @Test
    fun `a signed release cannot be produced without a green scan`() {
        val text = workflow.readText()
        // The capture stops at the newline on purpose: `(.+)$` under DOTALL runs
        // greedily to the end of the file, and would then be satisfied by the
        // string "dependency-scan" appearing anywhere below — including in the
        // job it is supposed to be checking for.
        val releaseNeeds = Regex("""(?ms)^ {2}release:.*?^ {4}needs:[ \t]*([^\n]+)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?: error("could not find the release job's needs: in ci.yml")
        assertTrue(
            "the release job does not depend on dependency-scan, so a tagged build " +
                "can ship past a known advisory as long as the tests pass (needs: $releaseNeeds)",
            releaseNeeds.contains("dependency-scan"),
        )
    }

    @Test
    fun `update proposals cover both supply chains`() {
        // The scan says "what we have is vulnerable". Only an update feed says
        // "what we have was fixed upstream and we did not take it".
        val text = dependabot.readText()
        for (ecosystem in listOf("gradle", "github-actions")) {
            assertTrue(
                "dependabot.yml does not watch the $ecosystem ecosystem",
                text.contains("package-ecosystem: $ecosystem"),
            )
        }
    }

    @Test
    fun `every accepted advisory is a complete, reviewable record`() {
        for ((index, entry) in acceptedEntries().withIndex()) {
            val where = "accepted-advisories.json entry $index"
            for (key in listOf("ids", "coordinate", "scope", "reason", "expires")) {
                assertTrue("$where is missing \"$key\"", entry.containsKey(key))
            }

            val ids = entry.getValue("ids").jsonArray
            assertTrue("$where accepts nothing; delete it instead", ids.isNotEmpty())

            // A reason is the whole justification for muting a finding. One that
            // says "false positive" and stops is not a record of anything.
            val reason = entry.getValue("reason").jsonPrimitive.content
            assertTrue(
                "$where has a reason too short to be an argument: \"$reason\"",
                reason.length >= 80,
            )

            val expires = entry.getValue("expires").jsonPrimitive.content
            assertTrue(
                "$where has an expiry that is not an ISO date: \"$expires\"",
                Regex("""^\d{4}-\d{2}-\d{2}$""").matches(expires),
            )
        }
    }

    @Test
    fun `no accepted advisory is a wildcard`() {
        // The coordinate may be a glob — one entry can cover every artifact in a
        // group. The advisory ids may not: a wildcard there would suppress
        // future advisories in the same artifact, which is precisely the event
        // this scan exists to catch.
        for ((index, entry) in acceptedEntries().withIndex()) {
            for (id in entry.getValue("ids").jsonArray.map { it.jsonPrimitive.content }) {
                assertFalse(
                    "accepted-advisories.json entry $index uses a pattern advisory id " +
                        "(\"$id\"); ids must be exact",
                    id.contains('*') || id.contains('?'),
                )
            }
        }
    }

    @Test
    fun `nothing vulnerable is accepted into the APK`() {
        // Build-time findings are a judgement call about a machine we control.
        // A shipped finding is code on a user's phone reachable from a received
        // message, and no expiry date makes that acceptable by default.
        val scopes = acceptedEntries().map { it.getValue("scope").jsonPrimitive.content }
        assertEquals(
            "accepted-advisories.json accepts an advisory in shipped code. That is a " +
                "product decision, not a baseline entry — take the upgrade, or make " +
                "the case explicitly and widen this test with it.",
            emptyList<String>(), scopes.filterNot { it == "build" },
        )
    }

    private fun acceptedEntries(): List<JsonObject> {
        val root = Json.parseToJsonElement(baselineFile.readText()).jsonObject
        val accepted = root["accepted"] as? JsonArray
            ?: error("accepted-advisories.json has no \"accepted\" array")
        return accepted.map { it.jsonObject }
    }
}
