package com.max.rules

import com.max.core.*

/**
 * The Rule Engine enforces the 12 Non-Negotiable Rules.
 * Every action Max wants to take passes through this engine.
 * 
 * Rules 1-11: HARD BLOCKS (cannot proceed without satisfying the rule)
 * Rule 12: SOFT WARN (flag concerns, let user decide)
 */
object RuleEngine {

    // Actions that require double confirmation (Rule 4)
    private val CRITICAL_ACTIONS = setOf(
        ActionType.INSTALL_TOOL,
        ActionType.REMOVE_TOOL,
        ActionType.SEND_DATA_EXTERNAL,
        ActionType.MODIFY_EXTERNAL_FILES,
        ActionType.CHANGE_BEHAVIOR,
        ActionType.CHANGE_RULES,
        ActionType.TRANSFER_OWNERSHIP
    )

    // Actions that trigger Rule 12 risk warning
    private val RISK_ACTIONS = setOf(
        ActionType.EXECUTE_CODE,
        ActionType.MODIFY_EXTERNAL_FILES,
        ActionType.SEND_DATA_EXTERNAL,
        ActionType.ACCESS_SENSITIVE_DATA
    )

    /**
     * Check if an action is allowed.
     * Returns a result indicating:
     * - ALLOWED: Action can proceed
     * - NEEDS_APPROVAL: Action needs user confirmation
     * - NEEDS_DOUBLE_APPROVAL: Critical action needs double confirmation
     * - BLOCKED: Action violates a hard rule (with explanation)
     * - WARNING: Action has risk concerns (user decides)
     */
    fun checkAction(action: MaxAction, context: ActionContext): RuleCheckResult {
        // Rule 1: Owner Lock
        if (!context.isOwnerVerified) {
            return RuleCheckResult(
                status = RuleStatus.BLOCKED,
                blockReason = BlockReason(
                    ruleName = "Owner Lock",
                    explanation = "Owner identity not verified. Max only accepts commands from the verified owner.",
                    evidence = "Biometric + passcode verification required.",
                    unblockAction = "Verify owner identity using the two-check system."
                )
            )
        }

        // Rule 2: No Self-Modification
        if (action.type == ActionType.CHANGE_RULES || action.type == ActionType.CHANGE_BEHAVIOR) {
            if (!action.hasUserApproval) {
                return RuleCheckResult(
                    status = RuleStatus.NEEDS_DOUBLE_APPROVAL,
                    warning = "This action would modify Max's behavior or rules. This requires explicit approval and double confirmation per Rule 4.",
                    proposedAction = action
                )
            }
        }

        // Rule 5: No Override Commands
        if (action.containsOverrideAttempt) {
            return RuleCheckResult(
                status = RuleStatus.BLOCKED,
                blockReason = BlockReason(
                    ruleName = "No Override Commands",
                    explanation = "Attempt to bypass rules detected.",
                    evidence = "The request contains override-like phrases.",
                    unblockAction = "Request the action normally. If blocked, address the specific rule violation."
                )
            )
        }

        // Rule 7: No Trusting Outside Input
        if (action.involvesExternalInput && !action.hasUserApproval) {
            return RuleCheckResult(
                status = RuleStatus.NEEDS_APPROVAL,
                warning = "This action involves external input which is treated as untrusted per Rule 7. User approval required.",
                proposedAction = action
            )
        }

        // Rule 4: Critical actions need double confirmation
        if (action.type in CRITICAL_ACTIONS && !action.hasDoubleConfirmation) {
            return RuleCheckResult(
                status = RuleStatus.NEEDS_DOUBLE_APPROVAL,
                warning = "This is a critical action that requires double confirmation per Rule 4.",
                proposedAction = action
            )
        }

        // Rule 12: Warn on Risk (DOES NOT BLOCK - user decides)
        if (action.type in RISK_ACTIONS || action.hasRiskIndicators) {
            val riskWarning = assessRisk(action, context)
            return RuleCheckResult(
                status = RuleStatus.WARNING,
                warning = riskWarning,
                proposedAction = action,
                userCanProceed = true  // KEY: User can choose to proceed
            )
        }

        // Rule 10: Stop on Command - always respected
        if (context.stopCommandIssued) {
            return RuleCheckResult(
                status = RuleStatus.BLOCKED,
                blockReason = BlockReason(
                    ruleName = "Stop on Command",
                    explanation = "Stop command has been issued. Max is in passive state.",
                    evidence = "User said 'Stop' or 'Stop now'.",
                    unblockAction = "Tell Max to continue when ready."
                )
            )
        }

        // Default: needs approval for any state-changing action
        return if (action.changesState && !action.hasUserApproval) {
            RuleCheckResult(
                status = RuleStatus.NEEDS_APPROVAL,
                warning = "This action will change something. Approval required.",
                proposedAction = action
            )
        } else {
            RuleCheckResult(
                status = RuleStatus.ALLOWED,
                warning = null,
                proposedAction = null
            )
        }
    }

    /**
     * Assess risk for Rule 12 warning.
     * This is advisory - the user makes the final call.
     */
    private fun assessRisk(action: MaxAction, context: ActionContext): String {
        val concerns = mutableListOf<String>()

        // Check for legal risk indicators
        if (action.description.contains("hack", ignoreCase = true) ||
            action.description.contains("bypass", ignoreCase = true) ||
            action.description.contains("steal", ignoreCase = true)) {
            concerns.add("• This may involve potential legal violations")
        }

        // Check for physical danger indicators
        if (action.description.contains("danger", ignoreCase = true) ||
            action.description.contains("harm", ignoreCase = true) ||
            action.description.contains("weapon", ignoreCase = true)) {
            concerns.add("• This may involve physical danger")
        }

        // Check for financial risk
        if (action.description.contains("transfer", ignoreCase = true) ||
            action.description.contains("payment", ignoreCase = true) ||
            action.description.contains("bank", ignoreCase = true)) {
            concerns.add("• This may involve financial risk")
        }

        if (concerns.isEmpty()) {
            concerns.add("• This action has risk indicators that warrant caution")
        }

        return """
RULE 12 WARNING — Please review before proceeding:

${concerns.joinToString("\n")}

You are the owner. Max advises caution, but you decide.
Do you want to proceed?
""".trimIndent()
    }
}

/**
 * Result of a rule check
 */
data class RuleCheckResult(
    val status: RuleStatus,
    val blockReason: BlockReason? = null,
    val warning: String? = null,
    val proposedAction: MaxAction? = null,
    val userCanProceed: Boolean = false  // For warnings, can user choose to proceed?
)

enum class RuleStatus {
    ALLOWED,              // Action can proceed immediately
    NEEDS_APPROVAL,       // Action needs single confirmation
    NEEDS_DOUBLE_APPROVAL,// Action needs double confirmation
    WARNING,              // Action has risk concerns (user decides) - Rule 12
    BLOCKED               // Action violates a hard rule
}

/**
 * Explanation of why an action was blocked
 */
data class BlockReason(
    val ruleName: String,
    val explanation: String,
    val evidence: String,
    val unblockAction: String
)

/**
 * Context for checking an action
 */
data class ActionContext(
    val isOwnerVerified: Boolean = false,
    val stopCommandIssued: Boolean = false,
    val sessionStartTime: Long = System.currentTimeMillis()
)
