# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Testing
```bash
# Build the project
./gradlew build

# Run unit tests
./gradlew test

# Run instrumentation tests 
./gradlew connectedAndroidTest

# Run tests with coverage
./gradlew test -Pcoverage

# Run a single test class
./gradlew test --tests "ClassName"

# Run a single test method
./gradlew test --tests "ClassName.testMethodName"
```

### Code Quality
```bash
# Run detekt static analysis
./gradlew detekt

# Run ktlint formatting check
./gradlew ktlintCheck

# Auto-format code with ktlint
./gradlew ktlintFormat

# Run kover test coverage
./gradlew koverHtmlReport
```

## Architecture Overview

This is an Android messenger application built with Kotlin and Jetpack Compose, following Clean Architecture principles with a domain-driven design approach.

### Domain Layer Structure
- **Entities**: Core business objects (`Chat`, `Message`, `Participant`) with validation logic
- **Use Cases**: Business logic operations for chat and message management
- **Repository Interface**: Data access abstraction
- **Validation**: Dedicated validators for domain entities with error types

### Key Architectural Patterns
- **Result Pattern**: `ResultWithError<T, E>` wrapper for handling success/failure states
- **Use Case Pattern**: Each business operation is encapsulated in a dedicated use case class
- **Validation Pattern**: Separate validator classes with specific error types
- **Immutable Collections**: Uses `kotlinx-collections-immutable` for thread-safe data structures

### Testing Strategy
- **Fakes over Mocks**: Uses fake implementations (`RepositoryFake`) instead of mocking frameworks
- **Builder Pattern**: Test builders for domain entities (`ChatBuilder`, `MessageBuilder`)
- **Integration Tests**: Tests cover use case interactions with repository layer
- **Turbine**: For testing Kotlin Flow emissions

### Code Quality Tools
- **Detekt**: Static analysis with custom rules in `config/detekt/detekt.yml`
- **Ktlint**: Code formatting with Compose rules
- **Kover**: Test coverage reporting
- **Custom Rules**: Compose-specific detekt and ktlint rules enabled

### Package Organization
```
domain/
├── entity/           # Core business entities with validation
│   ├── chat/        # Chat-related entities and validation
│   └── message/     # Message-related entities and validation
└── usecase/         # Business logic operations
    ├── chat/        # Chat management use cases
    └── message/     # Message management use cases
```

### Annotation Processing
- **KAPT**: Currently using KAPT for Hilt dependency injection (Hilt 2.56.2)
- **KSP Migration**: Multiple KSP migration attempts failed due to compatibility issues
  - Hilt 2.51.1: KSP generates invalid factory class names for Hilt modules
  - Hilt 2.56.2: Same issue persists - method names like `chatId-aANd5Fw` are invalid Java identifiers
  - Root cause: Known bug in KSP+Hilt integration where generated method names contain invalid characters
  - Decision: Stay with KAPT until KSP+Hilt compatibility is fully resolved