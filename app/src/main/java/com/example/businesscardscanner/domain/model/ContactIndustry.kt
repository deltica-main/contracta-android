package com.example.businesscardscanner.domain.model

fun Contact.industryDisplayLabel(): String? {
    val value = industry?.trim().orEmpty()
    if (value.isBlank()) return null
    val custom = industryCustom?.trim().orEmpty()
    return if (value.equals("Other", ignoreCase = true) && custom.isNotBlank()) {
        custom
    } else {
        value
    }
}
