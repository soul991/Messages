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

## Dependency updates

Schedule updates with the full release test suite rather than in isolation:

```sh
./gradlew :protection-engine:test :core-messaging:testDebugUnitTest \
          :app:testDebugUnitTest lint :app:assembleRelease
```

The toolchain (`gradle/libs.versions.toml`) is on a 2024-era AGP/Kotlin/AndroidX
line. The review flagged this as an update signal only and asserted no CVE. Treat
a bump as its own change with its own verification-metadata regeneration and
review, not as a drive-by.
