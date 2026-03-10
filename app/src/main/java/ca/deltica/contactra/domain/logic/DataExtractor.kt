package ca.deltica.contactra.domain.logic

import android.util.Patterns
import java.util.Locale

/**
 * Simple rule-based parsing of raw OCR text to extract contact fields. It uses
 * line-based heuristics to guess the name, job title and company. Emails,
 * phone numbers and websites are detected using Android's built-in patterns.
 */
data class ExtractedData(
    val name: String? = null,
    val title: String? = null,
    val company: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
    val industry: String? = null,
    val industryCustom: String? = null,
    val industrySource: String? = null
)

data class StructuredExtractionResult(
    val data: ExtractedData,
    val didRetry: Boolean,
    val retryMessage: String? = null,
    val confidenceByField: Map<OcrFieldType, Double> = emptyMap(),
    val audit: OcrExtractionAudit = OcrExtractionAudit()
)

object DataExtractor {
    private val titleKeywords = listOf(
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

    private val titleAbbreviations = setOf(
        "ceo", "cto", "cfo", "cio", "coo", "cmo", "cso", "cro", "svp", "evp", "vp"
    )

    private val companyKeywords = listOf(
        "inc", "inc.", "incorporated",
        "llc", "l.l.c", "ltd", "ltd.", "limited",
        "corp", "corporation", "company", "co", "co.",
        "group", "holdings", "ventures", "partners", "associates",
        "solutions", "systems", "technologies", "technology",
        "labs", "studio", "studios", "media", "consulting"
    )

    private val addressKeywords = listOf(
        "street", "st.", "road", "rd.", "avenue", "ave", "boulevard", "blvd",
        "suite", "ste", "floor", "fl", "drive", "dr", "lane", "ln", "court",
        "ct", "highway", "hwy", "po box", "box", "building", "bldg", "tower"
    )

    private val websiteRegex = Regex("\\b([a-z0-9-]+\\.)+[a-z]{2,}\\b", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val compositeSeparatorRegex = Regex("\\s+(?:\\||/|-|--)\\s+")
    private val honorifics = setOf("mr", "mrs", "ms", "dr", "prof")
    private val genericEmailPrefixes = setOf("info", "hello", "contact", "support", "sales", "team", "office")
    private const val maxRetryAttempts = 1

    fun extract(text: String): ExtractedData {
        val result = OcrAuditParser.parse(rawText = text, relaxed = true)
        return result.toExtractedData()
    }

    /**
     * Two-pass structured parsing:
     * 1) Parse with default heuristics.
     * 2) If core fields are missing or low-confidence, retry once with line reprioritization.
     */
    fun extractReliably(
        text: String,
        providedLines: List<NormalizedOcrLine> = emptyList()
    ): StructuredExtractionResult {
        val normalized = normalizeText(text)
        val lines = sanitizeOcrLines(normalized.lines())
        if (!hasTextualSignal(lines) && providedLines.isEmpty()) {
            return StructuredExtractionResult(
                data = ExtractedData(),
                didRetry = false,
                retryMessage = "Could not read text from this image."
            )
        }

        val strict = OcrAuditParser.parse(
            rawText = normalized,
            providedLines = providedLines,
            relaxed = false
        )
        var selected = strict
        var retried = false

        if (requiresCoreRetry(strict.toExtractedData(), strict.confidenceByField)) {
            val relaxed = OcrAuditParser.parse(
                rawText = normalized,
                providedLines = providedLines,
                relaxed = true
            )
            selected = chooseBest(strict, relaxed)
            retried = true
        }

        val extracted = selected.toExtractedData()
        val lowCoreConfidence = isCoreLowConfidence(selected.confidenceByField)
        val retryMessage = when {
            selected.audit.normalizedLines.isEmpty() -> "Could not read text from this image."
            retried || lowCoreConfidence -> "Please review the extracted fields before continuing."
            else -> null
        }

        return StructuredExtractionResult(
            data = extracted,
            didRetry = retried,
            retryMessage = retryMessage,
            confidenceByField = selected.confidenceByField,
            audit = selected.audit
        )
    }

    /**
     * Normalizes phone numbers to E.164 where possible.
     * Falls back to digit-only local format when country context is unknown.
     */
    fun normalizePhoneNumber(raw: String?, defaultCountryCode: String = "1"): String? {
        val source = raw?.trim().orEmpty()
        if (source.isBlank()) return null

        val withoutExtension = source
            .replace(Regex("(?i)\\s*(ext|extension|x)\\s*\\d+$"), "")
            .trim()

        if (withoutExtension.isBlank()) return null

        val hasLeadingPlus = withoutExtension.startsWith("+")
        val digits = withoutExtension.filter { it.isDigit() }
        if (digits.length < 7) return null

        if (hasLeadingPlus) {
            return if (digits.length in 8..15) "+$digits" else digits
        }

        if (withoutExtension.startsWith("00") && digits.length > 2) {
            val internationalDigits = digits.drop(2)
            if (internationalDigits.length in 8..15) {
                return "+$internationalDigits"
            }
        }

        if (digits.length == 10 && defaultCountryCode.isNotBlank()) {
            return "+$defaultCountryCode$digits"
        }
        if (digits.length == 11 && digits.startsWith(defaultCountryCode)) {
            return "+$digits"
        }

        return digits
    }

    private fun sanitizeOcrLines(lines: List<String>): List<String> {
        return lines
            .map { it.replace("\t", " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { isImageOnlyNoiseLine(it) }
    }

    private fun isImageOnlyNoiseLine(line: String): Boolean {
        val alphanumericCount = line.count { it.isLetterOrDigit() }
        if (alphanumericCount == 0) return true

        val hasEmail = Patterns.EMAIL_ADDRESS.matcher(line).find()
        val hasPhone = phoneRegex.containsMatchIn(line)
        val hasWebsite = Patterns.WEB_URL.matcher(line).find() || websiteRegex.containsMatchIn(line)
        if (hasEmail || hasPhone || hasWebsite) return false

        val letterCount = line.count { it.isLetter() }
        val symbolCount = line.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (letterCount == 0 && alphanumericCount <= 2) return true
        return symbolCount > 0 && alphanumericCount <= 2 && symbolCount >= (alphanumericCount * 2)
    }

    private fun hasTextualSignal(lines: List<String>): Boolean {
        if (lines.isEmpty()) return false
        val totalLetters = lines.sumOf { line -> line.count { it.isLetter() } }
        val tokenCount = lines.sumOf { line ->
            line.split(Regex("\\s+")).count { token -> token.any { it.isLetterOrDigit() } }
        }
        return totalLetters >= 2 && tokenCount >= 2
    }

    private fun normalizeWebsite(website: String?): String? {
        val value = website?.trim()?.trimEnd('.', ',', ';', ')', '>')
        return value?.takeIf { it.isNotBlank() }
    }

    private fun requiresCoreRetry(
        data: ExtractedData,
        confidenceByField: Map<OcrFieldType, Double> = emptyMap()
    ): Boolean {
        val hasCoreValue = !data.name.isNullOrBlank() || !data.company.isNullOrBlank()
        if (!hasCoreValue) return true

        if (isCoreLowConfidence(confidenceByField)) {
            return true
        }

        val nameScore = scoreNameConfidence(data.name)
        val companyScore = scoreCompanyConfidence(data.company)
        return nameScore <= 1 && companyScore <= 1
    }

    private fun isCoreLowConfidence(confidenceByField: Map<OcrFieldType, Double>): Boolean {
        if (confidenceByField.isEmpty()) return true
        val nameScore = confidenceByField[OcrFieldType.NAME] ?: 0.0
        val companyScore = confidenceByField[OcrFieldType.COMPANY] ?: 0.0
        val titleScore = confidenceByField[OcrFieldType.TITLE] ?: 0.0
        return (nameScore < 0.5 && companyScore < 0.5) || titleScore < 0.3
    }

    private fun chooseBest(
        strict: OcrAuditParseResult,
        relaxed: OcrAuditParseResult
    ): OcrAuditParseResult {
        val strictCoreCount = strict.coreFieldCount()
        val relaxedCoreCount = relaxed.coreFieldCount()
        if (relaxedCoreCount > strictCoreCount) return relaxed
        if (strictCoreCount > relaxedCoreCount) return strict

        val strictCoreConfidence = strict.coreConfidenceScore()
        val relaxedCoreConfidence = relaxed.coreConfidenceScore()
        return if (relaxedCoreConfidence > strictCoreConfidence) relaxed else strict
    }

    private fun buildRetryInput(lines: List<String>): String {
        return lines
            .flatMap { splitCompositeLine(it) }
            .map { it.trim() }
            .filter { it.isNotBlank() && !isImageOnlyNoiseLine(it) }
            .distinct()
            .sortedByDescending { scoreRetryPriority(it) }
            .joinToString("\n")
    }

    private fun scoreRetryPriority(line: String): Int {
        val lower = line.lowercase(Locale.getDefault())
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasDigits = line.any { it.isDigit() }

        var score = 0
        if (words.size in 2..4 && !hasDigits && looksLikeNameWords(words)) score += 5
        if (containsKeyword(lower, companyKeywords)) score += 4
        if (containsKeyword(lower, titleKeywords)) score += 3
        if (line.any { it.isLetter() }) score += 1
        if (isAddressLine(lower)) score -= 2
        if (isPhoneLine(line)) score -= 1
        if (Patterns.EMAIL_ADDRESS.matcher(line).find()) score -= 1
        return score
    }

    private fun looksLikeNameWords(words: List<String>): Boolean {
        val properCaseWords = words.count { token ->
            token.firstOrNull()?.isUpperCase() == true && token.any { it.isLetter() }
        }
        return properCaseWords.toFloat() / words.size.toFloat() >= 0.6f
    }

    private fun mergeExtractionResults(primary: ExtractedData, retry: ExtractedData): ExtractedData {
        val mergedName = selectBetterCoreValue(primary.name, retry.name, ::scoreNameConfidence)
        val mergedCompany = selectBetterCoreValue(primary.company, retry.company, ::scoreCompanyConfidence)
        val mergedPhone = selectBetterPhone(primary.phone, retry.phone)

        return ExtractedData(
            name = mergedName,
            title = choosePreferred(primary.title, retry.title),
            company = mergedCompany,
            email = choosePreferred(primary.email, retry.email),
            phone = mergedPhone,
            website = choosePreferred(primary.website, retry.website),
            industry = choosePreferred(primary.industry, retry.industry),
            industryCustom = choosePreferred(primary.industryCustom, retry.industryCustom),
            industrySource = choosePreferred(primary.industrySource, retry.industrySource)
        )
    }

    private fun selectBetterCoreValue(
        primary: String?,
        retry: String?,
        scorer: (String?) -> Int
    ): String? {
        val primaryScore = scorer(primary)
        val retryScore = scorer(retry)
        return when {
            retryScore > primaryScore -> retry?.trim()
            retryScore == primaryScore && !retry.isNullOrBlank() && primary.isNullOrBlank() -> retry.trim()
            else -> primary?.trim()
        }
    }

    private fun selectBetterPhone(primary: String?, retry: String?): String? {
        val normalizedPrimary = normalizePhoneNumber(primary)
        val normalizedRetry = normalizePhoneNumber(retry)
        return when {
            normalizedPrimary.isNullOrBlank() -> normalizedRetry
            normalizedRetry.isNullOrBlank() -> normalizedPrimary
            normalizedRetry.startsWith("+") && !normalizedPrimary.startsWith("+") -> normalizedRetry
            normalizedRetry.length > normalizedPrimary.length -> normalizedRetry
            else -> normalizedPrimary
        }
    }

    private fun choosePreferred(primary: String?, retry: String?): String? {
        val primaryValue = primary?.trim().takeIf { !it.isNullOrBlank() }
        val retryValue = retry?.trim().takeIf { !it.isNullOrBlank() }
        return when {
            primaryValue == null -> retryValue
            retryValue == null -> primaryValue
            retryValue.length > primaryValue.length + 6 -> retryValue
            else -> primaryValue
        }
    }

    private fun scoreNameConfidence(name: String?): Int {
        val value = name?.trim().orEmpty()
        if (value.isBlank()) return 0
        if (value.any { it.isDigit() }) return 1
        val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in 2..4) return 1
        return if (looksLikeNameWords(words)) 3 else 2
    }

    private fun scoreCompanyConfidence(company: String?): Int {
        val value = company?.trim().orEmpty()
        if (value.isBlank()) return 0
        val lower = value.lowercase(Locale.getDefault())
        if (containsKeyword(lower, companyKeywords)) return 3
        if (value.any { it.isDigit() }) return 1
        val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 2 && words.any { token -> token.firstOrNull()?.isUpperCase() == true }) {
            return 2
        }
        return 1
    }

    private fun extractEmail(text: String): String? {
        val matcher = Patterns.EMAIL_ADDRESS.matcher(text)
        val candidates = mutableListOf<EmailCandidate>()
        while (matcher.find()) {
            val value = matcher.group()
            if (!value.isNullOrBlank()) {
                candidates.add(EmailCandidate(value, matcher.start()))
            }
        }
        if (candidates.isEmpty()) return null
        return candidates
            .map { candidate ->
                val local = candidate.value.substringBefore("@").lowercase(Locale.getDefault())
                var score = 0
                if (local.contains('.')) score += 2
                if (local.contains('_') || local.contains('-')) score += 1
                if (genericEmailPrefixes.contains(local)) score -= 2
                if (local.length <= 2) score -= 1
                candidate.copy(score = score)
            }
            .maxWithOrNull(compareBy<EmailCandidate> { it.score }.thenBy { it.index })
            ?.value
    }

    private fun extractWebsite(text: String, email: String?): String? {
        val normalized = text.replace("\n", " ")
        val candidates = mutableListOf<WebsiteCandidate>()
        val urlMatcher = Patterns.WEB_URL.matcher(normalized)
        while (urlMatcher.find()) {
            candidates.add(WebsiteCandidate(urlMatcher.group(), urlMatcher.start()))
        }
        websiteRegex.findAll(normalized).forEach { match ->
            candidates.add(WebsiteCandidate(match.value, match.range.first))
        }
        val emailLower = email?.lowercase(Locale.getDefault())
        val cleaned = candidates
            .map { candidate ->
                val value = candidate.value.trim().trimEnd('.', ',', ';', ')', '>')
                val lower = value.lowercase(Locale.getDefault())
                if (value.contains("@")) return@map null
                if (lower.startsWith("mailto:")) return@map null
                val domain = emailLower?.substringAfter("@")
                var score = 0
                if (lower.startsWith("http")) score += 3
                if (lower.startsWith("www.")) score += 2
                if (lower.contains("linkedin.com")) score += 1
                if (lower.contains("github.com")) score += 1
                if (!lower.contains(".")) score -= 2
                if (!domain.isNullOrBlank() && lower.contains(domain)) score -= 1
                WebsiteCandidate(value, candidate.index, score)
            }
            .filterNotNull()
        return cleaned
            .maxWithOrNull(compareBy<WebsiteCandidate> { it.score }.thenBy { it.index })
            ?.value
    }

    private fun extractPhone(lines: List<String>): String? {
        val candidates = mutableListOf<PhoneCandidate>()
        lines.forEachIndexed { index, line ->
            val lower = line.lowercase(Locale.getDefault())
            val isFax = lower.contains("fax") || lower.contains("f:")
            val isMobile = lower.contains("mobile") || lower.contains("cell") || lower.contains("m:")
            val isPhoneLabel = lower.contains("phone") || lower.contains("tel") || lower.contains("t:")
            phoneRegex.findAll(line).forEach { match ->
                val value = match.value.trim()
                val digits = value.count { it.isDigit() }
                if (digits >= 7) {
                    var score = digits
                    if (isPhoneLabel) score += 2
                    if (isMobile) score += 2
                    if (isFax) score -= 5
                    candidates.add(PhoneCandidate(value, index, score))
                }
            }
        }
        return candidates.maxWithOrNull(compareBy<PhoneCandidate> { it.score }.thenBy { it.index })?.value
    }

    private fun isPhoneLine(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        if (lower.contains("fax") || lower.contains("f:")) return true
        val digits = line.count { it.isDigit() }
        if (digits < 7) return false
        val letters = line.count { it.isLetter() }
        return letters <= 2
    }

    private fun isWebsiteLine(line: String, email: String?): Boolean {
        if (Patterns.WEB_URL.matcher(line).find()) return true
        if (websiteRegex.containsMatchIn(line)) {
            val emailLower = email?.lowercase(Locale.getDefault())
            return emailLower == null || !line.lowercase(Locale.getDefault()).contains(emailLower)
        }
        return false
    }

    private fun isAddressLine(lowerLine: String): Boolean {
        if (lowerLine.any { it.isDigit() } && containsKeyword(lowerLine, addressKeywords)) {
            return true
        }
        if (Regex("\\b\\d{5}(-\\d{4})?\\b").containsMatchIn(lowerLine)) {
            return true
        }
        if (lowerLine.contains(",")) {
            val tokens = lowerLine.split(",")
            if (tokens.any { token ->
                    val t = token.trim()
                    t.length in 2..3 && t.all { it.isLetter() }
                }) {
                return true
            }
        }
        return false
    }

    private fun containsKeyword(line: String, keywords: List<String>): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        return keywords.any { lower.contains(it) }
    }

    private fun splitCompositeLine(line: String): List<String> {
        val parts = line.split(compositeSeparatorRegex).map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size >= 2 && parts.all { it.any { ch -> ch.isLetter() } }) parts else listOf(line)
    }

