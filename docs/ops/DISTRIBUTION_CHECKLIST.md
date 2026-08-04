# Distribution Checklist

**R-32 compliance:** This checklist ensures that source archives and releases
don't leak workstation metadata, contain unnecessary build artifacts, or bundle
large design references that aren't needed for compilation.

## Before creating a source archive or public clone

### 1. Clean build artifacts

```sh
./gradlew clean
rm -rf .gradle/
rm -rf .kotlin/
```

Verify no `build/` directories remain:

```sh
find . -type d -name build -not -path "./.git/*"
```

Should return nothing. If any exist, remove them.

### 2. Verify gitignored paths

The following should never appear in `git status` or a source archive:

- `build/` (all modules)
- `local.properties`
- `keystore.properties`
- `google-services.json`
- `design-refs/` (large third-party reference material, ~180MB)
- `.gradle/`, `.kotlin/`, `.idea/`
- Any `.apk`, `.aab`, `.keystore`, or `.jks` files

If any of these are tracked, they were added by mistake. Remove them:

```sh
git rm --cached path/to/file
```

### 3. Use a clean checkout for distribution

**Never distribute from your working directory.** Always create a fresh clone
(run from the repository root, so no workstation path is written down here —
this is a document about not leaking workstation metadata):

```sh
git clone . /tmp/messages-dist
cd /tmp/messages-dist
git checkout <release-tag-or-commit>
```

This guarantees:
- No untracked files (test outputs, editor temp files, local notes)
- No workstation paths in generated reports
- No `.git/` history bloat from development iterations

### 4. Verify the archive

Before sharing:

```sh
tar -czf messages-v1.0-src.tar.gz messages-dist/
tar -tzf messages-v1.0-src.tar.gz | grep -E '(build/|local\.properties|keystore|google-services|design-refs/)'
```

Should return nothing. If it does, the archive is leaking excluded material.

## Design reference material

`design-refs/` contains community UI references (~180MB: WhatsApp/Telegram
screenshots, PDFs, SVGs) used during visual design. It is excluded via
`.gitignore` because:

1. **Licensing:** Third-party screenshots and community material may not be
   freely redistributable.
2. **Size:** 180MB of PNG/SVG/PDF bloat in every clone is unnecessary — the
   design is already implemented in code.
3. **Privacy:** Original files may contain user names, timestamps, or paths.

The current `.gitignore` entry:

```
# Local design reference material (user-supplied, ~180MB including the
# original community zips under design-refs/archives/) — never committed
design-refs/
/*.zip
```

If you need to share design context with a new contributor, provide the
implemented design system (`:design-system` module) and the live app, not the
raw reference zips.

## Release artifact distribution

For APK/AAB releases:

1. Build from a clean checkout (see above), with signing intent explicit:

   ```sh
   ./gradlew :app:assembleRelease -PrequireSigning=true
   ```

   `-PrequireSigning=true` (R-28) makes the build **fail** if the keystore is
   missing, instead of quietly emitting an unsigned APK that looks shippable.

2. Verify the artifact:

   ```sh
   ./scripts/verify-release-artifact.sh app/build/outputs/apk/release/app-release.apk
   ```

   This replaces the manual inspection that used to live here. It checks the
   signature scheme (v2/v3), that the certificate matches the identity recorded
   in [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md), that the release is not
   debuggable, that `android:allowBackup="false"` and `dataExtractionRules` are
   in the merged manifest, that no debug-only components or the debug-harness
   permission shipped, that the bundled pattern library is present, and that no
   signing or local configuration was packaged. It exits non-zero on any
   failure, and prints the artifact SHA-256.

3. Run full lint: `./gradlew :app:lintRelease`
4. Record the artifact SHA-256 (printed in step 2) in the release notes.

CI runs the same script on every build — unsigned on branches and pull requests,
and with full signature verification on `v*` tags. See
[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).

## Git repository maintenance

**Do not perform these without explicit authorization:**

R-32 notes that `git fsck` reports an invalid remote HEAD ref and unreachable
objects. These are **not** fixed automatically because:

1. **R-23 is still open:** Sensitive data (personal info, credentials, config)
   remains in Git history. Fixing unreachable objects before rewriting history
   would bake that data into a permanent, distributed state.
2. **History rewrites are destructive:** They invalidate all existing clones,
   forks, and commit references. Coordinate before running `git filter-repo`
   or `git gc --prune=now`.

Once R-23 is complete, clean up with:

```sh
# After coordinated history rewrite for R-23:
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git fsck --full
```

## Summary

- **Distribute from a clean checkout, never the working tree.**
- **Keep `build/`, `local.properties`, `keystore.properties`, `google-services.json`, and `design-refs/` out of source archives.**
- **Do not run `git gc` or fix unreachable objects until R-23 (sensitive history rewrite) is complete.**
