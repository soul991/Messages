# bin/ — parked material

Files here are not part of the Messages app build. They were moved out of the
project root during the 2026-07-18 cleanup so the root only contains the app
modules and their docs.

- `DetectiveDialer-main/` — a separate, older project (dialer app with its own
  Android/backend trees and audit docs). It is unrelated to the Messages
  Gradle build (`settings.gradle.kts` never included it). Kept for reference;
  delete this folder whenever you no longer need it.
- `.DS_Store` — macOS Finder metadata (already gitignored).
