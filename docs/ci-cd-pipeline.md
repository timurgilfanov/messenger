# CI/CD Pipeline

The CI/CD pipeline in `.github/workflows/main.yaml` is organized by test categories and follows the Testing Strategy execution matrix:

**Every Commit (push + PR):**
- `build-<mock|prod>`: lint + detekt + architecture tests + APK generation
- `unit-component-tests`: Unit and Component tests (fast feedback)

**Pre-merge (PR only):**
- `feature-tests`: Feature tests on emulators
- `application-tests-emulator`: Application unit tests on the CI runner + instrumentation tests on Firebase Test Lab emulators

**Post-merge (main branch):**
- `application-tests-devices`: Application instrumentation tests on real devices via Firebase Test Lab

**Pre-release (tags):**
- `release-candidate-tests`: Release Candidate tests on multiple devices with release build
