package com.jinatra.wend.eval

import com.jinatra.wend.engine.ProposalStatus
import java.security.MessageDigest
import java.util.UUID

data class EvaluationCase(
    val id: String,
    val index: Int,
    val category: String,
    val request: String,
    val context: EvaluationContext,
    val expected: String,
    val expectedStatus: ProposalStatus?,
) {
    val newBlockId: String get() = "d0000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
}

data class Corpus(val version: String, val sha256: String, val cases: List<EvaluationCase>) {
    companion object {
        fun load(): Corpus {
            val bytes = checkNotNull(Corpus::class.java.getResourceAsStream("/phase0-en-v1.json")).use { it.readBytes() }
            val expectedHash = checkNotNull(Corpus::class.java.getResourceAsStream("/phase0-en-v1.sha256")).bufferedReader().use { it.readText().trim().substringBefore(' ') }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(hash == expectedHash) { "Frozen evaluation corpus checksum mismatch" }
            val root = Json.parse(bytes.toString(Charsets.UTF_8)).objectValue()
            check(root["schemaVersion"] == 1L)
            val cases = (root["cases"] as List<*>).map { entry ->
                val item = entry.objectValue()
                val context = item.getValue("context").objectValue()

                @Suppress("UNCHECKED_CAST")
                val overrides = (context["statuses"] as? Map<String, String>) ?: emptyMap()
                EvaluationCase(
                    item.getValue("id") as String,
                    (item.getValue("index") as Long).toInt(),
                    item.getValue("category") as String,
                    item.getValue("request") as String,
                    Fixtures.context(context.getValue("fixture") as String, (context["now"] as? Long)?.toInt(), context["allowPastBedtime"] as? Boolean ?: false, overrides),
                    Json.stringify(item.getValue("expected")),
                    (item["proposalStatus"] as? String)?.let(ProposalStatus::valueOf),
                )
            }
            check(cases.size == 150 && cases.map { it.id }.toSet().size == 150 && cases.map { it.index } == (1..150).toList())
            cases.forEach { UUID.fromString(it.id) }
            return Corpus(root.getValue("version") as String, hash, cases)
        }
    }

    fun validate(): List<String> = cases.mapNotNull { case ->
        val decoded = DecisionDecoder.decode(case.expected, case.context, case.newBlockId)
        if (decoded !is Decoded.Valid) return@mapNotNull "Case ${case.index}: invalid expected decision"
        val status = decoded.decision.proposal(case.context)?.status
        if (status != case.expectedStatus) "Case ${case.index}: expected ${case.expectedStatus}, engine returned $status" else null
    }
}
