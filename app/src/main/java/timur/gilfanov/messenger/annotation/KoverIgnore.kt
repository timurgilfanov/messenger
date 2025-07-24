package timur.gilfanov.messenger.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KoverIgnore(val reason: String = "")
