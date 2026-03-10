package ca.deltica.contactra.ui.viewmodel

import ca.deltica.contactra.domain.logic.ExtractedData
import ca.deltica.contactra.domain.logic.IndustryCatalog
import ca.deltica.contactra.domain.logic.OcrExtractionAudit
import ca.deltica.contactra.domain.logic.OcrFieldType
import java.net.URI
import java.util.Locale

data class ReviewFieldSuggestions(
    val name: List<String> = emptyList(),
    val company: List<String> = emptyList(),
    val title: List<String> = emptyList(),
    val email: List<String> = emptyList(),
    val phone: List<String> = emptyList(),
    val website: List<String> = emptyList(),
    val address: List<String> = emptyList(),
    val industry: List<String> = emptyList()
)

internal object ReviewSuggestionEngine {
    private const val MAX_SUGGESTIONS_PER_FIELD = 7

    private val emailRegex =
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val websiteRegex =
        Regex("\\b(?:https?://)?(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val zipRegex = Regex("\\b\\d{5}(?:-\\d{4})?\\b")

    private val marketingPhrases = setOf(
        "your partner",
        "group of companies",
        "trusted",
        "innovative",
        "delivering",
        "powering",
        "connecting",
        "transforming",
        "excellence",
        "empowering",
        "leading"
    )

    private val addressKeywords = setOf(
        "street", "st", "road", "rd", "avenue", "ave", "boulevard", "blvd",
        "suite", "ste", "floor", "fl", "drive", "dr", "lane", "ln",
        "court", "ct", "highway", "hwy", "po box", "building", "bldg"
    )

    private val genericOnlyWords = setOf(
        "the", "group", "company", "solutions", "services", "global",
        "business", "international", "partners", "inc", "ltd", "llc", "co"
    )

    private val roleAcronyms = setOf(
        "ceo", "cto", "cfo", "cio", "coo", "cmo", "svp", "evp", "vp"
    )

    private val trackingQueryKeys = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "gclid", "fbclid"
    )

    fun build(
        audit: OcrExtractionAudit,
        extracted: ExtractedData,
        rawOcrText: String,
        inferredIndustry: String?,
        companyIndustry: String?
    ): ReviewFieldSuggestions {
        val quarantinedLines = audit.quarantinedSlogans.map { it.lineIndex }.toSet()
        val rawLines = audit.normalizedLines.map { it.text }
        val deterministicEmails = emailRegex.findAll(rawOcrText).map { it.value }.toList()
        val deterministicPhones = phoneRegex.findAll(rawOcrText).map { it.value.trim() }.toList()
        val deterministicWebsites = websiteRegex.findAll(rawOcrText)
            .map { it.value.trim() }
            .filterNot { it.contains("@") }
            .toList()

        return ReviewFieldSuggestions(
            name = buildSuggestions(
                kind = SuggestionKind.NAME,
                rawValues = listOfNotNull(extracted.name) +
                    collectAuditValues(audit, OcrFieldType.NAME, quarantinedLines)
            ),
            company = buildSuggestions(
                kind = SuggestionKind.COMPANY,
                rawValues = listOfNotNull(extracted.company) +
                    collectAuditValues(audit, OcrFieldType.COMPANY, quarantinedLines)
            ),
            title = buildSuggestions(
                kind = SuggestionKind.TITLE,
                rawValues = listOfNotNull(extracted.title) +
                    collectAuditValues(audit, OcrFieldType.TITLE, quarantinedLines)
            ),
            email = buildSuggestions(
                kind = SuggestionKind.EMAIL,
                rawValues = listOfNotNull(extracted.email) +
                    collectAuditValues(audit, OcrFieldType.EMAIL, quarantinedLines) +
                    deterministicEmails
            ),
            phone = buildSuggestions(
                kind = SuggestionKind.PHONE,
                rawValues = listOfNotNull(extracted.phone) +
                    collectAuditValues(audit, OcrFieldType.PHONE, quarantinedLines) +
                    deterministicPhones
            ),
            website = buildSuggestions(
                kind = SuggestionKind.WEBSITE,
                rawValues = listOfNotNull(extracted.website) +
                    collectAuditValues(audit, OcrFieldType.WEBSITE, quarantinedLines) +
                    deterministicWebsites
            ),
            address = buildSuggestions(
                kind = SuggestionKind.ADDRESS,
                rawValues = listOfNotNull(extracted.address) +
                    collectAuditValues(audit, OcrFieldType.ADDRESS, quarantinedLines) +
                    collectAddressValues(rawLines)
            ),
            industry = buildSuggestions(
                kind = SuggestionKind.INDUSTRY,
                rawValues = listOfNotNull(extracted.industry, inferredIndustry, companyIndustry) +
                    collectIndustryValues(rawLines)
            )
        )
    }

