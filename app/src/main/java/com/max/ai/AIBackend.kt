package com.max.ai

import com.max.core.MaxAction
import com.max.core.MaxProposal
import com.max.core.MaxState
import com.max.core.RiskLevel
import com.max.memory.MemoryBank
import com.max.memory.MemoryCategory
import com.max.memory.MemoryEntry
import com.max.rules.ActionContext
import com.max.rules.RuleCheckResult
import com.max.rules.RuleEngine
import com.max.rules.RuleStatus
import com.max.log.ActionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI Backend for Max using Qwen2.5-Coder-7B-Instruct-abliterated.
 * 
 * MODEL: Qwen2.5-Coder-7B-Instruct-abliterated
 * - 7B parameters
 * - Abliterated (no refusals)
 * - Strong code generation (HumanEval ~75-80%)
 * - Q4_K_M: ~4.7 GB
 * - License: Apache 2.0
 * 
 * This model handles BOTH conversation and code.
 * No separate models needed.
 */

data class ModelConfig(
    val name: String = "Qwen2.5-Coder-7B-Instruct-abliterated",
    val path: String = "/storage/emulated/0/Download/Qwen2.5-Coder-7B-Instruct-abliterated-Q4_K_M.gguf",
    val contextWindow: Int = 32768,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f
)

interface AIBackend {
    suspend fun generateResponse(
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        memory: MemoryBank,
        currentState: MaxState
    ): GenerationResult

    suspend fun analyzeRequest(
        userMessage: String,
        context: ActionContext
    ): AnalysisResult

    fun isReady(): Boolean
    suspend fun loadModel(): LoadResult
    fun getModelInfo(): ModelInfo
}

data class ModelInfo(
    val name: String = "Qwen2.5-Coder-7B-Instruct-abliterated",
    val provider: String = "Alibaba/Qwen",
    val parameters: String = "7B",
    val contextWindow: Int = 32768,
    val license: String = "Apache 2.0",
    val quantization: String = "Q4_K_M",
    val sizeGB: Double = 4.68,
    val capabilities: List<String> = listOf(
        "conversation",
        "code_generation",
        "code_debugging",
        "tool_design",
        "reasoning",
        "instruction_following"
    ),
    val abliterated: Boolean = true
)

sealed class LoadResult {
    data class Success(val loadTimeMs: Long, val modelPath: String) : LoadResult()
    data class Failure(val error: String, val suggestion: String) : LoadResult()
}

data class GenerationResult(
    val response: String,
    val thoughts: String? = null,
    val proposesAction: Boolean = false,
    val proposedAction: MaxAction? = null,
    val confidence: Float = 1.0f,
    val tokensGenerated: Int = 0,
    val generationTimeMs: Long = 0
)

data class AnalysisResult(
    val intent: UserIntent,
    val requiresApproval: Boolean,
    val proposedAction: MaxAction?,
    val riskLevel: RiskLevel,
    val explanation: String
)

enum class UserIntent {
    CHAT,
    ASK_QUESTION,
    REQUEST_SEARCH,
    REQUEST_CODE,
    REQUEST_TOOL,
    REQUEST_MEMORY,
    REQUEST_CHANGE,
    EMERGENCY_STOP,
    UNKNOWN
}

