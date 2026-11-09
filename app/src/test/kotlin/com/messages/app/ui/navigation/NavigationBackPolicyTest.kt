package com.messages.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G3: Navigation Back Policy verification suite.
 * Tests that back gesture handling behaves deterministically:
 * - Blocked folder returns to Inbox, never closes the app.
 * - Search and selection state exit on back without losing list state.
 * - Leaf screens (chat, sub-settings, locked space) fall back to their canonical parent
 *   when opened via notification or cold deep-link without an existing back stack.
 */
class NavigationBackPolicyTest {

    @Test
    fun `blocked folder returns to inbox on back`() {
        var resetToInboxCalled = false
        val handled = NavigationBackPolicy.handleHomeBack(
            searchActive = false,
            selectionActive = false,
            currentFolder = "BLOCKED",
            onResetFolderToInbox = { resetToInboxCalled = true },
        )
        assertTrue("Back should be handled when in BLOCKED folder", handled)
        assertTrue("Folder should be reset to INBOX", resetToInboxCalled)
    }

    @Test
    fun `dock folders return to inbox on back`() {
        listOf("PROMOTIONS", "SPAM", "REVIEW").forEach { folder ->
            var resetToInboxCalled = false
            val handled = NavigationBackPolicy.handleHomeBack(
                searchActive = false,
                selectionActive = false,
                currentFolder = folder,
                onResetFolderToInbox = { resetToInboxCalled = true },
            )
            assertTrue("Back should be handled for $folder", handled)
            assertTrue("Folder should be reset to INBOX for $folder", resetToInboxCalled)
        }
    }

    @Test
    fun `inbox root without selection or search allows system back to exit`() {
        var resetCalled = false
        val handled = NavigationBackPolicy.handleHomeBack(
            searchActive = false,
            selectionActive = false,
            currentFolder = "INBOX",
            onResetFolderToInbox = { resetCalled = true },
        )
        assertFalse("At INBOX root, back should not be consumed internally", handled)
        assertFalse("Reset to inbox should not be called", resetCalled)
    }

    @Test
    fun `search active is cleared before folder navigation`() {
        var exitSearchCalled = false
        var resetFolderCalled = false
        val handled = NavigationBackPolicy.handleHomeBack(
            searchActive = true,
            selectionActive = false,
            currentFolder = "BLOCKED",
            onExitSearch = { exitSearchCalled = true },
            onResetFolderToInbox = { resetFolderCalled = true },
        )
        assertTrue("Back should be handled for active search", handled)
        assertTrue("Exit search must be called", exitSearchCalled)
        assertFalse("Folder reset should not happen until search is exited", resetFolderCalled)
    }

    @Test
    fun `selection active is cleared before search or folder navigation`() {
        var clearSelectionCalled = false
        var exitSearchCalled = false
        var resetFolderCalled = false
        val handled = NavigationBackPolicy.handleHomeBack(
            searchActive = true,
            selectionActive = true,
            currentFolder = "BLOCKED",
            onClearSelection = { clearSelectionCalled = true },
            onExitSearch = { exitSearchCalled = true },
            onResetFolderToInbox = { resetFolderCalled = true },
        )
        assertTrue("Back should be handled for active selection", handled)
        assertTrue("Clear selection must be called", clearSelectionCalled)
        assertFalse("Exit search should wait for selection to clear", exitSearchCalled)
        assertFalse("Folder reset should wait for selection to clear", resetFolderCalled)
    }

    @Test
    fun `chat fallback route targets home`() {
        assertEquals("home", NavigationBackPolicy.resolveFallbackRoute("chat/123"))
        assertEquals("home", NavigationBackPolicy.resolveFallbackRoute("chat/123?q=order"))
    }

    @Test
    fun `main screen fallback routes target home`() {
        val homeChildren = listOf(
            "archived",
            "outbox",
            "dashboard",
            "starred",
            "compose",
            "why/99",
            "settings",
            "contactDetail/123",
            "secret_setup",
            "secret_prompt",
            "secret_space",
        )
        homeChildren.forEach { route ->
            assertEquals("Route $route should fall back to home", "home", NavigationBackPolicy.resolveFallbackRoute(route))
        }
    }

    @Test
    fun `settings sub-screens fall back to their parent screens`() {
        assertEquals("settings", NavigationBackPolicy.resolveFallbackRoute("notification_settings"))
        assertEquals("settings", NavigationBackPolicy.resolveFallbackRoute("about"))
        assertEquals("settings", NavigationBackPolicy.resolveFallbackRoute("drive_backup"))
        assertEquals("settings", NavigationBackPolicy.resolveFallbackRoute("trash"))
        assertEquals("about", NavigationBackPolicy.resolveFallbackRoute("update_settings"))
        assertEquals("update_settings", NavigationBackPolicy.resolveFallbackRoute("update_check"))
        assertEquals("drive_backup", NavigationBackPolicy.resolveFallbackRoute("spam_backup_picker"))
    }

    @Test
    fun `secret space child screens fall back to secret_space`() {
        val secretChildren = listOf(
            "secret_compose",
            "secret_settings",
            "secret_chat/456",
            "secret_why/123",
        )
        secretChildren.forEach { route ->
            assertEquals("Route $route should fall back to secret_space", "secret_space", NavigationBackPolicy.resolveFallbackRoute(route))
        }
    }
}
