package app.smartlife.patches.smartlife.shared

/**
 * Minimal JSON tree used to edit the app's asset config files structurally.
 *
 * Plain text replacement cannot tell an array element from an object key or a
 * property value, and leaves dangling commas when the file has whitespace. Parsing
 * into a tree and writing it back keeps the output valid JSON whatever the input
 * formatting. String and number tokens are kept verbatim ([raw]) so values that
 * are not touched are written back byte-for-byte; only insignificant whitespace
 * is dropped (the shipped assets are already minified).
 *
 * Gson is compile-only in this project (not bundled into the patches), so this
 * small parser avoids adding a runtime dependency.
 */
internal sealed class JsonNode {
    class Obj(val entries: MutableList<Pair<Str, JsonNode>>) : JsonNode()
    class Arr(val items: MutableList<JsonNode>) : JsonNode()

    /** A string token. [raw] includes the surrounding quotes and original escapes. */
    class Str(val raw: String, val value: String) : JsonNode()

    /** A number, `true`, `false` or `null`, kept as the original token text. */
    class Lit(val raw: String) : JsonNode()

    fun write(): String = StringBuilder().also { writeTo(it) }.toString()

    private fun writeTo(out: StringBuilder) {
        when (this) {
            is Obj -> {
                out.append('{')
                entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) out.append(',')
                    out.append(key.raw).append(':')
                    value.writeTo(out)
                }
                out.append('}')
            }
            is Arr -> {
                out.append('[')
                items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    item.writeTo(out)
                }
                out.append(']')
            }
            is Str -> out.append(raw)
            is Lit -> out.append(raw)
        }
    }

    /** Visit this node and every node below it, depth first. */
    fun forEachNode(action: (JsonNode) -> Unit) {
        action(this)
        when (this) {
            is Obj -> entries.forEach { it.second.forEachNode(action) }
            is Arr -> items.forEach { it.forEachNode(action) }
            else -> Unit
        }
    }

    /**
     * Remove string elements whose value is in [targets] from every array in the tree.
     * Object keys and property values are never touched.
     * @return the values that were removed.
     */
    fun removeArrayStrings(targets: Set<String>): Set<String> {
        val removed = mutableSetOf<String>()
        forEachNode { node ->
            if (node is Arr) node.items.removeAll { item ->
                val hit = item is Str && item.value in targets
                if (hit) removed += (item as Str).value
                hit
            }
        }
        return removed
    }

    /**
     * Remove `"key":"value"` members (string values only) from every object in the tree
     * when the key or the value is in [targets].
     * @return the target names that matched at least one member.
     */
    fun removeStringMembers(targets: Set<String>): Set<String> {
        val removed = mutableSetOf<String>()
        forEachNode { node ->
            if (node is Obj) node.entries.removeAll { (key, value) ->
                if (value !is Str) return@removeAll false
                val hits = listOf(key.value, value.value).filter { it in targets }
                removed += hits
                hits.isNotEmpty()
            }
        }
        return removed
    }

    companion object {
        fun parse(text: String): JsonNode {
            val parser = Parser(text)
            val root = parser.readValue()
            parser.skipWhitespace()
            parser.expectEnd()
            return root
        }
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun skipWhitespace() {
            // Also skip a UTF-8 BOM at the start of the file.
            while (pos < s.length && (s[pos].isWhitespace() || s[pos] == '﻿')) pos++
        }

        fun expectEnd() {
            if (pos != s.length) fail("unexpected trailing content")
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("Invalid JSON at offset $pos: $message")

        private fun peek(): Char {
            skipWhitespace()
            if (pos >= s.length) fail("unexpected end of input")
            return s[pos]
        }

        private fun expect(c: Char) {
            if (peek() != c) fail("expected '$c'")
            pos++
        }

        fun readValue(): JsonNode = when (peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            else -> readLiteral()
        }

        private fun readObject(): Obj {
            expect('{')
            val entries = mutableListOf<Pair<Str, JsonNode>>()
            if (peek() == '}') {
                pos++
                return Obj(entries)
            }
            while (true) {
                if (peek() != '"') fail("expected object key")
                val key = readString()
                expect(':')
                entries += key to readValue()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return Obj(entries) }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun readArray(): Arr {
            expect('[')
            val items = mutableListOf<JsonNode>()
            if (peek() == ']') {
                pos++
                return Arr(items)
            }
            while (true) {
                items += readValue()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return Arr(items) }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun readString(): Str {
            expect('"')
            val start = pos - 1
            val value = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("unterminated string")
                when (val c = s[pos++]) {
                    '"' -> return Str(s.substring(start, pos), value.toString())
                    '\\' -> {
                        if (pos >= s.length) fail("unterminated escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> value.append(e)
                            'b' -> value.append('\b')
                            'f' -> value.append('\u000C')
                            'n' -> value.append('\n')
                            'r' -> value.append('\r')
                            't' -> value.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) fail("bad unicode escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: fail("bad unicode escape")
                                value.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> value.append(c)
                }
            }
        }

        private fun readLiteral(): Lit {
            val start = pos
            while (pos < s.length && s[pos] !in ",:]}" && !s[pos].isWhitespace()) pos++
            val raw = s.substring(start, pos)
            val valid = raw == "true" || raw == "false" || raw == "null" ||
                NUMBER.matches(raw)
            if (!valid) fail("unexpected token '$raw'")
            return Lit(raw)
        }

        companion object {
            private val NUMBER = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
        }
    }
}
