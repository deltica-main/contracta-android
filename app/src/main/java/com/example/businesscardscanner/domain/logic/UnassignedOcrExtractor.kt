package com.example.businesscardscanner.domain.logic

import java.util.Locale
import kotlin.math.abs

enum class UnassignedItemKind {
    TITLE_LIKE,
    ADDRESS_LIKE,
    COMPANY_LIKE,
    OTHER
}

data class UnassignedOcrItem(
    val id: String,
    val displayText: String,
    val lines: List<String>,
    val lineIndices: List<Int>,
    val kind: UnassignedItemKind,
    val isGrouped: Boolean
)

object UnassignedOcrExtractor {
    private val emailRegex =
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val websiteRegex =
        Regex("\\b(?:https?://)?(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val separatorRegex = Regex("^[|/\\\\\\-_=~*.]+$")
    private val zipRegex = Regex("\\b\\d{5}(?:-\\d{4})?\\b")
    private val canadianPostalRegex =
        Regex("\\b[ABCEGHJKLMNPRSTVXY]\\d[ABCEGHJKLMNPRSTVXY]\\s?\\d[ABCEGHJKLMNPRSTVXY]\\d\\b", RegexOption.IGNORE_CASE)

    private val roleKeywords = setOf(
        "founder", "co-founder", "owner", "principal", "managing partner",
        "general partner", "senior partner", "associate partner",
        "ceo", "cto", "cfo", "cio", "coo", "cmo", "cso", "cro", "cdo", "cpo",
        "chro", "cao", "president", "vice president", "assistant vice president",
        "associate vice president", "vp", "svp", "evp", "avp", "chair", "chairperson",
        "chairman", "chairwoman", "board member", "board director", "chief", "officer",
        "managing director", "executive director", "director", "senior director",
        "associate director", "art director", "creative director",
        "manager", "general manager", "program manager", "project manager",
        "product manager", "portfolio manager", "relationship manager", "account manager",
        "operations manager", "supervisor", "coordinator", "administrator", "head",
        "lead", "team lead", "strategy", "strategist", "planner",
        "engineer", "engineering", "principal engineer", "staff engineer",
        "software engineer", "systems engineer", "network engineer", "security engineer",
        "quality engineer", "qa engineer", "test engineer", "devops engineer",
        "site reliability engineer", "sre", "data engineer", "ml engineer",
        "machine learning engineer", "ai engineer", "developer", "development",
        "architect", "solution architect", "enterprise architect", "cloud architect",
        "designer", "technologist", "asset management technologist", "technician",
        "scientist", "researcher", "analyst", "business analyst", "specialist",
        "consultant", "advisor", "adviser", "associate",
        "sales", "sales manager", "account executive", "representative", "agent",
        "broker", "marketing", "marketing manager", "communications",
        "business development", "customer success", "customer support", "support engineer",
        "recruiter", "talent acquisition", "human resources", "hr manager",
        "accountant", "controller", "auditor", "treasurer",
        "counsel", "attorney", "lawyer", "paralegal",
        "operations", "operator", "product"
    )

    private val corporateKeywords = setOf(
        "inc", "inc.", "incorporated", "llc", "l.l.c", "ltd", "ltd.", "limited",
        "corp", "corporation", "company", "co", "co.", "group", "holdings",
        "ventures", "partners", "associates", "solutions", "systems",
        "technologies", "technology", "studio", "studios", "consulting", "services", "llp"
    )

    private val addressKeywords = setOf(
        "street", "st", "st.", "road", "rd", "rd.", "avenue", "ave", "ave.", "boulevard", "blvd",
        "suite", "ste", "floor", "fl", "drive", "dr", "dr.", "lane", "ln", "ln.",
        "court", "ct", "ct.", "highway", "hwy", "building", "bldg", "po box", "box",
        "parkway", "pkwy", "terrace", "ter", "place", "pl", "circle", "cir", "way", "trail", "trl"
    )

    private val marketingKeywords = setOf(
        "your partner", "group of companies", "trusted", "innovative", "delivering",
        "powering", "connecting", "transforming", "committed", "excellence",
        "future", "mission", "vision", "solutions for", "leading", "empowering"
    )

    fun extract(
        audit: OcrExtractionAudit,
        selectedValues: Map<OcrFieldType, String?> = emptyMap()
    ): List<UnassignedOcrItem> {
        if (audit.normalizedLines.isEmpty()) return emptyList()

        val usedLineIndices = audit.fieldAudits.values
            .mapNotNull { it.selectedCandidate }
            .flatMap { it.sourceLineIndices }
            .toSet()
        val quarantinedIndices = audit.quarantinedSlogans.map { it.lineIndex }.toSet()

        val selectedEmail = selectedValues[OcrFieldType.EMAIL]?.trim().orEmpty()
        val selectedPhone = selectedValues[OcrFieldType.PHONE]?.trim().orEmpty()
        val selectedWebsite = selectedValues[OcrFieldType.WEBSITE]?.trim().orEmpty()
        val hasCapturedEmail = selectedEmail.isNotBlank()
        val hasCapturedPhone = selectedPhone.isNotBlank()
        val hasCapturedWebsite = selectedWebsite.isNotBlank()

        val candidates = audit.normalizedLines
            .sortedBy { it.index }
            .filter { line ->
                line.index !in usedLineIndices &&
                    line.index !in quarantinedIndices &&
                    !isDecorative(line.text) &&
                    !isCapturedDeterministicLine(
                        text = line.text,
                        hasCapturedEmail = hasCapturedEmail,
                        hasCapturedPhone = hasCapturedPhone,
                        hasCapturedWebsite = hasCapturedWebsite,
                        selectedEmail = selectedEmail,
                        selectedPhone = selectedPhone,
                        selectedWebsite = selectedWebsite
                    ) &&
                    !looksLikeSlogan(line.text)
            }

        if (candidates.isEmpty()) return emptyList()

        val byIndex = candidates.associateBy { it.index }
        val consumed = mutableSetOf<Int>()
        val groupedItems = mutableListOf<UnassignedOcrItem>()

        audit.addressBlocks.forEach { block ->
            val sortedIndices = block.distinct().sorted()
            if (sortedIndices.size < 2) return@forEach
            if (!sortedIndices.all { it in byIndex }) return@forEach
            if (sortedIndices.any { it in consumed }) return@forEach
            val lines = sortedIndices.mapNotNull { byIndex[it]?.text?.trim() }.filter { it.isNotBlank() }
            if (lines.size < 2) return@forEach
            val item = buildItem(
                lines = lines,
                lineIndices = sortedIndices,
                isGrouped = true
            )
            groupedItems += item
            consumed += sortedIndices
        }

        val remaining = candidates.filter { it.index !in consumed }.sortedBy { it.index }
        var cluster = mutableListOf<NormalizedOcrLine>()

        fun flushCluster() {
            if (cluster.isEmpty()) return
            if (cluster.size >= 2 && cluster.any { looksLikeAddress(it.text) }) {
                val indices = cluster.map { it.index }
                val lines = cluster.map { it.text.trim() }.filter { it.isNotBlank() }
                groupedItems += buildItem(lines = lines, lineIndices = indices, isGrouped = true)
                consumed += indices
            }
            cluster = mutableListOf()
        }

        remaining.forEach { current ->
            val previous = cluster.lastOrNull()
            if (previous == null) {
                cluster += current
                return@forEach
            }
            val shouldJoin = canJoinGroup(previous, current)
            if (shouldJoin) {
                cluster += current
            } else {
                flushCluster()
                cluster += current
            }
        }
        flushCluster()

        val singleItems = remaining
            .filter { it.index !in consumed }
            .map { line ->
                buildItem(
                    lines = listOf(line.text.trim()),
                    lineIndices = listOf(line.index),
                    isGrouped = false
                )
            }

        val deduped = linkedMapOf<String, UnassignedOcrItem>()
        (groupedItems + singleItems)
            .sortedWith(compareBy<UnassignedOcrItem> { it.lineIndices.firstOrNull() ?: Int.MAX_VALUE })
            .forEach { item ->
                val key = normalizeForDedup(item.displayText)
                if (key.isNotBlank() && !deduped.containsKey(key)) {
                    deduped[key] = item
                }
            }

        return deduped.values
            .sortedWith(
                compareByDescending<UnassignedOcrItem> { orderingScore(it) }
                    .thenBy { it.lineIndices.firstOrNull() ?: Int.MAX_VALUE }
            )
    }

    private fun buildItem(
        lines: List<String>,
        lineIndices: List<Int>,
        isGrouped: Boolean
    ): UnassignedOcrItem {
        val cleanLines = lines.map { it.trim() }.filter { it.isNotBlank() }
        val sortedIndices = lineIndices.distinct().sorted()
        val displayText = cleanLines.joinToString("\n")
        val id = if (sortedIndices.isNotEmpty()) {
            "line_${sortedIndices.joinToString("_")}"
        } else {
            "line_${normalizeForDedup(displayText).hashCode()}"
        }
        return UnassignedOcrItem(
            id = id,
            displayText = displayText,
            lines = cleanLines,
            lineIndices = sortedIndices,
            kind = classifyKind(displayText),
            isGrouped = isGrouped
        )
    }

    private fun orderingScore(item: UnassignedOcrItem): Int {
        val base = when (item.kind) {
            UnassignedItemKind.TITLE_LIKE -> 40
            UnassignedItemKind.ADDRESS_LIKE -> 36
            UnassignedItemKind.COMPANY_LIKE -> 30
            UnassignedItemKind.OTHER -> 20
        }
        return base + item.lines.size.coerceAtMost(3)
    }

    private fun classifyKind(text: String): UnassignedItemKind {
        return when {
            looksLikeTitle(text) -> UnassignedItemKind.TITLE_LIKE
            looksLikeAddress(text) -> UnassignedItemKind.ADDRESS_LIKE
            looksLikeCompany(text) -> UnassignedItemKind.COMPANY_LIKE
            else -> UnassignedItemKind.OTHER
        }
    }

    private fun looksLikeTitle(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return containsAnyKeyword(lower, roleKeywords)
    }

    private fun looksLikeAddress(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        val hasStreet = containsAnyKeyword(lower, addressKeywords)
        val hasPostal = zipRegex.containsMatchIn(text) || canadianPostalRegex.containsMatchIn(text)
        val hasNumber = Regex("(^|\\s)\\d+[A-Za-z]?(\\s|$)").containsMatchIn(text)
        return (hasStreet && hasNumber) || hasPostal
    }

    private fun looksLikeCompany(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        if (containsAnyKeyword(lower, corporateKeywords)) return true
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val upperWords = words.count { token ->
            token.any { it.isLetter() } && token.filter { it.isLetter() }.all { it.isUpperCase() }
        }
        return words.isNotEmpty() && upperWords.toDouble() / words.size.toDouble() >= 0.66
    }

    private fun isCapturedDeterministicLine(
        text: String,
        hasCapturedEmail: Boolean,
        hasCapturedPhone: Boolean,
        hasCapturedWebsite: Boolean,
        selectedEmail: String,
        selectedPhone: String,
        selectedWebsite: String
    ): Boolean {
        val lower = text.lowercase(Locale.US)
        if (hasCapturedEmail && (emailRegex.containsMatchIn(text) || lower.contains(selectedEmail.lowercase(Locale.US)))) {
            return true
        }
        if (hasCapturedPhone && (phoneRegex.containsMatchIn(text) || selectedPhone.any { it.isDigit() } && digits(text).contains(digits(selectedPhone)))) {
            return true
        }
        if (hasCapturedWebsite && (websiteRegex.containsMatchIn(text) || lower.contains(selectedWebsite.lowercase(Locale.US)))) {
            return true
        }
        return false
    }

    private fun digits(text: String): String {
        return text.filter { it.isDigit() }
    }

    private fun looksLikeSlogan(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hits = marketingKeywords.count { keyword ->
            if (keyword.any { it.isWhitespace() || it == '.' }) {
                lower.contains(keyword)
            } else {
                Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(lower)
            }
        }
        val hasDigits = text.any { it.isDigit() }
        return !hasDigits && words.size >= 4 && hits >= 1
    }

    private fun isDecorative(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return true
        if (separatorRegex.matches(trimmed)) return true
        if (!trimmed.any { it.isLetterOrDigit() }) return true
        if (trimmed.length == 1) return true
        val alphaNumCount = trimmed.count { it.isLetterOrDigit() }
        if (alphaNumCount <= 2 && trimmed.length <= 3) return true
        return false
    }

    private fun canJoinGroup(previous: NormalizedOcrLine, current: NormalizedOcrLine): Boolean {
        val consecutive = current.index == previous.index + 1
        if (!consecutive) return false
        if (previous.left != null && current.left != null && previous.bottom != null && current.top != null) {
            val leftDelta = abs(previous.left - current.left)
            val verticalGap = current.top - previous.bottom
            return leftDelta <= 24f && verticalGap in -4f..28f
        }
        return true
    }

    private fun containsAnyKeyword(valueLower: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword ->
            if (keyword.any { it.isWhitespace() || it == '.' }) {
                valueLower.contains(keyword)
            } else {
                Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(valueLower)
            }
        }
    }

    private fun normalizeForDedup(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