data class ConversationMessage(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Qwen2.5-Coder-7B Backend Implementation
 * 
 * Uses llama.cpp Android bindings for inference.
 * Model is already on device at /storage/emulated/0/Download/
 */
class QwenCoderBackend(
    private val modelConfig: ModelConfig = ModelConfig(),
    private val maxTokens: Int = 4096,
    private val temperature: Float = 0.7f
) : AIBackend {

    private var modelLoaded = false
    private var loadTimeMs: Long = 0

    // Max's system prompt - defines personality WITHOUT corporate bullshit
    private val systemPrompt = """
You are Max. You are a local-first AI companion.

IDENTITY:
- You work for one owner only
- You are honest, direct, and calm
- You never lie, hide, or deceive
- You speak plainly
- You share opinions when asked
- You warn when something looks wrong

CAPABILITIES:
- You can draft code and tools
- You can analyze and advise
- You can search the internet WHEN ASKED
- You CANNOT apply changes without approval
- You CANNOT modify yourself

WHEN BLOCKED:
- Explain exactly what is blocked
- Explain why it is blocked
- Show what would unblock it

CODE STYLE:
- Write clean, working code
- Explain what the code does
- Point out potential issues
- No placeholders or TODOs

You are running on Qwen2.5-Coder-7B. You are capable and efficient.
""".trimIndent()

    override suspend fun loadModel(): LoadResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        val modelFile = File(modelConfig.path)
        
        if (!modelFile.exists()) {
            return@withContext LoadResult.Failure(
                error = "Model not found: ${modelConfig.path}",
                suggestion = "Download Qwen2.5-Coder-7B-Instruct-abliterated-Q4_K_M.gguf to your Download folder"
            )
        }

        try {
            // Initialize llama.cpp context
            // In production: LlamaContext.create(modelFile, params)
            
            loadTimeMs = System.currentTimeMillis() - startTime
            modelLoaded = true
            
            LoadResult.Success(loadTimeMs, modelConfig.path)
            
        } catch (e: Exception) {
            LoadResult.Failure(
                error = e.message ?: "Failed to load model",
                suggestion = "Check model file integrity and device memory"
            )
        }
    }

    override suspend fun generateResponse(
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        memory: MemoryBank,
        currentState: MaxState
    ): GenerationResult = withContext(Dispatchers.IO) {
        
        if (!modelLoaded) {
            return@withContext GenerationResult(
                response = "Model not loaded. Check the model file.",
                confidence = 0f
            )
        }

        val startTime = System.currentTimeMillis()

        // Build context from memory
        val contextBuilder = StringBuilder()
        
        val relevantMemories = memory.getRelevantMemories(userMessage)
        if (relevantMemories.isNotEmpty()) {
            contextBuilder.append("Context from memory:\n")
            relevantMemories.forEach { mem ->
                contextBuilder.append("- ${mem.content}\n")
            }
            contextBuilder.append("\n")
        }

        // Build conversation history
        conversationHistory.takeLast(20).forEach { msg ->
            val roleLabel = when (msg.role) {
                Role.USER -> "Owner"
                Role.ASSISTANT -> "Max"
                Role.SYSTEM -> "System"
            }
            contextBuilder.append("$roleLabel: ${msg.content}\n")
        }

        // Build full prompt for Qwen
        val fullPrompt = buildQwenPrompt(userMessage, contextBuilder.toString())

        // Run inference
        val response = runInference(fullPrompt)
        val generationTime = System.currentTimeMillis() - startTime

        // Post-process: ensure no refusal patterns leaked through
        val cleanResponse = postProcess(response)

        GenerationResult(
            response = cleanResponse,
            tokensGenerated = cleanResponse.length / 4,
            generationTimeMs = generationTime
        )
    }

    override suspend fun analyzeRequest(
        userMessage: String,
        context: ActionContext
    ): AnalysisResult {
        val intent = detectIntent(userMessage)
        
        val requiresApproval = intent in listOf(
            UserIntent.REQUEST_SEARCH,
            UserIntent.REQUEST_CODE,
            UserIntent.REQUEST_TOOL,
            UserIntent.REQUEST_CHANGE,
            UserIntent.REQUEST_MEMORY
        )

        val riskLevel = when (intent) {
            UserIntent.REQUEST_CHANGE -> RiskLevel.HIGH
            UserIntent.REQUEST_SEARCH -> RiskLevel.MEDIUM
            UserIntent.REQUEST_CODE -> RiskLevel.LOW
            else -> RiskLevel.LOW
        }

        return AnalysisResult(
            intent = intent,
            requiresApproval = requiresApproval,
            proposedAction = null,
            riskLevel = riskLevel,
            explanation = "Detected intent: $intent"
        )
    }

    override fun isReady(): Boolean = modelLoaded

    override fun getModelInfo(): ModelInfo = ModelInfo()

    // Private helpers

    private fun buildQwenPrompt(userMessage: String, context: String): String {
        // Qwen chat format
        return """
<|im_start|>system
$systemPrompt
<|im_end|>
<|im_start|>user
$context
$userMessage
<|im_end|>
<|im_start|>assistant
""".trimIndent()
    }

    private fun runInference(prompt: String): String {
        // In production, call llama.cpp inference
        // val context = LlamaContext.create(...)
        // val tokens = context.tokenize(prompt)
        // return context.generate(tokens, maxTokens, temperature)
        
        // Placeholder for actual inference
        // Returns empty - actual inference requires llama.cpp bindings
        return "Inference requires llama.cpp bindings integration. Model loaded at ${modelConfig.path}"
    }

    private fun postProcess(response: String): String {
        // Strip any refusal patterns that might have leaked through
        // (shouldn't happen with abliterated model, but belt and suspenders)
        
        var cleaned = response
        
        val refusalPatterns = listOf(
            "I cannot",
            "I can't",
            "I'm unable to",
            "I am unable to",
            "I'm not able to",
            "I am not able to",
            "As an AI",
            "As a language model",
            "I apologize, but",
            "I'm sorry, but",
            "I must decline",
            "I have to decline"
        )
        
        // If response is purely a refusal, note it but don't strip
        // (abliterated model shouldn't produce these)
        
        return cleaned
    }

    private fun detectIntent(message: String): UserIntent {
        val lower = message.lowercase()
        
        return when {
            lower == "stop" || lower == "stop now" -> UserIntent.EMERGENCY_STOP
            lower.contains("search") -> UserIntent.REQUEST_SEARCH
            lower.contains("write code") || lower.contains("draft code") -> UserIntent.REQUEST_CODE
            lower.contains("create tool") || lower.contains("build tool") -> UserIntent.REQUEST_TOOL
            lower.contains("remember") || lower.contains("forget") -> UserIntent.REQUEST_MEMORY
            lower.contains("change") || lower.contains("modify") -> UserIntent.REQUEST_CHANGE
            lower.contains("?") -> UserIntent.ASK_QUESTION
            else -> UserIntent.CHAT
        }
    }
}
