package com.classcue.kexing.engine

data class TextToken(val text: String, val start: Int, val end: Int)

object TextSelection {
    private val tokenPattern = Regex("[A-Za-z0-9_./:+-]+|[\\u3400-\\u9FFF]|[^\\s]")

    fun tokenize(text: String): List<TextToken> = tokenPattern
        .findAll(text)
        .map { match -> TextToken(match.value, match.range.first, match.range.last + 1) }
        .toList()

    fun selectedText(source: String, tokens: List<TextToken>, selectedIndices: Set<Int>): String {
        val selected = selectedIndices.filter { it in tokens.indices }.distinct().sorted()
        if (selected.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var groupStart = selected.first()
        var previous = groupStart
        selected.drop(1).forEach { index ->
            if (index != previous + 1) {
                parts += source.substring(tokens[groupStart].start, tokens[previous].end).trim()
                groupStart = index
            }
            previous = index
        }
        parts += source.substring(tokens[groupStart].start, tokens[previous].end).trim()
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }
}
