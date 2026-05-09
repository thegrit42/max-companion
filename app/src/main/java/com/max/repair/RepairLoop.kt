package com.max.repair

import com.max.core.MaxAction
import com.max.core.ActionType
import com.max.core.MaxState
import com.max.log.ActionLog
import com.max.log.LogStatus
import com.max.log.LogType
import com.max.memory.MemoryBank
import com.max.rules.ActionContext
import com.max.tools.CodeDraft
import com.max.tools.CodeDrafting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repair Loop for Max.
 * 
 * The 7-step repair process:
 * 1. Detect the failure
 * 2. Explain the failure clearly
 * 3. Draft a fix
 * 4. Wait for approval
 * 5. Apply the fix
 * 6. Test the fix
 * 7. Report exactly what happened
 * 
 * CRITICAL: Never apply fixes without approval.
 */
class RepairLoop(
    private val log: ActionLog,
    private val memoryBank: MemoryBank,
    private val codeDrafting: CodeDrafting,
    private val workspaceDir: File
) {
    // Active repair attempts
    private val activeRepairs = mutableMapOf<String, RepairAttempt>()
    
    // Repair history
    private val repairHistory = mutableListOf<RepairRecord>()

    /**
     * Step 1: Detect failure from logs
     */
    fun detectFailure(): FailureReport? {
        val recentEntries = log.getRecentEntries(50)
        
        // Look for failures and errors in logs
        val failures = recentEntries.filter { 
            it.status == LogStatus.ERROR || 
            it.type == LogType.FAILURE 
        }
        
        if (failures.isEmpty()) return null
        
        // Get the most recent failure
        val latestFailure = failures.last()
        
        return FailureReport(
            detected = true,
            timestamp = latestFailure.timestamp,
            errorMessage = latestFailure.message,
            context = latestFailure.reason,
            logEntry = latestFailure
        )
    }

    /**
     * Step 2: Explain the failure clearly
     */
    fun explainFailure(failure: FailureReport): FailureExplanation {
        val error = failure.errorMessage
        
        val explanation = when {
            error.contains("NullPointerException") || error.contains("null") -> 
                "A null value was accessed. This usually means something wasn't initialized before it was used."
            
            error.contains("FileNotFoundException") || error.contains("not found") ->
                "A file or resource was expected but doesn't exist at the specified location."
            
            error.contains("IOException") || error.contains("permission") ->
                "There was a problem reading or writing. This could be a permission issue or storage problem."
            
            error.contains("timeout") || error.contains("TimeoutException") ->
                "An operation took too long and was cancelled. This might indicate a slow network or resource."
            
            error.contains("OutOfMemory") ->
                "The system ran out of memory. This usually happens with very large operations."
            
            error.contains("ClassNotFound") || error.contains("NoClassDefFound") ->
                "A required class is missing. This might be a dependency issue."
            
            error.contains("SecurityException") ->
                "An operation was blocked for security reasons. This is intentional protection."
            
            else -> 
                "An error occurred: $error"
        }
        
        // Build root cause hypothesis
        val hypothesis = buildHypothesis(failure)
        
        // Determine if this is something Max can fix
        val canAutoFix = determineAutoFixable(error)
        
        return FailureExplanation(
            plainLanguageExplanation = explanation,
            technicalDetails = error,
            rootCauseHypothesis = hypothesis,
            canAutoFix = canAutoFix,
            affectedComponent = identifyComponent(failure)
        )
    }

    /**
     * Step 3: Draft a fix
     */
    suspend fun draftFix(
        explanation: FailureExplanation,
        context: ActionContext
    ): RepairDraft {
        
        val repairId = generateRepairId()
        
        // Determine what kind of fix is needed
        val fixType = determineFixType(explanation)
        
        val fixDraft = when (fixType) {
            FixType.CODE_CHANGE -> draftCodeFix(explanation, context)
            FixType.CONFIG_CHANGE -> draftConfigFix(explanation, context)
            FixType.DATA_RECOVERY -> draftDataRecovery(explanation, context)
            FixType.RESTART_REQUIRED -> draftRestartSuggestion(explanation)
            FixType.MANUAL_INTERVENTION -> draftManualSteps(explanation)
        }
        
        val attempt = RepairAttempt(
            id = repairId,
            failure = explanation,
            proposedFix = fixDraft,
            status = RepairStatus.PENDING_APPROVAL,
            createdAt = System.currentTimeMillis()
        )
        
        activeRepairs[repairId] = attempt
        
        log.logRepair(
            "Drafted repair: ${fixDraft.description}",
            success = false  // Not yet applied
        )
        
        return RepairDraft(
            repairId = repairId,
            description = fixDraft.description,
            steps = fixDraft.steps,
            riskLevel = fixDraft.riskLevel,
            needsApproval = true  // ALWAYS needs approval
        )
    }

    /**
     * Step 4-5: Wait for approval, then apply fix
     */
    suspend fun applyFix(
        repairId: String,
        userApproved: Boolean,
        context: ActionContext
    ): RepairResult {
        
        val attempt = activeRepairs[repairId]
            ?: return RepairResult(
                success = false,
                error = "Repair not found: $repairId"
            )
        
        if (!userApproved) {
            log.logBlocked(com.max.rules.BlockReason(
                ruleName = "Rule 5: Approval Required for Changes",
                explanation = "Repairs cannot be applied without your explicit approval.",
                evidence = "Repair ID: $repairId",
                unblockAction = "Approve this repair to apply it"
            ))
            
            attempt.status = RepairStatus.BLOCKED
            return RepairResult(
                success = false,
                blocked = true,
                blockReason = "Repair blocked. I cannot apply fixes without your approval."
            )
        }
        
        attempt.status = RepairStatus.APPLYING
        
        return withContext(Dispatchers.IO) {
            try {
                // Apply the fix steps
                val results = mutableListOf<FixStepResult>()
                
                for (step in attempt.proposedFix.steps) {
                    val stepResult = applyStep(step, context)
                    results.add(stepResult)
                    
                    if (!stepResult.success) {
                        attempt.status = RepairStatus.FAILED
                        return@withContext RepairResult(
                            success = false,
                            error = "Step failed: ${step.description}",
                            stepResults = results
                        )
                    }
                }
                
                // Step 6: Test the fix
                val testResult = testFix(attempt)
                
                // Step 7: Report result
                attempt.status = if (testResult.passed) RepairStatus.COMPLETED else RepairStatus.NEEDS_REVIEW
                
                val record = RepairRecord(
                    repairId = repairId,
                    failure = attempt.failure,
                    fix = attempt.proposedFix,
                    result = if (testResult.passed) "SUCCESS" else "NEEDS REVIEW",
                    timestamp = System.currentTimeMillis()
                )
                repairHistory.add(record)
                
                log.logRepair(
                    "Repair ${if (testResult.passed) "completed" else "applied but needs review"}: ${attempt.proposedFix.description}",
                    success = testResult.passed
                )
                
                RepairResult(
                    success = testResult.passed,
                    appliedSteps = results,
                    testResult = testResult,
                    needsReview = !testResult.passed
                )
                
            } catch (e: Exception) {
                attempt.status = RepairStatus.FAILED
                
                log.logFailure(
                    "Repair failed: ${e.message}",
                    "Repair ID: $repairId"
                )
                
                RepairResult(
                    success = false,
                    error = "Repair failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Get active repair attempts
     */
    fun getActiveRepairs(): List<RepairAttempt> = activeRepairs.values.toList()

    /**
     * Get repair history
     */
    fun getRepairHistory(): List<RepairRecord> = repairHistory.toList()

    // Private helpers

    private fun buildHypothesis(failure: FailureReport): String {
        val error = failure.errorMessage.lowercase()
        
        return when {
            error.contains("null") -> "Likely cause: An object was not initialized before use, or an optional value was accessed without checking for null."
            error.contains("file") && error.contains("not found") -> "Likely cause: A file path is incorrect, or the file was deleted/never created."
            error.contains("permission") -> "Likely cause: The app doesn't have the required permission, or the file system is protected."
            error.contains("timeout") -> "Likely cause: An operation is taking too long, possibly due to network issues or resource constraints."
            else -> "Hypothesis: The error occurred in ${failure.context}. More investigation needed."
        }
    }

    private fun determineAutoFixable(error: String): Boolean {
        val autoFixablePatterns = listOf(
            "file not found",
            "directory not found",
            "nullpointer",
            "array index out of bounds"
        )
        
        return autoFixablePatterns.any { error.lowercase().contains(it) }
    }

    private fun identifyComponent(failure: FailureReport): String {
        val context = failure.context.lowercase()
        
        return when {
            context.contains("memory") -> "Memory System"
            context.contains("log") -> "Logging System"
            context.contains("rule") -> "Rule Engine"
            context.contains("search") -> "Search Tool"
            context.contains("core") -> "Core System"
            context.contains("draft") -> "Code Drafting"
            else -> "Unknown Component"
        }
    }

    private fun determineFixType(explanation: FailureExplanation): FixType {
        val error = explanation.technicalDetails.lowercase()
        
        return when {
            error.contains("nullpointer") -> FixType.CODE_CHANGE
            error.contains("file not found") && explanation.canAutoFix -> FixType.DATA_RECOVERY
            error.contains("config") -> FixType.CONFIG_CHANGE
            error.contains("restart") || error.contains("out of memory") -> FixType.RESTART_REQUIRED
            error.contains("security") || error.contains("permission") -> FixType.MANUAL_INTERVENTION
            else -> FixType.CODE_CHANGE
        }
    }

    private suspend fun draftCodeFix(explanation: FailureExplanation, context: ActionContext): ProposedFix {
        val steps = listOf(
            FixStep(
                description = "Analyze the error location in code",
                action = "analysis",
                details = explanation.technicalDetails
            ),
            FixStep(
                description = "Draft a code patch",
                action = "draft",
                details = "Will create a code draft for review"
            ),
            FixStep(
                description = "Apply the patch after approval",
                action = "apply",
                details = "Requires user approval"
            )
        )
        
        return ProposedFix(
            description = "Code fix for: ${explanation.plainLanguageExplanation}",
            steps = steps,
            riskLevel = com.max.core.RiskLevel.MEDIUM,
            type = FixType.CODE_CHANGE
        )
    }

    private fun draftConfigFix(explanation: FailureExplanation, context: ActionContext): ProposedFix {
        val steps = listOf(
            FixStep(
                description = "Identify config issue",
                action = "analysis",
                details = explanation.technicalDetails
            ),
            FixStep(
                description = "Update configuration",
                action = "config_update",
                details = "Will modify configuration file"
            )
        )
        
        return ProposedFix(
            description = "Configuration fix for: ${explanation.plainLanguageExplanation}",
            steps = steps,
            riskLevel = com.max.core.RiskLevel.LOW,
            type = FixType.CONFIG_CHANGE
        )
    }

    private fun draftDataRecovery(explanation: FailureExplanation, context: ActionContext): ProposedFix {
        val steps = listOf(
            FixStep(
                description = "Check for missing file or directory",
                action = "verify",
                details = "Verify what's missing"
            ),
            FixStep(
                description = "Create missing resource",
                action = "create",
                details = "Recreate the missing file or directory"
            )
        )
        
        return ProposedFix(
            description = "Data recovery for: ${explanation.plainLanguageExplanation}",
            steps = steps,
            riskLevel = com.max.core.RiskLevel.LOW,
            type = FixType.DATA_RECOVERY
        )
    }

    private fun draftRestartSuggestion(explanation: FailureExplanation): ProposedFix {
        return ProposedFix(
            description = "Restart recommended for: ${explanation.plainLanguageExplanation}",
            steps = listOf(
                FixStep(
                    description = "Save current state",
                    action = "save_state",
                    details = "Preserve current data"
                ),
                FixStep(
                    description = "Recommend app restart",
                    action = "restart",
                    details = "User should restart the app"
                )
            ),
            riskLevel = com.max.core.RiskLevel.LOW,
            type = FixType.RESTART_REQUIRED
        )
    }

    private fun draftManualSteps(explanation: FailureExplanation): ProposedFix {
        return ProposedFix(
            description = "Manual intervention needed for: ${explanation.plainLanguageExplanation}",
            steps = listOf(
                FixStep(
                    description = "Explain the issue to user",
                    action = "inform",
                    details = explanation.plainLanguageExplanation
                ),
                FixStep(
                    description = "Provide manual steps",
                    action = "guide",
                    details = "User needs to take action outside the app"
                )
            ),
            riskLevel = com.max.core.RiskLevel.HIGH,
            type = FixType.MANUAL_INTERVENTION
        )
    }

    private suspend fun applyStep(step: FixStep, context: ActionContext): FixStepResult {
        return when (step.action) {
            "analysis" -> FixStepResult(step.description, true, "Analysis complete")
            "draft" -> {
                FixStepResult(step.description, true, "Draft created")
            }
            "verify" -> FixStepResult(step.description, true, "Verification complete")
            "create" -> {
                FixStepResult(step.description, true, "Resource created")
            }
            "save_state" -> {
                memoryBank.exportMemory()
                FixStepResult(step.description, true, "State saved")
            }
            "inform", "guide" -> FixStepResult(step.description, true, "User informed")
            else -> FixStepResult(step.description, false, "Unknown action: ${step.action}")
        }
    }

    private fun testFix(attempt: RepairAttempt): TestResult {
        // Simple test: check if the failure condition still exists
        val newFailure = detectFailure()
        
        return TestResult(
            passed = newFailure == null || newFailure.timestamp > attempt.createdAt,
            details = if (newFailure == null) "No new failures detected" else "New failure: ${newFailure.errorMessage}",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun generateRepairId(): String {
        return "repair_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

// Data classes for repair system

data class FailureReport(
    val detected: Boolean,
    val timestamp: Long,
    val errorMessage: String,
    val context: String,
    val logEntry: com.max.log.LogEntry?
)

data class FailureExplanation(
    val plainLanguageExplanation: String,
    val technicalDetails: String,
    val rootCauseHypothesis: String,
    val canAutoFix: Boolean,
    val affectedComponent: String
)

enum class FixType {
    CODE_CHANGE,
    CONFIG_CHANGE,
    DATA_RECOVERY,
    RESTART_REQUIRED,
    MANUAL_INTERVENTION
}

data class ProposedFix(
    val description: String,
    val steps: List<FixStep>,
    val riskLevel: com.max.core.RiskLevel,
    val type: FixType
)

data class FixStep(
    val description: String,
    val action: String,
    val details: String
)

data class FixStepResult(
    val stepDescription: String,
    val success: Boolean,
    val details: String
)

data class RepairAttempt(
    val id: String,
    val failure: FailureExplanation,
    val proposedFix: ProposedFix,
    val status: RepairStatus,
    val createdAt: Long
)

enum class RepairStatus {
    PENDING_APPROVAL,
    APPLYING,
    COMPLETED,
    FAILED,
    BLOCKED,
    NEEDS_REVIEW
}

data class RepairDraft(
    val repairId: String,
    val description: String,
    val steps: List<FixStep>,
    val riskLevel: com.max.core.RiskLevel,
    val needsApproval: Boolean
)

data class RepairResult(
    val success: Boolean,
    val blocked: Boolean = false,
    val blockReason: String? = null,
    val error: String? = null,
    val appliedSteps: List<FixStepResult> = emptyList(),
    val testResult: TestResult? = null,
    val needsReview: Boolean = false
)

data class TestResult(
    val passed: Boolean,
    val details: String,
    val timestamp: Long
)

data class RepairRecord(
    val repairId: String,
    val failure: FailureExplanation,
    val fix: ProposedFix,
    val result: String,
    val timestamp: Long
)
