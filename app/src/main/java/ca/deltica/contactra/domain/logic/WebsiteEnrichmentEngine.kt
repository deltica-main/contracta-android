package ca.deltica.contactra.domain.logic

import java.util.Locale

data class WebsiteEnrichmentResult(
    val enrichedCompany: String?,
    val companyValidatedFromWebsite: Boolean,
    val inferredIndustry: String?,
    val industryConfidence: Double?
)

/**
 * Local website/email-domain enrichment used by OCR parsing.
 * No network calls are required; enrichment relies on domain-derived heuristics.
 */
object WebsiteEnrichmentEngine {
    private val publicEmailDomains = setOf(
        "gmail.com", "outlook.com", "hotmail.com", "live.com", "yahoo.com",
        "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com"
    )

    private val nonCompanyLabels = setOf(
        "www", "m", "mail", "app", "portal", "info", "contact", "support", "help", "api", "dev"
    )

    fun enrich(
        company: String?,
        website: String?,
        email: String?,
        title: String?
    ): WebsiteEnrichmentResult {
        val domain = extractDomain(website) ?: extractDomainFromEmail(email)
        if (domain.isNullOrBlank() || publicEmailDomains.contains(domain)) {
            return WebsiteEnrichmentResult(
                enrichedCompany = company,
                companyValidatedFromWebsite = false,
                inferredIndustry = null,
                industryConfidence = null
            )
        }

        val companyFromDomain = deriveCompanyFromDomain(domain)
        val mergedCompany = mergeCompany(company, companyFromDomain)
        val (industry, confidence) = IndustryCatalog.inferIndustryFromWebsiteSignals(
            website = website,
            pageTitle = title,
            metaDescription = null,
            company = mergedCompany
        )

        return WebsiteEnrichmentResult(
            enrichedCompany = mergedCompany,
            companyValidatedFromWebsite = !companyFromDomain.isNullOrBlank(),
            inferredIndustry = industry,
            industryConfidence = confidence
        )
    }

    private fun extractDomain(value: String?): String? {
        val source = value?.trim()?.lowercase(Locale.getDefault()) ?: return null
        if (source.isBlank()) return null

        var host = source.substringAfter("://", source)
        host = host.substringBefore('/').substringBefore('?').substringBefore('#').trim().trim('.')
        if (host.startsWith("www.")) {
            host = host.removePrefix("www.")
        }
        if (host.contains("@")) {
            host = host.substringAfter("@")
        }
        if (!host.contains('.') || host.isBlank()) return null
        return host
    }

    private fun extractDomainFromEmail(email: String?): String? {
        val value = email?.trim()?.lowercase(Locale.getDefault()) ?: return null
        if (value.isBlank() || !value.contains("@")) return null
        val domain = value.substringAfter("@")
        if (!domain.contains('.')) return null
        return domain
    }

    private fun deriveCompanyFromDomain(domain: String): String? {
        val parts = domain.split('.').filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val secondLevel = when {
            parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2].length <= 3 -> {
                parts[parts.size - 3]
            }
            else -> parts[parts.size - 2]
        }

        val label = secondLevel
            .split('-', '_')
            .filter { it.isNotBlank() && !nonCompanyLabels.contains(it) }
            .joinToString(" ")
            .trim()

        if (label.isBlank()) return null

        return label.split(Regex("\\s+"))
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
            .takeIf { it.any { ch -> ch.isLetter() } }
    }

    private fun mergeCompany(currentCompany: String?, domainCompany: String?): String? {
        val current = currentCompany?.trim().takeIf { !it.isNullOrBlank() }
        val domainValue = domainCompany?.trim().takeIf { !it.isNullOrBlank() }
        if (current == null) return domainValue
        if (domainValue == null) return current

        val normalizedCurrent = IndustryCatalog.normalizeCompany(current)
        val normalizedDomain = IndustryCatalog.normalizeCompany(domainValue)
        if (normalizedCurrent.isNullOrBlank() || normalizedDomain.isNullOrBlank()) return current

        val currentTokens = normalizedCurrent.split(' ').filter { it.isNotBlank() }.toSet()
        val domainTokens = normalizedDomain.split(' ').filter { it.isNotBlank() }.toSet()
        val overlap = currentTokens.intersect(domainTokens)

        if (overlap.isNotEmpty()) {
            return current
        }

        return if (looksWeakCompanyName(current)) {
            domainValue
        } else {
            current
        }
    }

    private fun looksWeakCompanyName(company: String): Boolean {
        val trimmed = company.trim()
        if (trimmed.length < 3) return true
        if (trimmed.all { it.isDigit() || it.isWhitespace() }) return true

        val normalized = IndustryCatalog.normalizeCompany(trimmed).orEmpty()
        val words = normalized.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true

        val genericOnly = words.all {
            it in setOf("company", "corp", "inc", "llc", "group", "global", "solutions", "services")
        }
        return genericOnly
    }
}
