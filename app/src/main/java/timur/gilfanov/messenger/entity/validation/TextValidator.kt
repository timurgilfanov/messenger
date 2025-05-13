package timur.gilfanov.messenger.entity.validation

class TextValidator(private val maxLength: Int) {
    fun validate(text: String): Result<Unit> = when {
        text.isBlank() -> Result.failure(
            IllegalArgumentException("Message text cannot be empty"),
        )
        text.length > maxLength -> Result.failure(
            IllegalArgumentException("Message text cannot exceed $maxLength characters"),
        )
        else -> Result.success(Unit)
    }
}
