package timur.gilfanov.annotations

// JUnit Category interfaces for unit tests
// todo: can we use annotations instead of JUnit test categories?
interface Unit

interface Component

interface Architecture

interface Feature

// Direct annotations for instrumentation test filtering
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureTest

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationTest

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReleaseCandidateTest
