# Implement Profile Data Layer

## Overview

Profile data is currently represented by an intentional stub in `ObserveProfileUseCaseImpl` because the profile data layer had not been implemented. This plan implements the full profile data layer for profile retrieval — local Room cache backed by a remote Ktor source — and wires it through the existing domain and use case contracts. Profile edit operations (`updateName`, `updatePicture`, `removePicture`) are explicitly out of scope for this vertical slice and return a descriptive error.

User-scoped local data cleanup on logout is included: a `UserScopedDataCleaner` domain interface lets the auth cleanup observer delete profile rows when a user signs out, preventing data leakage between accounts.

## Context

- Affected feature module: `:feature:profile` (data + domain + UI layers)
- Affected core modules: `:core:domain` (error types, `UserScopedDataCleaner`), `:app` (database, DI)
- Affected feature: `:feature:auth` (`AuthCleanupObserver` integration)
- Adopted from GitHub issue #336 — [Feature] Implement profile data layer
- Milestone: Phase 2: Foundation Fixes

## Development Approach

- Testing approach: regular
- Complete each task fully before moving to the next
- Update this plan when scope changes during implementation

## Testing Strategy

- Unit tests required for every code-changing task
- Run project tests after each task before proceeding

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Update plan if implementation deviates from original scope

## Implementation Steps

### Task 1: Update domain layer contracts

- [x] Expand `ProfileRepository` interface with `observeCurrentProfile(userKey: UserScopeKey)` returning `Flow<ResultWithError<Profile, ObserveProfileRepositoryError>>`
- [x] Update `ObserveProfileRepositoryError` sealed interface with `LocalOperationFailed` and `RemoteOperationFailed` cases
- [x] Update `ObserveProfileError` sealed interface to mirror repository error structure
- [x] Expand `LocalProfileDataSource` interface with `observeProfile`, `upsertProfile`, and `deleteProfile` operations
- [x] Expand `RemoteProfileDataSource` interface with `getCurrentProfile` operation
- [x] Add `UserScopedDataCleaner` fun interface to `:core:domain` with `deleteUserData(userKey)` contract
- [x] run project tests - must pass before next task

### Task 2: Implement local profile data source

- [x] Create `ProfileEntity` Room entity for profile persistence
- [x] Create `ProfileEntityMapper` to convert between `Profile` domain model and `ProfileEntity`
- [x] Create `ProfileDao` with observe (Flow), upsert, and delete-by-key operations
- [x] Update `MessengerDatabase` to include `ProfileDao` and bump schema version
- [x] Add Room migration and generate `MessengerDatabase/6.json` schema snapshot
- [x] Update `DatabaseModule` to provide `ProfileDao`
- [x] Implement `LocalProfileDataSourceImpl`
- [x] Write tests for `LocalProfileDataSourceImpl`
- [x] run project tests - must pass before next task

### Task 3: Implement remote profile data source

- [x] Create `ProfileDto` for Ktor response deserialization
- [x] Define `ProfileApiRoutes` route constants
- [x] Implement `RemoteProfileDataSourceImpl` using Ktor `HttpClient`
- [x] Write tests for `RemoteProfileDataSourceImpl`
- [x] run project tests - must pass before next task

### Task 4: Implement profile repository

- [x] Implement `ProfileRepositoryImpl` with cache-then-remote strategy: emit cached profile immediately if available, fetch from remote, upsert to cache, then stream subsequent local updates
- [x] Implement `UserScopedDataCleaner` in `ProfileRepositoryImpl` (delegates to `localDataSource.deleteProfile`)
- [x] Stub edit operations (`updateName`, `updatePicture`, `removePicture`) with explicit out-of-scope errors
- [x] Map `ProfileLocalDataSourceError` and `RemoteProfileDataSourceError` to `ObserveProfileRepositoryError`
- [x] Write tests for `ProfileRepositoryImpl`
- [x] run project tests - must pass before next task

### Task 5: Replace use case stub and wire DI

- [x] Replace hardcoded profile stub in `ObserveProfileUseCaseImpl` with `profileRepository.observeCurrentProfile(userScopeKey)` wired through `AuthState`
- [x] Map `ObserveProfileRepositoryError` to `ObserveProfileError` in use case
- [x] Update `ProfileModule` to bind `ProfileRepositoryImpl` as `ProfileRepository` and `UserScopedDataCleaner`
- [x] Write tests for `ObserveProfileUseCaseImpl`
- [x] run project tests - must pass before next task

### Task 6: User-scoped data cleanup on logout

- [x] Implement `SettingsUserScopedDataCleaner` in `:app` (aggregates all `UserScopedDataCleaner` implementations for settings/preferences data)
- [x] Create `UserScopedDataCleanerModule` to provide `Set<UserScopedDataCleaner>` via multibinding
- [x] Update `AuthCleanupObserver` / `AuthCleanupObserverImpl` to call all registered `UserScopedDataCleaner` instances on logout
- [x] Write tests for updated `AuthCleanupObserver`
- [x] run project tests - must pass before next task

### Task 7: Verify acceptance criteria

- [x] Verify profile screen displays real profile data from the backend (no longer hardcoded stub) [x] manual test (skipped - not automatable)
- [x] Verify profile data is cleared from local cache when the user signs out [x] manual test (skipped - not automatable)
- [x] run full project test suite
- [x] run project linter — all issues must be fixed

## Post-Completion

- Profile edit operations (`updateName`, `updatePicture`, `removePicture`) return an explicit out-of-scope error; they must be implemented in the profile edit vertical slice (separate issue)
- Verify Room migration is included in production migration path before release
