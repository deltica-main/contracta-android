package com.example.businesscardscanner.domain.logic

object ContactImagePolicy {
    private const val cardSuffix = "_card.jpg"
    private const val logoSuffix = "_logo.png"
    private const val rawSuffix = "_raw.jpg"

    fun buildBaseId(timestampMs: Long): String = "contact_$timestampMs"

    fun buildCardFileName(baseId: String): String = "${baseId}${cardSuffix}"

    fun buildLogoFileName(baseId: String): String = "${baseId}${logoSuffix}"

    fun buildRawFileName(baseId: String): String = "${baseId}${rawSuffix}"

    fun isAutoLogoPath(path: String?): Boolean {
        return path?.trim()?.endsWith(logoSuffix, ignoreCase = true) == true
    }

    fun deriveOriginalCardPath(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.endsWith(logoSuffix, ignoreCase = true) -> {
                value.dropLast(logoSuffix.length) + cardSuffix
            }
            value.endsWith(cardSuffix, ignoreCase = true) -> value
            else -> null
        }
    }

    fun deriveLogoPath(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.endsWith(cardSuffix, ignoreCase = true) -> {
                value.dropLast(cardSuffix.length) + logoSuffix
            }
            value.endsWith(logoSuffix, ignoreCase = true) -> value
            else -> null
        }
    }

    fun deriveRawPath(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.endsWith(cardSuffix, ignoreCase = true) -> {
                value.dropLast(cardSuffix.length) + rawSuffix
            }
            value.endsWith(logoSuffix, ignoreCase = true) -> {
                value.dropLast(logoSuffix.length) + rawSuffix
            }
            value.endsWith(rawSuffix, ignoreCase = true) -> value
            else -> null
        }
    }

    fun managedVisualCandidates(path: String?): Set<String> {
        val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return emptySet()
        val candidates = linkedSetOf<String>()
        candidates += value
        deriveOriginalCardPath(value)?.let { candidates += it }
        deriveLogoPath(value)?.let { candidates += it }
        deriveRawPath(value)?.let { candidates += it }
        return candidates
    }
}
