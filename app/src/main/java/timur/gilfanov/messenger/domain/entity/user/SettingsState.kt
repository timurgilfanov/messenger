package timur.gilfanov.messenger.domain.entity.user

/**
 * Lifecycle state of a settings snapshot, inferred from accompanying [SettingsMetadata].
 *
 * The value guides repository decisions such as triggering recovery, falling back to defaults,
 * or sync local changes.
 */
enum class SettingsState {
    /** No settings persisted yet (metadata timestamps remain at zero). */
    EMPTY,

    /** Default values generated on-device with no remote sync history. */
    DEFAULT,

    /** Settings that match the last successful remote sync (authoritative copy). */
    IN_SYNC_WITH_REMOTE,

    /** Settings changed locally since last sync with remote. */
    MODIFIED,
}
