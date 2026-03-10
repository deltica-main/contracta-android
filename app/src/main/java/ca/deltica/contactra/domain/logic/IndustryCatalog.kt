package ca.deltica.contactra.domain.logic

import java.util.Locale
import java.net.URI

/**
 * Central industry list and simple keyword-based matching logic.
 */
object IndustryCatalog {
    val manualSelectionIndustries: List<String> = listOf(
        "Utilities",
        "Construction",
        "Manufacturing",
        "Technology",
        "Finance",
        "Healthcare",
        "Education",
        "Government",
        "Legal",
        "Real estate",
        "Retail",
        "Hospitality",
        "Transportation",
        "Non profit",
        "Energy",
        "Telecom",
        "Consulting",
        "Other"
    )

    val industries: List<String> = listOf(
        "Technology / Software",
        "IT Services",
        "Telecommunications",
        "Finance & Banking",
        "Accounting",
        "Insurance",
        "Healthcare",
        "Pharmaceuticals & Biotechnology",
        "Medical Devices",
        "Legal",
        "Education",
        "Government / Public Sector",
        "Nonprofit / NGO",
        "Manufacturing",
        "Construction",
        "Real Estate",
        "Architecture & Design",
        "Engineering & Industrial",
        "Energy & Utilities",
        "Transportation & Logistics",
        "Automotive",
        "Aerospace & Defense",
        "Retail & E-commerce",
        "Hospitality & Travel",
        "Food & Beverage",
        "Media & Entertainment",
        "Marketing & Advertising",
        "Consulting / Professional Services",
        "Human Resources / Staffing",
        "Agriculture",
        "Security",
        "Sports & Fitness",
        "Beauty & Wellness",
        "Environmental / Sustainability",
        "Mining & Metals",
        "Publishing",
        "Gaming",
        "Other"
    )

