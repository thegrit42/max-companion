package com.max.tools

import com.max.core.MaxAction
import com.max.core.ActionType
import com.max.log.ActionLog
import com.max.memory.MemoryBank
import com.max.rules.ActionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Code Drafting System for Max.
 * 
 * CRITICAL: Max can DRAFT code, but NEVER apply it without approval.
 * 
 * This system allows Max to:
 * - Write code drafts for new tools
 * - Write patch drafts for fixes
 * - Describe what each draft changes
 * - STOP before applying anything
 * - Wait for approval before proceeding
 */
class CodeDrafting(
    private val workspaceDir: File,
    private val log: ActionLog,
    private val memoryBank: MemoryBank
) {
    // Directory for drafts (not applied until approved)
    private val draftsDir = File(workspaceDir, "drafts").apply { mkdirs() }
    
    // Pending drafts awaiting approval
    private val pendingDrafts = mutableMapOf<String, CodeDraft>()

    /**
     * Create a code draft - does NOT apply it
     */
    suspend fun createDraft(
        description: String,
        codeContent: String,
        language: String,
        targetPath: String?,  // Where it would go if approved
        context: ActionContext
    ): CodeDraft {
        
        val draftId = generateDraftId()
        val draftFile = File(draftsDir, "$draftId.$language")
        
        val draft = CodeDraft(
            id = draftId,
            description = description,
            code = codeContent,
            language = language,
            targetPath = targetPath,
            createdAt = System.currentTimeMillis(),
            status = DraftStatus.PENDING_APPROVAL,
            impactAssessment = assessImpact(codeContent, targetPath)
        )
        
        // Save draft to file
        withContext(Dispatchers.IO) {
            draftFile.writeText(buildDraftFile(draft))
        }
        
        pendingDrafts[draftId] = draft
        
        log.logApprovalRequest(
            MaxAction(
                type = ActionType.DRAFT_CODE,
                description = "Drafted code: $description",
                payload = draftId
            ),
            "Code draft created. Awaiting approval to apply."
        )
        
        return draft
    }

    /**
     * Apply a draft - ONLY after user approval
     */
    suspend fun applyDraft(
        draftId: String,
        userApproved: Boolean,
        context: ActionContext
    ): ApplyResult {
        
        val draft = pendingDrafts[draftId]
            ?: return ApplyResult(
                success = false,
                error = "Draft not found: $draftId"
            )
        
        if (!userApproved) {
            log.logBlocked(com.max.rules.BlockReason(
                ruleName = "Rule 5: Approval Required for Code Changes",
                explanation = "Code changes cannot be applied without your explicit approval.",
                evidence = "Draft ID: $draftId, Target: ${draft.targetPath}",
                unblockAction = "Approve this draft to apply it"
            ))
            
            return ApplyResult(
                success = false,
                blocked = true,
                blockReason = "Code application blocked. I cannot apply changes without your approval."
            )
        }
        
        // Double-check for HIGH risk changes
        if (draft.impactAssessment.riskLevel == com.max.core.RiskLevel.HIGH) {
            log.logWarning(
                "This code change affects: ${draft.impactAssessment.affectedAreas.joinToString()}",
                null
            )
            // Return that it needs double confirmation
            return ApplyResult(
                success = false,
                needsDoubleConfirm = true,
                warning = "This is a high-risk change. Please confirm twice."
            )
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val targetFile = draft.targetPath?.let { File(it) }
                    ?: return@withContext ApplyResult(
                        success = false,
                        error = "No target path specified for draft"
                    )
                
                // Create backup
                val backupPath = if (targetFile.exists()) {
                    val backup = File(targetFile.parent, "${targetFile.name}.backup_${System.currentTimeMillis()}")
                    targetFile.copyTo(backup, overwrite = true)
                    backup.absolutePath
                } else null
                
                // Create parent directories if needed
                targetFile.parentFile?.mkdirs()
                
                // Write the new content
                targetFile.writeText(draft.code)
                
                // Mark draft as applied
                pendingDrafts.remove(draftId)
                
                log.logApproval(
                    MaxAction(
                        type = ActionType.EXECUTE_CODE,
                        description = "Applied code draft: ${draft.description}",
                        payload = draftId
                    ),
                    approved = true,
                    userNote = "Applied to ${targetFile.absolutePath}"
                )
                
                ApplyResult(
                    success = true,
                    appliedPath = targetFile.absolutePath,
                    backupPath = backupPath,
                    draft = draft
                )
                
            } catch (e: Exception) {
                log.logFailure(
                    "Failed to apply draft: ${e.message}",
                    "Draft ID: $draftId"
                )
                
                ApplyResult(
                    success = false,
                    error = "Failed to apply: ${e.message}"
                )
            }
        }
    }

    /**
     * Get a pending draft by ID
     */
    fun getDraft(draftId: String): CodeDraft? = pendingDrafts[draftId]

    /**
     * List all pending drafts
     */
    fun listPendingDrafts(): List<CodeDraft> = pendingDrafts.values.toList()

    /**
     * Discard a draft without applying
     */
    fun discardDraft(draftId: String): Boolean {
        val draft = pendingDrafts.remove(draftId) ?: return false
        
        // Delete the draft file
        File(draftsDir, "$draftId.${draft.language}").delete()
        
        log.logRequest("Draft discarded: $draftId", com.max.core.MaxState.ACTING)
        
        return true
    }

    /**
     * Generate a patch draft for fixing something
     */
    suspend fun createPatchDraft(
        targetFile: String,
        problem: String,
        proposedFix: String,
        context: ActionContext
    ): CodeDraft {
        
        val file = File(targetFile)
        val originalContent = if (file.exists()) file.readText() else ""
        
        val patchDescription = """
            PATCH FOR: $targetFile
            PROBLEM: $problem
            
            PROPOSED CHANGES:
            $proposedFix
            
            ORIGINAL FILE:
            ${if (originalContent.isNotEmpty()) originalContent.take(500) + "..." else "(new file)"}
        """.trimIndent()
        
        return createDraft(
            description = "Patch: $problem",
            codeContent = proposedFix,
            language = file.extension.ifEmpty { "txt" },
            targetPath = targetFile,
            context = context
        )
    }

    /**
     * Create a tool specification draft
     */
    suspend fun createToolDraft(
        toolName: String,
        toolDescription: String,
        implementation: String,
        context: ActionContext
    ): CodeDraft {
        
        return createDraft(
            description = "Tool: $toolName - $toolDescription",
            codeContent = implementation,
            language = "kt",  // Kotlin for Android
            targetPath = File(workspaceDir, "tools/${toolName.lowercase().replace(" ", "_")}.kt").absolutePath,
            context = context
        )
    }

    // Private helpers

    private fun generateDraftId(): String {
        return "draft_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun buildDraftFile(draft: CodeDraft): String {
        return """
// === CODE DRAFT ===
// ID: ${draft.id}
// Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(draft.createdAt))}
// Status: ${draft.status}
// 
// DESCRIPTION:
// ${draft.description}
//
// TARGET: ${draft.targetPath ?: "Not specified"}
// LANGUAGE: ${draft.language}
//
// === CODE BELOW ===

${draft.code}
        """.trimIndent()
    }

    private fun assessImpact(code: String, targetPath: String?): ImpactAssessment {
        val affectedAreas = mutableListOf<String>()
        var riskLevel = com.max.core.RiskLevel.LOW
        
        if (targetPath != null) {
            when {
                targetPath.contains("rules", ignoreCase = true) -> {
                    affectedAreas.add("Rules system")
                    riskLevel = com.max.core.RiskLevel.HIGH
                }
                targetPath.contains("core", ignoreCase = true) -> {
                    affectedAreas.add("Core system")
                    riskLevel = com.max.core.RiskLevel.HIGH
                }
                targetPath.contains("memory", ignoreCase = true) -> {
                    affectedAreas.add("Memory system")
                    riskLevel = com.max.core.RiskLevel.MEDIUM
                }
                targetPath.contains("log", ignoreCase = true) -> {
                    affectedAreas.add("Logging system")
                    riskLevel = com.max.core.RiskLevel.MEDIUM
                }
                else -> {
                    affectedAreas.add("User files")
                    riskLevel = com.max.core.RiskLevel.LOW
                }
            }
        }
        
        // Check for dangerous patterns in code
        val dangerousPatterns = listOf(
            "Runtime.getRuntime().exec",
            "ProcessBuilder",
            "deleteRecursively",
            "deleteFile",
            "format",
            "rm -rf"
        )
        
        for (pattern in dangerousPatterns) {
            if (code.contains(pattern)) {
                affectedAreas.add("Contains: $pattern")
                riskLevel = com.max.core.RiskLevel.HIGH
            }
        }
        
        return ImpactAssessment(
            riskLevel = riskLevel,
            affectedAreas = affectedAreas,
            reversible = true  // We create backups
        )
    }
}

/**
 * A code draft
 */
data class CodeDraft(
    val id: String,
    val description: String,
    val code: String,
    val language: String,
    val targetPath: String?,
    val createdAt: Long,
    val status: DraftStatus,
    val impactAssessment: ImpactAssessment
)

enum class DraftStatus {
    PENDING_APPROVAL,
    APPROVED,
    APPLIED,
    DISCARDED,
    FAILED
}

/**
 * Impact assessment for a code change
 */
data class ImpactAssessment(
    val riskLevel: com.max.core.RiskLevel,
    val affectedAreas: List<String>,
    val reversible: Boolean
)

/**
 * Result of applying a draft
 */
data class ApplyResult(
    val success: Boolean,
    val blocked: Boolean = false,
    val blockReason: String? = null,
    val needsDoubleConfirm: Boolean = false,
    val warning: String? = null,
    val error: String? = null,
    val appliedPath: String? = null,
    val backupPath: String? = null,
    val draft: CodeDraft? = null
)
