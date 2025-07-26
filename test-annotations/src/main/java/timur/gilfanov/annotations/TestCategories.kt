package timur.gilfanov.annotations

// JUnit Category interfaces for unit tests
interface Unit

interface Component

interface Architecture

interface Feature

interface Application

interface ReleaseCandidate

// Direct annotations for instrumentation test filtering
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationTest

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReleaseCandidateTest
