package timur.gilfanov.messenger.domain.usecase.user

sealed interface UpdateNameError : UserOperationError {
    data class NameLengthOutOfBounds(val length: Int, val min: Int, val max: Int) : UpdateNameError
    data class PlatformPolicyViolation(val reason: String) : UpdateNameError
}
