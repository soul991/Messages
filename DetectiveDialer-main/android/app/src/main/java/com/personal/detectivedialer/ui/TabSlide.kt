package com.personal.detectivedialer.ui

/**
 * Pure tab-slide direction logic for the bottom-nav transitions (bug batch #7),
 * kept free of Compose/Navigation types so it is unit-testable on the JVM.
 *
 * Direction is derived from the two tabs' fixed positions via a general
 * `newIndex - oldIndex` sign — NOT from Navigation's push/pop classification.
 * That classification was the bug: every tab switch does popUpTo(DASHBOARD), so
 * returning to Calls was always a "pop" (enter-from-left) and every other tab a
 * "push" (enter-from-right), which only happened to reverse correctly for the
 * Calls↔Contacts pair. An index comparison reverses correctly for every pair.
 */
object TabSlide {

    /** Position of a route among the ordered bottom tabs, or -1 if it isn't one. */
    fun indexOf(orderedTabRoutes: List<String>, route: String?): Int =
        orderedTabRoutes.indexOfFirst { it == route }

    /**
     * Slide direction for a move from [fromRoute] to [toRoute]:
     *   +1 → target is to the RIGHT (new screen enters from the right),
     *   -1 → target is to the LEFT  (new screen enters from the left),
     *    0 → not a tab-to-tab move (caller should keep its default push/pop feel).
     */
    fun direction(orderedTabRoutes: List<String>, fromRoute: String?, toRoute: String?): Int {
        val from = indexOf(orderedTabRoutes, fromRoute)
        val to = indexOf(orderedTabRoutes, toRoute)
        if (from < 0 || to < 0 || from == to) return 0
        return if (to > from) 1 else -1
    }
}
