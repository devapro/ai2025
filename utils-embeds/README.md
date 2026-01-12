# Utils Module - RAG Embeddings Generator

This module provides a utility application for creating embeddings from text documents and storing them in a vector database for RAG (Retrieval-Augmented Generation) applications.

## Features

- **Text Chunking**: Intelligently splits text into manageable chunks with overlap
- **Markdown Support**: Structure-aware chunking for software documentation
  - Preserves code blocks (never splits them)
  - Maintains heading context
  - Respects document structure
- **Folder Processing**: Index entire documentation directories recursively
  - Processes multiple files in batch
  - Configurable file extension filtering
  - Per-file metadata tracking
  - Continues processing on individual file errors
- **Embedding Generation**: Uses OpenAI's API to generate vector embeddings
- **Vector Storage**: Stores embeddings in SQLite database with cosine similarity search
- **Best Practices**: Implements RAG best practices for production-ready systems

## Architecture

The application consists of three main components:

### 1. TextChunker
Splits input text into chunks with configurable size and overlap.

**Features:**
- Configurable chunk size (default: 500 characters)
- Configurable overlap (default: 100 characters)
- Smart boundary detection (sentence/word boundaries)
- **Markdown support**: Structure-aware chunking for `.md` files
  - Never splits code blocks
  - Preserves heading context in all chunks
  - Groups content by sections
  - Maintains document hierarchy
- Metadata preservation

### 2. EmbeddingGenerator
Generates vector embeddings using OpenAI's API.

**Features:**
- Uses `text-embedding-3-small` model (1536 dimensions)
- Batch processing for efficiency
- Rate limiting protection
- Error handling and retry logic

### 3. VectorDatabase
Stores and retrieves embeddings using SQLite with Exposed ORM.

**Features:**
- Cosine similarity search
- Efficient vector storage
- Metadata support
- Query filtering

## Requirements

- Java 21 or higher
- OpenAI API key
- Gradle 8.x

## Setup

1. **Set OpenAI API Key**

Create a `.env` file in the project root:

```bash
OPENAI_API_KEY=your-api-key-here
```

2. **Build the Application**

```bash
./gradlew :utils:build
```

## Usage

### Running the Application

```bash
./gradlew :utils:run
```

**Automatic Markdown Detection**: The application automatically detects `.md` files and uses structure-aware chunking to preserve code blocks and document structure.

Or with custom arguments:

```bash
./gradlew :utils:run --args="input.md output.db 500 100"
```

### Command Line Arguments

```
./gradlew :utils:run --args="<input_path> <db_path> <chunk_size> <chunk_overlap> <file_extensions>"
```

- `input_path`: Path to file or directory to process (default: `embeddings.md`)
- `db_path`: Path to SQLite database (default: `embeddings.db`)
- `chunk_size`: Size of each chunk in characters (default: `500`)
- `chunk_overlap`: Overlap between chunks (default: `100`)
- `file_extensions`: Comma-separated file extensions to process (default: `md,txt`)

### Examples

**Process a single file with larger chunks:**

```bash
./gradlew :utils:run --args="my-docs.md my-embeddings.db 1000 200"
```

**Index an entire documentation folder:**

```bash
./gradlew :utils:run --args="./docs embeddings.db 500 100 md,txt"
```

**Index only Markdown files in a directory:**

```bash
./gradlew :utils:run --args="./documentation docs.db 800 150 md"
```

**Process multiple file types with custom settings:**

```bash
./gradlew :utils:run --args="./knowledge-base kb.db 600 120 md,txt,rst"
```

## Output

The application will:

1. Scan directory or read single file
2. Process each file and split text into chunks
3. Generate embeddings for all chunks
4. Store embeddings in SQLite database
5. Test search functionality
6. Display statistics and sample results

### Sample Output (Single File)

```
=== Step 1: Chunking text ===
Created 25 chunks
Chunk 0 preview: RAG (Retrieval-Augmented Generation) is a technique...

=== Step 2: Generating embeddings ===
Generated 25 embeddings
Embedding dimension: 1536

=== Step 3: Storing embeddings in database ===
Total embeddings in database: 25

=== Step 4: Testing search functionality ===
Search results:
  1. Similarity: 0.9823
     Text: RAG (Retrieval-Augmented Generation) is...
  2. Similarity: 0.8745
     Text: The RAG process consists of...
  3. Similarity: 0.8234
     Text: Benefits of RAG include...
```

