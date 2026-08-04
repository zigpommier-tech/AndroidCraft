package com.androidcraft.studio.engine.image.onnx

import org.json.JSONObject
import java.io.File

/**
 * CLIP byte-level BPE tokenizer (the one Stable Diffusion 1.x uses), loaded from a standard
 * `vocab.json` + `merges.txt` pair. Produces the 77-length `input_ids` the CLIP text encoder wants.
 */
class ClipTokenizer(vocabJson: File, mergesTxt: File) {

    private val encoder: Map<String, Int>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder: Map<Int, Char> = bytesToUnicode()

    private val bos: Int
    private val eos: Int
    private val maxLen = 77

    // CLIP's tokenization regex.
    private val pattern = Regex(
        "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|" +
            "\\p{L}+|\\p{N}|[^\\s\\p{L}\\p{N}]+",
        RegexOption.IGNORE_CASE,
    )

    init {
        val obj = JSONObject(vocabJson.readText())
        val map = HashMap<String, Int>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.getInt(k)
        }
        encoder = map
        bos = encoder["<|startoftext|>"] ?: 49406
        eos = encoder["<|endoftext|>"] ?: 49407

        val ranks = HashMap<Pair<String, String>, Int>()
        mergesTxt.useLines { lines ->
            lines.drop(1).forEachIndexed { i, line ->
                val parts = line.trim().split(" ")
                if (parts.size == 2) ranks[parts[0] to parts[1]] = i
            }
        }
        bpeRanks = ranks
    }

    /** Returns exactly [maxLen] token ids (bos … eos, eos-padded). */
    fun encode(text: String): IntArray {
        val tokens = ArrayList<Int>()
        tokens.add(bos)
        for (match in pattern.findAll(text.lowercase().trim())) {
            val token = match.value
            val mapped = token.toByteArray(Charsets.UTF_8)
                .joinToString("") { byteEncoder[it.toInt() and 0xFF].toString() }
            for (piece in bpe(mapped).split(" ")) {
                encoder[piece]?.let { tokens.add(it) }
            }
        }
        val ids = IntArray(maxLen) { eos }
        val limit = minOf(tokens.size, maxLen - 1)
        for (i in 0 until limit) ids[i] = tokens[i]
        ids[minOf(tokens.size, maxLen - 1)] = eos
        return ids
    }

    private fun bpe(token: String): String {
        if (token.isEmpty()) return token
        var word = token.map { it.toString() }.toMutableList()
        word[word.lastIndex] = word.last() + "</w>"

        while (word.size > 1) {
            var minRank = Int.MAX_VALUE
            var minPair = -1
            for (i in 0 until word.size - 1) {
                val rank = bpeRanks[word[i] to word[i + 1]] ?: continue
                if (rank < minRank) {
                    minRank = rank
                    minPair = i
                }
            }
            if (minPair == -1) break
            val merged = word[minPair] + word[minPair + 1]
            val next = ArrayList<String>(word.size - 1)
            next.addAll(word.subList(0, minPair))
            next.add(merged)
            if (minPair + 2 <= word.lastIndex) next.addAll(word.subList(minPair + 2, word.size))
            word = next
        }
        return word.joinToString(" ")
    }

    private fun bytesToUnicode(): Map<Int, Char> {
        val bs = ArrayList<Int>()
        (('!'.code)..('~'.code)).forEach { bs.add(it) }
        (('¡'.code)..('¬'.code)).forEach { bs.add(it) }
        (('®'.code)..('ÿ'.code)).forEach { bs.add(it) }
        val cs = ArrayList(bs)
        var n = 0
        for (b in 0 until 256) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        val map = HashMap<Int, Char>()
        for (i in bs.indices) map[bs[i]] = cs[i].toChar()
        return map
    }
}
