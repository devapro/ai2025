package io.github.devapro.ai.agent

import io.github.devapro.ai.mcp.McpManager
import io.github.devapro.ai.tools.Tool
import org.slf4j.LoggerFactory

/**
 * Component for managing and providing tools for the AI agent
 * Combines internal tools (integrated in code) and external MCP tools (via JSON-RPC)
 */
class ToolProvider(
    private val internalTools: List<Tool>,
    private val mcpManager: McpManager
) {
    private val logger = LoggerFactory.getLogger(ToolProvider::class.java)

    /**
     * Get available tools from all sources, converted to OpenAI format
     * Sources:
     * 1. Internal tools (RAG, file tools, etc.) - integrated directly in code
     * 2. External MCP tools - from configured MCP servers via stdio/HTTP
     */
    suspend fun getAvailableTools(): List<OpenAITool> {
        val allTools = mutableListOf<OpenAITool>()

        // Add internal tools (integrated in code)
        logger.info("Adding ${internalTools.size} internal tool(s)")
        internalTools.forEach { tool ->
            try {
                val toolDef = tool.createToolDefinition()
                // Convert from tools.model.OpenAITool to agent.OpenAITool
                allTools.add(OpenAITool(
                    function = OpenAIFunction(
                        name = toolDef.function.name,
                        description = toolDef.function.description,
                        parameters = toolDef.function.parameters
                    )
                ))
                logger.debug("  - ${toolDef.function.name}: ${toolDef.function.description?.take(60)}...")
            } catch (e: Exception) {
                logger.error("Error creating tool definition for ${tool::class.simpleName}: ${e.message}", e)
            }
        }

        // Add external MCP tools
        if (mcpManager.isAvailable()) {
            try {
                val mcpTools = mcpManager.getAllTools()
                logger.info("Adding ${mcpTools.size} external MCP tool(s)")
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
            logger.debug("No external MCP servers available")
        }

        logger.info("Total tools available: ${allTools.size}")
        return allTools
    }

    /**
     * Get internal tool by name
     * Used for routing tool calls to internal tools
     */
    fun getInternalTool(name: String): Tool? {
        return internalTools.find { tool ->
            tool.createToolDefinition().function.name == name
        }
    }
}
