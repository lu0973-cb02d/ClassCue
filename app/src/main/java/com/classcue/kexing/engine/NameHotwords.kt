package com.classcue.kexing.engine

import java.io.File

/**
 * Builds the hotword list that biases the streaming recognizer toward the user's own name and
 * forms of address. sherpa-onnx rejects the whole list if any character is missing from the
 * model's tokens, so every entry is checked character by character first.
 */
object NameHotwords {
    private const val MAX_ENTRIES = 20
    private const val MAX_LENGTH = 12

    fun build(candidates: List<String>, vocabulary: Set<String>): List<String> = candidates
        .map { it.trim() }
        .filter { it.length in 2..MAX_LENGTH }
        .filter { word -> word.none { it == '/' || it.isWhitespace() } }
        .filter { word -> word.all { it.toString() in vocabulary } }
        .distinct()
        .take(MAX_ENTRIES)

    /** First column of a sherpa-onnx tokens.txt ("<token> <id>" per line). */
    fun readVocabulary(tokens: File): Set<String> = tokens.useLines { lines ->
        lines.mapNotNull { line -> line.substringBeforeLast(' ', "").takeIf { it.isNotEmpty() } }.toSet()
    }
}
