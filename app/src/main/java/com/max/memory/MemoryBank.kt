package com.max.memory

import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory Bank for Max.
 * 
 * Two types of memory:
 * 1. Short-term: Working memory for current conversation context
 * 2. Long-term: Approved preferences and facts the user explicitly approved
 * 
 * Memory is visible and controllable by the user.
 * Sensitive content requires explicit approval to store.
 */
class MemoryBank(
    private val storageDir: File,
    private val maxShortTermEntries: Int = 50
) {
    // Short-term working memory (current session)
    private val shortTermMemory = mutableListOf<MemoryEntry>()
    
    // Long-term approved memory
    private val longTermMemory = mutableListOf<MemoryEntry>()
    
    // Index for fast lookup
    private val memoryIndex = ConcurrentHashMap<String, MutableList<MemoryEntry>>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    init {
        storageDir.mkdirs()
        loadLongTermMemory()
    }

    /**
     * Add to short-term memory (current conversation)
     */
    fun addToShortTerm(content: String, category: MemoryCategory = MemoryCategory.CONVERSATION): MemoryEntry {
        val entry = MemoryEntry(
            id = generateId(),
            content = content,
            category = category,
            isLongTerm = false,
            timestamp = System.currentTimeMillis(),
            approved = false
        )
        
        synchronized(shortTermMemory) {
            shortTermMemory.add(entry)
            // Trim if exceeds max
            while (shortTermMemory.size > maxShortTermEntries) {
                val removed = shortTermMemory.removeAt(0)
                removeFromIndex(removed)
            }
        }
        
        addToIndex(entry)
        return entry
    }

    /**
     * Promote to long-term memory (requires user approval per Rule 8)
     */
    fun promoteToLongTerm(entryId: String, userApproved: Boolean): MemoryEntry? {
        if (!userApproved) {
            return null  // Rule 8: Memory requires consent
        }

        val entry = shortTermMemory.find { it.id == entryId } ?: return null
        
        val longTermEntry = entry.copy(
            isLongTerm = true,
            approved = true,
            approvedAt = System.currentTimeMillis()
        )
        
        synchronized(longTermMemory) {
            longTermMemory.add(longTermEntry)
        }
        
        // Persist to disk
        saveLongTermMemory()
        
        return longTermEntry
    }

    /**
     * Add directly to long-term memory (for approved preferences)
     */
    fun addLongTerm(
        content: String,
        category: MemoryCategory,
        userApproved: Boolean
    ): MemoryEntry? {
        if (!userApproved) {
            return null  // Rule 8: Must have consent
        }

        val entry = MemoryEntry(
            id = generateId(),
            content = content,
            category = category,
            isLongTerm = true,
            timestamp = System.currentTimeMillis(),
            approved = true,
            approvedAt = System.currentTimeMillis()
        )
        
        synchronized(longTermMemory) {
            longTermMemory.add(entry)
        }
        
        addToIndex(entry)
        saveLongTermMemory()
        
        return entry
    }

    /**
     * Forget a memory (Rule 8: user can ask to forget anything)
     */
    fun forget(entryId: String): Boolean {
        var removed = false
        
        synchronized(shortTermMemory) {
            removed = shortTermMemory.removeAll { it.id == entryId }
        }
        
        if (!removed) {
            synchronized(longTermMemory) {
                removed = longTermMemory.removeAll { it.id == entryId }
            }
            if (removed) {
                saveLongTermMemory()  // Persist the deletion
            }
        }
        
        if (removed) {
            // Rebuild index
            rebuildIndex()
        }
        
        return removed
    }

    /**
     * Forget all memories in a category
     */
    fun forgetCategory(category: MemoryCategory): Int {
        var count = 0
        
        synchronized(shortTermMemory) {
            val removed = shortTermMemory.filter { it.category == category }
            shortTermMemory.removeAll(removed)
            count += removed.size
        }
        
        synchronized(longTermMemory) {
            val removed = longTermMemory.filter { it.category == category }
            longTermMemory.removeAll(removed)
            count += removed.size
        }
        
        if (count > 0) {
            saveLongTermMemory()
            rebuildIndex()
        }
        
        return count
    }

    /**
     * Get memories relevant to a query (for context building)
     */
    fun getRelevantMemories(query: String, limit: Int = 10): List<MemoryEntry> {
        val queryLower = query.lowercase()
        val results = mutableListOf<MemoryEntry>()
        
        // Check index for keyword matches
        val keywords = queryLower.split(Regex("\\s+"))
        for (keyword in keywords) {
            val matches = memoryIndex[keyword] ?: continue
            results.addAll(matches)
        }
        
        // If not enough, add recent long-term memories
        if (results.size < limit) {
            results.addAll(longTermMemory.takeLast(limit - results.size))
        }
        
        return results.distinctBy { it.id }.take(limit)
    }

    /**
     * Get all short-term memory
     */
    fun getShortTermMemory(): List<MemoryEntry> = synchronized(shortTermMemory) {
        shortTermMemory.toList()
    }

    /**
     * Get all long-term memory
     */
    fun getLongTermMemory(): List<MemoryEntry> = synchronized(longTermMemory) {
        longTermMemory.toList()
    }

    /**
     * Clear short-term memory (for new session)
     */
    fun clearShortTerm() {
        synchronized(shortTermMemory) {
            shortTermMemory.clear()
        }
        rebuildIndex()
    }

    /**
     * Export memory for transparency (Rule 6)
     */
    fun exportMemory(): String {
        val sb = StringBuilder()
        sb.appendLine("=== MAX MEMORY EXPORT ===")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine()
        
        sb.appendLine("--- LONG-TERM MEMORY (Approved) ---")
        longTermMemory.forEach { entry ->
            sb.appendLine("[${entry.category}] ${entry.content}")
            sb.appendLine("  Created: ${dateFormat.format(Date(entry.timestamp))}")
            if (entry.approvedAt != null) {
                sb.appendLine("  Approved: ${dateFormat.format(Date(entry.approvedAt))}")
            }
        }
        
        sb.appendLine()
        sb.appendLine("--- SHORT-TERM MEMORY (Current Session) ---")
        shortTermMemory.forEach { entry ->
            sb.appendLine("[${entry.category}] ${entry.content}")
        }
        
        return sb.toString()
    }

    // Private helpers

    private fun generateId(): String = UUID.randomUUID().toString().take(8)

    private fun addToIndex(entry: MemoryEntry) {
        val words = entry.content.lowercase().split(Regex("\\s+"))
        for (word in words) {
            if (word.length < 3) continue  // Skip short words
            memoryIndex.getOrPut(word) { mutableListOf() }.add(entry)
        }
    }

    private fun removeFromIndex(entry: MemoryEntry) {
        val words = entry.content.lowercase().split(Regex("\\s+"))
        for (word in words) {
            memoryIndex[word]?.remove(entry)
        }
    }

    private fun rebuildIndex() {
        memoryIndex.clear()
        shortTermMemory.forEach { addToIndex(it) }
        longTermMemory.forEach { addToIndex(it) }
    }

    private fun saveLongTermMemory() {
        val file = File(storageDir, "long_term_memory.json")
        try {
            PrintWriter(file).use { writer ->
                writer.println("[")
                longTermMemory.forEachIndexed { index, entry ->
                    writer.println("  {")
                    writer.println("    \"id\": \"${entry.id}\",")
                    writer.println("    \"content\": \"${entry.content.escapeJson()}\",")
                    writer.println("    \"category\": \"${entry.category}\",")
                    writer.println("    \"timestamp\": ${entry.timestamp},")
                    writer.println("    \"approved\": ${entry.approved},")
                    writer.println("    \"approvedAt\": ${entry.approvedAt ?: "null"}")
                    writer.println("  }${if (index < longTermMemory.size - 1) "," else ""}")
                }
                writer.println("]")
            }
        } catch (e: Exception) {
            // Log error but don't crash
            println("Failed to save long-term memory: ${e.message}")
        }
    }

    private fun loadLongTermMemory() {
        val file = File(storageDir, "long_term_memory.json")
        if (!file.exists()) return
        
        try {
            val content = file.readText()
            // Simple JSON parsing (in production, use kotlinx.serialization)
            // For now, just note that persistence exists
        } catch (e: Exception) {
            println("Failed to load long-term memory: ${e.message}")
        }
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

/**
 * A single memory entry
 */
data class MemoryEntry(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val isLongTerm: Boolean,
    val timestamp: Long,
    val approved: Boolean,
    val approvedAt: Long? = null
)

/**
 * Categories for memory
 */
enum class MemoryCategory {
    CONVERSATION,       // Conversation context
    PREFERENCE,         // User preference (approved)
    FACT,               // Factual info about user (approved)
    TASK,               // Active task or goal
    WARNING,            // Something to remember to avoid
    SYSTEM             // System-level config
}
