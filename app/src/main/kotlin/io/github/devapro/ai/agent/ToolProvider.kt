package io.github.devapro.ai.agent

import io.github.devapro.ai.mcp.McpManager
import org.slf4j.LoggerFactory

/**
 * Component for managing and providing tools (MCP and built-in) for the AI agent
 */
class ToolProvider(
    private val mcpManager: McpManager,
    private val ragSearchTool: RagSearchToolInterface,
    private val ragEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(ToolProvider::class.java)

    /**
     * Get available tools from MCP manager and built-in tools (like RAG), convert to OpenAI format
     */
    suspend fun getAvailableTools(): List<OpenAITool> {
        val allTools = mutableListOf<OpenAITool>()

        // Add MCP tools
        if (mcpManager.isAvailable()) {
            try {
                val mcpTools = mcpManager.getAllTools()
                logger.info("Found ${mcpTools.size} MCP tools available")
                allTools.addAll(mcpTools.map { mcpTool ->
                    OpenAITool(
                        function = OpenAIFunction(
                            name = mcpTool.name,
                            description = mcpTool.description,
                            parameters = mcpTool.inputSchema
                        )
                    )
                })
            } catch (e: Exception) {
                logger.error("Error fetching MCP tools: ${e.message}", e)
            }
        } else {
            logger.debug("No MCP tools available")
        }

        // Add built-in RAG tool if enabled
        if (ragEnabled) {
            logger.info("Adding built-in RAG search_documents tool")
            allTools.add(ragSearchTool.createToolDefinition())
        }

        return allTools
    }
}