### Sample Output (Folder Processing)

```
Input is a directory, scanning for files...
Found 12 file(s) to process:
  - docs/intro.md (2483 bytes)
  - docs/api.md (5621 bytes)
  - docs/guide.md (8934 bytes)
  ...

=== Step 1: Processing files and creating chunks ===

Processing file 1/12: intro.md
  Read 2483 characters
  Using Markdown-aware chunking
  Created 6 chunks from intro.md

Processing file 2/12: api.md
  Read 5621 characters
  Using Markdown-aware chunking
  Created 14 chunks from api.md

...

=== Summary ===
Total chunks created: 87
Files processed: 12

Chunks per file:
  intro.md: 6 chunks
  api.md: 14 chunks
  guide.md: 22 chunks
  ...

=== Step 2: Generating embeddings ===
Processing 87 chunks...
Generated 87 embeddings
Embedding dimension: 1536

=== Step 3: Storing embeddings in database ===
Total embeddings in database: 87

=== Process completed successfully ===
Total files indexed: 12
Total chunks created: 87
Total embeddings stored: 87
Database location: embeddings.db
```

## Database Schema

The SQLite database contains a single table:

```sql
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY,
    text TEXT NOT NULL,
    vector TEXT NOT NULL,  -- JSON array of floats
    model VARCHAR(100) NOT NULL,
    chunk_index INTEGER NOT NULL,
    metadata TEXT,  -- JSON object
    created_at INTEGER NOT NULL
);
```

## Using the Database in Your Application

```kotlin
import io.github.devapro.ai.embeds.rag.*

// Initialize components
val vectorDb = VectorDatabase("embeddings.db")
val embeddingGenerator = EmbeddingGenerator(apiKey, httpClient)

// Search for relevant chunks
val queryEmbedding = embeddingGenerator.generateEmbedding("What is RAG?")
val results = vectorDb.search(
    queryEmbedding = queryEmbedding.vector,
    topK = 5,
    minSimilarity = 0.7
)

// Use results in your RAG pipeline
results.forEach { result ->
    println("Similarity: ${result.similarity}")
    println("File: ${result.metadata?.get("file")}")
    println("Path: ${result.metadata?.get("path")}")
    println("Text: ${result.text}")
}

// Filter results by specific file
val apiResults = results.filter {
    it.metadata?.get("file")?.contains("api") == true
}
```

## Best Practices Implemented

1. **Chunking Strategy**
   - Configurable chunk size and overlap
   - Sentence boundary detection
   - Metadata preservation

2. **Embedding Generation**
   - Batch processing for efficiency
   - Error handling and retries
   - Rate limiting respect

3. **Vector Storage**
   - Cosine similarity for semantic search
   - Efficient serialization (JSON)
   - Timestamp tracking for freshness

4. **Search Optimization**
   - Top-K retrieval
   - Similarity thresholding
   - Result sorting by relevance

## Performance Considerations

- **Chunk Size**: Smaller chunks (300-500) provide more precise retrieval but require more embeddings
- **Overlap**: 10-20% overlap helps preserve context at boundaries
- **Batch Size**: Process up to 100 chunks per API call for optimal performance
- **Database**: SQLite is suitable for up to ~100K embeddings; consider PostgreSQL with pgvector for larger datasets

## Cost Estimation

OpenAI text-embedding-3-small pricing:
- $0.02 per 1M tokens
- Average text: ~4 characters per token
- 10,000 words (~50KB) ≈ $0.001

## Troubleshooting

**Issue**: `OPENAI_API_KEY environment variable is required`
**Solution**: Create `.env` file with your API key

**Issue**: `Input file not found`
**Solution**: Ensure the input file exists or provide correct path

**Issue**: Rate limit errors
**Solution**: Reduce batch size or add delays between requests

## Future Enhancements

- Support for multiple embedding models
- Hybrid search (keyword + semantic)
- Query expansion and rewriting
- Re-ranking support
- Metadata filtering
- Incremental updates
- pgvector support for larger datasets

## License

Part of the AI Challenge project.
