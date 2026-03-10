
package ca.deltica.contactra.domain.logic

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class OcrFieldType {
    NAME,
    TITLE,
    COMPANY,
    EMAIL,
    PHONE,
    WEBSITE,
    ADDRESS
}

enum class OcrLineLabel {
    PERSON_NAME,
    COMPANY_NAME,
    JOB_TITLE,
    EMAIL,
    PHONE,
    WEBSITE,
    ADDRESS,
    TAGLINE_OR_SLOGAN,
    OTHER
}

data class NormalizedOcrLine(
    val text: String,
    val index: Int,
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null
)

data class OcrFieldCandidate(
    val field: OcrFieldType,
    val text: String,
    val lineIndex: Int,
    val confidence: Double,
    val label: OcrLineLabel,
    val reasons: List<String> = emptyList(),
    val sourceLineIndices: List<Int> = listOf(lineIndex)
)

data class OcrFieldAudit(
    val selectedCandidate: OcrFieldCandidate? = null,
    val alternatives: List<OcrFieldCandidate> = emptyList(),
    val evidenceLineIndices: List<Int> = emptyList(),
    val notes: List<String> = emptyList(),
    val debugRejectionCounts: Map<String, Int> = emptyMap()
)

data class OcrCoverageResidualLine(
    val text: String,
    val lineIndex: Int
)

data class OcrExtractionAudit(
    val schemaVersion: Int = 2,
    val normalizedLines: List<NormalizedOcrLine> = emptyList(),
    val fieldAudits: Map<OcrFieldType, OcrFieldAudit> = emptyMap(),
    val quarantinedSlogans: List<OcrFieldCandidate> = emptyList(),
    val addressBlocks: List<List<Int>> = emptyList(),
    val coverageFallbackLines: List<OcrCoverageResidualLine> = emptyList(),
    val validationNotes: List<String> = emptyList()
)

data class OcrAuditParseResult(
    val values: Map<OcrFieldType, String?>,
    val confidenceByField: Map<OcrFieldType, Double>,
    val audit: OcrExtractionAudit
)