    private enum class SuggestionKind {
        NAME,
        COMPANY,
        TITLE,
        EMAIL,
        PHONE,
        WEBSITE,
        ADDRESS,
        INDUSTRY
    }

    private data class NormalizedSuggestion(
        val display: String,
        val key: String
    )

    private fun collectAuditValues(
        audit: OcrExtractionAudit,
        fieldType: OcrFieldType,
        quarantinedLines: Set<Int>
    ): List<String> {
        val fieldAudit = audit.fieldAudits[fieldType] ?: return emptyList()
        return buildList {
            fieldAudit.selectedCandidate?.let { candidate ->
                if (candidate.lineIndex !in quarantinedLines) {
                    add(candidate.text)
                }
            }
            fieldAudit.alternatives.forEach { candidate ->
                if (candidate.lineIndex !in quarantinedLines) {
                    add(candidate.text)
                }
            }
        }
    }

    private fun collectAddressValues(lines: List<String>): List<String> {
        return lines.filter { line ->
            val normalized = line.lowercase(Locale.US)
            val hasAddressKeyword = addressKeywords.any { keyword ->
                Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(normalized)
            }
            val hasStreetNumber = line.any { it.isDigit() }
            hasStreetNumber && hasAddressKeyword || zipRegex.containsMatchIn(normalized)
        }
    }

    private fun collectIndustryValues(lines: List<String>): List<String> {
        return lines.mapNotNull { line ->
            val (industry, confidence) = IndustryCatalog.inferIndustry(title = line, company = line)
            industry?.takeIf { confidence == "high" || confidence == "medium" }
        }
    }

    private fun buildSuggestions(kind: SuggestionKind, rawValues: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        val output = mutableListOf<String>()
        for (raw in rawValues) {
            val normalized = normalizeSuggestion(raw, kind) ?: continue
            if (seen.add(normalized.key)) {
                output += normalized.display
            }
            if (output.size >= MAX_SUGGESTIONS_PER_FIELD) {
                break
            }
        }
        return output
    }

    private fun normalizeSuggestion(raw: String, kind: SuggestionKind): NormalizedSuggestion? {
        val cleaned = cleanDecorators(raw)
        if (cleaned.isBlank()) return null
        if (kind != SuggestionKind.ADDRESS && cleaned.length > 60) return null
        if (kind == SuggestionKind.ADDRESS && cleaned.length > 120) return null
        if (isJunkSuggestion(cleaned, kind)) return null

        return when (kind) {
            SuggestionKind.EMAIL -> {
                val value = cleaned.lowercase(Locale.US)
                if (!emailRegex.matches(value)) return null
                NormalizedSuggestion(display = value, key = value)
            }

            SuggestionKind.PHONE -> {
                val canonical = normalizePhoneCanonical(cleaned) ?: return null
                val display = formatPhoneForDisplay(canonical)
                NormalizedSuggestion(display = display, key = canonical)
            }

            SuggestionKind.WEBSITE -> {
                val website = normalizeWebsite(cleaned) ?: return null
                NormalizedSuggestion(display = website, key = website.lowercase(Locale.US))
            }

            SuggestionKind.NAME -> {
                val display = toTitleCase(cleaned, preserveAcronyms = false)
                NormalizedSuggestion(display = display, key = display.lowercase(Locale.US))
            }

            SuggestionKind.TITLE -> {
                val display = toTitleCase(cleaned, preserveAcronyms = true)
                NormalizedSuggestion(display = display, key = display.lowercase(Locale.US))
            }

            SuggestionKind.COMPANY -> {
                NormalizedSuggestion(display = cleaned, key = cleaned.lowercase(Locale.US))
            }

            SuggestionKind.ADDRESS -> {
                NormalizedSuggestion(display = cleaned, key = cleaned.lowercase(Locale.US))
            }

            SuggestionKind.INDUSTRY -> {
                val match = IndustryCatalog.industries.firstOrNull { option ->
                    option.equals(cleaned, ignoreCase = true)
                }
                val display = match ?: toTitleCase(cleaned, preserveAcronyms = true)
                NormalizedSuggestion(display = display, key = display.lowercase(Locale.US))
            }
        }
    }

