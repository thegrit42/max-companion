package com.max.core

import com.max.log.ActionLog
import com.max.log.ActionLog.ApprovalStatus
import com.max.log.LogStatus
import com.max.log.LogType
import com.max.memory.MemoryBank
import com.max.memory.MemoryCategory
import com.max.rules.ActionContext
import com.max.rules.RuleCheckResult
import com.max.rules.RuleEngine
import com.max.rules.RuleStatus
import com.max.ai.AIBackend
import com.max.ai.ConversationMessage
import com.max.ai.GenerationResult
import com.max.ai.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Max Core - Central coordinator.
 * 
 * Manages the three states: THINKING, PROPOSING, ACTING
 * Only ACTING can change state.
 * Transition to ACTING always requires approval.
 */
class MaxCore(
    private val aiBackend: AIBackend,
    private val memory: MemoryBank,
    private val logDir: File
) {
    private var currentState: MaxState = MaxState.THINKING
    private var conversationHistory = mutableListOf<ConversationMessage>()
    private var currentProposal: MaxProposal? = null
    private var actionContext: ActionContext = ActionContext()

    init {
        ActionLog.initialize(logDir)
    }

    /**
     * Process a user message.
     * Returns Max's response and any proposal.
     */
    suspend fun processUserMessage(message: String): MaxResponse = withContext(Dispatchers.Default) {
        
        // Check for stop command (Rule 10)
        if (message.lowercase() == "stop" || message.lowercase() == "stop now") {
            return@withContext handleStopCommand()
        }

        // Log the request
        ActionLog.logRequest(message, currentState)

        // Add to conversation history
        conversationHistory.add(ConversationMessage(
            role = Role.USER,
            content = message
        ))

        // Add to short-term memory
        memory.addToShortTerm(message, MemoryCategory.CONVERSATION)

        // Analyze the request
        val analysis = aiBackend.analyzeRequest(message, actionContext)

        // If action proposed, check rules
        if (analysis.proposedAction != null) {
            val ruleResult = RuleEngine.checkAction(analysis.proposedAction, actionContext)
            
            return@withContext when (ruleResult.status) {
                RuleStatus.BLOCKED -> {
                    // Hard block - cannot proceed
                    ActionLog.logBlocked(ruleResult.blockReason!!)
                    val response = formatBlockResponse(ruleResult.blockReason!!)
                    MaxResponse(
                        message = response,
                        state = MaxState.THINKING,
                        proposal = null,
                        requiresApproval = false,
                        isWarning = false
                    )
                }
                
                RuleStatus.WARNING -> {
                    // Rule 12: Warn but let user decide
                    ActionLog.logWarning(ruleResult.warning!!, null)
                    currentState = MaxState.PROPOSING
                    MaxResponse(
                        message = ruleResult.warning,
                        state = currentState,
                        proposal = null,
                        requiresApproval = true,
                        isWarning = true,
                        canProceed = true  // User can choose to proceed
                    )
                }
                
                RuleStatus.NEEDS_APPROVAL, RuleStatus.NEEDS_DOUBLE_APPROVAL -> {
                    // Need approval
                    currentState = MaxState.PROPOSING
                    val proposal = MaxProposal(
                        action = analysis.proposedAction,
                        explanation = analysis.explanation,
                        riskAssessment = analysis.riskLevel,
                        requiresDoubleConfirm = ruleResult.status == RuleStatus.NEEDS_DOUBLE_APPROVAL,
                        estimatedImpact = "Will change state"
                    )
                    currentProposal = proposal
                    ActionLog.logApprovalRequest(analysis.proposedAction, analysis.explanation)
                    
                    MaxResponse(
                        message = "I propose to: ${analysis.proposedAction.description}\n\n${analysis.explanation}",
                        state = currentState,
                        proposal = proposal,
                        requiresApproval = true,
                        isWarning = false
                    )
                }
                
                RuleStatus.ALLOWED -> {
                    // Can proceed (rare for state-changing actions)
                    executeWithAI(message)
                }
            }
        } else {
            // No action proposed - just respond
            executeWithAI(message)
        }
    }

    /**
     * User responds to a proposal.
     */
    suspend fun handleApprovalResponse(approved: Boolean, userNote: String? = null): MaxResponse {
        val proposal = currentProposal ?: return MaxResponse(
            message = "No pending proposal to respond to.",
            state = currentState,
            proposal = null,
            requiresApproval = false,
            isWarning = false
        )

        val action = proposal.action
        ActionLog.logApproval(action, approved, userNote)

        return if (approved) {
            // Transition to ACTING
            currentState = MaxState.ACTING
            
            // Execute the action
            val result = executeAction(action)
            
            // Return to THINKING after action
            currentState = MaxState.THINKING
            currentProposal = null
            
            result
        } else {
            // Denied - stay in THINKING
            currentState = MaxState.THINKING
            currentProposal = null
            
            MaxResponse(
                message = "Understood. I won't proceed with that action. What else can I help with?",
                state = currentState,
                proposal = null,
                requiresApproval = false,
                isWarning = false
            )
        }
    }

    /**
     * Handle a warning response (Rule 12)
     */
    suspend fun handleWarningResponse(proceed: Boolean): MaxResponse {
        ActionLog.logWarning("User decided on warning", proceed)
        
        return if (proceed) {
            // User chose to proceed despite warning
            currentState = MaxState.ACTING
            val result = executeWithAI("User approved proceeding despite warning.")
            currentState = MaxState.THINKING
            result
        } else {
            MaxResponse(
                message = "Understood. I'll stop here. What else can I help with?",
                state = MaxState.THINKING,
                proposal = null,
                requiresApproval = false,
                isWarning = false
            )
        }
    }

    /**
     * Get current state
     */
    fun getCurrentState(): MaxState = currentState

    /**
     * Get conversation history
     */
    fun getConversationHistory(): List<ConversationMessage> = conversationHistory.toList()

    /**
     * Clear conversation (start fresh)
     */
    fun clearConversation() {
        conversationHistory.clear()
        memory.clearShortTerm()
        currentState = MaxState.THINKING
        currentProposal = null
    }

    /**
     * Set owner verification status (Rule 1)
     */
    fun setOwnerVerified(verified: Boolean) {
        actionContext = actionContext.copy(isOwnerVerified = verified)
    }

    // Private helpers

    private fun handleStopCommand(): MaxResponse {
        actionContext = actionContext.copy(stopCommandIssued = true)
        currentState = MaxState.THINKING
        currentProposal = null
        
        return MaxResponse(
            message = "Stopping. I'm in passive state. Tell me when you want me to continue.",
            state = currentState,
            proposal = null,
            requiresApproval = false,
            isWarning = false
        )
    }

    private suspend fun executeWithAI(userMessage: String): MaxResponse {
        val generation = aiBackend.generateResponse(
            userMessage = userMessage,
            conversationHistory = conversationHistory,
            memory = memory,
            currentState = currentState
        )

        // Add response to history
        conversationHistory.add(ConversationMessage(
            role = Role.ASSISTANT,
            content = generation.response
        ))

        // Log the response
        ActionLog.logResponse(generation.response, currentState)

        return MaxResponse(
            message = generation.response,
            state = currentState,
            proposal = null,
            requiresApproval = false,
            isWarning = false
        )
    }

    private suspend fun executeAction(action: MaxAction): MaxResponse {
        // In production, this would actually execute the action
        // For now, return a placeholder
        
        val message = when (action.type) {
            ActionType.SEARCH_INTERNET -> 
                "Searching for: ${action.description}\n\n[Search results would appear here - requires internet permission]"
            
            ActionType.DRAFT_CODE ->
                "Here's my draft:\n\n${action.payload ?: "Code would be here"}\n\nThis is a draft. I won't apply it without your approval."
            
            ActionType.REMEMBER_PREFERENCE ->
                "I've noted: ${action.description}"
            
            ActionType.FORGET_MEMORY ->
                "I've forgotten: ${action.description}"
            
            else ->
                "Action completed: ${action.description}"
        }

        conversationHistory.add(ConversationMessage(
            role = Role.ASSISTANT,
            content = message
        ))

        ActionLog.logResponse(message, currentState)

        return MaxResponse(
            message = message,
            state = currentState,
            proposal = null,
            requiresApproval = false,
            isWarning = false
        )
    }

    private fun formatBlockResponse(reason: com.max.rules.BlockReason): String {
        return """
BLOCKED: ${reason.ruleName}

${reason.explanation}

Evidence: ${reason.evidence}

To proceed: ${reason.unblockAction}
""".trimIndent()
    }
}

/**
 * Max's response to the user
 */
data class MaxResponse(
    val message: String,
    val state: MaxState,
    val proposal: MaxProposal?,
    val requiresApproval: Boolean,
    val isWarning: Boolean,
    val canProceed: Boolean = false  // For warnings, can user proceed?
)
