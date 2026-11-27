package com.example.snoozer

import com.example.snoozer.util.Strings
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** English and Korean, read from the app's string files. */
val EN = ResourceStrings(Locale.ENGLISH)
val KO = ResourceStrings(Locale.KOREAN)

/**
 * [Strings] for unit tests, read from the app's own res/values/strings.xml files the way Android
 * reads them: the language's folder first (values-ko), then the English default. Tests run on
 * the plain JVM, where Android's resources aren't available.
 */
class ResourceStrings(private val phone: Locale = Locale.ENGLISH) : Strings {

    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()

    init {
        // The default folder first, then the language's, so its words replace the defaults.
        val folders = listOf("values") + if (phone.language == "en") emptyList() else listOf("values-${phone.language}")
        folders.forEach { folder ->
            val file = File(RES, "$folder/strings.xml")
            if (file.exists()) read(file)
        }
    }

    /** Like the app: the language of the words found, which is English for an untranslated phone language. */
    override val locale: Locale get() = Locale.forLanguageTag(strings.getValue("strings_locale"))

    override fun get(id: Int, vararg args: Any): String {
        val name = nameOf(STRING_IDS, id)
        val text = strings[name] ?: error("No string \"$name\" for $locale")
        return format(text, args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = nameOf(PLURAL_IDS, id)
        val forms = plurals[name] ?: error("No plurals \"$name\" for $locale")
        val text = forms[quantity(count)] ?: forms["other"] ?: error("No \"other\" form of \"$name\" for $locale")
        return format(text, args)
    }

    /** Android's plural categories for the app's languages: English has one and other, Korean only other. */
    private fun quantity(count: Int): String = when (locale.language) {
        "en" -> if (count == 1) "one" else "other"
        else -> "other"
    }

    private fun format(text: String, args: Array<out Any>): String =
        if (args.isEmpty()) text else String.format(locale, text, *args)

    private fun read(file: File) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> strings[name] = unescape(node.textContent)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to unescape(item.textContent)
                    }
                }
            }
        }
    }

    companion object {
        /** Gradle runs unit tests from the module folder (app/). */
        private val RES = File("src/main/res")

        private val STRING_IDS = idsOf("string")
        private val PLURAL_IDS = idsOf("plurals")

        private fun idsOf(type: String): Map<Int, String> =
            runCatching { Class.forName("com.example.snoozer.R\$$type") }.getOrNull()
                ?.fields?.associate { it.getInt(null) to it.name }
                .orEmpty()

        private fun nameOf(ids: Map<Int, String>, id: Int): String = ids[id] ?: error("Unknown resource id $id")

        /**
         * Android's rules for a string's text: outside double quotes, runs of white space become
         * one space and the ends are trimmed; inside them, text is kept as is. Backslash escapes
         * \n, \t, \', \", \\, \@, \? and \uXXXX.
         */
        fun unescape(raw: String): String {
            val out = StringBuilder()
            var quoted = false
            var space = false
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    c == '\\' && i + 1 < raw.length -> {
                        space = false
                        val n = raw[i + 1]
                        i++
                        when (n) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'u' -> {
                                out.append(raw.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }
                            else -> out.append(n)
                        }
                    }
                    c == '"' -> quoted = !quoted
                    !quoted && c.isWhitespace() -> {
                        // Leading space is dropped; a run counts once.
                        if (!space && out.isNotEmpty()) out.append(' ')
                        space = true
                    }
                    else -> {
                        space = false
                        out.append(c)
                    }
                }
                i++
            }
            // Trailing space outside quotes is dropped too.
            if (space && out.endsWith(' ')) out.setLength(out.length - 1)
            return out.toString()
        }
    }
}