    private fun isJunkSuggestion(value: String, kind: SuggestionKind): Boolean {
        val lower = value.lowercase(Locale.US)
        if (kind == SuggestionKind.NAME || kind == SuggestionKind.COMPANY || kind == SuggestionKind.TITLE) {
            if (marketingPhrases.any { lower.contains(it) }) return true
            if (emailRegex.containsMatchIn(value) || websiteRegex.containsMatchIn(value) || phoneRegex.containsMatchIn(value)) {
                return true
            }
            val words = value.split(Regex("\\s+"))
                .map { it.filter(Char::isLetter).lowercase(Locale.US) }
                .filter { it.isNotBlank() }
            if (words.isNotEmpty() && words.size <= 3 && words.all { it in genericOnlyWords }) {
                return true
            }
        }
        return false
    }

    private fun cleanDecorators(value: String): String {
        return value
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replace(Regex("\\s*[|\\u2022\\u00B7]+\\s*"), " ")
            .replace(Regex("\\s*-{2,}\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ',', ';', ':', '|', '-', '_', '\u2022', '\u00B7')
    }

    private fun toTitleCase(value: String, preserveAcronyms: Boolean): String {
        return value.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.split("-").joinToString("-") { subWord ->
                    val lowered = subWord.lowercase(Locale.US)
                    if (preserveAcronyms && lowered in roleAcronyms) {
                        lowered.uppercase(Locale.US)
                    } else if (subWord.length <= 1) {
                        subWord.uppercase(Locale.US)
                    } else {
                        lowered.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                        }
                    }
                }
            }
    }

    private fun formatPhoneForDisplay(canonical: String): String {
        val digits = canonical.filter { it.isDigit() }
        return when {
            canonical.startsWith("+") && digits.length == 11 && digits.startsWith("1") -> {
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 11)}"
            }

            canonical.startsWith("+") -> "+$digits"
            digits.length == 10 -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6, 10)}"
            else -> canonical
        }
    }

    private fun normalizePhoneCanonical(raw: String): String? {
        val source = raw.trim()
        if (source.isBlank()) return null

        val withoutExtension = source
            .replace(Regex("(?i)\\s*(ext|extension|x)\\s*\\d+$"), "")
            .trim()
        if (withoutExtension.isBlank()) return null

        val hasLeadingPlus = withoutExtension.startsWith("+")
        val digits = withoutExtension.filter { it.isDigit() }
        if (digits.length < 7) return null

        if (hasLeadingPlus) {
            return if (digits.length in 8..15) "+$digits" else null
        }
        if (withoutExtension.startsWith("00") && digits.length > 2) {
            val internationalDigits = digits.drop(2)
            if (internationalDigits.length in 8..15) {
                return "+$internationalDigits"
            }
        }
        if (digits.length == 10) return "+1$digits"
        if (digits.length == 11 && digits.startsWith("1")) return "+$digits"
        return if (digits.length in 8..15) "+$digits" else null
    }

    private fun normalizeWebsite(raw: String): String? {
        val cleaned = raw.trim().trimEnd('/')
        if (cleaned.isBlank() || cleaned.contains(" ")) return null
        val withScheme = if (cleaned.startsWith("http://", ignoreCase = true) ||
            cleaned.startsWith("https://", ignoreCase = true)
        ) {
            cleaned
        } else {
            "https://$cleaned"
        }
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host?.lowercase(Locale.US) ?: return null
            val path = uri.path.orEmpty().trimEnd('/').takeIf { it.isNotBlank() && it != "/" }
            val query = uri.rawQuery
                ?.split("&")
                ?.map { it.trim() }
                ?.filter { part ->
                    val key = part.substringBefore("=").lowercase(Locale.US)
                    key.isNotBlank() && key !in trackingQueryKeys
                }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString("&")

            buildString {
                append("https://")
                append(host)
                if (!path.isNullOrBlank()) {
                    if (!path.startsWith("/")) append("/")
                    append(path)
                }
                if (!query.isNullOrBlank()) {
                    append("?")
                    append(query)
                }
            }
        }.getOrNull()
    }
}
