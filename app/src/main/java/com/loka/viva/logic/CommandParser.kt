package com.loka.viva.logic

object CommandParser {
    data class Intent(val action: Action, val query: String = "")

    enum class Action {
        OPEN_URL, SEARCH, OPEN_APP, NEW_WINDOW, CLOSE_WINDOW, SETTINGS, AI_QUERY, UNKNOWN
    }

    fun parse(text: String): Intent {
        val lower = text.lowercase()
        return when {
            Regex("""افتح\\s+(.+)""").find(lower)?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("http") } == true ->
                Intent(Action.OPEN_URL, it)
            Regex("""search\\s+(.+)""").find(lower)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } == true ->
                Intent(Action.SEARCH, it)
            Regex("""open\\s+app\\s+(.+)""").find(lower)?.groupValues?.getOrNull(1) == true ->
                Intent(Action.OPEN_APP, it)
            Regex("""نافذة\\s+جديدة""").find(lower) != null ->
                Intent(Action.NEW_WINDOW)
            Regex("""اغلق\\s+النافذة""").find(lower) != null ->
                Intent(Action.CLOSE_WINDOW)
            Regex("""settings|settings""").find(lower) != null ->
                Intent(Action.SETTINGS)
            else ->
                Intent(Action.UNKNOWN)
        }
    }
}