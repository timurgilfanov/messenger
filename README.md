# Messenger
[![codecov](https://codecov.io/gh/timurgilfanov/messenger/graph/badge.svg?token=MF0478WVBI)](https://codecov.io/gh/timurgilfanov/messenger)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-purple.svg)
![Compose BOM](https://img.shields.io/badge/Compose_BOM-2025.07.00-blue.svg)
![Min SDK](https://img.shields.io/badge/MinSDK-24-green.svg)
![Target SDK](https://img.shields.io/badge/TargetSDK-35-green.svg)

An Android messenger application showcasing Clean Architecture, comprehensive testing strategy, and modern Android development practices using Kotlin, Jetpack Compose, and Orbit MVI.

## 🏗️ Architecture

This application follows **Clean Architecture** principles with clear separation of concerns and unidirectional data flow using **Orbit MVI**.

```mermaid
graph TB
    subgraph "Presentation Layer"
        UI[UI<br/>Jetpack Compose]
        VM[ViewModels<br/>Orbit MVI]
        UIState[UI State]
    end
    
    subgraph "Domain Layer"
        UC[Use Cases]
        E[Entities]
        V[Validators]
        RI[Repository Interfaces]
    end
    
    subgraph "Data Layer"
        REPO[Repository Implementations]
        subgraph "Local Data"
            ROOM[Room Database]
            DAO[DAOs]
            LDS[Local Data Sources]
        end
        subgraph "Remote Data"
            KTOR[Ktor Client]
            RDS[Remote Data Sources]
            DTO[DTOs & Mappers]
        end
    end
    
    UI --> VM
    VM --> UIState
    UIState --> UI
    VM --> UC
    UC --> RI
    RI -.-> REPO
    UC --> E
    UC --> V
    REPO --> LDS
    REPO --> RDS
    LDS --> DAO
    DAO --> ROOM
    RDS --> KTOR
    RDS --> DTO
    
    style UI fill:#e1f5fe
    style VM fill:#e1f5fe
    style UIState fill:#e1f5fe
    style UC fill:#fff3e0
    style E fill:#fff3e0
    style V fill:#fff3e0
    style RI fill:#fff3e0
    style REPO fill:#f3e5f5
    style ROOM fill:#f3e5f5
    style DAO fill:#f3e5f5
    style LDS fill:#f3e5f5
    style KTOR fill:#f3e5f5
    style RDS fill:#f3e5f5
    style DTO fill:#f3e5f5
```

### Data Flow

```mermaid
sequenceDiagram
    participant UI as UI (Compose)
    participant VM as ViewModel
    participant UC as Use Case
    participant R as Repository
    participant L as Local Source
    participant RE as Remote Source
    
    UI->>VM: User Action
    VM->>UC: Execute Use Case
    UC->>R: Request Data
    
    alt Cache Available
        R->>L: Get from Cache
        L-->>R: Cached Data
    else Cache Miss
        R->>RE: Fetch from API
        RE-->>R: Remote Data
        R->>L: Update Cache
    end
    
    R-->>UC: Domain Entity
    UC-->>VM: Result<T, Error>
    VM->>VM: Update State
    VM-->>UI: UI State
```

### Key Architectural Decisions

- **Clean Architecture**: Ensures testability, maintainability, and scalability
- **Orbit MVI**: Provides predictable state management with clear side effects handling
- **Domain-Driven Design**: Rich domain models with business logic encapsulation
- **Repository Pattern**: Abstracts data sources and provides single source of truth
- **Result Pattern**: Type-safe error handling without exceptions
- **Dependency Injection**: Using Hilt for compile-time safety and testability

## 🚀 Quick Start

```bash
# Run all pre-commit checks (formatting, linting, architecture tests, unit & component tests)
./gradlew preCommit

# Run specific test categories
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.annotations.Unit
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.annotations.FeatureTest 

# Generate coverage report
./gradlew koverXmlReportMockDebug
```

## 📱 Features

- Offline-first architecture
- Delta synchronization for efficient data updates
- Comprehensive error handling with typed errors

## 🛠️ Tech Stack

- **Language**: Kotlin 2.2.0
- **UI**: Jetpack Compose (BOM 2025.07.00)
- **Architecture**: Clean Architecture + Orbit MVI
- **DI**: Hilt 2.57
- **Database**: Room 2.7.2
- **Networking**: Ktor 3.2.3
- **Testing**: JUnit4, Turbine, Robolectric
- **Code Quality**: Detekt, Ktlint, Konsist

## 📊 Testing Strategy

Comprehensive testing pyramid with 5 test categories. See [Testing Strategy](https://github.com/timurgilfanov/messenger/blob/main/docs/Testing%20Strategy.md) for details.

| Category     | Coverage Target   | Execution            |
|--------------|-------------------|----------------------|
| Architecture | Rules enforcement | Every commit         |
| Unit         | 80%+              | Every commit         |
| Component    | 70%+              | Every commit         |
| Feature      | 50%+              | Pre-merge            |
| Application  | 40%+              | Pre-merge/Post-merge |
