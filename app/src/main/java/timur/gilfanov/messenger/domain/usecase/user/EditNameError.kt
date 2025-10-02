package timur.gilfanov.messenger.domain.usecase.user

sealed interface EditNameError : UserOperationError {
    data class NameLengthOutOfBounds(val length: Int, val min: Int, val max: Int) : EditNameError
    data class PlatformPolicyViolation(val reason: String) : EditNameError
}
