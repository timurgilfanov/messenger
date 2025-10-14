package timur.gilfanov.messenger.data.source

@JvmInline
value class ErrorReason(val value: String)

val Throwable.errorReason: ErrorReason
    get() = ErrorReason(this.message ?: this::class.simpleName ?: "Exception has no name")
