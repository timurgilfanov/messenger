# Package Organization

```
app/src/main/java/timur/gilfanov/messenger/
├── domain/
│   ├── entity/           # Core business entities with validation
│   │   ├── chat/        # Chat-related entities and validation
│   │   ├── message/     # Message-related entities and validation
│   │   ├── profile/     # Profile entity and validation
│   │   └── settings/    # Settings entity
│   └── usecase/         # Business logic operations
│       ├── chat/        # Chat management use cases
│       ├── message/     # Message management use cases
│       ├── profile/     # Profile use cases
│       ├── settings/    # Settings use cases
│       └── common/      # Shared use cases
├── data/
│   ├── repository/      # Repository implementations
│   ├── source/          # Data sources
│   │   ├── local/       # Room database
│   │   ├── remote/      # API calls
│   │   └── paging/      # Paging data sources
│   └── worker/          # WorkManager workers
├── ui/
│   ├── screen/          # Compose screens and ViewModels
│   ├── theme/           # Theme and styling
│   └── activity/        # Activity classes
├── di/                  # Hilt dependency injection modules
└── navigation/          # Navigation destinations
```
