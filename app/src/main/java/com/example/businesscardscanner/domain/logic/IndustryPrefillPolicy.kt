package com.example.businesscardscanner.domain.logic

data class IndustryPrefillDecision(
    val industry: String?,
    val industryCustom: String?,
    val source: String
)

data class IndustryMergeDecision(
    val industry: String?,
    val industryCustom: String?,
    val source: String?,
    val metadataNote: String?
)

data class IndustrySelection(
    val industry: String?,
    val industryCustom: String?
)

object IndustrySource {
    const val USER_SELECTED = "user_selected"
    const val ENRICHMENT_INFERRED = "enrichment_inferred"
    const val HEURISTIC_INFERRED = "heuristic_inferred"
    const val EMPTY = "empty"

    fun normalizeForDraft(industry: String, source: String?): String {
        if (industry.isBlank()) return EMPTY
        return when (source?.trim()?.lowercase()) {
            null, "", EMPTY -> USER_SELECTED
            "manual", USER_SELECTED -> USER_SELECTED
            "website", ENRICHMENT_INFERRED -> ENRICHMENT_INFERRED
            "auto", "company", HEURISTIC_INFERRED -> HEURISTIC_INFERRED
            else -> USER_SELECTED
        }
    }

    fun toPersistedValue(industry: String?, source: String?): String? {
        if (industry.isNullOrBlank()) return null
        return normalizeForDraft(industry = industry, source = source)
            .takeIf { it != EMPTY }
    }

    fun isUserSelected(source: String?): Boolean {
        return source.equals(USER_SELECTED, ignoreCase = true)
    }

    fun isInferred(source: String?): Boolean {
        return source.equals(ENRICHMENT_INFERRED, ignoreCase = true) ||
            source.equals(HEURISTIC_INFERRED, ignoreCase = true)
    }

    fun inferredRank(source: String?): Int {
        return when {
            source.equals(ENRICHMENT_INFERRED, ignoreCase = true) -> 2
            source.equals(HEURISTIC_INFERRED, ignoreCase = true) -> 1
            else -> 0
        }
    }
}

object IndustryPrefillPolicy {
    const val ENRICHMENT_PREFILL_MIN_CONFIDENCE: Double = 0.75
    private const val INTERNAL_INDUSTRY_METADATA_PREFIX = "[industry_alt]"

    fun normalizeSelection(industry: String?, industryCustom: String?): IndustrySelection {
        val normalizedIndustry = industry?.trim().orEmpty()
        val normalizedCustom = industryCustom?.trim().orEmpty()
        if (normalizedIndustry.isBlank()) {
            return if (normalizedCustom.isBlank()) {
                IndustrySelection(industry = null, industryCustom = null)
            } else {
                IndustrySelection(industry = "Other", industryCustom = normalizedCustom)
            }
        }

        val mapped = IndustryCatalog.toManualSelectionIndustry(normalizedIndustry) ?: normalizedIndustry
        return if (mapped.equals("Other", ignoreCase = true)) {
            IndustrySelection(industry = "Other", industryCustom = normalizedCustom.ifBlank { null })
        } else {
            IndustrySelection(industry = mapped, industryCustom = null)
        }
    }

    fun resolve(
        currentIndustry: String?,
        currentIndustryCustom: String?,
        currentSource: String?,
        enrichmentEnabled: Boolean,
        enrichmentIndustry: String?,
        enrichmentConfidence: Double?,
        companyName: String?
    ): IndustryPrefillDecision {
        val existingSelection = normalizeSelection(currentIndustry, currentIndustryCustom)
        if (!existingSelection.industry.isNullOrBlank()) {
            return IndustryPrefillDecision(
                industry = existingSelection.industry,
                industryCustom = existingSelection.industryCustom,
                source = IndustrySource.normalizeForDraft(
                    industry = existingSelection.industry,
                    source = currentSource
                )
            )
        }

        val normalizedEnrichment = normalizeSelection(enrichmentIndustry, null)
        if (enrichmentEnabled &&
            !normalizedEnrichment.industry.isNullOrBlank() &&
            (enrichmentConfidence ?: 0.0) >= ENRICHMENT_PREFILL_MIN_CONFIDENCE
        ) {
            return IndustryPrefillDecision(
                industry = normalizedEnrichment.industry,
                industryCustom = normalizedEnrichment.industryCustom,
                source = IndustrySource.ENRICHMENT_INFERRED
            )
        }

        val companyHeuristic = normalizeSelection(
            industry = IndustryCatalog.inferIndustryFromCompanyName(companyName),
            industryCustom = null
        )
        if (!companyHeuristic.industry.isNullOrBlank()) {
            return IndustryPrefillDecision(
                industry = companyHeuristic.industry,
                industryCustom = companyHeuristic.industryCustom,
                source = IndustrySource.HEURISTIC_INFERRED
            )
        }

        return IndustryPrefillDecision(
            industry = null,
            industryCustom = null,
            source = IndustrySource.EMPTY
        )
    }

