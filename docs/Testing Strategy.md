# Testing Strategy

To balance between short time to find bug and waiting time for tests to run I need a strategy for testing. This document will describe what needed to be tested, on what runtime and at what moment. Also, what quality gates do I need.

## Test Categories

**Architecture** — verify architecture rules. Example: entity layer do not depend on any other layer. 

**Unit** — test single method or class with minimal dependencies. Example: text validator.

**Component** — test multiply classes together. Example: button behaviour or appearance, form validation. 

**Screenshot** — verify UI component visual appearance and prevent unintended visual regressions. Example: message bubble appearance, dark theme rendering, error states display.

**Feature** — test integration between two or more components. Example: UI or ViewModel send a message to use case and handle different responses.

**Application** — test deployable binary to verify application functionality. Example: pressing chat preview on chat's list shows selected chat.

**Release Candidate** — verifies the critical user journeys of a release build and performance. Example: sending a message, sing-in flow.

Test classes should be annotated with it's test category, so CI/CD could know what tests to run for each stage. 

## Execution Matrix

| Category          | Data layer         | Network access                          | Execution                                         | OS Versions                             | Build type             | Lifecycle                        |
|-------------------|--------------------|-----------------------------------------|---------------------------------------------------|-----------------------------------------|------------------------|----------------------------------|
| Architecture      | N/A                | No                                      | Local                                             | N/A                                     | Debuggable             | Every commit                     |
| Unit              | Fakes              | No                                      | Local                                             | N/A                                     | Debuggable             | Every commit                     | 
| Component         | Fakes              | No                                      | Local <br/> Robolectric                           | N/A                                     | Debuggable             | Every commit                     |
| Screenshot        | Fakes              | No                                      | Local <br/> Robolectric                           | N/A                                     | Debuggable             | Pre-merge                        |
| Feature           | Real, in-memory DB | Mocked                                  | Local <br/> Robolectric <br/> Emulator            | N/A                                     | Debuggable             | Pre-merge                        |
| Application       | Real, in-memory DB | Mocked <br/> **Staging** <br/> **Prod** | Emulator <br/> **1 phone, 1 foldable**            | Latest 2 major                          | Debuggable             | Pre-merge <br/> **Post-merge**   |
| Release Candidate | Real               | Prod                                    | Emulator <br/> **4 phones, 1 foldable, 1 tablet** | Latest 4 major versions + older popular | Minified release build | Post-merge <br/> **Pre-release** |

## Test Pyramid

* Unit: 80%
* Component: 70%
* Screenshot: All UI components
* Feature: 50%
* Application: 40%
* Release Candidate: 30%

## Execution Rime:
* Unit, Architecture: < 100ms per test
* Component: < 1 second per test
* Screenshot: < 2 seconds per test
* Feature: < 5 seconds per test
* Application: < 30 seconds per test
* Release Candidate: < 5 minutes per critical journey

## Quality Gates
### Pre-merge
* Static analysis passes 100%
* Unit and Component coverage > 80% for new code
* Screenshot tests pass with no visual regressions
* Enforce test pyramid ratio with significant tolerance

### Pre-release
* No performance regressions > 10%

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
