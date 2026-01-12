package io.github.devapro.ai.embeds.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownChunkerTest {

    @Test
    fun testSimpleMarkdownChunking() {
        val chunker = TextChunker(chunkSize = 200, chunkOverlap = 50)

        val markdown = """
            # Introduction

            This is a simple paragraph about the introduction.

            ## Details

            More details here in another paragraph.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        assertTrue(chunks.isNotEmpty(), "Should create chunks")
        // Each chunk should have heading metadata
        chunks.forEach { chunk ->
            assertTrue(
                chunk.metadata.containsKey("heading"),
                "Chunk should have heading metadata"
            )
        }
    }

    @Test
    fun testCodeBlockPreservation() {
        val chunker = TextChunker(chunkSize = 150, chunkOverlap = 30)

        val markdown = """
            # Code Example

            Here's some code:

            ```kotlin
            fun main() {
                println("Hello World")
                println("This is a code block")
                println("It should stay intact")
            }
            ```

            That was the code.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // Find chunk containing code block
        val codeChunk = chunks.find { it.text.contains("```kotlin") }
        assertTrue(codeChunk != null, "Should have chunk with code block")

        // Verify code block is complete
        assertTrue(codeChunk!!.text.contains("fun main()"), "Code block should be complete")
        assertTrue(codeChunk.text.contains("```"), "Code block should have delimiters")

        // Count occurrences of ``` - should be 2 (opening and closing)
        val delimiterCount = codeChunk.text.split("```").size - 1
        assertEquals(2, delimiterCount, "Code block should be intact with both delimiters")
    }

    @Test
    fun testLargeCodeBlockIsolation() {
        val chunker = TextChunker(chunkSize = 100, chunkOverlap = 20)

        val largeCode = "fun example() {\n" + "    println(\"line\")\n".repeat(20) + "}"

        val markdown = """
            # API Documentation

            Short intro.

            ```kotlin
            $largeCode
            ```

            More text after.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // Code block larger than chunk size should get its own chunk (with heading context)
        val codeChunk = chunks.find { it.text.contains("```kotlin") }
        assertTrue(codeChunk != null, "Should have code chunk")

        // Code should be complete despite being larger than chunk size
        val codeStart = codeChunk!!.text.indexOf("```kotlin")
        val codeEnd = codeChunk.text.lastIndexOf("```")
        assertTrue(codeEnd > codeStart, "Code block should be complete")

        // Should have metadata indicating it's a code block
        assertTrue(
            codeChunk.metadata["type"] == "code" || codeChunk.text.contains("```"),
            "Code chunk should be marked or contain code"
        )
    }

    @Test
    fun testHeadingContextPreservation() {
        val chunker = TextChunker(chunkSize = 150, chunkOverlap = 30)

        val markdown = """
            # Main Topic

            ${"Sentence. ".repeat(20)}

            ## Subtopic

            ${"More content. ".repeat(10)}
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // All chunks should have their heading in metadata or text
        chunks.forEach { chunk ->
            val hasHeading = chunk.text.contains("#") || chunk.metadata["heading"]?.isNotEmpty() == true
            assertTrue(hasHeading, "Chunk should preserve heading context: ${chunk.text.take(50)}")
        }
    }

    @Test
    fun testSectionGrouping() {
        val chunker = TextChunker(chunkSize = 300, chunkOverlap = 50)

        val markdown = """
            # Section One

            Short paragraph in section one.

            # Section Two

            Short paragraph in section two.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // Should have at least 2 chunks (one per section if they fit)
        assertTrue(chunks.size >= 2, "Should have multiple chunks for different sections")
    }

    @Test
    fun testListPreservation() {
        val chunker = TextChunker(chunkSize = 200, chunkOverlap = 40)

        val markdown = """
            # Features

            - Feature one
            - Feature two
            - Feature three

            More content here.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // List should be kept together if possible
        val listChunk = chunks.find { it.text.contains("-Feature") || it.text.contains("- Feature") }
        assertTrue(listChunk != null, "Should have chunk with list")
    }

    @Test
    fun testEmptyMarkdown() {
        val chunker = TextChunker()
        val chunks = chunker.chunkMarkdown("")
        assertTrue(chunks.isEmpty(), "Empty markdown should produce no chunks")
    }

    @Test
    fun testNoHeadings() {
        val chunker = TextChunker(chunkSize = 100, chunkOverlap = 20)

        val markdown = """
            Just some regular text.
            Without any headings.
            Multiple paragraphs.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        assertTrue(chunks.isNotEmpty(), "Should create chunks even without headings")
    }

    @Test
    fun testMultipleCodeBlocks() {
        val chunker = TextChunker(chunkSize = 200, chunkOverlap = 40)

        val markdown = """
            # Examples

            First example:
            ```python
            print("hello")
            ```

            Second example:
            ```javascript
            console.log("world")
            ```
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        // Count chunks containing code blocks
        val codeChunks = chunks.filter { it.text.contains("```") }
        assertTrue(codeChunks.isNotEmpty(), "Should have chunks with code blocks")

        // Verify each code block is complete
        codeChunks.forEach { chunk ->
            val openCount = chunk.text.split("```").size - 1
            assertTrue(openCount % 2 == 0, "Code blocks should be complete (even number of ```)")
        }
    }

    @Test
    fun testRealWorldDocumentation() {
        val chunker = TextChunker(chunkSize = 500, chunkOverlap = 100)

        val markdown = """
            # API Documentation

            ## Installation

            To install the library, run:

            ```bash
            npm install example-lib
            ```

            ## Usage

            Here's how to use the library:

            ```javascript
            import { Example } from 'example-lib';

            const instance = new Example();
            instance.doSomething();
            ```

            ### Configuration

            You can configure the library by passing options.

            ## Advanced Features

            The library supports advanced features like callbacks and promises.
        """.trimIndent()

        val chunks = chunker.chunkMarkdown(markdown)

        assertTrue(chunks.isNotEmpty(), "Should create chunks from real documentation")

        // Verify code blocks are intact
        val codeChunks = chunks.filter { it.text.contains("```") }
        codeChunks.forEach { chunk ->
            val codeBlocksComplete = chunk.text.split("```").size - 1
            assertTrue(codeBlocksComplete % 2 == 0, "All code blocks should be complete")
        }

        // Verify headings are preserved
        chunks.forEach { chunk ->
            assertTrue(
                chunk.metadata.containsKey("heading"),
                "All chunks should have heading context"
            )
        }
    }
}
