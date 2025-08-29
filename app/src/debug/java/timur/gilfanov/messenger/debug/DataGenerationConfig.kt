package timur.gilfanov.messenger.debug

/**
 * Configuration object for controlling how sample data is generated.
 * Used by SampleDataProvider to create different types of test data.
 */
data class DataGenerationConfig(
    /**
     * Total number of chats to generate
     */
    val chatCount: Int,

    /**
     * Range of messages to generate per chat
     */
    val messageCountRange: IntRange,

    /**
     * Whether to include edge cases like:
     * - Very long messages
     * - Unicode characters and emojis
     * - Empty chats
     * - Messages with special formatting
     */
    val includeEdgeCases: Boolean = false,

    /**
     * Whether to use polished, realistic content suitable for demos.
     * When true, uses curated names and messages.
     * When false, uses more varied/random content.
     */
    val polishedContent: Boolean = false,

    /**
     * The original scenario this config was created from
     */
    val scenario: DataScenario,
) {
    /**
     * Generate a random message count within the configured range
     */
    fun getRandomMessageCount(): Int = messageCountRange.random()

    /**
     * Whether this is an empty scenario (no data)
     */
    val isEmpty: Boolean
        get() = chatCount == 0 && messageCountRange.first == 0

    /**
     * Whether this is a minimal scenario (small amount of data)
     */
    val isMinimal: Boolean
        get() = chatCount <= 5 && messageCountRange.last <= 10

    /**
     * Whether this is a heavy scenario (large amount of data)
     */
    val isHeavy: Boolean
        get() = chatCount >= 30 || messageCountRange.last >= 100
}
