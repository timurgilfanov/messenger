## 🖼️ Screenshot Testing

- Tooling: Roborazzi + Robolectric for fast, deterministic Compose screenshots.
- Goldens: `app/src/test/snapshots/**` (committed). Diffs/actuals: `app/build/roborazzi/**`.

Local commands
- Verify screenshots: `./gradlew verifyRoborazziMockDebug`
- Record/update baselines: `./gradlew recordRoborazziMockDebug`

CI workflows
- PR verification: job `screenshot-tests` calls reusable workflow `.github/workflows/screenshots-verify.yml` (verify only) and uploads artifacts named `roborazzi-screenshots` on diffs.
- Update baselines: apply the `update-screenshots` label to your PR to auto-record and commit goldens to the PR branch (same-repo PRs only).

Stability
- Locale/Timezone fixed via `LocaleTimeZoneRule` (US/UTC). Device config via Robolectric `@Config(sdk = [33], qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")`. Prefer recording on Linux for parity with CI.