    private val roleKeywords: List<Pair<String, String>> = listOf(
        "software" to "Technology / Software",
        "developer" to "Technology / Software",
        "engineer" to "Technology / Software",
        "devops" to "Technology / Software",
        "sre" to "Technology / Software",
        "data" to "Technology / Software",
        "machine learning" to "Technology / Software",
        "ai" to "Technology / Software",
        "cloud" to "Technology / Software",
        "it" to "IT Services",
        "sysadmin" to "IT Services",
        "system administrator" to "IT Services",
        "network administrator" to "IT Services",
        "telecom" to "Telecommunications",
        "accountant" to "Accounting",
        "bookkeeper" to "Accounting",
        "controller" to "Accounting",
        "cpa" to "Accounting",
        "finance" to "Finance & Banking",
        "banker" to "Finance & Banking",
        "investment" to "Finance & Banking",
        "wealth" to "Finance & Banking",
        "advisor" to "Finance & Banking",
        "insurance" to "Insurance",
        "underwriter" to "Insurance",
        "claims" to "Insurance",
        "actuary" to "Insurance",
        "doctor" to "Healthcare",
        "physician" to "Healthcare",
        "nurse" to "Healthcare",
        "clinic" to "Healthcare",
        "hospital" to "Healthcare",
        "medical" to "Healthcare",
        "dentist" to "Healthcare",
        "pharma" to "Pharmaceuticals & Biotechnology",
        "biotech" to "Pharmaceuticals & Biotechnology",
        "clinical" to "Pharmaceuticals & Biotechnology",
        "medtech" to "Medical Devices",
        "device" to "Medical Devices",
        "lawyer" to "Legal",
        "attorney" to "Legal",
        "legal" to "Legal",
        "paralegal" to "Legal",
        "teacher" to "Education",
        "professor" to "Education",
        "instructor" to "Education",
        "education" to "Education",
        "government" to "Government / Public Sector",
        "municipal" to "Government / Public Sector",
        "public sector" to "Government / Public Sector",
        "nonprofit" to "Nonprofit / NGO",
        "ngo" to "Nonprofit / NGO",
        "charity" to "Nonprofit / NGO",
        "foundation" to "Nonprofit / NGO",
        "manufacturing" to "Manufacturing",
        "factory" to "Manufacturing",
        "production" to "Manufacturing",
        "construction" to "Construction",
        "contractor" to "Construction",
        "builder" to "Construction",
        "real estate" to "Real Estate",
        "realtor" to "Real Estate",
        "broker" to "Real Estate",
        "property" to "Real Estate",
        "mortgage" to "Real Estate",
        "architect" to "Architecture & Design",
        "design" to "Architecture & Design",
        "industrial" to "Engineering & Industrial",
        "mechanical" to "Engineering & Industrial",
        "electrical" to "Engineering & Industrial",
        "energy" to "Energy & Utilities",
        "utilities" to "Energy & Utilities",
        "oil" to "Energy & Utilities",
        "gas" to "Energy & Utilities",
        "solar" to "Energy & Utilities",
        "wind" to "Energy & Utilities",
        "logistics" to "Transportation & Logistics",
        "supply chain" to "Transportation & Logistics",
        "transport" to "Transportation & Logistics",
        "shipping" to "Transportation & Logistics",
        "freight" to "Transportation & Logistics",
        "warehouse" to "Transportation & Logistics",
        "automotive" to "Automotive",
        "auto" to "Automotive",
        "vehicle" to "Automotive",
        "aerospace" to "Aerospace & Defense",
        "defense" to "Aerospace & Defense",
        "aviation" to "Aerospace & Defense",
        "retail" to "Retail & E-commerce",
        "ecommerce" to "Retail & E-commerce",
        "e-commerce" to "Retail & E-commerce",
        "store" to "Retail & E-commerce",
        "hospitality" to "Hospitality & Travel",
        "hotel" to "Hospitality & Travel",
        "resort" to "Hospitality & Travel",
        "travel" to "Hospitality & Travel",
        "restaurant" to "Food & Beverage",
        "cafe" to "Food & Beverage",
        "food" to "Food & Beverage",
        "beverage" to "Food & Beverage",
        "catering" to "Food & Beverage",
        "media" to "Media & Entertainment",
        "entertainment" to "Media & Entertainment",
        "film" to "Media & Entertainment",
        "music" to "Media & Entertainment",
        "broadcast" to "Media & Entertainment",
        "marketing" to "Marketing & Advertising",
        "advertising" to "Marketing & Advertising",
        "brand" to "Marketing & Advertising",
        "pr" to "Marketing & Advertising",
        "consulting" to "Consulting / Professional Services",
        "consultant" to "Consulting / Professional Services",
        "advisory" to "Consulting / Professional Services",
        "hr" to "Human Resources / Staffing",
        "human resources" to "Human Resources / Staffing",
        "recruiter" to "Human Resources / Staffing",
        "staffing" to "Human Resources / Staffing",
        "agriculture" to "Agriculture",
        "farm" to "Agriculture",
        "security" to "Security",
        "cybersecurity" to "Security",
        "investigations" to "Security",
        "fitness" to "Sports & Fitness",
        "trainer" to "Sports & Fitness",
        "gym" to "Sports & Fitness",
        "beauty" to "Beauty & Wellness",
        "salon" to "Beauty & Wellness",
        "spa" to "Beauty & Wellness",
        "wellness" to "Beauty & Wellness",
        "environment" to "Environmental / Sustainability",
        "sustainability" to "Environmental / Sustainability",
        "mining" to "Mining & Metals",
        "metals" to "Mining & Metals",
        "publishing" to "Publishing",
        "editor" to "Publishing",
        "gaming" to "Gaming",
        "game" to "Gaming"
    )

