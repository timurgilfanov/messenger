# Debug Data Population Setup

This document describes how to use the debug data population feature for development and testing.

## Overview

The debug data population system automatically generates realistic test data in debug builds, making it easy to develop and test the application without manually creating data. The system supports multiple data scenarios and can be configured at build time or runtime.

## Features

- **Automatic Data Generation**: Sample data is populated on app launch in debug builds
- **Multiple Scenarios**: Choose from different data sets (minimal, standard, heavy, demo, etc.)
- **Build-time Configuration**: Set scenario via Gradle properties
- **Runtime Override**: Change scenario via Android Studio or command line
- **Debug UI**: Settings screen accessible via persistent notification
- **Auto-activity**: Optional simulation of new messages for realistic testing
- **DataStore Integration**: Settings persist between app launches
- **Clean Separation**: All debug code in debug source set, zero impact on release builds

## Data Scenarios

| Scenario   | Chats | Messages per Chat | Description                               |
|------------|-------|-------------------|-------------------------------------------|
| EMPTY      | 0     | 0                 | Empty state testing                       |
| MINIMAL    | 5     | 5-10              | Minimal data for quick testing            |
| STANDARD   | 100   | 20-50             | Standard development data                 |
| HEAVY      | 1000  | 1-2000            | Heavy data for performance testing        |
| EDGE_CASES | 10    | 1-100             | Edge cases with Unicode and long messages |
| DEMO       | 8     | 10-30             | Polished data for demonstrations          |

## Usage

### 1. Android Studio Run Configurations

Pre-configured run configurations are available:

- **Mock Debug - Minimal Data**: Quick testing with minimal data
- **Mock Debug - Standard Data**: Default development data
- **Mock Debug - Heavy Data**: Performance testing
- **Mock Debug - Demo Data**: Polished for demos
- **Mock Debug - Empty Data**: Empty state testing

Select the desired configuration from the run configurations dropdown and click Run.

### 2. Gradle Tasks

Convenient Gradle tasks for command-line usage:

```bash
# Run with different scenarios
./gradlew runWithMinimalData
./gradlew runWithStandardData
./gradlew runWithHeavyData
./gradlew runWithDemoData
./gradlew runWithEmptyData
./gradlew runWithEdgeCases
```

### 3. Build-time Configuration

Set scenario at build time using Gradle properties:

```bash
# Build APK with specific scenario
./gradlew assembleMockDebug -PdataScenario=HEAVY

# Install with specific scenario
./gradlew installMockDebug -PdataScenario=DEMO
```

### 4. Manual ADB Commands

Override scenario for already installed app:

```bash
# Launch with specific scenario
adb shell am start -n timur.gilfanov.messenger/.MainActivity \\
    --es debug_data_scenario MINIMAL

# Clear app data and launch with scenario
adb shell pm clear timur.gilfanov.messenger
adb shell am start -n timur.gilfanov.messenger/.MainActivity \\
    --es debug_data_scenario DEMO
```

### 5. Debug Settings UI

1. Launch the app in debug mode
2. Tap the persistent debug notification
3. Use the debug settings screen to:
   - Switch between scenarios
   - Regenerate data
   - Clear all data
   - Toggle auto-activity simulation
   - Configure debug preferences

## Configuration Priority

The system uses the following priority order:

1. **Intent Extra** (highest) - From Android Studio or ADB
2. **BuildConfig** - Set via Gradle property at build time
3. **Saved Preference** - Stored in DataStore from previous sessions
4. **Default** (lowest) - STANDARD scenario

## Auto-Activity Simulation

When enabled, the system periodically generates new messages to simulate active conversations:

- Random intervals between 5-15 seconds
- Messages appear from other participants
- Realistic message content based on chat type
- Can be toggled from debug settings UI

## Development Workflow

### Quick Setup
```bash
# Standard development setup
./gradlew runWithStandardData
```

### Performance Testing
```bash
# Test with heavy data
./gradlew runWithHeavyData
```

### Demo Preparation
```bash
# Build demo APK
./gradlew assembleMockDebug -PdataScenario=DEMO
```

### Empty State Testing
```bash
# Test empty state
./gradlew runWithEmptyData
```

## Sync and Timestamp Strategy

The debug data system uses a hybrid timestamp strategy to ensure generated data appears properly in the app:

### The Problem
- **Real-world timestamps**: Production app stores sync timestamps from actual usage (e.g., 2025 timestamps)
- **Test timestamps**: Generated debug data traditionally used epoch-based timestamps (1970) for reproducibility
- **Sync rejection**: The sync system would see debug data as "old" and ignore it

### The Solution: Hybrid Timestamps
1. **Read current sync timestamp** before generating data
2. **Initialize server timestamp** to be newer than the last sync point
3. **Generate deterministic data** with timestamps that advance from that point
4. **Ensure sync compatibility** while maintaining test reproducibility

### Race Condition Fixes
The system also includes atomic state management to prevent race conditions:
- **Unified ServerState**: Single source of truth for server state
- **Atomic updates**: Thread-safe operations using MutableStateFlow
- **Concurrent safety**: Prevents ConcurrentModificationException during data generation

## Implementation Details

### Architecture

