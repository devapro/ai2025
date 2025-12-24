package io.github.devapro.ai.agent

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType

/**
 * Component for counting tokens in OpenAI messages using jtokkit
 */
class TokenCounter {
    private val encodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val encoding: Encoding = encodingRegistry.getEncoding(EncodingType.O200K_BASE)

    /**
     * Count tokens in messages before sending request
     * Uses jtokkit to estimate token count
     *
     * @param messages List of OpenAI messages to count tokens for
     * @return Estimated total token count
     */
    fun countTokens(messages: List<OpenAIMessage>): Int {
        var totalTokens = 0

        // Count tokens for each message
        messages.forEach { message ->
            // Count tokens for role
            totalTokens += encoding.countTokens(message.role)
            // Count tokens for content (if present)
            message.content?.let {
                totalTokens += encoding.countTokens(it)
            }
            // Add overhead per message (typically 3-4 tokens per message for formatting)
            totalTokens += 4
        }

        // Add overhead for reply priming (typically 3 tokens)
        totalTokens += 3

        return totalTokens
    }
}
