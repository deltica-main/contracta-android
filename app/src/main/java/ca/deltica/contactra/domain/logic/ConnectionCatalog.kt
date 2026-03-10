package ca.deltica.contactra.domain.logic

/**
 * Curated relationship options shown when saving meeting context.
 */
object ConnectionCatalog {
    const val OTHER = "Other"

    val options: List<String> = listOf(
        "Client",
        "Customer",
        "Vendor",
        "Supplier",
        "Coworker",
        "Colleague",
        "Manager",
        "Direct report",
        "Recruiter",
        "Partner",
        "Friend",
        OTHER
    )
}
