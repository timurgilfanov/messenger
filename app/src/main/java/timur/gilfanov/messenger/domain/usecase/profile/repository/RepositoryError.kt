package timur.gilfanov.messenger.domain.usecase.profile.repository

@Deprecated(
    message = "Renamed to RemoteError in common package",
    replaceWith = ReplaceWith(
        "RemoteError",
        "timur.gilfanov.messenger.domain.usecase.common.RemoteError",
    ),
)
typealias RepositoryError = timur.gilfanov.messenger.domain.usecase.common.RemoteError
