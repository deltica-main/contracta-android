package com.example.businesscardscanner.ui.viewmodel

import com.example.businesscardscanner.domain.logic.DataExtractor
import com.example.businesscardscanner.domain.logic.IndustryPrefillPolicy
import com.example.businesscardscanner.domain.model.Contact
import java.util.Locale

data class MergePreviewField(
    val label: String,
    val existingValue: String,
    val incomingValue: String,
    val mergedValue: String,
    val changesExisting: Boolean,
    val willOverwriteExisting: Boolean
)

internal fun mergePreviewValue(incoming: String?, existing: String?): String? {
    return incoming?.takeIf { it.isNotBlank() } ?: existing?.takeIf { it.isNotBlank() }
}

internal fun buildDuplicateReasons(
    existing: Contact,
    incomingEmail: String?,
    incomingPhone: String?
): List<String> {
    val reasons = mutableListOf<String>()
    val normalizedIncomingEmail = incomingEmail?.trim()?.lowercase(Locale.US)
    val normalizedExistingEmail = existing.email?.trim()?.lowercase(Locale.US)
    if (!normalizedIncomingEmail.isNullOrBlank() &&
        !normalizedExistingEmail.isNullOrBlank() &&
        normalizedIncomingEmail == normalizedExistingEmail
    ) {
        reasons += "Same email"
    }

    val normalizedIncomingPhone = DataExtractor.normalizePhoneNumber(incomingPhone)
    val normalizedExistingPhone = DataExtractor.normalizePhoneNumber(existing.phone)
    if (!normalizedIncomingPhone.isNullOrBlank() &&
        !normalizedExistingPhone.isNullOrBlank() &&
        normalizedIncomingPhone == normalizedExistingPhone
    ) {
        reasons += "Same phone"
    }

    return reasons.ifEmpty { listOf("Matching contact info") }
}

internal fun buildMergePreviewFields(
    existing: Contact,
    reviewFields: ReviewFields,
    normalizedIncomingPhone: String?,
    resolvedIndustry: String?,
    resolvedIndustryCustom: String?,
    resolvedIndustrySource: String?,
    incomingRawOcrText: String?
): List<MergePreviewField> {
    val industryMergeDecision = IndustryPrefillPolicy.resolveMerge(
        existingIndustry = existing.industry,
        existingIndustryCustom = existing.industryCustom,
        existingSource = existing.industrySource,
        incomingIndustry = resolvedIndustry,
        incomingIndustryCustom = resolvedIndustryCustom,
        incomingSource = resolvedIndustrySource
    )
    val mergedRawOcrText = IndustryPrefillPolicy.appendIndustryMetadata(
        rawOcrText = mergePreviewValue(incomingRawOcrText, existing.rawOcrText),
        metadataNote = industryMergeDecision.metadataNote
    )
    val incomingFields = listOf(
        "Name" to reviewFields.name.ifBlank { null },
        "Title" to reviewFields.title.ifBlank { null },
        "Company" to reviewFields.company.ifBlank { null },
        "Email" to reviewFields.email.ifBlank { null },
        "Phone" to normalizedIncomingPhone?.ifBlank { null },
        "Website" to reviewFields.website.ifBlank { null },
        "Industry" to resolvedIndustry?.ifBlank { null },
        "Industry custom" to resolvedIndustryCustom?.ifBlank { null },
        "Industry source" to resolvedIndustrySource?.ifBlank { null },
        "OCR text" to incomingRawOcrText?.ifBlank { null }
    )
    val existingFields = mapOf(
        "Name" to existing.name,
        "Title" to existing.title,
        "Company" to existing.company,
        "Email" to existing.email,
        "Phone" to existing.phone,
        "Website" to existing.website,
        "Industry" to existing.industry,
        "Industry custom" to existing.industryCustom,
        "Industry source" to existing.industrySource,
        "OCR text" to existing.rawOcrText
    )
    val mergedFields: Map<String, String?> = mapOf(
        "Industry" to industryMergeDecision.industry,
        "Industry custom" to industryMergeDecision.industryCustom,
        "Industry source" to industryMergeDecision.source,
        "OCR text" to mergedRawOcrText
    )

    return incomingFields.map { (label, incoming) ->
        val existingValue = existingFields[label]
        val merged = mergedFields[label] ?: mergePreviewValue(incoming, existingValue)
        val existingDisplay = existingValue?.takeIf { it.isNotBlank() }.orEmpty()
        val incomingDisplay = incoming?.takeIf { it.isNotBlank() }.orEmpty()
        val mergedDisplay = merged?.takeIf { it.isNotBlank() }.orEmpty()
        val changesExisting = mergedDisplay != existingDisplay
        val willOverwriteExisting = existingDisplay.isNotBlank() &&
            incomingDisplay.isNotBlank() &&
            incomingDisplay != existingDisplay
        MergePreviewField(
            label = label,
            existingValue = existingDisplay,
            incomingValue = incomingDisplay,
            mergedValue = mergedDisplay,
            changesExisting = changesExisting,
            willOverwriteExisting = willOverwriteExisting
        )
    }
}
