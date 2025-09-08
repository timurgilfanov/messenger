# Repository Guidelines

## Project Structure & Module Organization
- Single Android app module: `app/`.
- Sources: `app/src/main/java/timur/gilfanov/messenger/...` (Compose UI in `ui/`, domain in `domain/`, data in `data/`).
- Variants: `mock`, `dev`, `staging`, `production` flavors; `debug`/`release` build types.
- Tests: unit/component/architecture in `app/src/test/...`; instrumentation/feature/application in `app/src/androidTest/...`.
- Debug fakes and test scaffolding in `app/src/debug/...`.
- Static analysis config in `config/`; CI in `.github/workflows/`.

## Build, Test, and Development Commands
- Format, lint, static analysis, and core tests: `./gradlew preCommit`.
- Lint only: `./gradlew lintMockDebug`; Detekt: `./gradlew detekt`; Ktlint fix: `./gradlew ktlintFormat`.
- Unit tests (by category): `./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Unit` (use `Architecture` or `Component` to switch).
- Instrumentation tests (by annotation): `./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.FeatureTest`.
- Coverage XML: `./gradlew koverXmlReportMockDebug` and category report: `./gradlew generateCategorySpecificReports -PtestCategory=... -PbuildFlavor=mock -PbuildType=debug`.
- Assemble APKs: `./gradlew assembleMockDebug`.

## Coding Style & Naming Conventions
- Kotlin style: official (`kotlin.code.style=official`), 4-space indent; follow Ktlint (Android Studio style) and Detekt (`config/detekt/detekt.yml`).
- Packages lower_snake; classes UpperCamel; functions lowerCamel. ViewModels end with `ViewModel`; use cases end with `UseCase`; errors end with `Error`.
- Compose: `@Composable` functions may use PascalCase (ktlint rule configured).

## Testing Guidelines
- Frameworks: JUnit4, Robolectric, Turbine, Konsist, Compose UI test.
- Categories/annotations: `timur.gilfanov.messenger.annotations.{Architecture,Unit,Component,Feature,Application}`.
- Coverage targets (guidance): Unit 80%+, Component 70%+, Feature 50%+, Application 40%+.
- Prefer test names: ``fun `does X when Y`()`` and mirror source package paths.

## Commit & Pull Request Guidelines
- Do not use type prefixes in commit messages (no `feat:`, `fix:`, etc.). Write a short, imperative summary (≤ 50 chars), optional body wrapped at 72 chars, and reference issues in the footer (e.g., "Refs #123"). Examples: "Add reply action to message bubble", "Prevent crash on empty paging source", "Rename SendMessageUseCase to SubmitMessageUseCase".
- PRs must include: clear description, linked issue, screenshots for UI changes, and notes on testing/coverage. Keep CI green (lint, detekt, tests).

## Security & Configuration
- Do not commit secrets. CI runs TruffleHog and uploads coverage to Codecov via repo secrets.
- Environment is set via flavors and `BuildConfig` (e.g., `API_BASE_URL`, `USE_REAL_REMOTE_DATA_SOURCES`). Avoid hardcoding secrets or changing production defaults without discussion.
