# Testing Strategy

To balance between short time to find bug and waiting time for tests to run I need a strategy for testing. This document will describe what needed to be tested, on what runtime and at what moment. Also, what quality gates do I need.

## Test Categories

**Unit** — test single method or class with minimal dependencies. Example: text validator.

**Component** — test multiply classes together. Example: button behaviour or appearance, form validation.

**Architecture** — verify architecture rules. Example: entity layer do not depend on any other layer. 

**Feature** — test integration between two or more components. Example: UI or ViewModel send a message to use case and handle different responses.

**Application** — test deployable binary to verify application functionality. Example: pressing chat preview on chat's list shows selected chat.

**Release Candidate** — verifies the critical user journeys of a release build and performance. Example: sending a message, sing-in flow.

Test classes should be annotated with it's test category, so CI/CD could know what tests to run for each stage. 

## Execution Matrix

| Category          | Network access                          | Execution                                            | Build type             | Lifecycle                      |
|-------------------|-----------------------------------------|------------------------------------------------------|------------------------|--------------------------------|
| Architecture      | No                                      | Local                                                | Debuggable             | Every commit                   |
| Unit              | No                                      | Local                                                | Debuggable             | Every commit                   | 
| Component         | No                                      | Local <br/> Robolectric <br/> Emulator               | Debuggable             | Every commit                   |
| Feature           | Mocked                                  | Local <br/> Robolectric <br/> Emulator <br/> Devices | Debuggable             | Pre-merge                      |
| Application       | Mocked <br/> **Staging** <br/> **Prod** | Emulator <br/> **Devices**                           | Debuggable             | Pre-merge <br/> **Post-merge** |
| Release Candidate | Prod                                    | Emulator <br/> Devices                               | Minified release build | Post-merge <br/> Pre-release   |

Bold in application test type row means that running against staging and production backend is preferable on post-merge state. It can and should be calibrated based on real life metrics of time to bug and time to merge.

Tests of all categories can be executed sequentially.

## Test Triggers Matrix

| Category          | Environment (where)                   | Trigger (when) |
|-------------------|---------------------------------------|----------------|
| Architecture      | Local                                 | Every commit   |
| Unit              | Local                                 | Every commit   |
| Component         | Local                                 | Every commit   |
| Feature           | Local, emulators                      | Pre-merge      |
| Application       | Local, emulators, 1 phone, 1 foldable | Post-merge     |
| Release Candidate | 8 phones, 1 foldable, 1 tablet        | Pre-release    |


## Device Coverage Strategy

| Category          | Devices                        | OS Versions                             | Screen Sizes     |
|-------------------|--------------------------------|-----------------------------------------|------------------|
| Feature           | Emulator                       | Latest 1 major versions                 | Phone only       |
| Application       | 1 phone, 1 foldable            | Latest 2 major versions                 | Phone, Foldable  |
| Release Candidate | 4 phones, 1 foldable, 1 tablet | Latest 4 major versions + older popular | All form factors |

## Test Pyramid

* Unit: 70%
* Component: 20%
* Feature: 8%
* Application: 2%
* Architecture: 0.1%

## Execution Rime:
* Unit, Architecture: < 100ms per test
* Component: < 1 second per test
* Feature: < 5 seconds per test
* Application: < 30 seconds per test
* Release Candidate: < 5 minutes per critical journey

## Quality Gates
### Pre-merge
* Static analysis passes 100%
* Unit and Component coverage > 80% for new code
* Enforce test pyramid ratio with significant tolerance

### Pre-release
* No performance regressions > 10%

## Test Data Management Strategy
* Unit/Component: Use builders or fixtures
* Feature: Use fake repositories
* Application: Use fake repositories without network and seeded test accounts on stage and prod.
* Release Candidate: Use production-like data sets

## Flaky Test Handling
Let's try zero tolerance.

## Performance Testing
* Startup time benchmarks
* Memory usage limits
* Frame rate requirements for UI tests

## Accessibility Testing
Do not do.

## Security Testing
Do not do.

## Monitoring
* Test coverage trend