```
app/src/debug/java/timur/gilfanov/messenger/debug/
├── DataScenario.kt              # Scenario definitions
├── DataGenerationConfig.kt      # Configuration objects
├── SampleDataProvider.kt        # Data generation logic  
├── DebugDataRepository.kt       # Data management with DataStore
├── DebugMessengerApplication.kt # Debug application class
├── DebugNotificationService.kt  # Persistent notification
├── datastore/
│   └── DebugPreferences.kt      # DataStore keys
├── di/
│   └── DebugDataStoreModule.kt  # Dependency injection
└── ui/
    ├── DebugSettingsActivity.kt # Settings UI
    └── DebugSettingsViewModel.kt # Settings logic

app/src/debug/java/timur/gilfanov/messenger/data/source/
├── local/
│   ├── LocalDebugDataSource.kt     # Interface for local debug operations
│   └── LocalDebugDataSourceImpl.kt # Database cleanup implementation
└── remote/
    ├── RemoteDebugDataSource.kt    # Interface for server debug operations
    └── RemoteDataSourceFake.kt     # Updated with hybrid timestamps
```

### Key Components

- **DataScenario**: Enum defining available scenarios
- **SampleDataProvider**: Generates realistic chats and messages
- **DebugDataRepository**: Manages data lifecycle with DataStore persistence
- **DebugMessengerApplication**: Handles scenario detection and initialization
- **DebugNotificationService**: Provides persistent access to debug controls
- **LocalDebugDataSource**: Interface for clearing local database data
- **RemoteDebugDataSource**: Interface for server state management with timestamp control

### Build Integration

- **Gradle Properties**: Support for build-time scenario configuration
- **BuildConfig Fields**: Compile-time scenario embedding
- **Gradle Tasks**: Convenient command-line access
- **Android Studio**: Pre-configured run configurations

### Technical Implementation

#### Hybrid Timestamp Strategy
```kotlin
// 1. Read current sync timestamp before generating data
val lastSyncTimestamp = localDataSources.sync.getLastSyncTimestamp()

// 2. Initialize server with timestamp newer than sync point
lastSyncTimestamp?.let { timestamp ->
    remoteDebugDataSource.setInitialTimestamp(timestamp)
}

// 3. Clear and generate data (now with proper timestamps)
remoteDebugDataSource.clearServerData()
// Generated data automatically uses timestamps newer than sync points
```

#### Atomic State Management
```kotlin
// Unified ServerState prevents race conditions
private data class ServerState(
    val chats: Map<ChatId, Chat> = emptyMap(),
    val operationTimestamps: Map<String, Instant> = emptyMap(),
    val currentTimestamp: Instant = Instant.fromEpochMilliseconds(0),
    val lastSyncTimestamp: Instant = Instant.fromEpochMilliseconds(0),
)

// Thread-safe updates
serverState.update { state ->
    state.recordOperation("addChat")
}
```

#### Test Determinism
- Tests use deterministic timestamps for reproducibility
- Production uses hybrid approach respecting real sync points
- Both approaches ensure timestamp progression to prevent sync issues

## Troubleshooting

### App Shows No Data
- Check you're using mock flavor: `mockDebug` variant
- Verify in logs: Look for "DebugMessengerApp" log messages
- Try regenerating data from debug notification
- Check for sync timestamp mismatches (see "Sync Issues" below)

### Sync Issues (Data Generated But Not Visible)
This typically happens when there's a timestamp mismatch between generated data and stored sync points:

**Symptoms:**
- Logs show "Generated X chats" but app displays empty state
- Data regeneration succeeds but no chats appear
- Sync timestamps show old dates (1970s) vs current dates (2020s)

**Solutions:**
```bash
# Clear app data to reset sync timestamps
adb shell pm clear timur.gilfanov.messenger

# Force regenerate with fresh sync state
adb shell am broadcast -a timur.gilfanov.messenger.DEBUG_ACTION --es action regenerate_data

# Check logs for timestamp information
adb logcat -s DebugDataRepository:D LocalSyncDataSource:D MessengerRepository:D
```

### Debug Notification Not Showing
- Check notification permissions (Android 13+)
- Verify debug build and mock flavor
- Look for notification permission errors in logs

### Scenario Not Applied
- Check priority order: Intent > BuildConfig > Saved > Default
- Verify scenario name spelling (case-sensitive)
- Clear app data to reset saved preferences

### Race Conditions During Testing
If you encounter `ConcurrentModificationException` or null pointer errors:
- This indicates race conditions in the fake data sources
- Run tests individually rather than in parallel
- Check that atomic state management is working correctly

### Build Errors
- Ensure debug source set structure is correct
- Check Hilt dependencies are properly configured
- Verify DataStore is included in dependencies

## Contributing

When adding new scenarios or features:

1. Update `DataScenario` enum
2. Add generation logic to `SampleDataProvider`
3. Create corresponding Gradle task if needed
4. Add Android Studio run configuration
5. Update this documentation

## Related Files

- `app/build.gradle.kts` - Build configuration and tasks
- `app/src/debug/AndroidManifest.xml` - Debug manifest configuration
- `.idea/runConfigurations/` - Android Studio run configurations
- `app/src/debug/java/` - All debug-specific code