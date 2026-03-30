# Development Commands

## Building and Testing
```bash
# Build the project
./gradlew build

# Run unit tests across all modules (JVM + Android library + app)
./gradlew testAllMockDebugUnitTests
./gradlew testAllProductionReleaseUnitTests

# Run unit tests for specific flavors (app module only)
./gradlew testDevDebugUnitTest
./gradlew testStagingDebugUnitTest
./gradlew testProductionDebugUnitTest

# Run unit tests for a single JVM module
./gradlew :core:domain:test

# Run instrumentation tests
./gradlew connectedDebugAndroidTest                        # all modules androidTest 
./gradlew connectedMockDebugAndroidTest                    # :app androidTest
./gradlew :feature:auth:connectedDebugAndroidTest          # :feature:auth androidTest

# Run tests with coverage
./gradlew testAllMockDebugUnitTests -Pcoverage

# Run a single test class
./gradlew testMockDebugUnitTest --tests "ClassName"
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ClassName

# Run a single test method
./gradlew testMockDebugUnitTest --tests "ClassName.testMethodName"

# Run tests by category (property-based approach)
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Architecture
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Unit
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Component
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Feature
./gradlew connectedDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.FeatureTest
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.ApplicationTest

# Exclude specific categories
./gradlew testAllMockDebugUnitTests -PexcludeCategory=timur.gilfanov.messenger.annotations.Architecture

# Pre-commit checks
./gradlew preCommit                   # Run all pre-commit checks (checks staged files)
                                      # IMPORTANT: Run this before committing when changes are ready for compilation and testing
./gradlew preCommit -Pforce           # Run all checks unconditionally
```

## Code Quality
```bash
# Auto-format code with ktlint and use detekt to static analysis with autocorrection
./gradlew ktlintFormat detekt --auto-correct

# Run kover test coverage
./gradlew koverXmlReportMockDebug
```

### Code Quality Tools
- **Detekt**: Static analysis with custom rules in `config/detekt/detekt.yml`
- **Ktlint**: Code formatting with Compose rules
- **Kover**: Test coverage reporting
- **Custom Rules**: Compose-specific detekt and ktlint rules enabled

## Test Categories with Coverage
```bash
# Run specific test categories with coverage
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Unit -Pcoverage
./gradlew testAllMockDebugUnitTests -PtestCategory=timur.gilfanov.messenger.annotations.Component -Pcoverage

# Generate category-specific coverage reports
./gradlew koverXmlReportMockDebug && ./gradlew generateCategorySpecificReports -PtestCategory=timur.gilfanov.messenger.annotations.Unit
```
