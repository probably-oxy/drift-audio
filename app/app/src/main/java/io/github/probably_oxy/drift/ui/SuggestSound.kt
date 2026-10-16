package io.github.probably_oxy.drift.ui

import androidx.core.net.toUri

/**
 * Builds the link to the "Suggest a sound" GitHub issue form
 * (.github/ISSUE_TEMPLATE/sound-suggestion.yml). Opened in the user's browser —
 * Drift itself never touches the network. Query keys matching the form's field
 * ids pre-fill those fields.
 */
object SuggestSound {
    private const val NEW_ISSUE = "https://github.com/probably-oxy/drift-audio/issues/new"

    fun url(soundName: String? = null): String {
        val builder = NEW_ISSUE.toUri().buildUpon()
            .appendQueryParameter("template", "sound-suggestion.yml")
        if (!soundName.isNullOrBlank()) {
            builder.appendQueryParameter("title", "[Sound] $soundName")
            builder.appendQueryParameter("sound-name", soundName)
        }
        return builder.build().toString()
    }
}
