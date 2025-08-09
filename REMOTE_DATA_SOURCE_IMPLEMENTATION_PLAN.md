# Plan: Implement Remote Data Source with Ktor (Test-First Approach)

## Overview
Implement real remote data sources using Ktor client with immediate testing after each component. Since you don't have a server, we'll use Ktor's mock engine for testing throughout development.

## ✅ Phase 1: Foundation Setup (COMPLETED)
1. **✅ Add Ktor Dependencies** to `gradle/libs.versions.toml`:
   - `ktor-client-core`
   - `ktor-client-cio` (Android engine) 
   - `ktor-client-content-negotiation`
   - `ktor-serialization-kotlinx-json`
   - `ktor-client-logging`
   - `ktor-client-mock` (for testing without server)
   - Updated Ktor to version 3.2.3

2. **✅ Create Base Network DTOs** in `data/source/remote/dto/`:
   - Basic request/response wrappers (`ApiResponse<T>`)
   - Error response DTO with sealed class conversion
   - Type-safe `ApiErrorCode` sealed class implementation

3. **✅ Setup Ktor Client** in `data/source/remote/network/`:
   - Client configuration with JSON, logging, and retry policy
   - HttpRequestRetry with exponential backoff
   - Mock engine support for testing

## ✅ Phase 2: Implement & Test RemoteChatDataSource (COMPLETED)
1. **✅ Create Chat DTOs**:
   - `ChatDto.kt`, `ParticipantDto.kt` with full serialization support
   - `JoinChatRequestDto.kt`, `CreateChatRequestDto.kt`
   - Complete mappers between DTOs and domain entities

2. **✅ Implement `RemoteChatDataSourceImpl`**:
   - All CRUD methods: `createChat()`, `deleteChat()`, `joinChat()`, `leaveChat()`
   - Comprehensive error mapping to `RemoteDataSourceError`
   - Logger integration with existing abstractions
   - Robust exception handling with fallbacks

3. **✅ Write Tests for RemoteChatDataSource**:
   - Complete unit test suite with MockEngine
   - Test successful operations and error scenarios  
   - Verified DTO mapping and serialization
   - Used constants for reproducible timestamps

4. **✅ Advanced Error Handling**:
   - Type-safe sealed class error codes with custom serializer
   - Direct `ApiErrorCode` usage in `ErrorResponseDto`
   - Complex error data extraction (e.g., `CooldownActive` with duration)
   - Comprehensive error mapping between API and domain layers

## ✅ Phase 3: Implement & Test RemoteMessageDataSource (COMPLETED)
1. **✅ Create Message DTOs**:
   - Enhanced `MessageDto.kt` with full participant info, timestamps, and delivery status
   - `DeliveryStatusDto.kt` sealed class for status types
   - Request/response DTOs: `SendMessageRequestDto`, `EditMessageRequestDto`, `DeleteMessageRequestDto`
   - Complete mappers between DTOs and domain entities

2. **✅ Implement `RemoteMessageDataSourceImpl`**:
   - Flow-based `sendMessage()` with 10-step progress simulation
   - Flow-based `editMessage()` for message updates
   - Synchronous `deleteMessage()` with mode support (FOR_SENDER_ONLY/FOR_EVERYONE)
   - Comprehensive error handling following same patterns as Chat
   - Logger integration with existing abstractions

3. **✅ Write Tests for RemoteMessageDataSource**:
   - Complete unit test suite with MockEngine and NoOpLogger
   - Test Flow emissions for sending progress updates
   - Test all CRUD operations and error scenarios
   - Comprehensive exception handling tests for all catch blocks
   - Constants for reproducible test data

4. **✅ Complete all methods**:
   - All three methods implemented and tested: `sendMessage()`, `editMessage()`, `deleteMessage()`
   - Flow behavior verified with proper error emission
   - 100% exception handling coverage achieved

## 🚧 Phase 4: Implement & Test RemoteSyncDataSource (NEXT)
1. **Create Delta DTOs**:
   - `ChatDeltaDto.kt`, `ChatMetadataDto.kt`
   - `ChatListDeltaDto.kt`
   - Mappers for delta types

2. **Implement `RemoteSyncDataSourceImpl`**:
   - **NOTE**: Real-time sync will likely use WebSocket/SSE
   - Consider hybrid approach: REST for CRUD, WebSocket for real-time
   - Implement `chatsDeltaUpdates()` with Flow (mock for now)
   - Simulate streaming updates

3. **Write Tests for RemoteSyncDataSource**:
   - Test delta streaming simulation
   - Test reconnection scenarios
   - Verify incremental sync logic