    private data class LineScore(
        val line: String,
        val index: Int,
        val nameScore: Int,
        val titleScore: Int,
        val companyScore: Int
    )

    private fun scoreLine(line: String, index: Int): LineScore {
        val lower = line.lowercase(Locale.getDefault())
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val hasDigits = line.any { it.isDigit() }
        val hasAt = line.contains("@")
        val hasUrlish = line.contains("www.") || line.contains("http") || line.contains(".com")
        val hasAmpersand = line.contains("&")

        val letterWords = words.count { it.any { ch -> ch.isLetter() } }.coerceAtLeast(1)
        val properCaseWords = words.count { it.firstOrNull()?.isUpperCase() == true }
        val upperCaseWords = words.count { it.all { ch -> ch.isUpperCase() } }
        val caseRatio = (properCaseWords + upperCaseWords).toFloat() / letterWords.toFloat()
        val allUppercase = upperCaseWords >= letterWords && letterWords > 0

        var nameScore = 0
        if (!hasDigits && !hasAt && !hasUrlish && wordCount in 2..4 && caseRatio >= 0.6f) {
            nameScore += 3
        } else if (!hasDigits && !hasAt && !hasUrlish && wordCount in 2..4) {
            nameScore += 1
        }
        if (hasHonorific(words)) nameScore += 1
        if (hasMiddleInitial(words)) nameScore += 1
        if (allUppercase && wordCount in 2..3) nameScore += 1
        if (containsKeyword(lower, titleKeywords)) nameScore -= 2
        if (containsKeyword(lower, companyKeywords)) nameScore -= 2
        if (hasAmpersand) nameScore -= 2
        if (index <= 3) nameScore += 1
        if (wordCount > 4) nameScore -= 1

        var titleScore = 0
        if (containsKeyword(lower, titleKeywords)) titleScore += 3
        if (wordCount == 1 && isTitleAbbreviation(words.firstOrNull())) titleScore += 2
        if (line.contains("&")) titleScore += 1
        if (wordCount in 2..6) titleScore += 1
        if (containsKeyword(lower, companyKeywords)) titleScore -= 1
        if (hasDigits) titleScore -= 1
        if (index <= 4) titleScore += 1

        var companyScore = 0
        if (containsKeyword(lower, companyKeywords)) companyScore += 3
        if (allUppercase) companyScore += 2
        if (wordCount >= 2) companyScore += 1
        if (containsKeyword(lower, titleKeywords)) companyScore -= 1
        if (looksLikePersonName(words, caseRatio, hasDigits)) companyScore -= 2
        if (hasAmpersand) companyScore += 1
        if (index <= 5) companyScore += 1
        if (index >= 3) companyScore += 1
        if (hasDigits && !containsKeyword(lower, companyKeywords)) companyScore -= 1

        return LineScore(line, index, nameScore, titleScore, companyScore)
    }

