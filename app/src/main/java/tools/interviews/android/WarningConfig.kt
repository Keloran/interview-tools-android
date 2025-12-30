package tools.interviews.android

/**
 * Configuration for app-wide warning messages.
 *
 * To display a warning popup to users:
 * 1. Set the WARNING_MESSAGE below (up to 400 characters, newlines supported)
 * 2. Enable the "warning" feature flag in Flags.gg
 *
 * When the issue is resolved, disable the "warning" feature flag to hide the popup
 * without needing to release a new version.
 */
object WarningConfig {
    /**
     * The warning message to display to users.
     * - Maximum 400 characters
     * - Supports newlines (\n)
     * - Leave empty ("") to disable the warning even if the feature flag is enabled
     *
     * Example:
     * const val WARNING_MESSAGE = "Google Sign-In is temporarily unavailable due to an issue with Clerk.\n\nPlease use email sign-in instead."
     */
    const val WARNING_MESSAGE = "Google Sign-in is temporarily unavailable due to an issue with Clerk.\n\nThis is exclusively an Android issue.\n\nPlease use Github or Apple sign-in instead"

    const val WARNING_TITLE = "Notice"
}