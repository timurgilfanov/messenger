# Development Commands

## Building and Testing
```bash
# Build the project
./gradlew build

# Run unit tests (default: mock flavor)
./gradlew testMockDebugUnitTest

# Run unit tests for specific flavors
./gradlew testDevDebugUnitTest     # Development environment
./gradlew testStagingDebugUnitTest # Staging environment
./gradlew testProductionDebugUnitTest # Production environment

# Run instrumentation tests
./gradlew connectedMockDebugAndroidTest

# Run tests with coverage
./gradlew testMockDebugUnitTest -Pcoverage

# Run a single test class
./gradlew testMockDebugUnitTest --tests "ClassName"
./gradlew connectedMockDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ClassName

# Run a single test method
./gradlew testMockDebugUnitTest --tests "ClassName.testMethodName"

# Run tests by category (property-based approach)
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Architecture
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Unit
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Component
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Feature
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.FeatureTest
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.ApplicationTest

# Pre-commit checks
./gradlew preCommit                   # Run all pre-commit checks (checks staged files)
                                      # IMPORTANT: Run this before committing when changes are ready for compilation and testing
./gradlew preCommit -Pforce           # Run all checks unconditionally (CI / agent)

# Exclude specific categories
./gradlew testMockDebugUnitTest -PexcludeCategory=timur.gilfanov.messenger.annotations.Architecture
```

## Code Quality
- Do not generate comments
- Run code quality checks and auto corrections after code editing
```bash
# Auto-format code with ktlint and use detekt to static analysis with autocorrection
./gradlew ktlintFormat detekt --auto-correct

# Run kover test coverage
./gradlew koverXmlReportMockDebug
```

## Test Categories with Coverage
```bash
# Run specific test categories with coverage
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Unit -Pcoverage
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Component -Pcoverage

# Generate category-specific coverage reports
./gradlew koverXmlReportMockDebug && ./gradlew generateCategorySpecificReports -PtestCategory=timur.gilfanov.messenger.annotations.Unit
```

## Codecov Components
Components are configured in `codecov.yml` and automatically organize coverage by code paths:
```bash
# Components are path-based and work automatically with any coverage upload
# No special commands needed - just run tests with coverage and upload to Codecov
# View component coverage in Codecov dashboard under "Components" tab
```