    private val companyKeywords: List<Pair<String, String>> = listOf(
        "bank" to "Finance & Banking",
        "credit union" to "Finance & Banking",
        "insurance" to "Insurance",
        "hospital" to "Healthcare",
        "clinic" to "Healthcare",
        "university" to "Education",
        "college" to "Education",
        "school" to "Education",
        "agency" to "Government / Public Sector",
        "ministry" to "Government / Public Sector",
        "foundation" to "Nonprofit / NGO",
        "charity" to "Nonprofit / NGO",
        "labs" to "Pharmaceuticals & Biotechnology",
        "pharma" to "Pharmaceuticals & Biotechnology",
        "studio" to "Media & Entertainment",
        "media" to "Media & Entertainment",
        "restaurant" to "Food & Beverage",
        "hotel" to "Hospitality & Travel",
        "realty" to "Real Estate",
        "properties" to "Real Estate",
        "construction" to "Construction",
        "manufacturing" to "Manufacturing",
        "logistics" to "Transportation & Logistics",
        "consulting" to "Consulting / Professional Services"
    )

    private val websiteSignalKeywords: List<Pair<String, String>> = listOf(
        "hydro" to "Utilities",
        "utility" to "Utilities",
        "utilities" to "Utilities",
        "electric distribution" to "Utilities",
        "power" to "Utilities",
        "water" to "Utilities",
        "construction" to "Construction",
        "contracting" to "Construction",
        "builder" to "Construction",
        "builders" to "Construction",
        "manufacturing" to "Manufacturing",
        "industrial" to "Manufacturing",
        "factory" to "Manufacturing",
        "technology" to "Technology",
        "software" to "Technology",
        "saas" to "Technology",
        "digital" to "Technology",
        "finance" to "Finance",
        "bank" to "Finance",
        "capital" to "Finance",
        "wealth" to "Finance",
        "health" to "Healthcare",
        "medical" to "Healthcare",
        "clinic" to "Healthcare",
        "hospital" to "Healthcare",
        "dental" to "Healthcare",
        "education" to "Education",
        "school" to "Education",
        "college" to "Education",
        "university" to "Education",
        "government" to "Government",
        "public" to "Government",
        "city" to "Government",
        "county" to "Government",
        "law" to "Legal",
        "legal" to "Legal",
        "attorney" to "Legal",
        "llp" to "Legal",
        "real estate" to "Real estate",
        "realty" to "Real estate",
        "property" to "Real estate",
        "properties" to "Real estate",
        "retail" to "Retail",
        "store" to "Retail",
        "shop" to "Retail",
        "ecommerce" to "Retail",
        "hospitality" to "Hospitality",
        "hotel" to "Hospitality",
        "travel" to "Hospitality",
        "restaurant" to "Hospitality",
        "transport" to "Transportation",
        "transportation" to "Transportation",
        "logistics" to "Transportation",
        "shipping" to "Transportation",
        "freight" to "Transportation",
        "nonprofit" to "Non profit",
        "non profit" to "Non profit",
        "charity" to "Non profit",
        "foundation" to "Non profit",
        "energy" to "Energy",
        "oil" to "Energy",
        "gas" to "Energy",
        "solar" to "Energy",
        "wind" to "Energy",
        "telecom" to "Telecom",
        "telecommunications" to "Telecom",
        "wireless" to "Telecom",
        "fiber" to "Telecom",
        "mobile" to "Telecom",
        "consulting" to "Consulting",
        "consultant" to "Consulting",
        "advisory" to "Consulting"
    )

    private val websiteStrongKeywords: Set<String> = setOf(
        "hydro",
        "utilities",
        "electric distribution",
        "clinic",
        "hospital",
        "dental",
        "pharmacy",
        "university",
        "college",
        "school",
        "attorney",
        "llp",
        "law",
        "realty",
        "logistics",
        "telecom",
        "telecommunications",
        "consulting",
        "nonprofit",
        "foundation"
    )

    private val websiteStopWords: Set<String> = setOf(
        "solutions",
        "group",
        "services",
        "global",
        "leading",
        "trusted",
        "excellence",
        "innovation"
    )

    private val domainIgnoredLabels: Set<String> = setOf(
        "www",
        "m",
        "app",
        "portal",
        "api",
        "dev",
        "info",
        "mail",
        "co",
        "com",
        "org",
        "net",
        "io",
        "biz",
        "ai"
    )

