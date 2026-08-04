# Build Supply Chain

**R-28 compliance.** What is pinned, why, and how to change it without either
breaking the build or quietly disabling the protection.

## What is pinned

| Layer | Mechanism | Where |
|---|---|---|
| Gradle distribution | `distributionSha256Sum` | `gradle/wrapper/gradle-wrapper.properties` |
| Every resolved dependency artifact | SHA-256 checksums | `gradle/verification-metadata.xml` |
| Every GitHub Action | Full 40-char commit SHA | `.github/workflows/ci.yml` |
| Release signing identity | Certificate SHA-256 | `docs/ops/RELEASE_SIGNING.md`, asserted by `scripts/verify-release-artifact.sh` |

Pinning answers *"are these the bytes upstream published?"*. It has never
answered *"is anything wrong with those bytes?"* — see [§6](#6-vulnerability-scanning-v2-18).

## 1. The Gradle wrapper

`gradle-wrapper.properties` carries `distributionSha256Sum` alongside
`distributionUrl`. The wrapper refuses to run a distribution whose hash does not
match, so a tampered or substituted Gradle download fails closed.

When upgrading Gradle, take the checksum from Gradle's official publication at
<https://gradle.org/release-checksums/> — the `-bin.zip` **SHA-256** for the
exact version. Do not copy a hash out of a search result or generate it from an
already-downloaded file; that would attest to whatever you happened to download.

## 2. Dependency verification

`gradle/verification-metadata.xml` pins a SHA-256 for every artifact the build
resolves (567 components / 1009 checksums at time of writing). Its mere presence
switches verification on — there is no flag to remember.

Configuration is `verify-metadata=true`, `verify-signatures=false`: checksums
only. PGP signature verification is deliberately not enabled, because a large
fraction of the Android/Kotlin ecosystem either does not sign or signs with keys
that rotate without notice, which produces failures that get "fixed" by turning
verification off entirely. Checksum pinning is the level that survives contact
with this dependency tree.

### When a dependency changes

A version bump makes the build fail with *"artifact … not in dependency
verification metadata"*. That failure is the feature. Regenerate:

```sh
./gradlew --write-verification-metadata sha256 \
  :protection-engine:test :core-messaging:testDebugUnitTest :app:testDebugUnitTest \
  :app:assembleRelease :app:assembleDebug lint
```

The task list matters: Gradle can only record what it resolves, and a
configuration you leave out becomes a gap that fails later, in some unrelated
job. Use that exact list unless you have added a new kind of build.

Then — and this is the part that makes it worth anything — **review the diff**:

```sh
git diff gradle/verification-metadata.xml
```

Expect changes confined to the dependency you intended to change. Entries
appearing for artifacts you did not touch, or a checksum changing for a version
that did *not* change, is exactly the event this file exists to surface. Do not
commit through it.

To confirm the regenerated metadata is complete, force full re-resolution:

```sh
./gradlew --refresh-dependencies :app:assembleRelease lint
```

## 3. GitHub Actions

Actions are pinned to full commit SHAs, each annotated with the tag it came from
and the date it was resolved. A tag like `@v4` is mutable — pinning to it grants
standing write access to whoever controls that tag.

Re-resolve deliberately when bumping:

```sh
gh api repos/actions/checkout/commits/v4 --jq '.sha'
```

Never paste a hash you have not resolved yourself from the upstream repository.

## 4. Consumer ProGuard rules

`core-messaging/build.gradle.kts` declares
`consumerProguardFiles("consumer-rules.pro")`. That file now exists. It
previously did not, and AGP treats a missing consumer file as empty rather than
as an error — so the release build silently depended on `:app` carrying those
keep rules on the module's behalf.

The rules that belong to `:core-messaging` (the `@Serializable` backup envelope,
whose field names are a wire format read by restore) now travel with the module.
`:protection-engine` is a pure JVM jar and cannot ship consumer rules, so
`app/proguard-rules.pro` still covers it under the wider `com.messages.**` scope.

## 5. Release signing intent

Release builds are unsigned when `keystore.properties` is absent — which is what
a contributor without the key needs. Passing `-PrequireSigning=true` turns that
into a hard failure, and CI's tag-triggered release job always passes it. See
[`RELEASE_SIGNING.md`](RELEASE_SIGNING.md).

## 6. Vulnerability scanning (V2-18)

Checksums prove provenance, not safety. Until this existed, nothing in the build
asked whether any of the 570 pinned artifacts had a published advisory against
it, and the answer would have been the same whether it was zero or fifty.

`scripts/scan-dependencies.py` asks. It queries [OSV](https://osv.dev) for every
component in `gradle/verification-metadata.xml` and runs in CI on every push, on
a nightly schedule, and as a gate on the signed release job.

```sh
./gradlew :app:shippedDependencies          # writes the inventory the scan needs
python3 scripts/scan-dependencies.py        # exit 0 clean / 1 findings / 2 could not ask
```

Both scripts are standard library only, on purpose: adding a scanning dependency
to fix a supply-chain finding enlarges the supply chain it is meant to protect,
and every new artifact would need its own line in the verification metadata.

### The split that makes the number mean something

The scan reports two scopes, and they are not comparable:

| Scope | What it is | Threshold |
|---|---|---|
| **shipped** | The 148 external coordinates on `:app:releaseRuntimeClasspath` — what is actually in the APK, reachable from a received message | **Any advisory fails the build.** |
| **build** | Everything else Gradle resolves: AGP, KSP, lint, test libraries. Reaching it requires already controlling the build machine | Fails on anything not in the reviewed baseline. |

`releaseRuntimeClasspath` is precisely the shipped set — `compileOnly` and
annotation processors are absent, project modules contribute their transitives,
and R8 only ever removes. The build set is everything else by subtraction, and
deliberately over-reports: the verification metadata pins the losing side of a
version conflict too, so a version nothing actually resolves to can still be
flagged. That is the right error to make in a scope where nothing ships.

As of 2026-08-01: **0 shipped findings.** All 16 flagged components are
build-time, and every one arrives through `com.android.tools.build:gradle`.

### The baseline, and why it is a baseline

`gradle/accepted-advisories.json` holds the build-time findings that have been
read and accepted, each with a coordinate, exact advisory ids, a written reason,
and an expiry.

A severity gate would have been the conventional choice, and it was rejected on
measurement rather than taste. The tree carried 23 blocking advisories; moving to
the newest Android Gradle Plugin the toolchain can reach (8.13.2, which also
requires Gradle 8.13) was measured to leave **24** — newer AGP adds protobuf
variants faster than it retires them. There is no version of AGP that clears
these, so a severity gate would be a gate nobody could ever satisfy, and gates
like that get switched off. An exact-id baseline stays on.

Two rules keep it from becoming a mute button:

- **Advisory ids must be exact.** The scanner refuses wildcards. A new advisory
  in one of these same artifacts is still a build failure.
- **No `"scope": "shipped"` entries.** Accepting one means knowingly publishing
  vulnerable code to users; it is a product decision, not a bookkeeping entry.

Accepted findings are printed on every run rather than hidden, so an
acknowledgement stays a decision on record. Expiry does not imply a fix is
expected by then — it forces someone to re-run the measurement and confirm the
reasoning still holds, in particular that none of these has moved into the APK.

`DependencyScanTest` (in `:app`) holds this shape in place: the CI wiring, the
scheduled trigger, the release gate, and both baseline rules. Expiry evaluation
is left to the scanner so `./gradlew test` does not become time-dependent.

### If the scan cannot reach OSV

It exits 2 and fails the job. A green check that means *"we could not ask"* is
worse than no check, because it is indistinguishable from *"we asked and it was
clean"*. Re-run the workflow by hand (`workflow_dispatch`) once the API is back.

### Bill of materials

`scripts/generate-sbom.py` writes a CycloneDX 1.5 document of the shipped set,
attached to every signed release. It answers the question that arrives a year
later — *"an advisory landed against library X; was it in the build we shipped?"*
— which the verification metadata cannot, since that file is the superset of
everything the build ever resolved.

It is deterministic by default: no timestamp, and a serial derived from a
SHA-256 over the component list, so two builds of the same graph produce a
byte-identical document that can be diffed to prove nothing moved. The release
job passes `--timestamp`, where provenance matters more than comparability.

## Dependency updates

`.github/dependabot.yml` proposes updates weekly for Gradle dependencies and for
GitHub Actions, grouped by family (AGP, Kotlin, AndroidX, kotlinx, test
libraries) rather than one PR per artifact.

**Dependabot's PRs will fail, and that is the pin working.** It changes a version
but cannot regenerate `gradle/verification-metadata.xml`, so resolution stops
with *"artifact ... not in dependency verification metadata"*. Treat such a PR as
a notification with a ready-made diff, and finish it by hand per [§2](#2-dependency-verification).

Schedule updates with the full release test suite rather than in isolation:

```sh
./gradlew :protection-engine:test :core-messaging:testDebugUnitTest \
          :app:testDebugUnitTest lint :app:assembleRelease
```

The toolchain (`gradle/libs.versions.toml`) is on a 2024-era AGP/Kotlin/AndroidX
line. The review flagged this as an update signal and asserted no CVE; the scan
above has since confirmed no shipped advisory, and measured that the reachable
AGP upgrade does not reduce the build-time count. Treat a bump as its own change
with its own verification-metadata regeneration and review, not as a drive-by.
