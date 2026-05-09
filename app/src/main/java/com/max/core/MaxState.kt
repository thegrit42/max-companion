package com.max.core

/**
 * Max operates in exactly three states.
 * Only the ACTING state can change anything.
 * Transition to ACTING always requires explicit approval.
 */
enum class MaxState {
    THINKING,   // Analyzing, listening, remembering - no state changes
    PROPOSING,  // Has a proposal, waiting for approval - no state changes
    ACTING      // Executing an approved action - CAN change state
}

/**
 * Types of actions Max can propose
 */
enum class ActionType {
    // Communication
    RESPOND_TO_USER,
    SEARCH_INTERNET,
    
    // Code and tools
    DRAFT_CODE,
    EXECUTE_CODE,
    
    // File operations
    CREATE_FILE,
    MODIFY_FILE,
    DELETE_FILE,
    MODIFY_EXTERNAL_FILES,
    
    // Tool management
    INSTALL_TOOL,
    REMOVE_TOOL,
    CREATE_TOOL,
    
    // Data
    SEND_DATA_EXTERNAL,
    ACCESS_SENSITIVE_DATA,
    REMEMBER_PREFERENCE,
    FORGET_MEMORY,
    
    // System
    CHANGE_BEHAVIOR,
    CHANGE_RULES,
    TRANSFER_OWNERSHIP,
    SELF_REPAIR
}

/**
 * An action Max wants to take
 */
data class MaxAction(
    val type: ActionType,
    val description: String,
    val hasUserApproval: Boolean = false,
    val hasDoubleConfirmation: Boolean = false,
    val involvesExternalInput: Boolean = false,
    val hasRiskIndicators: Boolean = false,
    val changesState: Boolean = true,
    val containsOverrideAttempt: Boolean = false,
    val payload: String? = null  // For code drafts, file contents, etc.
)

/**
 * Risk level for an action
 */
enum class RiskLevel {
    LOW,        // Normal operation, minimal risk
    MEDIUM,     // Some caution needed
    HIGH,       // Significant risk, warn user (Rule 12)
    CRITICAL    // Maximum risk, double-confirm required
}

/**
 * A proposal from Max to the user
 */
data class MaxProposal(
    val action: MaxAction,
    val explanation: String,
    val riskAssessment: RiskLevel,
    val requiresDoubleConfirm: Boolean,
    val estimatedImpact: String
)
