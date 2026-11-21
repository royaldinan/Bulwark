package id.bulwark.data

import java.util.Locale

/**
 * Input checks for the report flow (PRD: a report must not be empty or a malformed domain). Kept in
 * `data/` so the Lapor screen stays a thin view over it.
 */
object ReportValidator {

    private val DOMAIN_REGEX =
        Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$")

    /** Returns the normalized domain, or null when [input] is empty or not a valid domain. */
    fun validDomain(input: String): String? {
        val normalized = DomainHasher.normalize(input)
        return normalized.takeIf { DOMAIN_REGEX.matches(it) }
    }

    /** Returns a lowercase 64-char SHA-256 hex, or null when [input] is not one. */
    fun validSha256(input: String): String? {
        val value = input.trim().lowercase(Locale.ROOT)
        return value.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}