object OcrAuditParser {
    private val emailRegex =
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val websiteRegex =
        Regex("\\b(?:https?://)?(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val usZipRegex = Regex("\\b\\d{5}(?:-\\d{4})?\\b")
    private val canadianPostalRegex =
        Regex("\\b[ABCEGHJKLMNPRSTVXY]\\d[ABCEGHJKLMNPRSTVXY]\\s?\\d[ABCEGHJKLMNPRSTVXY]\\d\\b", RegexOption.IGNORE_CASE)
    private val splitRegex = Regex("\\s+(?:\\||/|--)\\s+")

    private val corporateKeywords = setOf(
        "inc", "inc.", "incorporated", "llc", "l.l.c", "ltd", "ltd.", "limited",
        "corp", "corporation", "company", "co", "co.", "group", "holdings",
        "ventures", "partners", "associates", "solutions", "systems",
        "technologies", "technology", "studio", "studios", "hydro", "energy",
        "electric", "engineering", "consulting", "services", "llp"
    )

    private val corporateSuffixTokens = setOf(
        "inc", "inc.", "ltd", "ltd.", "corporation", "company", "corp", "llp", "llc", "co", "co."
    )

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

    private val addressKeywords = setOf(
        "street", "st", "st.", "road", "rd", "rd.", "avenue", "ave", "ave.", "boulevard", "blvd",
        "suite", "ste", "floor", "fl", "drive", "dr", "dr.", "lane", "ln", "ln.",
        "court", "ct", "ct.", "highway", "hwy", "building", "bldg", "po box", "box",
        "parkway", "pkwy", "terrace", "ter", "place", "pl", "circle", "cir", "way", "trail", "trl"
    )

    private val unitKeywords = setOf(
        "unit", "suite", "ste", "floor", "fl", "apt", "apartment"
    )

    private val marketingKeywords = setOf(
        "your partner", "group of companies", "trusted", "innovative", "delivering",
        "powering", "connecting", "transforming", "committed", "excellence",
        "future", "mission", "vision", "solutions for", "leading", "empowering"
    )

    private val cityRegionRegex =
        Regex("\\b[\\p{L}][\\p{L}.' ]{1,}(?:,\\s*|\\s+)[A-Z]{2}\\b")

    private const val schemaVersion = 2
    private const val companyTitleSwapMargin = 0.18

    fun parse(
        rawText: String,
        providedLines: List<NormalizedOcrLine> = emptyList(),
        relaxed: Boolean = false
    ): OcrAuditParseResult {
        val normalizedLines = normalizeLines(rawText, providedLines)
        if (normalizedLines.isEmpty()) {
            return OcrAuditParseResult(
                values = emptyValues(),
                confidenceByField = emptyConfidence(),
                audit = OcrExtractionAudit(schemaVersion = schemaVersion)
            )
        }

        val lineScores = scoreLines(normalizedLines)
        val scoreByIndex = lineScores.associateBy { it.line.index }

        val quarantined = lineScores
            .filter { shouldQuarantine(it) }
            .map {
                OcrFieldCandidate(
                    field = OcrFieldType.COMPANY,
                    text = it.line.text,
                    lineIndex = it.line.index,
                    confidence = max(it.taglineScore, it.logoScore).coerceIn(0.0, 1.0),
                    label = OcrLineLabel.TAGLINE_OR_SLOGAN,
                    reasons = listOf("Detected as slogan or logo-like marketing text.")
                )
            }
        val quarantinedLineSet = quarantined.map { it.lineIndex }.toSet()

        val addressBlocks = buildAddressBlocks(
            lines = normalizedLines,
            scoresByIndex = scoreByIndex,
            quarantinedLines = quarantinedLineSet
        )

        val nameCandidates = buildNameCandidates(lineScores, quarantinedLineSet, relaxed)
        val titleCandidates = buildTitleCandidates(lineScores, relaxed)
        val companyCandidates = buildCompanyCandidates(lineScores, quarantinedLineSet, relaxed)
        val emailCandidates = buildEmailCandidates(lineScores)
        val phoneCandidates = buildPhoneCandidates(lineScores)
        val websiteCandidates = buildWebsiteCandidates(lineScores)
        val addressCandidates = buildAddressCandidates(
            lineScores = lineScores,
            blocks = addressBlocks,
            quarantinedLines = quarantinedLineSet,
            relaxed = relaxed
        )

        val usedCoreLines = mutableSetOf<Int>()
        val coreThreshold = if (relaxed) 0.45 else 0.55
        val titleThreshold = if (relaxed) 0.32 else 0.4
        val addressThreshold = if (relaxed) 0.42 else 0.52

        val titlePreferredLines = findTitlePreferredLines(lineScores, titleThreshold)

        var companyAudit = selectCandidate(
            field = OcrFieldType.COMPANY,
            candidates = companyCandidates,
            minConfidence = coreThreshold,
            usedCoreLines = usedCoreLines,
            quarantineLines = quarantinedLineSet,
            titleReservedLines = titlePreferredLines,
            validator = { candidate -> validateCompany(candidate.text) }
        )

        val nameAudit = selectCandidate(
            field = OcrFieldType.NAME,
            candidates = nameCandidates,
            minConfidence = coreThreshold,
            usedCoreLines = usedCoreLines,
            quarantineLines = quarantinedLineSet,
            validator = { candidate -> validatePersonName(candidate.text) }
        )

        var titleAudit = selectCandidate(
            field = OcrFieldType.TITLE,
            candidates = titleCandidates,
            minConfidence = titleThreshold,
            usedCoreLines = usedCoreLines,
            quarantineLines = quarantinedLineSet,
            validator = { candidate -> validateJobTitle(candidate.text) }
        )

        val titleRecovery = runTitleSecondPass(
            titleAudit = titleAudit,
            companyAudit = companyAudit,
            nameAudit = nameAudit,
            titleCandidates = titleCandidates,
            companyCandidates = companyCandidates,
            scoresByIndex = scoreByIndex,
            usedCoreLines = usedCoreLines,
            quarantineLines = quarantinedLineSet,
            titleThreshold = titleThreshold,
            companyThreshold = coreThreshold
        )
        titleAudit = titleRecovery.titleAudit
        companyAudit = titleRecovery.companyAudit

        val emailAudit = selectNonCoreCandidate(
            candidates = emailCandidates,
            minConfidence = if (relaxed) 0.75 else 0.85
        )
        val phoneAudit = selectNonCoreCandidate(
            candidates = phoneCandidates,
            minConfidence = if (relaxed) 0.6 else 0.72
        )
        val websiteAudit = selectNonCoreCandidate(
            candidates = websiteCandidates,
            minConfidence = if (relaxed) 0.55 else 0.68
        )
        val addressAudit = selectAddressCandidate(
            candidates = addressCandidates,
            minConfidence = addressThreshold,
            quarantineLines = quarantinedLineSet
        )

        val fieldAudits = linkedMapOf(
            OcrFieldType.NAME to nameAudit,
            OcrFieldType.TITLE to titleAudit,
            OcrFieldType.COMPANY to companyAudit,
            OcrFieldType.EMAIL to emailAudit,
            OcrFieldType.PHONE to phoneAudit,
            OcrFieldType.WEBSITE to websiteAudit,
            OcrFieldType.ADDRESS to addressAudit
        )

        val values = OcrFieldType.values().associateWith { field ->
            fieldAudits[field]?.selectedCandidate?.text
        }
        val confidenceByField = OcrFieldType.values().associateWith { field ->
            fieldAudits[field]?.selectedCandidate?.confidence ?: 0.0
        }

        val coverageFallbackLines = buildCoverageFallbackLines(
            normalizedLines = normalizedLines,
            scoresByIndex = scoreByIndex,
            quarantinedLines = quarantinedLineSet,
            fieldAudits = fieldAudits
        )

        val validationNotes = buildList {
            OcrFieldType.values().forEach { field ->
                val audit = fieldAudits[field] ?: return@forEach
                if (audit.selectedCandidate == null) {
                    add("${field.name.lowercase(Locale.US)} unresolved.")
                }
                addAll(audit.notes.map { note -> "${field.name.lowercase(Locale.US)}: $note" })
            }
        }

        return OcrAuditParseResult(
            values = values,
            confidenceByField = confidenceByField,
            audit = OcrExtractionAudit(
                schemaVersion = schemaVersion,
                normalizedLines = normalizedLines,
                fieldAudits = fieldAudits,
                quarantinedSlogans = quarantined,
                addressBlocks = addressBlocks.map { it.lineIndices },
                coverageFallbackLines = coverageFallbackLines,
                validationNotes = validationNotes
            )
        )
    }

    private data class LineSignals(
        val words: List<String>,
        val wordCount: Int,
        val digitCount: Int,
        val hasEmail: Boolean,
        val hasPhone: Boolean,
        val hasWebsite: Boolean,
        val hasContactTokens: Boolean,
        val hasCorporateKeyword: Boolean,
        val hasCorporateSuffixToken: Boolean,
        val hasRoleKeyword: Boolean,
        val hasMarketingKeyword: Boolean,
        val marketingKeywordHits: Int,
        val hasAddressKeyword: Boolean,
        val hasStreetNumber: Boolean,
        val hasStreetType: Boolean,
        val hasUnitMarker: Boolean,
        val hasUsZip: Boolean,
        val hasCanadianPostal: Boolean,
        val hasPostal: Boolean,
        val hasCityRegion: Boolean,
        val hasAmpersand: Boolean,
        val titleCaseRatio: Double,
        val upperCaseRatio: Double,
        val decorative: Boolean
    )

    private data class LineScore(
        val line: NormalizedOcrLine,
        val positionRatio: Double,
        val nameScore: Double,
        val titleScore: Double,
        val companyScore: Double,
        val emailScore: Double,
        val phoneScore: Double,
        val websiteScore: Double,
        val addressScore: Double,
        val taglineScore: Double,
        val logoScore: Double,
        val hasContactTokens: Boolean,
        val hasStreetNumber: Boolean,
        val hasStreetType: Boolean,
        val hasUnitMarker: Boolean,
        val hasPostal: Boolean,
        val hasCityRegion: Boolean,
        val hasMarketingKeyword: Boolean,
        val hasCorporateKeyword: Boolean,
        val hasCorporateSuffixToken: Boolean,
        val hasRoleKeyword: Boolean,
        val decorative: Boolean,
        val reasons: List<String>
    )

    private data class AddressBlock(
        val lines: List<NormalizedOcrLine>,
        val lineIndices: List<Int>,
        val coherence: Double,
        val usedGeometry: Boolean
    )

    private data class TitleRecoveryResult(
        val titleAudit: OcrFieldAudit,
        val companyAudit: OcrFieldAudit
    )

    private fun emptyValues(): Map<OcrFieldType, String?> {
        return OcrFieldType.values().associateWith { null }
    }

    private fun emptyConfidence(): Map<OcrFieldType, Double> {
        return OcrFieldType.values().associateWith { 0.0 }
    }

    private fun normalizeLines(
        rawText: String,
        providedLines: List<NormalizedOcrLine>
    ): List<NormalizedOcrLine> {
        val source = if (providedLines.isNotEmpty()) {
            providedLines
        } else {
            rawText
                .lines()
                .mapIndexed { index, text ->
                    NormalizedOcrLine(text = text, index = index)
                }
        }
        return source
            .flatMap { line ->
                val cleaned = sanitizeLine(line.text)
                if (cleaned.isBlank()) {
                    emptyList()
                } else {
                    cleaned.split(splitRegex)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { part -> line.copy(text = part) }
                }
            }
            .filter { it.text.any { ch -> ch.isLetterOrDigit() } }
            .mapIndexed { idx, line -> line.copy(index = idx) }
    }

    private fun sanitizeLine(value: String): String {
        return value
            .replace("\t", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('|', '/', '-', ',', ';')
    }

    private fun scoreLines(lines: List<NormalizedOcrLine>): List<LineScore> {
        val maxBottom = lines.maxOfOrNull { it.bottom ?: 0f } ?: 0f
        return lines.map { line ->
            val signals = analyzeLine(line.text)
            val positionRatio = computeVerticalRatio(line, maxBottom, lines.size)
            val reasons = mutableListOf<String>()

            var emailScore = 0.0
            if (signals.hasEmail) {
                emailScore = 0.98
                reasons += "contains email pattern"
            }

            var phoneScore = 0.0
            if (signals.hasPhone) {
                phoneScore = 0.75 + (signals.digitCount.coerceAtMost(15) / 60.0)
                reasons += "contains phone pattern"
            }

            var websiteScore = 0.0
            if (signals.hasWebsite) {
                websiteScore = if (line.text.lowercase(Locale.US).contains("http") || line.text.lowercase(Locale.US).contains("www.")) 0.95 else 0.82
                reasons += "contains website pattern"
            }

            var taglineScore = 0.0
            if (signals.hasMarketingKeyword) {
                taglineScore += 0.45
                reasons += "contains marketing language"
            }
            if (signals.wordCount >= 6 && !signals.hasContactTokens && signals.digitCount == 0) {
                taglineScore += 0.15
            }
            if (positionRatio >= 0.6) {
                taglineScore += 0.08
            }
            if (line.text.lowercase(Locale.US).contains("group of companies")) {
                taglineScore += 0.2
            }
            val marketingDensity = if (signals.wordCount == 0) 0.0 else {
                signals.marketingKeywordHits.toDouble() / signals.wordCount.toDouble()
            }
            if (
                signals.wordCount >= 8 &&
                signals.digitCount == 0 &&
                !signals.hasContactTokens &&
                marketingDensity >= 0.2
            ) {
                taglineScore += 0.35
                reasons += "high-density marketing sentence"
            }
            if (signals.hasRoleKeyword) {
                taglineScore -= 0.35
                reasons += "kept because role keyword present"
            }
            if (signals.hasCorporateSuffixToken && !line.text.lowercase(Locale.US).contains("group of companies")) {
                taglineScore -= 0.25
                reasons += "kept because corporate suffix present"
            }
            taglineScore = taglineScore.coerceIn(0.0, 1.0)

            var logoScore = 0.0
            if (!signals.hasContactTokens && signals.digitCount == 0 && signals.wordCount in 1..3 && signals.upperCaseRatio >= 0.9) {
                logoScore += 0.65
            }
            if (!signals.hasContactTokens && signals.digitCount == 0 && signals.wordCount == 1 && line.text.length >= 4) {
                logoScore += 0.15
            }
            if (signals.hasCorporateSuffixToken || signals.hasRoleKeyword) {
                logoScore -= 0.2
            }
            logoScore = logoScore.coerceIn(0.0, 1.0)

            var nameScore = 0.0
            if (!signals.hasContactTokens && signals.digitCount == 0 && signals.wordCount in 2..4) {
                nameScore += 0.35
                if (signals.titleCaseRatio >= 0.66) {
                    nameScore += 0.35
                } else if (signals.upperCaseRatio >= 0.66) {
                    nameScore += 0.2
                }
                if (positionRatio <= 0.45) {
                    nameScore += 0.15
                }
            }
            if (signals.hasCorporateKeyword) {
                nameScore -= 0.5
            }
            if (signals.hasMarketingKeyword || taglineScore >= 0.55 || logoScore >= 0.8) {
                nameScore -= 0.4
            }
            nameScore = nameScore.coerceIn(0.0, 1.0)

            var titleScore = 0.0
            if (!signals.hasContactTokens && signals.digitCount == 0 && signals.wordCount in 1..8) {
                titleScore += 0.18
                if (signals.hasRoleKeyword) {
                    titleScore += 0.62
                    reasons += "contains role keyword"
                }
                if (line.text.contains("/") || line.text.contains("&")) {
                    titleScore += 0.1
                }
            }
            if (signals.hasCorporateKeyword && !signals.hasRoleKeyword) {
                titleScore -= 0.18
            }
            if (signals.hasStreetType || signals.hasPostal || signals.hasCityRegion) {
                titleScore -= 0.22
            }
            if (taglineScore >= 0.55) {
                titleScore -= 0.3
            }
            titleScore = titleScore.coerceIn(0.0, 1.0)

            var companyScore = 0.0
            if (!signals.hasContactTokens && signals.wordCount >= 1) {
                if (signals.hasCorporateKeyword) {
                    companyScore += 0.6
                    reasons += "contains corporate keyword"
                }
                if (signals.upperCaseRatio >= 0.66) {
                    companyScore += 0.2
                }
                if (signals.hasAmpersand) {
                    companyScore += 0.1
                }
                if (positionRatio <= 0.65) {
                    companyScore += 0.1
                }
            }
            if (signals.hasRoleKeyword) {
                companyScore -= 0.22
            }
            if (taglineScore >= 0.55 && !signals.hasCorporateKeyword) {
                companyScore -= 0.35
            }
            if (signals.hasMarketingKeyword && !signals.hasCorporateKeyword) {
                companyScore -= 0.2
            }
            if (signals.hasMarketingKeyword && !signals.hasRoleKeyword && !signals.hasStreetNumber && !signals.hasPostal) {
                companyScore -= 0.45
            }
            if (line.text.lowercase(Locale.US).contains("group of companies")) {
                companyScore -= 0.5
            }
            companyScore = companyScore.coerceIn(0.0, 1.0)

            var addressScore = 0.0
            if (signals.hasStreetNumber) {
                addressScore += 0.28
            }
            if (signals.hasStreetType) {
                addressScore += 0.24
            }
            if (signals.hasUnitMarker) {
                addressScore += 0.12
            }
            if (signals.hasCityRegion) {
                addressScore += 0.18
            }
            if (signals.hasPostal) {
                addressScore += 0.28
            }
            if (signals.hasStreetNumber && signals.hasStreetType) {
                addressScore += 0.1
            }
            if (positionRatio >= 0.45) {
                addressScore += 0.08
            }
            if (signals.hasMarketingKeyword && !signals.hasPostal && !signals.hasStreetType) {
                addressScore -= 0.35
            }
            if (signals.hasCorporateKeyword && !signals.hasStreetType && !signals.hasPostal && !signals.hasStreetNumber) {
                addressScore -= 0.22
            }
            if (signals.hasContactTokens) {
                addressScore -= 0.4
            }
            addressScore = addressScore.coerceIn(0.0, 1.0)
            if (addressScore >= 0.45) {
                reasons += "address features present"
            }

            LineScore(
                line = line,
                positionRatio = positionRatio,
                nameScore = nameScore,
                titleScore = titleScore,
                companyScore = companyScore,
                emailScore = emailScore,
                phoneScore = phoneScore.coerceIn(0.0, 1.0),
                websiteScore = websiteScore,
                addressScore = addressScore,
                taglineScore = taglineScore,
                logoScore = logoScore,
                hasContactTokens = signals.hasContactTokens,
                hasStreetNumber = signals.hasStreetNumber,
                hasStreetType = signals.hasStreetType,
                hasUnitMarker = signals.hasUnitMarker,
                hasPostal = signals.hasPostal,
                hasCityRegion = signals.hasCityRegion,
                hasMarketingKeyword = signals.hasMarketingKeyword,
                hasCorporateKeyword = signals.hasCorporateKeyword,
                hasCorporateSuffixToken = signals.hasCorporateSuffixToken,
                hasRoleKeyword = signals.hasRoleKeyword,
                decorative = signals.decorative,
                reasons = reasons.distinct()
            )
        }
    }

    private fun analyzeLine(text: String): LineSignals {
        val lower = text.lowercase(Locale.US)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val digitCount = text.count { it.isDigit() }

        val properCaseWords = words.count { token ->
            token.firstOrNull()?.isUpperCase() == true && token.any { it.isLetter() }
        }
        val upperWords = words.count { token ->
            token.any { it.isLetter() } && token.filter { it.isLetter() }.all { it.isUpperCase() }
        }
        val titleCaseRatio = if (wordCount == 0) 0.0 else properCaseWords.toDouble() / wordCount.toDouble()
        val upperCaseRatio = if (wordCount == 0) 0.0 else upperWords.toDouble() / wordCount.toDouble()

        val hasEmail = emailRegex.containsMatchIn(text)
        val hasPhone = phoneRegex.containsMatchIn(text)
        val hasWebsite = websiteRegex.containsMatchIn(text)
        val hasContactTokens = hasEmail || hasPhone || hasWebsite
        val hasCorporateKeyword = containsAnyKeyword(lower, corporateKeywords)
        val hasCorporateSuffixToken = containsAnyKeyword(lower, corporateSuffixTokens)
        val hasRoleKeyword = containsAnyKeyword(lower, roleKeywords)
        val hasMarketingKeyword = containsAnyKeyword(lower, marketingKeywords)
        val marketingKeywordHits = keywordMatchCount(lower, marketingKeywords)
        val hasAddressKeyword = containsAnyKeyword(lower, addressKeywords)
        val hasStreetNumber = Regex("(^|\\s)\\d+[A-Za-z]?(\\s|$)").containsMatchIn(text)
        val hasStreetType = containsAnyKeyword(lower, addressKeywords)
        val hasUnitMarker = containsAnyKeyword(lower, unitKeywords) || Regex("\\b#\\s*\\d+\\b").containsMatchIn(lower)
        val hasUsZip = usZipRegex.containsMatchIn(text)
        val hasCanadianPostal = canadianPostalRegex.containsMatchIn(text)
        val hasPostal = hasUsZip || hasCanadianPostal
        val hasCityRegion = cityRegionRegex.containsMatchIn(text.uppercase(Locale.US))
        val hasAmpersand = text.contains("&")
        val decorative = text.length <= 2 || text.all { !it.isLetterOrDigit() }

        return LineSignals(
            words = words,
            wordCount = wordCount,
            digitCount = digitCount,
            hasEmail = hasEmail,
            hasPhone = hasPhone,
            hasWebsite = hasWebsite,
            hasContactTokens = hasContactTokens,
            hasCorporateKeyword = hasCorporateKeyword,
            hasCorporateSuffixToken = hasCorporateSuffixToken,
            hasRoleKeyword = hasRoleKeyword,
            hasMarketingKeyword = hasMarketingKeyword,
            marketingKeywordHits = marketingKeywordHits,
            hasAddressKeyword = hasAddressKeyword,
            hasStreetNumber = hasStreetNumber,
            hasStreetType = hasStreetType,
            hasUnitMarker = hasUnitMarker,
            hasUsZip = hasUsZip,
            hasCanadianPostal = hasCanadianPostal,
            hasPostal = hasPostal,
            hasCityRegion = hasCityRegion,
            hasAmpersand = hasAmpersand,
            titleCaseRatio = titleCaseRatio,
            upperCaseRatio = upperCaseRatio,
            decorative = decorative
        )
    }

    private fun shouldQuarantine(score: LineScore): Boolean {
        val dominatesCore = score.taglineScore >= 0.5 &&
            score.taglineScore >= max(score.nameScore, max(score.titleScore, max(score.companyScore, score.addressScore)))
        val isStrongLogoOnly = score.logoScore >= 0.8 &&
            score.companyScore < 0.45 &&
            score.titleScore < 0.45 &&
            score.nameScore < 0.45 &&
            !score.hasCorporateSuffixToken
        return dominatesCore || isStrongLogoOnly
    }

    private fun buildAddressBlocks(
        lines: List<NormalizedOcrLine>,
        scoresByIndex: Map<Int, LineScore>,
        quarantinedLines: Set<Int>
    ): List<AddressBlock> {
        val blocks = mutableListOf<AddressBlock>()
        val sorted = lines.sortedBy { it.index }
        var current = mutableListOf<NormalizedOcrLine>()

        fun flushCurrent() {
            if (current.size < 2 || current.size > 4) {
                current = mutableListOf()
                return
            }
            val scoreSlice = current.mapNotNull { scoresByIndex[it.index] }
            val hasAddressSignal = scoreSlice.any {
                it.hasStreetNumber || it.hasStreetType || it.hasUnitMarker || it.hasPostal || it.hasCityRegion
            }
            val hasContactTokens = scoreSlice.any { it.hasContactTokens }
            if (!hasAddressSignal || hasContactTokens) {
                current = mutableListOf()
                return
            }
            val (coherence, usedGeometry) = computeBlockCoherence(current)
            blocks += AddressBlock(
                lines = current.toList(),
                lineIndices = current.map { it.index },
                coherence = coherence,
                usedGeometry = usedGeometry
            )
            current = mutableListOf()
        }

        sorted.forEach { line ->
            val score = scoresByIndex[line.index] ?: return@forEach
            val stop = line.index in quarantinedLines ||
                score.hasContactTokens ||
                score.taglineScore >= 0.72 ||
                score.logoScore >= 0.72
            val hasAddressSignal = score.hasStreetNumber ||
                score.hasUnitMarker ||
                score.hasPostal ||
                score.hasCityRegion ||
                (score.hasStreetType && line.text.any { it.isDigit() }) ||
                score.addressScore >= 0.52
            if (stop || !hasAddressSignal) {
                flushCurrent()
                return@forEach
            }

            if (current.isEmpty()) {
                current += line
                return@forEach
            }

            val previous = current.last()
            if (canJoinAddressBlock(previous, line)) {
                current += line
                if (current.size == 4) {
                    flushCurrent()
                }
            } else {
                flushCurrent()
                current += line
            }
        }
        flushCurrent()

        return blocks
            .distinctBy { it.lineIndices }
            .sortedBy { it.lineIndices.firstOrNull() ?: Int.MAX_VALUE }
    }

    private fun canJoinAddressBlock(previous: NormalizedOcrLine, current: NormalizedOcrLine): Boolean {
        val indexConsecutive = current.index == previous.index + 1
        val bothHaveGeometry = listOf(previous.left, previous.top, previous.right, previous.bottom, current.left, current.top, current.right, current.bottom)
            .all { it != null }
        if (!bothHaveGeometry) {
            return indexConsecutive
        }
        val previousHeight = ((previous.bottom ?: 0f) - (previous.top ?: 0f)).coerceAtLeast(1f)
        val currentHeight = ((current.bottom ?: 0f) - (current.top ?: 0f)).coerceAtLeast(1f)
        val avgHeight = (previousHeight + currentHeight) / 2f

        val leftDelta = abs((current.left ?: 0f) - (previous.left ?: 0f))
        val verticalGap = (current.top ?: 0f) - (previous.bottom ?: 0f)
        val allowedGap = max(10f, avgHeight * 1.7f)
        val leftTolerance = max(18f, avgHeight * 1.8f)

        return leftDelta <= leftTolerance &&
            verticalGap >= -avgHeight * 0.5f &&
            verticalGap <= allowedGap
    }

    private fun computeBlockCoherence(lines: List<NormalizedOcrLine>): Pair<Double, Boolean> {
        if (lines.size < 2) return 0.0 to false
        val bothHaveGeometry = lines.all { it.left != null && it.top != null && it.bottom != null }
        if (!bothHaveGeometry) {
            val consecutive = lines.zipWithNext().all { (a, b) -> b.index == a.index + 1 }
            return if (consecutive) 0.75 to false else 0.45 to false
        }

        val leftDeltas = lines.zipWithNext().map { (a, b) -> abs((b.left ?: 0f) - (a.left ?: 0f)) }
        val gaps = lines.zipWithNext().map { (a, b) -> (b.top ?: 0f) - (a.bottom ?: 0f) }
        val heights = lines.map { ((it.bottom ?: 0f) - (it.top ?: 0f)).coerceAtLeast(1f) }
        val avgHeight = heights.average().toFloat().coerceAtLeast(1f)
        val allowedGap = max(10f, avgHeight * 1.7f)
        val leftTolerance = max(18f, avgHeight * 1.8f)

        val leftPenalty = leftDeltas.map { delta -> (delta / leftTolerance).coerceIn(0f, 1f) }.average()
        val gapPenalty = gaps.map { gap ->
            val normalized = when {
                gap < -avgHeight * 0.5f -> 1f
                gap > allowedGap -> 1f
                else -> abs(gap) / allowedGap
            }
            normalized.coerceIn(0f, 1f)
        }.average()
        val coherence = (1.0 - ((leftPenalty + gapPenalty) / 2.0)).coerceIn(0.0, 1.0)
        return coherence to true
    }

    private fun findTitlePreferredLines(
        lineScores: List<LineScore>,
        titleThreshold: Double
    ): Set<Int> {
        return lineScores
            .filter { score ->
                score.titleScore >= titleThreshold &&
                    score.titleScore - score.companyScore >= companyTitleSwapMargin &&
                    !score.hasContactTokens &&
                    score.taglineScore < 0.55
            }
            .map { it.line.index }
            .toSet()
    }

    private fun buildNameCandidates(
        scores: List<LineScore>,
        quarantinedLines: Set<Int>,
        relaxed: Boolean
    ): List<OcrFieldCandidate> {
        val minScore = if (relaxed) 0.28 else 0.4
        return scores.mapNotNull { score ->
            if (score.line.index in quarantinedLines) return@mapNotNull null
            if (score.nameScore < minScore) return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.NAME,
                text = score.line.text,
                lineIndex = score.line.index,
                confidence = score.nameScore,
                label = OcrLineLabel.PERSON_NAME,
                reasons = score.reasons + "person-name classifier"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildTitleCandidates(
        scores: List<LineScore>,
        relaxed: Boolean
    ): List<OcrFieldCandidate> {
        val minScore = if (relaxed) 0.2 else 0.3
        return scores.mapNotNull { score ->
            if (score.titleScore < minScore) return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.TITLE,
                text = score.line.text,
                lineIndex = score.line.index,
                confidence = score.titleScore,
                label = OcrLineLabel.JOB_TITLE,
                reasons = score.reasons + "job-title classifier"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildCompanyCandidates(
        scores: List<LineScore>,
        quarantinedLines: Set<Int>,
        relaxed: Boolean
    ): List<OcrFieldCandidate> {
        val minScore = if (relaxed) 0.24 else 0.34
        return scores.mapNotNull { score ->
            if (score.line.index in quarantinedLines) return@mapNotNull null
            if (score.companyScore < minScore) return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.COMPANY,
                text = score.line.text,
                lineIndex = score.line.index,
                confidence = score.companyScore,
                label = OcrLineLabel.COMPANY_NAME,
                reasons = score.reasons + "company-name classifier"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildEmailCandidates(scores: List<LineScore>): List<OcrFieldCandidate> {
        return scores.mapNotNull { score ->
            if (score.emailScore <= 0.0) return@mapNotNull null
            val match = emailRegex.find(score.line.text)?.value ?: return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.EMAIL,
                text = match,
                lineIndex = score.line.index,
                confidence = score.emailScore,
                label = OcrLineLabel.EMAIL,
                reasons = score.reasons + "email pattern"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildPhoneCandidates(scores: List<LineScore>): List<OcrFieldCandidate> {
        return scores.mapNotNull { score ->
            if (score.phoneScore <= 0.0) return@mapNotNull null
            val match = phoneRegex.find(score.line.text)?.value ?: return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.PHONE,
                text = match.trim(),
                lineIndex = score.line.index,
                confidence = score.phoneScore,
                label = OcrLineLabel.PHONE,
                reasons = score.reasons + "phone pattern"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildWebsiteCandidates(scores: List<LineScore>): List<OcrFieldCandidate> {
        return scores.mapNotNull { score ->
            if (score.websiteScore <= 0.0) return@mapNotNull null
            val match = websiteRegex.find(score.line.text)?.value ?: return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.WEBSITE,
                text = match.trim().trimEnd('.', ',', ';'),
                lineIndex = score.line.index,
                confidence = score.websiteScore,
                label = OcrLineLabel.WEBSITE,
                reasons = score.reasons + "website pattern"
            )
        }.sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun buildAddressCandidates(
        lineScores: List<LineScore>,
        blocks: List<AddressBlock>,
        quarantinedLines: Set<Int>,
        relaxed: Boolean
    ): List<OcrFieldCandidate> {
        val lineMinScore = if (relaxed) 0.28 else 0.38
        val blockMinScore = if (relaxed) 0.34 else 0.46

        val lineCandidates = lineScores.mapNotNull { score ->
            if (score.line.index in quarantinedLines) return@mapNotNull null
            if (score.addressScore < lineMinScore) return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.ADDRESS,
                text = score.line.text,
                lineIndex = score.line.index,
                confidence = score.addressScore,
                label = OcrLineLabel.ADDRESS,
                reasons = score.reasons + "address-line classifier",
                sourceLineIndices = listOf(score.line.index)
            )
        }

        val blockCandidates = blocks.mapNotNull { block ->
            val score = scoreAddressBlock(block, lineScores)
            if (score.first < blockMinScore) return@mapNotNull null
            OcrFieldCandidate(
                field = OcrFieldType.ADDRESS,
                text = block.lines.joinToString(", ") { it.text.trim() },
                lineIndex = block.lineIndices.first(),
                confidence = score.first,
                label = OcrLineLabel.ADDRESS,
                reasons = score.second,
                sourceLineIndices = block.lineIndices
            )
        }

        return (lineCandidates + blockCandidates)
            .distinctBy { candidate -> "${candidate.text.lowercase(Locale.US)}|${candidate.sourceLineIndices.joinToString(",")}" }
            .sortedWith(compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex })
    }

    private fun scoreAddressBlock(
        block: AddressBlock,
        lineScores: List<LineScore>
    ): Pair<Double, List<String>> {
        val scoreMap = lineScores.associateBy { it.line.index }
        val scores = block.lineIndices.mapNotNull { scoreMap[it] }
        if (scores.isEmpty()) return 0.0 to listOf("empty block")

        val hasStreetNumber = scores.any { it.hasStreetNumber }
        val hasStreetType = scores.any { it.hasStreetType }
        val hasUnit = scores.any { it.hasUnitMarker }
        val hasCityRegion = scores.any { it.hasCityRegion }
        val hasPostal = scores.any { it.hasPostal }
        val hasMarketingDominance = scores.count { it.hasMarketingKeyword } > scores.count {
            it.hasStreetNumber || it.hasStreetType || it.hasPostal || it.hasCityRegion
        }
        val corporateDominanceWithoutAddress = scores.count { it.hasCorporateKeyword } >= 2 &&
            !hasStreetType && !hasPostal && !hasStreetNumber
        val hasContactTokens = scores.any { it.hasContactTokens }
        val avgPosition = scores.map { it.positionRatio }.average()

        var score = 0.0
        val reasons = mutableListOf<String>()
        if (hasStreetNumber) {
            score += 0.24
            reasons += "street number"
        }
        if (hasStreetType) {
            score += 0.22
            reasons += "street type token"
        }
        if (hasUnit) {
            score += 0.1
            reasons += "unit marker"
        }
        if (hasCityRegion) {
            score += 0.18
            reasons += "city region pattern"
        }
        if (hasPostal) {
            score += 0.26
            reasons += "postal code pattern"
        }
        if (block.lineIndices.size in 2..4) {
            score += 0.12
            reasons += "multi-line block"
        }
        score += 0.16 * block.coherence
        reasons += "coherence ${formatPct(block.coherence)}"

        if (avgPosition >= 0.5) {
            score += 0.08
            reasons += "lower-page prior"
        }
        if (hasMarketingDominance) {
            score -= 0.35
            reasons += "marketing-dominant penalty"
        }
        if (corporateDominanceWithoutAddress) {
            score -= 0.22
            reasons += "corporate-without-address penalty"
        }
        if (hasContactTokens) {
            score -= 0.4
            reasons += "contact-token penalty"
        }

        return score.coerceIn(0.0, 1.0) to reasons.distinct()
    }

    private fun selectCandidate(
        field: OcrFieldType,
        candidates: List<OcrFieldCandidate>,
        minConfidence: Double,
        usedCoreLines: MutableSet<Int>,
        quarantineLines: Set<Int>,
        titleReservedLines: Set<Int> = emptySet(),
        validator: (OcrFieldCandidate) -> Pair<Boolean, String?>
    ): OcrFieldAudit {
        val notes = mutableListOf<String>()
        val debugCounts = mutableMapOf<String, Int>()
        var selected: OcrFieldCandidate? = null

        for (candidate in candidates) {
            if (candidate.sourceLineIndices.any { it in quarantineLines }) {
                notes += "Skipped quarantined slogan candidate."
                increment(debugCounts, "quarantined")
                if (field == OcrFieldType.TITLE) {
                    increment(debugCounts, "tagline_filtered")
                }
                continue
            }

            if (field == OcrFieldType.COMPANY &&
                candidate.lineIndex in titleReservedLines &&
                hasAlternativeCoreCandidate(
                    candidates = candidates,
                    current = candidate,
                    usedCoreLines = usedCoreLines,
                    quarantineLines = quarantineLines,
                    minConfidence = minConfidence,
                    validator = validator,
                    titleReservedLines = titleReservedLines
                )
            ) {
                notes += "Skipped company candidate to preserve stronger title signal."
                increment(debugCounts, "line_locked")
                continue
            }

            if (candidate.lineIndex in usedCoreLines) {
                notes += "Skipped candidate due to core-field conflict."
                increment(debugCounts, "line_locked")
                continue
            }

            if (candidate.confidence < minConfidence) {
                notes += "Candidate confidence below threshold (${formatPct(candidate.confidence)})."
                increment(debugCounts, "below_threshold")
                continue
            }

            val (valid, reason) = validator(candidate)
            if (!valid) {
                notes += reason ?: "Candidate failed sanity checks."
                increment(debugCounts, "validator_failed")
                continue
            }

            selected = candidate
            usedCoreLines += candidate.sourceLineIndices
            break
        }

        val evidence = candidates
            .take(4)
            .flatMap { it.sourceLineIndices }
            .distinct()
            .sorted()

        return OcrFieldAudit(
            selectedCandidate = selected,
            alternatives = candidates.take(6),
            evidenceLineIndices = evidence,
            notes = notes.distinct(),
            debugRejectionCounts = debugCounts.toSortedMap()
        )
    }

    private fun hasAlternativeCoreCandidate(
        candidates: List<OcrFieldCandidate>,
        current: OcrFieldCandidate,
        usedCoreLines: Set<Int>,
        quarantineLines: Set<Int>,
        minConfidence: Double,
        validator: (OcrFieldCandidate) -> Pair<Boolean, String?>,
        titleReservedLines: Set<Int>
    ): Boolean {
        return candidates.any { candidate ->
            candidate.lineIndex != current.lineIndex &&
                candidate.confidence >= minConfidence &&
                candidate.lineIndex !in usedCoreLines &&
                candidate.sourceLineIndices.none { it in quarantineLines } &&
                candidate.lineIndex !in titleReservedLines &&
                validator(candidate).first
        }
    }

    private fun selectNonCoreCandidate(
        candidates: List<OcrFieldCandidate>,
        minConfidence: Double
    ): OcrFieldAudit {
        val debugCounts = mutableMapOf<String, Int>()
        val selected = candidates.firstOrNull { candidate ->
            if (candidate.confidence < minConfidence) {
                increment(debugCounts, "below_threshold")
                false
            } else {
                true
            }
        }
        val notes = if (selected == null) {
            listOf("No candidate met confidence threshold (${formatPct(minConfidence)}).")
        } else {
            emptyList()
        }

        return OcrFieldAudit(
            selectedCandidate = selected,
            alternatives = candidates.take(6),
            evidenceLineIndices = candidates.take(4).flatMap { it.sourceLineIndices }.distinct().sorted(),
            notes = notes,
            debugRejectionCounts = debugCounts.toSortedMap()
        )
    }

    private fun selectAddressCandidate(
        candidates: List<OcrFieldCandidate>,
        minConfidence: Double,
        quarantineLines: Set<Int>
    ): OcrFieldAudit {
        val notes = mutableListOf<String>()
        val debugCounts = mutableMapOf<String, Int>()
        var selected: OcrFieldCandidate? = null

        for (candidate in candidates) {
            if (candidate.sourceLineIndices.any { it in quarantineLines }) {
                notes += "Skipped quarantined slogan candidate."
                increment(debugCounts, "quarantined")
                increment(debugCounts, "tagline_filtered")
                continue
            }
            if (candidate.confidence < minConfidence) {
                notes += "Candidate confidence below threshold (${formatPct(candidate.confidence)})."
                increment(debugCounts, "below_threshold")
                continue
            }
            val (valid, reason) = validateAddress(candidate.text)
            if (!valid) {
                notes += reason ?: "Address candidate failed sanity checks."
                increment(debugCounts, "validator_failed")
                continue
            }
            selected = candidate
            break
        }

        if (selected == null) {
            notes += "No candidate met confidence threshold (${formatPct(minConfidence)})."
        }

        return OcrFieldAudit(
            selectedCandidate = selected,
            alternatives = candidates.take(6),
            evidenceLineIndices = candidates.take(4).flatMap { it.sourceLineIndices }.distinct().sorted(),
            notes = notes.distinct(),
            debugRejectionCounts = debugCounts.toSortedMap()
        )
    }

    private fun runTitleSecondPass(
        titleAudit: OcrFieldAudit,
        companyAudit: OcrFieldAudit,
        nameAudit: OcrFieldAudit,
        titleCandidates: List<OcrFieldCandidate>,
        companyCandidates: List<OcrFieldCandidate>,
        scoresByIndex: Map<Int, LineScore>,
        usedCoreLines: MutableSet<Int>,
        quarantineLines: Set<Int>,
        titleThreshold: Double,
        companyThreshold: Double
    ): TitleRecoveryResult {
        var updatedTitleAudit = titleAudit
        var updatedCompanyAudit = companyAudit
        val currentTitleScore = titleAudit.selectedCandidate?.confidence ?: 0.0
        val nameLine = nameAudit.selectedCandidate?.lineIndex
        val companyLine = companyAudit.selectedCandidate?.lineIndex
        val passTwoDebug = titleAudit.debugRejectionCounts.toMutableMap()
        val passTwoNotes = titleAudit.notes.toMutableList()

        val orderedCandidates = titleCandidates.sortedWith(
            compareByDescending<OcrFieldCandidate> { it.confidence }.thenBy { it.lineIndex }
        )

        for (candidate in orderedCandidates) {
            if (candidate.sourceLineIndices.any { it in quarantineLines }) {
                increment(passTwoDebug, "quarantined")
                increment(passTwoDebug, "tagline_filtered")
                continue
            }
            if (candidate.confidence < titleThreshold) {
                increment(passTwoDebug, "below_threshold")
                continue
            }
            if (candidate.confidence <= currentTitleScore + 0.05) {
                continue
            }
            val (validTitle, _) = validateJobTitle(candidate.text)
            if (!validTitle) {
                increment(passTwoDebug, "validator_failed")
                continue
            }
            if (candidate.lineIndex == nameLine) {
                increment(passTwoDebug, "line_locked")
                continue
            }

            val candidateUsedByCompany = candidate.lineIndex == companyLine
            if (candidate.lineIndex in usedCoreLines && !candidateUsedByCompany) {
                increment(passTwoDebug, "line_locked")
                continue
            }

            if (candidateUsedByCompany) {
                val lineScore = scoresByIndex[candidate.lineIndex]
                val titleVsCompanyMargin = (lineScore?.titleScore ?: candidate.confidence) - (lineScore?.companyScore ?: 0.0)
                if (titleVsCompanyMargin < companyTitleSwapMargin) {
                    increment(passTwoDebug, "line_locked")
                    continue
                }
                val usedWithoutCompany = usedCoreLines.toMutableSet().apply {
                    remove(candidate.lineIndex)
                }
                val replacement = pickCompanyReplacementCandidate(
                    companyCandidates = companyCandidates,
                    currentCompanyLine = candidate.lineIndex,
                    blockedLines = usedWithoutCompany + setOf(candidate.lineIndex),
                    quarantineLines = quarantineLines,
                    minConfidence = companyThreshold
                )
                if (replacement == null) {
                    increment(passTwoDebug, "line_locked")
                    continue
                }

                val companyNotes = companyAudit.notes + "Company moved to alternate line to preserve stronger title signal."
                updatedCompanyAudit = companyAudit.copy(
                    selectedCandidate = replacement,
                    notes = companyNotes.distinct()
                )
                usedCoreLines.remove(candidate.lineIndex)
                usedCoreLines.add(replacement.lineIndex)
            }

            usedCoreLines.add(candidate.lineIndex)
            passTwoNotes += "Title selected in second pass."
            updatedTitleAudit = titleAudit.copy(
                selectedCandidate = candidate,
                notes = passTwoNotes.distinct(),
                debugRejectionCounts = passTwoDebug.toSortedMap()
            )
            return TitleRecoveryResult(
                titleAudit = updatedTitleAudit,
                companyAudit = updatedCompanyAudit
            )
        }

        updatedTitleAudit = updatedTitleAudit.copy(debugRejectionCounts = passTwoDebug.toSortedMap())
        return TitleRecoveryResult(
            titleAudit = updatedTitleAudit,
            companyAudit = updatedCompanyAudit
        )
    }

    private fun pickCompanyReplacementCandidate(
        companyCandidates: List<OcrFieldCandidate>,
        currentCompanyLine: Int,
        blockedLines: Set<Int>,
        quarantineLines: Set<Int>,
        minConfidence: Double
    ): OcrFieldCandidate? {
        return companyCandidates.firstOrNull { candidate ->
            candidate.lineIndex != currentCompanyLine &&
                candidate.confidence >= minConfidence &&
                candidate.lineIndex !in blockedLines &&
                candidate.sourceLineIndices.none { it in quarantineLines } &&
                validateCompany(candidate.text).first
        }
    }

    private fun buildCoverageFallbackLines(
        normalizedLines: List<NormalizedOcrLine>,
        scoresByIndex: Map<Int, LineScore>,
        quarantinedLines: Set<Int>,
        fieldAudits: Map<OcrFieldType, OcrFieldAudit>
    ): List<OcrCoverageResidualLine> {
        val usedLines = fieldAudits.values
            .mapNotNull { it.selectedCandidate }
            .flatMap { it.sourceLineIndices }
            .toSet()

        return normalizedLines.mapNotNull { line ->
            val score = scoresByIndex[line.index] ?: return@mapNotNull null
            if (line.index in usedLines) return@mapNotNull null
            if (line.index in quarantinedLines) return@mapNotNull null
            if (score.hasContactTokens) return@mapNotNull null
            if (score.decorative) return@mapNotNull null
            if (score.taglineScore >= 0.5 || score.logoScore >= 0.8) return@mapNotNull null
            if (!line.text.any { it.isLetterOrDigit() }) return@mapNotNull null
            OcrCoverageResidualLine(
                text = line.text,
                lineIndex = line.index
            )
        }
    }

    private fun validatePersonName(value: String): Pair<Boolean, String?> {
        val trimmed = value.trim()
        if (trimmed.any { it.isDigit() }) return false to "Name rejected: contains digits."
        if (containsCorporateKeyword(trimmed)) return false to "Name rejected: contains corporate keywords."
        if (containsMarketingKeyword(trimmed)) return false to "Name rejected: looks like slogan."
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in 2..4) return false to "Name rejected: expected 2-4 words."
        val properCaseWords = words.count { token ->
            token.firstOrNull()?.isUpperCase() == true && token.any { it.isLetter() }
        }
        if (properCaseWords.toDouble() / words.size.toDouble() < 0.5) {
            return false to "Name rejected: weak person-name casing pattern."
        }
        return true to null
    }

    private fun validateCompany(value: String): Pair<Boolean, String?> {
        val trimmed = value.trim()
        if (containsMarketingKeyword(trimmed) && !containsCorporateKeyword(trimmed)) {
            return false to "Company rejected: looks like marketing slogan."
        }
        if (trimmed.split(Regex("\\s+")).size >= 8 && !containsCorporateKeyword(trimmed)) {
            return false to "Company rejected: too long without company signals."
        }
        return true to null
    }

    private fun validateJobTitle(value: String): Pair<Boolean, String?> {
        val lower = value.lowercase(Locale.US)
        if (emailRegex.containsMatchIn(value) || phoneRegex.containsMatchIn(value) || websiteRegex.containsMatchIn(value)) {
            return false to "Title rejected: line is contact detail."
        }
        if (containsMarketingKeyword(value) && !containsAnyKeyword(lower, roleKeywords)) {
            return false to "Title rejected: looks like slogan."
        }
        val hasRoleKeyword = containsAnyKeyword(lower, roleKeywords)
        if (!hasRoleKeyword && value.split(Regex("\\s+")).size > 5) {
            return false to "Title rejected: weak role signal."
        }
        return true to null
    }

    private fun validateAddress(value: String): Pair<Boolean, String?> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false to "Address rejected: empty."
        if (emailRegex.containsMatchIn(trimmed) || phoneRegex.containsMatchIn(trimmed) || websiteRegex.containsMatchIn(trimmed)) {
            return false to "Address rejected: contains contact tokens."
        }
        val lower = trimmed.lowercase(Locale.US)
        val hasStreetNumber = Regex("(^|\\s)\\d+[A-Za-z]?(\\s|$)").containsMatchIn(trimmed)
        val hasStreetType = containsAnyKeyword(lower, addressKeywords)
        val hasUnit = containsAnyKeyword(lower, unitKeywords)
        val hasPostal = usZipRegex.containsMatchIn(trimmed) || canadianPostalRegex.containsMatchIn(trimmed)
        val hasCityRegion = cityRegionRegex.containsMatchIn(trimmed.uppercase(Locale.US))
        val hasAddressSignal = hasStreetNumber || hasStreetType || hasUnit || hasPostal || hasCityRegion
        if (!hasAddressSignal) {
            return false to "Address rejected: weak location signal."
        }
        if (containsMarketingKeyword(trimmed) && !hasPostal && !hasStreetType && !hasStreetNumber) {
            return false to "Address rejected: marketing-dominant text."
        }
        return true to null
    }

    private fun containsCorporateKeyword(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return containsAnyKeyword(lower, corporateKeywords)
    }

    private fun containsMarketingKeyword(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return containsAnyKeyword(lower, marketingKeywords)
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

    private fun keywordMatchCount(valueLower: String, keywords: Set<String>): Int {
        return keywords.count { keyword ->
            if (keyword.any { it.isWhitespace() || it == '.' }) {
                valueLower.contains(keyword)
            } else {
                Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(valueLower)
            }
        }
    }

    private fun computeVerticalRatio(
        line: NormalizedOcrLine,
        maxBottom: Float,
        lineCount: Int
    ): Double {
        if (line.top != null && line.bottom != null && maxBottom > 0f) {
            return ((line.top + line.bottom) / 2f / maxBottom).toDouble().coerceIn(0.0, 1.0)
        }
        if (lineCount <= 1) return 0.5
        return line.index.toDouble() / (lineCount - 1).toDouble()
    }

    private fun increment(map: MutableMap<String, Int>, key: String) {
        map[key] = (map[key] ?: 0) + 1
    }

    private fun formatPct(value: Double): String {
        return "${(value.coerceIn(0.0, 1.0) * 100.0).toInt()}%"
    }
}
