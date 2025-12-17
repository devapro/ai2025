package io.github.devapro.ai.scheduler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.devapro.ai.agent.AiAgent
import io.github.devapro.ai.repository.FileRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Scheduler that sends daily event summaries to all registered users at 10 AM
 */
class DailySummaryScheduler(
    private val aiAgent: AiAgent,
    private val fileRepository: FileRepository,
    private val bot: Bot,
    private val targetHour: Int = 10,
    private val targetMinute: Int = 0
) {
    private val logger = LoggerFactory.getLogger(DailySummaryScheduler::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var schedulerJob: Job? = null

    /**
     * Start the daily scheduler
     */
    fun start() {
        logger.info("Starting daily summary scheduler for $targetHour:${targetMinute.toString().padStart(2, '0')}")

        schedulerJob = coroutineScope.launch {
            while (isActive) {
                val delayMs = calculateDelayUntilTarget()
                logger.info("Next daily summary scheduled in ${delayMs / 1000 / 60} minutes")

                delay(delayMs)

                if (isActive) {
                    sendDailySummaries()
                }
            }
        }
    }

    /**
     * Stop the scheduler
     */
    fun stop() {
        logger.info("Stopping daily summary scheduler...")
        schedulerJob?.cancel()
        coroutineScope.cancel()
        logger.info("Daily summary scheduler stopped")
    }

    /**
     * Calculate delay in milliseconds until the target time (10 AM)
     */
    private fun calculateDelayUntilTarget(): Long {
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(targetHour, targetMinute)

        var targetDateTime = now.with(targetTime)

        // If target time has already passed today, schedule for tomorrow
        if (now.isAfter(targetDateTime)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val delayMs = ChronoUnit.MILLIS.between(now, targetDateTime)
        return delayMs
    }

    /**
     * Send daily summaries to all registered users
     */
    private suspend fun sendDailySummaries() {
        logger.info("Starting daily summary broadcast at ${LocalDateTime.now()}")

        val userIds = fileRepository.getAllUserIds()

        if (userIds.isEmpty()) {
            logger.warn("No users found in users.md file")
            return
        }

        logger.info("Sending daily summaries to ${userIds.size} users")

        userIds.forEach { userId ->
            try {
                sendSummaryToUser(userId)
            } catch (e: Exception) {
                logger.error("Failed to send summary to user $userId: ${e.message}", e)
            }
        }

        logger.info("Daily summary broadcast completed")
    }

    /**
     * Send summary to a specific user
     */
    private suspend fun sendSummaryToUser(userId: Long) {
        logger.info("Sending daily summary to user $userId")

        try {
            // Get summary from AI agent
            val summary = aiAgent.processMessage(
                userId,
                "Please provide a summary of all events and tasks scheduled for today. Include time, title, location, and any important details. This is an automated daily summary."
            )

            // Send summary message to user via Telegram
            bot.sendMessage(
                chatId = ChatId.fromId(userId),
                text = "☀️ *Good morning! Here's your daily schedule summary:*\n\n$summary",
                parseMode = ParseMode.MARKDOWN
            )

            logger.info("Successfully sent daily summary to user $userId")
        } catch (e: Exception) {
            logger.error("Error sending summary to user $userId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Trigger summary manually (for testing purposes)
     */
    suspend fun triggerManualSummary() {
        logger.info("Manual summary trigger requested")
        sendDailySummaries()
    }
}
