package com.messages.app.ui.navigation

/**
 * G3 Back Navigation Policy.
 *
 * Enforces predictable back gesture and back button behavior across all screens:
 * 1. On Home:
 *    - Selection active → clear selection (stay on current folder).
 *    - Search active → exit search (stay on current folder).
 *    - Folder is "BLOCKED" (from 3-dot overflow menu) or any tab other than "INBOX" → return to "INBOX".
 *    - Folder is "INBOX" (with no search/selection) → unhandled, exits app.
 * 2. On Child screens (Chat, Archived, Outbox, Settings, Dashboard, etc.):
 *    - If there is a previous entry on the back stack, pop to it.
 *    - If opened via cold deep link/notification (empty back stack), navigate to canonical parent
 *      (e.g., chat -> home, about -> settings) instead of closing the app.
 */
object NavigationBackPolicy {

    /**
     * Handles back actions on HomeScreen.
     * Returns true if back was handled internally, or false if allowed to bubble up (exiting app).
     */
    fun handleHomeBack(
        searchActive: Boolean,
        selectionActive: Boolean,
        currentFolder: String,
        onClearSelection: () -> Unit = {},
        onExitSearch: () -> Unit = {},
        onResetFolderToInbox: () -> Unit = {},
    ): Boolean {
        if (selectionActive) {
            onClearSelection()
            return true
        }
        if (searchActive) {
            onExitSearch()
            return true
        }
        if (currentFolder != "INBOX") {
            onResetFolderToInbox()
            return true
        }
        return false
    }

    /**
     * Resolves the canonical fallback destination when popping the back stack returns false
     * (e.g. cold notification deep link into chat, or direct shortcut launch).
     */
    fun resolveFallbackRoute(currentRoute: String): String {
        return when {
            currentRoute.startsWith("chat/") -> "home"
            currentRoute.startsWith("contactDetail/") -> "home"
            currentRoute == "archived" -> "home"
            currentRoute == "outbox" -> "home"
            currentRoute == "dashboard" -> "home"
            currentRoute.startsWith("starred") -> "home"
            currentRoute == "compose" -> "home"
            currentRoute.startsWith("why/") -> "home"
            currentRoute == "settings" -> "home"
            currentRoute == "notification_settings" -> "settings"
            currentRoute == "about" -> "settings"
            currentRoute == "update_settings" -> "about"
            currentRoute == "update_check" -> "update_settings"
            currentRoute == "drive_backup" -> "settings"
            currentRoute == "spam_backup_picker" -> "drive_backup"
            currentRoute == "trash" -> "settings"
            currentRoute == "secret_setup" -> "home"
            currentRoute == "secret_prompt" -> "home"
            currentRoute == "secret_space" -> "home"
            currentRoute == "secret_compose" -> "secret_space"
            currentRoute == "secret_settings" -> "secret_space"
            currentRoute.startsWith("secret_chat/") -> "secret_space"
            currentRoute.startsWith("secret_why/") -> "secret_space"
            else -> "home"
        }
    }
}