    private val companyHeuristicKeywords: List<Pair<String, String>> = listOf(
        "hydro" to "Utilities",
        "utility" to "Utilities",
        "utilities" to "Utilities",
        "electric distribution" to "Utilities",
        "clinic" to "Healthcare",
        "dental" to "Healthcare",
        "pharmacy" to "Healthcare",
        "university" to "Education",
        "college" to "Education",
        "school" to "Education",
        "law" to "Legal",
        "llp" to "Legal",
        "attorney" to "Legal"
    )

    fun inferIndustry(title: String?, company: String?): Pair<String?, String?> {
        val titleText = title?.lowercase(Locale.getDefault())?.trim().orEmpty()
        val companyText = company?.lowercase(Locale.getDefault())?.trim().orEmpty()
        if (titleText.isBlank() && companyText.isBlank()) return null to null

        val scores = mutableMapOf<String, Int>()
        fun addScore(industry: String, weight: Int) {
            scores[industry] = (scores[industry] ?: 0) + weight
        }

        for ((keyword, industry) in roleKeywords) {
            if (titleText.isNotBlank() && containsKeyword(titleText, keyword)) {
                addScore(industry, 3)
            } else if (companyText.isNotBlank() && containsKeyword(companyText, keyword)) {
                addScore(industry, 1)
            }
        }
        for ((keyword, industry) in companyKeywords) {
            if (companyText.isNotBlank() && containsKeyword(companyText, keyword)) {
                addScore(industry, 2)
            } else if (titleText.isNotBlank() && containsKeyword(titleText, keyword)) {
                addScore(industry, 1)
            }
        }

        val best = scores.maxByOrNull { it.value }
        val industry = best?.key
        val confidence = when {
            best == null -> null
            best.value >= 3 -> "high"
            else -> "low"
        }
        return industry to confidence
    }

    fun inferIndustryFromWebsiteSignals(
        website: String?,
        pageTitle: String?,
        metaDescription: String?,
        company: String?
    ): Pair<String?, Double?> {
        val domainSignals = removeWebsiteStopWords(
            listOfNotNull(extractDomainTokenText(website), company)
                .joinToString(" ")
        )
        val titleSignals = removeWebsiteStopWords(pageTitle)
        val metaSignals = removeWebsiteStopWords(metaDescription)
        if (domainSignals.isBlank() && titleSignals.isBlank() && metaSignals.isBlank()) {
            return null to null
        }

        val scores = mutableMapOf<String, Double>()
        scoreWebsiteSignals(scores, domainSignals, weight = 1.0, strongBonus = 0.35)
        scoreWebsiteSignals(scores, titleSignals, weight = 0.65, strongBonus = 0.2)
        scoreWebsiteSignals(scores, metaSignals, weight = 0.35, strongBonus = 0.1)

        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return null to null
        if (best.value < 0.75) return null to null
        val runnerUp = ranked.getOrNull(1)?.value ?: 0.0
        val scoreGap = best.value - runnerUp
        if (runnerUp > 0.0 && scoreGap < 0.35) return null to null

        val marginRatio = (scoreGap / best.value).coerceIn(0.0, 1.0)
        val strengthRatio = (best.value / 2.0).coerceIn(0.0, 1.0)
        val confidence = ((marginRatio + strengthRatio) / 2.0).coerceIn(0.0, 1.0)
        return best.key to confidence
    }

    fun inferIndustryFromCompanyName(company: String?): String? {
        val normalizedCompany = normalizeForKeywordMatching(company)
        if (normalizedCompany.isBlank()) return null

        val scores = mutableMapOf<String, Int>()
        companyHeuristicKeywords.forEach { (keyword, industry) ->
            if (containsKeyword(normalizedCompany, keyword)) {
                scores[industry] = (scores[industry] ?: 0) + 1
            }
        }
        val best = scores.maxByOrNull { it.value } ?: return null
        val hasTie = scores.values.count { it == best.value } > 1
        if (hasTie || best.value <= 0) return null
        return best.key
    }

