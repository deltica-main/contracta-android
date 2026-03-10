package ca.deltica.contactra.domain.logic

import android.content.Context
import android.graphics.Bitmap

internal object CardCropDebugExporter {
    fun export(
        context: Context,
        baseId: String,
        before: Bitmap,
        after: Bitmap,
        auditRecord: CardCropAuditRecord? = null
    ) = Unit
}