    fun resolveMerge(
        existingIndustry: String?,
        existingIndustryCustom: String?,
        existingSource: String?,
        incomingIndustry: String?,
        incomingIndustryCustom: String?,
        incomingSource: String?
    ): IndustryMergeDecision {
        val existingSelection = normalizeSelection(existingIndustry, existingIndustryCustom)
        val incomingSelection = normalizeSelection(incomingIndustry, incomingIndustryCustom)

        val existingNormalizedSource = existingSelection.industry?.let {
            IndustrySource.normalizeForDraft(it, existingSource)
        }
        val incomingNormalizedSource = incomingSelection.industry?.let {
            IndustrySource.normalizeForDraft(it, incomingSource)
        }

        if (incomingSelection.industry.isNullOrBlank()) {
            return IndustryMergeDecision(
                industry = existingSelection.industry,
                industryCustom = existingSelection.industryCustom,
                source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                metadataNote = null
            )
        }
        if (existingSelection.industry.isNullOrBlank()) {
            return IndustryMergeDecision(
                industry = incomingSelection.industry,
                industryCustom = incomingSelection.industryCustom,
                source = incomingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                metadataNote = null
            )
        }

        if (!existingSelection.industryCustom.isNullOrBlank()) {
            return IndustryMergeDecision(
                industry = existingSelection.industry,
                industryCustom = existingSelection.industryCustom,
                source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                metadataNote = null
            )
        }

        if (IndustrySource.isUserSelected(existingNormalizedSource)) {
            return IndustryMergeDecision(
                industry = existingSelection.industry,
                industryCustom = existingSelection.industryCustom,
                source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                metadataNote = null
            )
        }

        if (IndustrySource.isUserSelected(incomingNormalizedSource)) {
            return IndustryMergeDecision(
                industry = incomingSelection.industry,
                industryCustom = incomingSelection.industryCustom,
                source = incomingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                metadataNote = null
            )
        }

        if (IndustrySource.isInferred(existingNormalizedSource) && IndustrySource.isInferred(incomingNormalizedSource)) {
            val sameIndustry = existingSelection.industry.equals(incomingSelection.industry, ignoreCase = true)
            val sameCustom = existingSelection.industryCustom.equals(incomingSelection.industryCustom, ignoreCase = true)
            if (sameIndustry && sameCustom) {
                return if (IndustrySource.inferredRank(incomingNormalizedSource) >
                    IndustrySource.inferredRank(existingNormalizedSource)
                ) {
                    IndustryMergeDecision(
                        industry = incomingSelection.industry,
                        industryCustom = incomingSelection.industryCustom,
                        source = incomingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                        metadataNote = null
                    )
                } else {
                    IndustryMergeDecision(
                        industry = existingSelection.industry,
                        industryCustom = existingSelection.industryCustom,
                        source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                        metadataNote = null
                    )
                }
            }

            val incomingRank = IndustrySource.inferredRank(incomingNormalizedSource)
            val existingRank = IndustrySource.inferredRank(existingNormalizedSource)
            return when {
                incomingRank > existingRank -> IndustryMergeDecision(
                    industry = incomingSelection.industry,
                    industryCustom = incomingSelection.industryCustom,
                    source = incomingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                    metadataNote = null
                )
                existingRank > incomingRank -> IndustryMergeDecision(
                    industry = existingSelection.industry,
                    industryCustom = existingSelection.industryCustom,
                    source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                    metadataNote = null
                )
                else -> IndustryMergeDecision(
                    industry = existingSelection.industry,
                    industryCustom = existingSelection.industryCustom,
                    source = existingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
                    metadataNote = buildIndustryAltMetadataNote(
                        incomingIndustry = incomingSelection.industry,
                        incomingIndustryCustom = incomingSelection.industryCustom,
                        incomingSource = incomingNormalizedSource
                    )
                )
            }
        }

        return IndustryMergeDecision(
            industry = incomingSelection.industry,
            industryCustom = incomingSelection.industryCustom,
            source = incomingNormalizedSource?.takeUnless { it == IndustrySource.EMPTY },
            metadataNote = null
        )
    }

    fun appendIndustryMetadata(rawOcrText: String?, metadataNote: String?): String? {
        val note = metadataNote?.trim().orEmpty()
        val base = rawOcrText?.trim().orEmpty()
        if (note.isBlank()) return base.ifBlank { null }
        if (base.contains(note)) return base.ifBlank { null }
        return if (base.isBlank()) {
            note
        } else {
            "$base\n$note"
        }
    }

    private fun buildIndustryAltMetadataNote(
        incomingIndustry: String?,
        incomingIndustryCustom: String?,
        incomingSource: String?
    ): String? {
        val value = incomingIndustry?.trim().orEmpty()
        if (value.isBlank()) return null
        val custom = incomingIndustryCustom?.trim().orEmpty()
        val displayValue = if (value.equals("Other", ignoreCase = true) && custom.isNotBlank()) {
            "Other:$custom"
        } else {
            value
        }
        val source = incomingSource?.trim().orEmpty()
            .ifBlank { IndustrySource.HEURISTIC_INFERRED }
        return "$INTERNAL_INDUSTRY_METADATA_PREFIX source=$source value=$displayValue"
    }
}
