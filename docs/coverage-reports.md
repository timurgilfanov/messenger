# Coverage Reports

The CI/CD pipeline provides **two-dimensional coverage analysis** using both Codecov Components and test-specific flags for comprehensive insights.

## Test-Specific Flags (What was tested)

### Test Category Coverage (Local Tests)
- `architecture,local` - Coverage from Architecture tests only
- `unit,local` - Coverage from Unit tests only
- `component,robolectric` - Coverage from Component tests only
- `feature,local` - Coverage from Feature tests only
- `application,local` - Coverage from Application unit tests only

### Device-Specific Coverage (Firebase Test Lab)
- `application,emulator,phone` - Coverage from Application tests on phone emulators only
- `application,device,phone` - Coverage from Application tests on phone devices only
- `application,device,foldable` - Coverage from Application tests on foldable devices only
- `release_candidate,device,phone` - Coverage from Release Candidate tests on phone devices only
- `release_candidate,device,tablet` - Coverage from Release Candidate tests on tablet devices only
- `release_candidate,device,foldable` - Coverage from Release Candidate tests on foldable devices only

## Codecov Components (Which code was tested)

### Domain Layer Components
- `domain_entities` - Domain entities shared across the architecture
- `domain_usecases` - Use case contracts and implementations across core and feature modules
- `validation_logic` - Validation code across domain and feature modules

### Feature-Based Components
- `chat_feature` - Chat-related entities, use cases, and UI
- `message_feature` - Message-related entities and use cases
- `auth_feature` - Auth feature code across data, domain, and UI layers

### UI Layer Components
- `ui_screens` - Screens and ViewModels across app and feature modules
- `ui_theme` - Shared theme and styling code in `:core:ui`

### Architecture Components
- `data_layer` - Cross-module repositories, data sources, DTOs, and transport models
- `application_core` - MainActivity and Application class

## Two-Dimensional Analysis

Combine Components + Flags for insights like:
- **"Chat Feature Unit Test Coverage"**: `chat_feature` component + `unit,local` flag
- **"Domain Entities Device Coverage"**: `domain_entities` component + `application,device,phone` flag
- **"UI Screens Component Test Coverage"**: `ui_screens` component + `component,robolectric` flag
- **"Validation Logic Architecture Coverage"**: `validation_logic` component + `architecture,local` flag

## Coverage Precision

Each coverage report contains **only** the coverage data from:
- **Specific test category** that was executed (Unit, Component, Architecture, Feature, Application, Release Candidate)
- **Specific environment** where tests ran (local, robolectric, emulator, device)
- **Specific device type** that was used (phone, tablet, foldable)
- **Specific code components** as defined in `codecov.yml` (automatically filtered by path)

This ensures accurate coverage tracking without cross-contamination and provides rich insights for both testing strategy and code organization decisions.
