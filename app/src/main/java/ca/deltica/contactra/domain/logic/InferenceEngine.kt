package ca.deltica.contactra.domain.logic

/**
 * Performs simple inference of industry from provided fields. Uses keyword
 * matching against job titles and company names. Each inference returns a
 * confidence flag: "high" if a strong match is found, otherwise "low".
 */
data class InferenceResult(
    val industry: String?,
    val industryConfidence: String?
)

object InferenceEngine {
    fun infer(title: String?, company: String?): InferenceResult {
        val (industry, industryConf) = IndustryCatalog.inferIndustry(title, company)
        return InferenceResult(
            industry = industry,
            industryConfidence = industryConf
        )
    }
}