    fun toManualSelectionIndustry(industry: String?): String? {
        val trimmed = industry?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        manualSelectionIndustries.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let {
            return it
        }
        val normalized = normalizeForKeywordMatching(trimmed)
        return when {
            containsKeyword(normalized, "utility") || containsKeyword(normalized, "utilities") -> "Utilities"
            containsKeyword(normalized, "construction") -> "Construction"
            containsKeyword(normalized, "manufacturing") || containsKeyword(normalized, "industrial") -> "Manufacturing"
            containsKeyword(normalized, "technology") || containsKeyword(normalized, "software") ||
                containsKeyword(normalized, "it services") -> "Technology"
            containsKeyword(normalized, "finance") || containsKeyword(normalized, "bank") ||
                containsKeyword(normalized, "insurance") || containsKeyword(normalized, "accounting") -> "Finance"
            containsKeyword(normalized, "health") || containsKeyword(normalized, "medical") ||
                containsKeyword(normalized, "pharma") -> "Healthcare"
            containsKeyword(normalized, "education") -> "Education"
            containsKeyword(normalized, "government") || containsKeyword(normalized, "public sector") -> "Government"
            containsKeyword(normalized, "legal") -> "Legal"
            containsKeyword(normalized, "real estate") -> "Real estate"
            containsKeyword(normalized, "retail") || containsKeyword(normalized, "commerce") -> "Retail"
            containsKeyword(normalized, "hospitality") || containsKeyword(normalized, "travel") -> "Hospitality"
            containsKeyword(normalized, "transport") || containsKeyword(normalized, "logistics") -> "Transportation"
            containsKeyword(normalized, "nonprofit") || containsKeyword(normalized, "ngo") -> "Non profit"
            containsKeyword(normalized, "energy") -> "Energy"
            containsKeyword(normalized, "telecom") || containsKeyword(normalized, "telecommunications") -> "Telecom"
            containsKeyword(normalized, "consulting") || containsKeyword(normalized, "advisory") -> "Consulting"
            else -> null
        }
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        val pattern = Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    private fun scoreWebsiteSignals(
        scores: MutableMap<String, Double>,
        signalText: String,
        weight: Double,
        strongBonus: Double
    ) {
        if (signalText.isBlank()) return
        websiteSignalKeywords.forEach { (keyword, industry) ->
            if (containsKeyword(signalText, keyword)) {
                val bonus = if (keyword in websiteStrongKeywords) strongBonus else 0.0
                scores[industry] = (scores[industry] ?: 0.0) + weight + bonus
            }
        }
    }

    private fun extractDomainTokenText(website: String?): String {
        val host = extractHost(website) ?: return ""
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.isEmpty()) return ""

        val domainLabels = when {
            labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2].length <= 3 -> {
                labels.dropLast(2)
            }
            labels.size >= 2 -> labels.dropLast(1)
            else -> labels
        }

        return domainLabels
            .flatMap { label -> label.split('-', '_') }
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { token -> token.isNotBlank() && token !in domainIgnoredLabels && token !in websiteStopWords }
            .joinToString(" ")
    }

    private fun extractHost(website: String?): String? {
        val source = website?.trim().orEmpty()
        if (source.isBlank()) return null
        val normalized = source.substringAfter("://", source).substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .trim('.')
        if (normalized.isBlank()) return null
        return runCatching {
            val uri = URI("https://$normalized")
            uri.host?.removePrefix("www.")?.lowercase(Locale.getDefault())
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun removeWebsiteStopWords(value: String?): String {
        val normalized = normalizeForKeywordMatching(value)
        if (normalized.isBlank()) return ""
        return normalized
            .split(' ')
            .filter { token -> token.isNotBlank() && token !in websiteStopWords }
            .joinToString(" ")
    }

    private fun normalizeForKeywordMatching(value: String?): String {
        return value
            ?.lowercase(Locale.getDefault())
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }

    fun normalizeCompany(company: String?): String? {
        return company
            ?.lowercase(Locale.getDefault())
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
    }
}
