#!/usr/bin/env bash
#
# R-27 — verify a release APK before it is treated as distributable.
#
# The review's point was that a green CI run proved almost nothing about the
# artifact: an unsigned APK, an APK signed by the wrong key, one built with a
# debug manifest, or one still shipping the debug-only injection receivers would
# all have sailed through. This script asserts the properties that actually make
# an artifact safe to publish, and exits non-zero on the first failure.
#
# Usage:
#   scripts/verify-release-artifact.sh app/build/outputs/apk/release/app-release.apk
#
# Environment:
#   EXPECTED_CERT_SHA256  Signing certificate fingerprint, colon-separated or
#                         bare hex, case-insensitive. Defaults to the identity
#                         recorded in docs/ops/RELEASE_SIGNING.md.
#   EXPECTED_VERSION_CODE Optional; asserted when set.
#   ALLOW_UNSIGNED        Set to 1 to skip signature checks. For inspecting a
#                         contributor build ONLY — never in a release job.

set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" ]]; then
    echo "usage: $0 <path-to-apk>" >&2
    exit 2
fi
if [[ ! -f "$APK" ]]; then
    if [[ -f "${APK%.apk}-unsigned.apk" ]]; then
        APK="${APK%.apk}-unsigned.apk"
    elif [[ -f "app/build/outputs/apk/release/Messages.apk" ]]; then
        APK="app/build/outputs/apk/release/Messages.apk"
    elif [[ -f "app/build/outputs/apk/release/Messages-unsigned.apk" ]]; then
        APK="app/build/outputs/apk/release/Messages-unsigned.apk"
    elif [[ -f "app/build/outputs/apk/release/app-release.apk" ]]; then
        APK="app/build/outputs/apk/release/app-release.apk"
    elif [[ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]]; then
        APK="app/build/outputs/apk/release/app-release-unsigned.apk"
    else
        echo "FAIL: no such APK: $APK" >&2
        exit 1
    fi
fi

# The identity from docs/ops/RELEASE_SIGNING.md. Rotating the signing key means
# updating this and that document together — deliberately awkward, because on
# Android an identity change is permanent for every installed user.
DEFAULT_CERT_SHA256="910C79B93B6DDFC7BEDBFE1FA7B48CFD24404CD4D2892CB8050201CC67C7FB9A"
EXPECTED_CERT_SHA256="${EXPECTED_CERT_SHA256:-$DEFAULT_CERT_SHA256}"

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1" >&2; failures=$((failures + 1)); }
info() { printf '        %s\n' "$1"; }

# Normalise a fingerprint to bare uppercase hex.
normalise() { tr -d ': \n\r\t' | tr '[:lower:]' '[:upper:]'; }

# ---- locate the SDK tools -------------------------------------------------

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
find_tool() {
    local name="$1" found
    if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return; fi
    # Highest build-tools version that has it.
    found=$(ls -d "$SDK_ROOT"/build-tools/*/ /opt/homebrew/share/android-commandlinetools/build-tools/*/ 2>/dev/null | sort -Vr | while read -r dir; do
        [[ -x "$dir$name" ]] && { echo "$dir$name"; break; }
    done)
    [[ -n "$found" ]] && echo "$found"
}

APKSIGNER=$(find_tool apksigner || true)
AAPT2=$(find_tool aapt2 || true)

# apksigner is a shell wrapper around a JAR, so it needs a JDK. CI provides one
# via setup-java; locally, fall back to the macOS locator so the script is
# usable on a developer machine without extra setup.
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in \
        "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" \
        "$(/usr/libexec/java_home 2>/dev/null || true)" \
        /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@17 \
        /usr/lib/jvm/temurin-17-jdk-amd64 \
        /usr/lib/jvm/java-17-openjdk-amd64
    do
        if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

# An attribute may be printed either as `=false` or in aapt2's typed form
# `(type 0x12)0x0`, depending on build-tools version. Match both rather than
# silently passing a check that never ran.
manifest_attr_is_true() {  # <manifest-dump> <attr-name>
    grep -qE "$2\(0x[0-9a-f]+\)=(\(type 0x12\)0xffffffff|true)" <<<"$1"
}
manifest_attr_is_false() { # <manifest-dump> <attr-name>
    grep -qE "$2\(0x[0-9a-f]+\)=(\(type 0x12\)0x0\b|false)" <<<"$1"
}

echo "Verifying $APK"
info "$(du -h "$APK" | cut -f1) — $(basename "$APK")"
echo

# ---- 1. signature ---------------------------------------------------------

echo "Signature"
if [[ "${ALLOW_UNSIGNED:-0}" == "1" ]]; then
    info "skipped (ALLOW_UNSIGNED=1) — this artifact is NOT distributable"
elif [[ -z "$APKSIGNER" ]]; then
    fail "apksigner not found; cannot verify signing (looked in $SDK_ROOT/build-tools)"
