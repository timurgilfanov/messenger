package timur.gilfanov.messenger.domain.usecase

sealed class SendMessageError {
    object NetworkNotAvailable : CreateMessageError()
    object ServerUnreachable : CreateMessageError()
    object ServerError : CreateMessageError()
    object UnknownError : CreateMessageError()
}
