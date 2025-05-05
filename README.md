# messenger
Sample Android application that mimic to Telegram messenger UX to try new things and train engineering muscles.

I plan to try:
* hybrid trunk and feature git flow with feature flags
* Hilt as DI library
* Orbit MVI as MVVM+ approach develop UI
* separate fakes in test modules.

## Set up

### Detekt and ktlint
* Install detekt and ktlint Android Studio plugins.
* Download detekt-compose-*.jar from [Compose ruleset](https://github.com/mrmans0n/compose-rules/releases) release.
* Config detekt to use ./config/detekt/detekt.yml config and Compose ruleset.