## Phase 5: Integration Testing
1. **Create Mock Server Helper**:
   - `MockServerScenarios.kt` with predefined responses
   - Support different test scenarios
   - Configurable delays and failures

2. **Integration Tests with Repository**:
   - Test data sources with `MessengerRepositoryImpl`
   - Verify end-to-end flow
   - Test error propagation through sealed classes

## Phase 6: Update Dependency Injection
1. **Create `NetworkModule`**:
   - Provide Ktor client configurations
   - Support mock/real server switch via build config
   - Configure retry policies and timeouts

2. **Update `DataSourceModule`**:
   - Add bindings for new implementations
   - Keep fake for specific test scenarios
   - Support feature flags for gradual rollout

## Phase 7: Real-Time Communication Strategy
1. **REST API Completion**:
   - ✅ Chat CRUD operations (completed)
   - ✅ Message CRUD operations (completed)
   - User authentication endpoints
   - File upload/download

2. **Real-Time Features (Future)**:
   - **WebSocket integration** for live messaging
   - **Server-Sent Events** for notifications
   - **Note**: Manual implementation preferred over code generation (see OpenAPI analysis)
   - Consider AsyncAPI for documentation

3. **Long-term Code Generation Strategy**:
   - **Document APIs** with OpenAPI 3.1 for REST endpoints
   - **Document real-time APIs** with AsyncAPI
   - **Monitor tooling evolution** - current code generation quality insufficient
   - **Hybrid approach**: Generated models, manual clients where needed

## Testing Strategy Throughout

### After Each Data Source Implementation:
1. **Unit Tests** (immediately after implementation):
   - Mock engine responses
   - Test all success paths
   - Test all error types
   - Verify DTO conversions

2. **Component Tests** (after completing a data source):
   - Test with fake repository
   - Verify Flow behavior
   - Test cancellation and timeouts

3. **Integration Tests** (after all sources complete):
   - Full repository integration
   - End-to-end scenarios
   - Performance testing

## ✅ Implementation Progress & Next Steps

### Completed Work
- ✅ **Foundation** (Ktor setup, dependencies, base DTOs)
- ✅ **RemoteChatDataSource** (full implementation with tests)
- ✅ **RemoteMessageDataSource** (full implementation with Flow-based operations)
- ✅ **Type-Safe Error Handling** (sealed class approach with custom serializer)
- ✅ **Comprehensive Testing** (MockEngine, all CRUD operations, Flow testing)
- ✅ **Code Quality** (detekt/ktlint passing, proper exception handling)

### Current Status
- **Next Priority**: RemoteSyncDataSource simulation for delta synchronization
- **Architecture Decision**: Manual implementation over code generation (superior quality)
- **Real-time Strategy**: REST for CRUD, WebSocket/SSE for live updates (future)
- **Major Milestone**: Both Chat and Message CRUD operations fully implemented

### Updated Implementation Order
1. **✅ Days 1-3**: Foundation + RemoteChatDataSource (COMPLETED)
2. **✅ Days 4-5**: RemoteMessageDataSource + Flow tests (COMPLETED)
3. **🚧 Days 6-7**: RemoteSyncDataSource simulation + tests (NEXT)
4. **Day 8**: Integration testing + DI updates
5. **Day 9**: Documentation + OpenAPI specs
6. **Future**: Real-time WebSocket/SSE integration

## Key Testing Considerations (Proven Effective)
- ✅ **MockEngine approach works excellently** - no real server needed
- ✅ **Constants for timestamps** - enables reproducible tests
- ✅ **Sealed classes for errors** - compile-time safety achieved
- ✅ **NoOpLogger integration** - clean testing without noise
- ✅ **Comprehensive error scenarios** - all RemoteDataSourceError types tested
- ✅ **Flow testing patterns** - progress updates and error emission verified
- ✅ **Direct exception testing** - all catch blocks covered with MockEngine
- Continue using `runTest` with `TestScope` for coroutines
- Apply same patterns to Sync data source

## Benefits Achieved
- ✅ **Superior type safety** with sealed class error handling
- ✅ **High code quality** - better than code generation could provide
- ✅ **Complete test coverage** - all success and error paths, including exception handling
- ✅ **No external dependencies** - fully self-contained testing
- ✅ **Excellent maintainability** - clear patterns established and replicated
- ✅ **Flow-based operations** - proper async handling with progress updates
- ✅ **Future-ready architecture** - easy to extend for real-time features

## Strategic Insights
- **Manual implementation approach validated** - higher quality than current tooling
- **OpenAPI/AsyncAPI for documentation only** - not code generation (yet)
- **Hybrid architecture optimal** - REST + WebSocket when server available
- **Test-first approach highly effective** - caught issues early