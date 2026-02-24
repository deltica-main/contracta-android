package com.example.businesscardscanner.ui.navigation

/**
 * Enumeration of navigation destinations. Routes are simple strings; for parameterised
 * routes, append a placeholder (e.g. "/{id}").
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Scan : Screen("scan?source={source}", "Scan") {
        const val ARG_SOURCE = "source"
        fun createRoute(source: String? = null): String {
            return if (source.isNullOrBlank()) {
                "scan"
            } else {
                "scan?source=${source}"
            }
        }
    }
    object Review : Screen("review", "Review")
    object MeetingContext : Screen("meetingContext", "Meeting Context")
    object Settings : Screen("settings", "Settings")
    object Support : Screen("support", "Support")
    object ContactList : Screen("contactList?query={query}", "Contacts") {
        const val ARG_QUERY = "query"
        fun createRoute(query: String? = null): String {
            return if (query.isNullOrBlank()) {
                "contactList"
            } else {
                "contactList?query=${query}"
            }
        }
    }
    object ContactDetail : Screen("contactDetail/{contactId}", "Contact") {
        const val ARG_CONTACT_ID = "contactId"
        fun createRoute(contactId: Long): String = "contactDetail/$contactId"
    }
}
