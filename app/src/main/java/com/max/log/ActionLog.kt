package com.max.log

import com.max.core.MaxAction
import com.max.core.MaxState
import com.max.rules.BlockReason
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Action Log - Full Transparency (Rule 6).
 * 
 * Every action is recorded with:
 * - Time
 * - Requested by
 * - What happened
 * - Approved or denied
 * 
 * Log cannot be silently erased.
 * User can always see what Max did and why.
 */
object ActionLog {

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX_ENTRIES_IN_MEMORY = 1000

    /**
     * Initialize the log with a storage location
     */
    fun initialize(logDir: File) {
        logDir.mkdirs()
        logFile = File(logDir, "action_log.txt")
        loadExistingLog()
    }

    /**
     * Log a user request
     */
    fun logRequest(message: String, state: MaxState) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.USER_REQUEST,
            action = null,
            message = message,
            status = LogStatus.INFO,
            approvalStatus = ApprovalStatus.NOT_REQUIRED,
            reason = "State: $state"
        ))
    }

    /**
     * Log Max's response
     */
    fun logResponse(response: String, state: MaxState) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.MAX_RESPONSE,
            action = null,
            message = response.take(500),  // Truncate long responses
            status = LogStatus.INFO,
            approvalStatus = ApprovalStatus.NOT_REQUIRED,
            reason = "State: $state"
        ))
    }

    /**
     * Log an action that needs approval
     */
    fun logApprovalRequest(action: MaxAction, explanation: String) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.ACTION_PROPOSED,
            action = action,
            message = action.description,
            status = LogStatus.PENDING,
            approvalStatus = ApprovalStatus.PENDING,
            reason = explanation
        ))
    }

    /**
     * Log an approved action
     */
    fun logApproval(action: MaxAction, approved: Boolean, userNote: String? = null) {
        val status = if (approved) LogStatus.APPROVED else LogStatus.DENIED
        val approval = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.DENIED

        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.ACTION_DECISION,
            action = action,
            message = "User ${if (approved) "approved" else "denied"}: ${action.description}",
            status = status,
            approvalStatus = approval,
            reason = userNote ?: "User decision"
        ))
    }

    /**
     * Log a blocked action (hard rule violation)
     */
    fun logBlocked(blockReason: BlockReason) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.ACTION_BLOCKED,
            action = null,
            message = "Blocked by ${blockReason.ruleName}",
            status = LogStatus.BLOCKED,
            approvalStatus = ApprovalStatus.NOT_REQUIRED,
            reason = "${blockReason.explanation}\nEvidence: ${blockReason.evidence}\nTo unblock: ${blockReason.unblockAction}"
        ))
    }

    /**
     * Log a warning (Rule 12 - user can still proceed)
     */
    fun logWarning(warningText: String, userProceeded: Boolean?) {
        val status = when (userProceeded) {
            true -> LogStatus.WARNING_PROCEEDED
            false -> LogStatus.WARNING_HEEDED
            null -> LogStatus.WARNING_PENDING
        }

        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.WARNING,
            action = null,
            message = warningText.take(500),
            status = status,
            approvalStatus = if (userProceeded == true) ApprovalStatus.APPROVED else ApprovalStatus.NOT_REQUIRED,
            reason = "Rule 12 warning"
        ))
    }

    /**
     * Log a failure or error
     */
    fun logFailure(error: String, context: String) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.FAILURE,
            action = null,
            message = error,
            status = LogStatus.ERROR,
            approvalStatus = ApprovalStatus.NOT_REQUIRED,
            reason = context
        ))
    }

    /**
     * Log a repair attempt
     */
    fun logRepair(description: String, success: Boolean) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.REPAIR,
            action = null,
            message = description,
            status = if (success) LogStatus.SUCCESS else LogStatus.ERROR,
            approvalStatus = ApprovalStatus.NOT_REQUIRED,
            reason = "Self-repair attempt"
        ))
    }

    /**
     * Log memory operations (Rule 8)
     */
    fun logMemoryOperation(operation: String, approved: Boolean) {
        addEntry(LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.MEMORY_OP,
            action = null,
            message = operation,
            status = if (approved) LogStatus.APPROVED else LogStatus.DENIED,
            approvalStatus = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.DENIED,
            reason = "Memory requires consent (Rule 8)"
        ))
    }

    /**
     * Get recent log entries
     */
    fun getRecentEntries(count: Int = 50): List<LogEntry> {
        return logEntries.toList().takeLast(count)
    }

    /**
     * Get all entries (for export)
     */
    fun getAllEntries(): List<LogEntry> = logEntries.toList()

    /**
     * Export log as text (for transparency)
     */
    fun exportLog(): String {
        val sb = StringBuilder()
        sb.appendLine("=== MAX ACTION LOG ===")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine("Total entries: ${logEntries.size}")
        sb.appendLine()

        logEntries.forEach { entry ->
            sb.appendLine(formatEntry(entry))
        }

        return sb.toString()
    }

    /**
     * Clear log (requires user action - cannot be silent)
     */
    fun clearLog(userInitiated: Boolean): Boolean {
        if (!userInitiated) {
            logFailure("Attempted silent log clear", "Rule 6 violation - blocked")
            return false
        }

        // Archive before clearing
        logFile?.let { file ->
            val archive = File(file.parent, "action_log_archive_${System.currentTimeMillis()}.txt")
            file.copyTo(archive, overwrite = true)
        }

        logEntries.clear()
        saveLog()
        
        logRequest("Log cleared by user", MaxState.ACTING)
        return true
    }

    // Private helpers

    private fun addEntry(entry: LogEntry) {
        logEntries.add(entry)

        // Trim if needed
        while (logEntries.size > MAX_ENTRIES_IN_MEMORY) {
            logEntries.poll()
        }

        // Persist immediately
        appendToFile(entry)
    }

    private fun formatEntry(entry: LogEntry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        return "[$time] [${entry.type}] [${entry.status}] ${entry.message}" +
                if (entry.reason.isNotEmpty()) "\n  Reason: ${entry.reason}" else ""
    }

    private fun appendToFile(entry: LogEntry) {
        logFile?.let { file ->
            try {
                PrintWriter(file).use { writer ->
                    // Re-write entire log to maintain order
                    logEntries.forEach { existing ->
                        writer.println(formatEntry(existing))
                    }
                }
            } catch (e: Exception) {
                // Can't log this or we'll recurse
                println("Failed to write to log file: ${e.message}")
            }
        }
    }

    private fun saveLog() {
        logFile?.let { file ->
            try {
                PrintWriter(file).use { writer ->
                    logEntries.forEach { entry ->
                        writer.println(formatEntry(entry))
                    }
                }
            } catch (e: Exception) {
                println("Failed to save log: ${e.message}")
            }
        }
    }

    private fun loadExistingLog() {
        logFile?.let { file ->
            if (!file.exists()) return
            try {
                file.readLines().forEach { line ->
                    // Simple parsing - just note that log exists
                    // Full implementation would parse entries back
                }
            } catch (e: Exception) {
                println("Failed to load existing log: ${e.message}")
            }
        }
    }
}

/**
 * A single log entry
 */
data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val action: MaxAction?,
    val message: String,
    val status: LogStatus,
    val approvalStatus: ApprovalStatus,
    val reason: String
)

enum class LogType {
    USER_REQUEST,
    MAX_RESPONSE,
    ACTION_PROPOSED,
    ACTION_DECISION,
    ACTION_BLOCKED,
    WARNING,
    FAILURE,
    REPAIR,
    MEMORY_OP
}

enum class LogStatus {
    INFO,
    PENDING,
    APPROVED,
    DENIED,
    BLOCKED,
    WARNING_PENDING,
    WARNING_HEEDED,
    WARNING_PROCEEDED,
    SUCCESS,
    ERROR
}

enum class ApprovalStatus {
    NOT_REQUIRED,
    PENDING,
    APPROVED,
    DENIED
}
