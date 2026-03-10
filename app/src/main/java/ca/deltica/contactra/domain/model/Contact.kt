package ca.deltica.contactra.domain.model

data class Contact(
    val id: Long = 0,
    val name: String?,
    val title: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
    val website: String?,
    val industry: String?,
    val industryCustom: String? = null,
    val industrySource: String?,
    val rawOcrText: String?,
    val imagePath: String?,
    val rawImagePath: String? = null,
    val cardCropQuad: String? = null,
    val cardCropVersion: Int = 0,
    val phoneExportStatus: String? = null,
    val phoneExportedAt: Long? = null,
    val phoneExportPayloadHash: String? = null
)
