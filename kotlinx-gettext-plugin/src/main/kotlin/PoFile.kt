/*
 * Copyright 2022 Victor Kropp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package name.kropp.kotlinx.gettext

import java.io.*

/**
 * Gettext .po (Portable Object) & .pot (PO Template) files support
 */
class PoFile(
    val entries: List<PoEntry>,
    val header: PoEntry = DEFAULT_POT_HEADER
) {
    fun write(out: OutputStream) {
        PrintStream(out, false, Charsets.UTF_8).use { writer ->
            writer.println(header)
            for (message in entries) {
                writer.println()
                message.write(writer)
            }
        }
    }

    fun update(messages: List<PoEntry>): PoFile {
        val grouped = messages.groupBy { it.text }.toMutableMap()
        val newEntries = entries.map { entry ->
            val updated = grouped.remove(entry.text)
            if (updated != null) {
                val newReferences = updated.flatMap { it.references }
                val updatedPaths = newReferences.map { it.substringBefore(':') }
                entry.copy(references = (entry.references.filterNot { updatedPaths.any { path -> it.startsWith(path)} } + newReferences).sortedWith(referenceComparator))
            } else {
                entry
            }
        } + grouped.map { group ->
            group.value.first().copy(references = group.value.flatMap { it.references }.sortedWith(referenceComparator))
        }
        return PoFile(newEntries, header)
    }

    companion object {
        @JvmStatic
        fun fromUnmerged(messages: List<PoEntry>): PoFile {
            val merged =
                messages
                    .groupBy { it.text }
                    .map { group ->
                        group.value.first().copy(references = group.value.flatMap { it.references })
                    }
            return PoFile(merged)
        }

        @JvmStatic
        val referenceComparator = Comparator<String> { r1, r2 ->
            val comparePaths = r1.substringBefore(':').compareTo(r2.substringBefore(':'))
            if (comparePaths != 0) {
                comparePaths
            } else {
                (r1.substringAfter(':').toIntOrNull() ?: 0).compareTo((r2.substringAfter(':').toIntOrNull() ?: 0))
            }
        }

        @JvmStatic
        fun read(input: InputStream): PoFile {
            val entries = mutableListOf<PoEntry>()

            var comments = mutableListOf<String>()
            var extractedComments = mutableListOf<String>()
            var references = mutableListOf<String>()
            var flags: String? = null
            var previous = mutableListOf<String>()
            var context: String? = null
            var text: String? = null
            var plural: String? = null
            var cases = mutableListOf<String>()

            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.isBlank() -> {
                            text?.let {
                                entries += PoEntry(comments, extractedComments, references, flags, previous, context, it, plural, cases)
                            }
                            comments = mutableListOf()
                            extractedComments = mutableListOf()
                            references = mutableListOf()
                            flags = null
                            previous = mutableListOf()
                            context = null
                            text = null
                            plural = null
                            cases = mutableListOf()
                        }
                        line == "#" -> comments += ""
                        line.startsWith("# ") -> comments += line.substringAfter("# ")
                        line.startsWith("#. ") -> extractedComments += line.substringAfter("#. ")
                        line.startsWith("#: ") -> references += line.substringAfter("#: ")
                        line.startsWith("#, ") -> flags = line.substringAfter("#, ")
                        line.startsWith("#| ") -> previous += line.substringAfter("#| ")
                        line.startsWith("msgctxt ") -> context = line.substringAfter("msgctxt ").unescape()
                        line.startsWith("msgid ") -> text = line.substringAfter("msgid ").unescape()
                        line.startsWith("msgid_plural ") -> plural = line.substringAfter("msgid_plural ").unescape()
                        line.startsWith("msgstr ") -> cases += line.substringAfter("msgstr ").unescape()
                        line.startsWith("msgstr[") -> cases += line.substringAfter("msgstr[").substringAfter("] ").unescape()
                        else -> {
                            if (cases.isNotEmpty()) {
                                cases[0] = cases[0] + "\"\n\"" + line.trim('"')
                            }
                        }
                    }
                }
                text?.let {
                    entries += PoEntry(comments, extractedComments, references, flags, previous, context, it, plural, cases)
                }
            }

            return PoFile(entries.filter { it.text.isNotEmpty() }, entries.firstOrNull { it.text.isEmpty() } ?: DEFAULT_POT_HEADER)
        }

        private fun String.unescape(): String {
            val trimmed = trim()
            val unquoted = if (trimmed.startsWith('"')) trimmed.substring(1, trimmed.lastIndex) else trimmed
            return unquoted.replace("\\\"", "\"").replace("\\n", "\n")
        }
    }
}

val DEFAULT_POT_HEADER = PoEntry(
    listOf(
        "SOME DESCRIPTIVE TITLE.",
        "Copyright (C) YEAR THE PACKAGE'S COPYRIGHT HOLDER",
        "This file is distributed under the same license as the PACKAGE package.",
        "FIRST AUTHOR <EMAIL@ADDRESS>, YEAR.",
        ""
    ),
    emptyList(),
    emptyList(),
    "fuzzy",
    emptyList(),
    null,
    "",
    null,
    listOf(
        "Project-Id-Version: PACKAGE VERSION\n"+
        "Report-Msgid-Bugs-To: \n"+
        "\n"+
        "PO-Revision-Date: YEAR-MO-DA HO:MI+ZONE\n"+
        "Last-Translator: FULL NAME <EMAIL@ADDRESS>\n"+
        "Language-Team: LANGUAGE <LL@li.org>\n"+
        "Language: \n"+
        "MIME-Version: 1.0\n"+
        "Content-Type: text/plain; charset=UTF-8\n"+
        "Content-Transfer-Encoding: 8bit\n"+
        "Plural-Forms: nplurals=INTEGER; plural=EXPRESSION;\n"
    )
)