## 🖼️ Screenshot Testing

- Tooling: Roborazzi + Robolectric for fast, deterministic Compose screenshots.
- Goldens: `app/src/test/screenshots/**` (committed, 50MB limit). Diffs/actuals: `app/build/outputs/roborazzi/**`.

Local commands
- Verify screenshots: `./gradlew verifyRoborazziMockDebug`
- Record/update baselines: `./gradlew recordRoborazziMockDebug`
- Check directory size: `./gradlew checkScreenshotSize`

CI workflows
- **PR verification**: job `screenshot-tests` calls reusable workflow `.github/workflows/screenshots-verify.yml` which verifies screenshots and automatically posts PR comments with visual diff details when changes are detected.
- **Update baselines**: apply the `update-screenshots` label to your PR to auto-record and commit goldens to the PR branch (same-repo PRs only).

Size Management
- Screenshots directory has a 50MB limit enforced by `./gradlew preCommit`
- If limit exceeded, consider removing old screenshots or optimizing images
- Directory size is checked and reported during pre-commit validation

Stability
- Locale/Timezone fixed via `LocaleTimeZoneRule` (US/UTC). Device config via Robolectric `@Config(sdk = [33], qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")`. Prefer recording on Linux for parity with CI.
