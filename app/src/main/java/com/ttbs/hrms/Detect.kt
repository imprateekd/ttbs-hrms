package com.ttbs.hrms

/**
 * Decides whether you are still punched in.
 *
 * The TTBS dashboard shows a WORK TIME card with two slots:
 *
 *     Punch In  09:12:33      Punch Out  00:00:00
 *
 * So the rule is exact:
 *   - Punch Out has a real time      -> you are OUT
 *   - Punch In has a time, Out is 00 -> you are STILL IN  (this is the alarm case)
 *   - both are 00:00:00              -> you never punched in today, nothing to remind
 *
 * Custom regexes and the two saved page samples stay as manual overrides / fallbacks.
 */
object Detect {

    enum class State { OUT, IN, NOT_PUNCHED, UNKNOWN, NEED_LOGIN }

    data class Result(val state: State, val reason: String)

    fun decide(
        text: String,
        hasPasswordField: Boolean,
        outRegex: String,
        inRegex: String,
        outSample: String,
        inSample: String,
        punchIn: String?,
        punchOut: String?
    ): Result {
        if (hasPasswordField) return Result(State.NEED_LOGIN, "The login form is showing")

        val t = text.trim()

        // Manual rules win, if you set them.
        if (outRegex.isNotBlank() && matches(outRegex, t)) {
            return Result(State.OUT, "Matched your punched-out rule")
        }
        if (inRegex.isNotBlank() && matches(inRegex, t)) {
            return Result(State.IN, "Matched your still-punched-in rule")
        }

        // The real thing: the Punch In / Punch Out slots.
        if (punchOut != null) {
            if (!isZero(punchOut)) {
                return Result(State.OUT, "Punch Out recorded at " + punchOut)
            }
            if (punchIn != null && !isZero(punchIn)) {
                return Result(
                    State.IN,
                    "Punched in at " + punchIn + ", Punch Out still 00:00:00"
                )
            }
            if (punchIn != null) {
                return Result(State.NOT_PUNCHED, "No punch-in recorded today")
            }
            return Result(State.UNKNOWN, "Saw the Punch Out slot but not the Punch In slot")
        }

        // Fallbacks.
        if (t.length < 20) return Result(State.UNKNOWN, "The page came back empty")

        if (outSample.isNotBlank() || inSample.isNotBlank()) {
            val so = if (outSample.isBlank()) -1.0 else similarity(t, outSample)
            val si = if (inSample.isBlank()) -1.0 else similarity(t, inSample)
            if (so >= 0.30 && so > si) {
                return Result(State.OUT, "Looks like your punched-out sample (match " + pct(so) + ")")
            }
            if (si >= 0.30 && si > so) {
                return Result(State.IN, "Looks like your still-in sample (match " + pct(si) + ")")
            }
        }

        return Result(State.UNKNOWN, "Could not find the Punch In / Punch Out times on the page")
    }

    /** "00:00:00", "00:00", "0:0:0" all count as empty. */
    fun isZero(time: String): Boolean {
        val digits = time.filter { it.isDigit() }
        return digits.isNotEmpty() && digits.all { it == '0' }
    }

    private fun matches(pattern: String, text: String): Boolean =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }
            .getOrDefault(false)

    private fun pct(v: Double): String = String.format(java.util.Locale.US, "%.0f%%", v * 100)

    fun normalize(s: String): Set<String> =
        s.lowercase()
            .replace(Regex("\\d{1,2}[:.]\\d{2}\\s*(am|pm)?"), " ")
            .replace(Regex("\\d+"), " ")
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()

    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val inter = x.intersect(y).size.toDouble()
        val union = x.union(y).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }
}
