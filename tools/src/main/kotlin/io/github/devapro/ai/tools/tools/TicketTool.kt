package io.github.devapro.ai.tools.tools

import io.github.devapro.ai.tools.Tool
import io.github.devapro.ai.tools.model.OpenAIFunction
import io.github.devapro.ai.tools.model.OpenAITool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Tool for managing support tickets
 *
 * Supports:
 * - Listing all tickets
 * - Getting a specific ticket by ID
 * - Searching tickets by query (title, description, tags)
 * - Filtering tickets by status (open, in_progress, closed)
 *
 * Tickets are stored in a JSON file (tickets.json by default)
 *
 * Examples:
 * - Get all tickets: {"operation": "get_all_tickets"}
 * - Get specific ticket: {"operation": "get_ticket_by_id", "ticket_id": "TICKET-001"}
 * - Search tickets: {"operation": "search_tickets", "query": "login"}
 * - Filter by status: {"operation": "get_tickets_by_status", "status": "open"}
 */
class TicketTool(
    private val ticketsFilePath: String = "tickets.json"
) : Tool {

    private val logger = LoggerFactory.getLogger(TicketTool::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Serializable
    data class Ticket(
        val id: String,
        val title: String,
        val description: String,
        val status: String,
        val priority: String,
        val assignee: String,
        val reporter: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        val tags: List<String> = emptyList(),
        val resolution: String? = null
    )

    override fun createToolDefinition(): OpenAITool {
        return OpenAITool(
            function = OpenAIFunction(
                name = "manage_tickets",
                description = """
                    Manage support tickets - view, search, and filter tickets.

                    Operations:
                    - get_all_tickets: Get all tickets (returns all tickets in the system)
                    - get_ticket_by_id: Get a specific ticket by its ID (e.g., TICKET-001)
                    - search_tickets: Search tickets by keyword in title, description, or tags
                    - get_tickets_by_status: Filter tickets by status (open, in_progress, closed)

                    Each ticket includes:
                    - id: Unique ticket identifier
                    - title: Short description of the issue
                    - description: Detailed description
                    - status: Current status (open, in_progress, closed)
                    - priority: Priority level (low, medium, high, critical)
                    - assignee: Person assigned to the ticket
                    - reporter: Person who reported the issue
                    - created_at: When the ticket was created
                    - updated_at: Last update timestamp
                    - tags: Categories and keywords
                    - resolution: How the ticket was resolved (only for closed tickets)
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("operation") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("get_all_tickets")
                                add("get_ticket_by_id")
                                add("search_tickets")
                                add("get_tickets_by_status")
                            }
                            put("description", "The ticket operation to perform")
                        }
                        putJsonObject("ticket_id") {
                            put("type", "string")
                            put("description", "Ticket ID (required for get_ticket_by_id operation)")
                        }
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Search query for title, description, or tags (required for search_tickets)")
                        }
                        putJsonObject("status") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("open")
                                add("in_progress")
                                add("closed")
                            }
                            put("description", "Filter by ticket status (required for get_tickets_by_status)")
                        }
                    }
                    putJsonArray("required") {
                        add("operation")
                    }
                }
            )
        )
    }

    override suspend fun execute(arguments: JsonObject?): String {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required")
        }

        val operation = arguments["operation"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("'operation' argument is required")

        logger.info("Executing ticket operation: $operation")

        return try {
            when (operation) {
                "get_all_tickets" -> getAllTickets()
                "get_ticket_by_id" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return "Error: 'ticket_id' is required for get_ticket_by_id operation"
                    getTicketById(ticketId)
                }
                "search_tickets" -> {
                    val query = arguments["query"]?.jsonPrimitive?.content
                        ?: return "Error: 'query' is required for search_tickets operation"
                    searchTickets(query)
                }
                "get_tickets_by_status" -> {
                    val status = arguments["status"]?.jsonPrimitive?.content
                        ?: return "Error: 'status' is required for get_tickets_by_status operation"
                    getTicketsByStatus(status)
                }
                else -> "Error: Unknown operation '$operation'"
            }
        } catch (e: Exception) {
            logger.error("Ticket operation failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Load tickets from JSON file
     */
    private fun loadTickets(): List<Ticket> {
        val ticketsFile = File(ticketsFilePath)

        if (!ticketsFile.exists()) {
            logger.warn("Tickets file not found: $ticketsFilePath")
            return emptyList()
        }

        return try {
            val content = ticketsFile.readText()
            json.decodeFromString<List<Ticket>>(content)
        } catch (e: Exception) {
            logger.error("Failed to load tickets from file: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get all tickets
     */
    private fun getAllTickets(): String {
        val tickets = loadTickets()

        if (tickets.isEmpty()) {
            return "No tickets found. The ticket system may be empty or the tickets file is missing."
        }

        val summary = buildString {
            appendLine("Found ${tickets.size} total tickets:")
            appendLine()

            // Group by status
            val byStatus = tickets.groupBy { it.status }
            byStatus.forEach { (status, statusTickets) ->
                appendLine("$status: ${statusTickets.size} tickets")
            }
            appendLine()
            appendLine("All tickets:")
            appendLine(json.encodeToString(tickets))
        }

        return summary
    }

    /**
     * Get a specific ticket by ID
     */
    private fun getTicketById(ticketId: String): String {
        val tickets = loadTickets()
        val ticket = tickets.find { it.id.equals(ticketId, ignoreCase = true) }

        return if (ticket != null) {
            buildString {
                appendLine("Ticket found:")
                appendLine(json.encodeToString(ticket))
            }
        } else {
            "Ticket not found: $ticketId"
        }
    }

    /**
     * Search tickets by query (searches in title, description, and tags)
     */
    private fun searchTickets(query: String): String {
        val tickets = loadTickets()
        val searchQuery = query.lowercase()

        val matches = tickets.filter { ticket ->
            ticket.title.lowercase().contains(searchQuery) ||
            ticket.description.lowercase().contains(searchQuery) ||
            ticket.tags.any { tag -> tag.lowercase().contains(searchQuery) }
        }

        return if (matches.isNotEmpty()) {
            buildString {
                appendLine("Found ${matches.size} tickets matching '$query':")
                appendLine()
                appendLine(json.encodeToString(matches))
            }
        } else {
            "No tickets found matching query: '$query'"
        }
    }

    /**
     * Get tickets filtered by status
     */
    private fun getTicketsByStatus(status: String): String {
        val tickets = loadTickets()
        val matches = tickets.filter { it.status.equals(status, ignoreCase = true) }

        return if (matches.isNotEmpty()) {
            buildString {
                appendLine("Found ${matches.size} tickets with status '$status':")
                appendLine()
                appendLine(json.encodeToString(matches))
            }
        } else {
            "No tickets found with status: '$status'"
        }
    }
}