else
    if ! sig_output=$("$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1); then
        fail "apksigner rejected the APK"
        info "$sig_output"
    else
        pass "APK signature verifies"

        # v2/v3 protect the whole archive; v1 alone leaves it malleable.
        if grep -qE '^Verified using v2 scheme .*: true|^Verified using v3 scheme .*: true' <<<"$sig_output"; then
            pass "signed with APK Signature Scheme v2/v3"
        else
            fail "no v2/v3 signature — v1-only APKs are not acceptable for release"
        fi

        actual=$(grep -iE 'Signer #1 certificate SHA-?256 digest' <<<"$sig_output" \
            | head -1 | sed 's/.*: *//' | normalise)
        expected=$(printf '%s' "$EXPECTED_CERT_SHA256" | normalise)
        if [[ -z "$actual" ]]; then
            fail "could not read the signing certificate digest"
        elif [[ "$actual" == "$expected" ]]; then
            pass "signing certificate matches the expected identity"
        else
            fail "signing certificate MISMATCH"
            info "expected $expected"
            info "actual   $actual"
        fi
    fi
fi
echo

# ---- 2. manifest ----------------------------------------------------------

echo "Manifest"
if [[ -z "$AAPT2" ]]; then
    fail "aapt2 not found; cannot inspect the manifest"
else
    manifest=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null || true)
    badging=$("$AAPT2" dump badging "$APK" 2>/dev/null || true)

    if [[ -z "$manifest" ]]; then
        fail "could not read AndroidManifest.xml from the APK"
    else
        # A debuggable release build exposes the whole process via jdwp.
        if manifest_attr_is_true "$manifest" "android:debuggable"; then
            fail "android:debuggable is TRUE in a release artifact"
        else
            pass "not debuggable"
        fi

        # R-01: platform backup must stay off, or app-private state (including
        # locked-space material) is copied into the user's Google account.
        # Note the attribute must be present AND false — absent means the
        # platform default of true.
        if manifest_attr_is_false "$manifest" "android:allowBackup"; then
            pass "android:allowBackup is false"
        else
            fail "android:allowBackup is not false — platform backup would include app-private data"
        fi

        # R-01's other half: the Android 12+ transfer/backup rules.
        if grep -qE 'android:dataExtractionRules' <<<"$manifest"; then
            pass "dataExtractionRules is declared"
        else
            fail "dataExtractionRules is missing — Android 12+ D2D transfer is not constrained"
        fi

        # R-24: the debug-only injection/cleanup receivers must not ship.
        if grep -qE 'com\.messages\.app\.debug\.' <<<"$manifest"; then
            fail "debug-only components are present in the release manifest"
        else
            pass "no debug-only components"
        fi

        # Exported components are the app's attack surface; list them so a
        # reviewer sees any change, and fail on the debug harness permission.
        if grep -qE 'DEBUG_HARNESS' <<<"$manifest"; then
            fail "the debug harness permission is declared in the release manifest"
        else
            pass "no debug harness permission"
        fi
    fi

    if [[ -n "$badging" ]]; then
        pkg_line=$(grep -m1 '^package:' <<<"$badging" || true)
        info "$pkg_line"
        version_code=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$pkg_line")
        if [[ -n "${EXPECTED_VERSION_CODE:-}" ]]; then
            if [[ "$version_code" == "$EXPECTED_VERSION_CODE" ]]; then
                pass "versionCode is $version_code as expected"
            else
                fail "versionCode is $version_code, expected $EXPECTED_VERSION_CODE"
            fi
        fi
        if grep -q "name='com.messages.app'" <<<"$pkg_line"; then
            pass "applicationId is com.messages.app"
        else
            fail "unexpected applicationId"
        fi
    fi
fi
echo

# ---- 3. contents ----------------------------------------------------------

echo "Contents"
entries=$(unzip -Z1 "$APK" 2>/dev/null || true)
if [[ -z "$entries" ]]; then
    fail "could not list APK entries"
else
    if grep -qE '^classes\.dex$' <<<"$entries"; then
        pass "contains compiled dex"
    else
        fail "no classes.dex — this is not a complete APK"
    fi
    # The bundled pattern library is what classification falls back on; an APK
    # that shrank it away would silently classify nothing.
    if grep -qE 'patterns\.json$' <<<"$entries"; then
        pass "bundled pattern library is present"
    else
        fail "patterns.json is missing from the APK"
    fi
    # Nothing from the signing setup should ever be packaged.
    if grep -qiE '(keystore\.properties|\.jks$|local\.properties)' <<<"$entries"; then
        fail "signing or local configuration is packaged inside the APK"
    else
        pass "no signing/local configuration packaged"
    fi
fi
echo

echo "SHA-256 of artifact:"
if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$APK" | sed 's/^/  /'
else
    sha256sum "$APK" | sed 's/^/  /'
fi
echo

if (( failures > 0 )); then
    echo "FAILED — $failures check(s) did not pass." >&2
    exit 1
fi
echo "All checks passed."