    private fun pickBest(
        scores: List<LineScore>,
        selector: (LineScore) -> Int,
        used: MutableSet<String>,
        minScore: Int
    ): String? {
        return scores
            .sortedWith(compareByDescending(selector).thenBy { it.index })
            .firstOrNull { selector(it) >= minScore && !used.contains(it.line) }
            ?.also { used.add(it.line) }
            ?.line
    }

    private fun normalizeText(text: String): String {
        return text.lines().joinToString("\n") { line ->
            line.replace("\t", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }.trim()
    }

    private fun hasHonorific(words: List<String>): Boolean {
        return words.firstOrNull()?.trim('.', ',')?.lowercase(Locale.getDefault()) in honorifics
    }

    private fun hasMiddleInitial(words: List<String>): Boolean {
        return words.any { it.length == 2 && it[1] == '.' && it[0].isLetter() }
    }

    private fun isTitleAbbreviation(word: String?): Boolean {
        val value = word?.lowercase(Locale.getDefault()) ?: return false
        return titleAbbreviations.contains(value)
    }

    private fun looksLikePersonName(words: List<String>, caseRatio: Float, hasDigits: Boolean): Boolean {
        val wordCount = words.size
        if (hasDigits) return false
        if (wordCount !in 2..4) return false
        return caseRatio >= 0.6f
    }

    private data class LineCandidate(val line: String, val index: Int)
    private data class EmailCandidate(val value: String, val index: Int, val score: Int = 0)
    private data class PhoneCandidate(val value: String, val index: Int, val score: Int)
private data class WebsiteCandidate(val value: String, val index: Int, val score: Int = 0)
}

private fun OcrAuditParseResult.toExtractedData(): ExtractedData {
    val name = values[OcrFieldType.NAME]?.trim().takeIf { !it.isNullOrBlank() }
    val title = values[OcrFieldType.TITLE]?.trim().takeIf { !it.isNullOrBlank() }
    val company = values[OcrFieldType.COMPANY]?.trim().takeIf { !it.isNullOrBlank() }
    val email = values[OcrFieldType.EMAIL]?.trim().takeIf { !it.isNullOrBlank() }
    val phone = DataExtractor.normalizePhoneNumber(values[OcrFieldType.PHONE])
    val website = values[OcrFieldType.WEBSITE]?.trim()?.trimEnd('.', ',', ';')
        .takeIf { !it.isNullOrBlank() }
    val address = values[OcrFieldType.ADDRESS]?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
    return ExtractedData(
        name = name,
        title = title,
        company = company,
        email = email,
        phone = phone,
        website = website,
        address = address
    )
}

private fun OcrAuditParseResult.coreFieldCount(): Int {
    return listOf(
        values[OcrFieldType.NAME],
        values[OcrFieldType.TITLE],
        values[OcrFieldType.COMPANY]
    ).count { !it.isNullOrBlank() }
}

private fun OcrAuditParseResult.coreConfidenceScore(): Double {
    return (confidenceByField[OcrFieldType.NAME] ?: 0.0) +
        (confidenceByField[OcrFieldType.TITLE] ?: 0.0) +
        (confidenceByField[OcrFieldType.COMPANY] ?: 0.0)
}
