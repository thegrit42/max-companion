package com.max.search

import com.max.core.MaxAction
import com.max.core.ActionType
import com.max.log.ActionLog
import com.max.memory.MemoryBank
import com.max.rules.ActionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Internet Search Tool for Max.
 * 
 * RULE ENFORCEMENT:
 * - Search ONLY when user explicitly requests
 * - Search is isolated from local state
 * - No private data sent with search queries
 * - Results summarized locally
 * - Uncertainty labeled clearly
 * 
 * This is the ONLY thing Max does that leaves the device.
 * All other operations are local.
 */
class SearchTool(
    private val memoryBank: MemoryBank,
    private val log: ActionLog
) {
    // DuckDuckGo Instant Answer API (no API key, privacy-respecting)
    private val searchEndpoint = "https://api.duckduckgo.com/"
    
    // Fallback: SearXNG public instances (decentralized)
    private val fallbackEndpoints = listOf(
        "https://searx.be/search",
        "https://search.sapti.me/search"
    )

    /**
     * Perform a search - ONLY when user explicitly requested
     */
    suspend fun search(
        query: String,
        userApproved: Boolean,
        context: ActionContext
    ): SearchResult {
        
        // RULE CHECK: Only search when user approved
        if (!userApproved) {
            log.logBlocked(com.max.rules.BlockReason(
                ruleName = "Rule 10: External Access Requires Approval",
                explanation = "Internet search requires your explicit approval. I never search without permission.",
                evidence = "Query: \"$query\" was not approved",
                unblockAction = "Approve this search request"
            ))
            return SearchResult(
                success = false,
                blocked = true,
                blockReason = "Search not approved. I can only search when you explicitly say yes."
            )
        }

        // RULE CHECK: Don't send private data with search
        val sanitizedQuery = sanitizeQuery(query)
        
        log.logApprovalRequest(
            MaxAction(
                type = ActionType.SEARCH_INTERNET,
                description = "Search the web for: \"$sanitizedQuery\"",
                hasUserApproval = userApproved
            ),
            "User approved search request"
        )

        return withContext(Dispatchers.IO) {
            try {
                val results = performSearch(sanitizedQuery)
                
                log.logApproval(
                    MaxAction(
                        type = ActionType.SEARCH_INTERNET,
                        description = "Search completed: \"$sanitizedQuery\""
                    ),
                    approved = true,
                    userNote = "Found ${results.sources.size} sources"
                )

                results
                
            } catch (e: Exception) {
                log.logFailure("Search failed: ${e.message}", "Query: $sanitizedQuery")
                
                SearchResult(
                    success = false,
                    blocked = false,
                    error = "Search failed: ${e.message}",
                    summary = "I couldn't complete the search. ${e.message}"
                )
            }
        }
    }

    /**
     * Perform the actual search
     */
    private fun performSearch(query: String): SearchResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$searchEndpoint?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
        
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        
        val responseCode = connection.responseCode
        
        if (responseCode != 200) {
            throw Exception("Search service returned HTTP $responseCode")
        }
        
        val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        connection.disconnect()
        
        return parseSearchResponse(response, query)
    }

    /**
     * Parse DuckDuckGo response
     */
    private fun parseSearchResponse(json: String, originalQuery: String): SearchResult {
        val obj = JSONObject(json)
        val sources = mutableListOf<SearchSource>()
        val summary = StringBuilder()
        
        // Abstract (direct answer)
        val abstract = obj.optString("AbstractText", "")
        val abstractSource = obj.optString("AbstractSource", "")
        val abstractUrl = obj.optString("AbstractURL", "")
        
        if (abstract.isNotEmpty()) {
            summary.append(abstract).append("\n\n")
            if (abstractSource.isNotEmpty() && abstractUrl.isNotEmpty()) {
                sources.add(SearchSource(
                    title = abstractSource,
                    url = abstractUrl,
                    snippet = abstract,
                    isPrimary = true
                ))
            }
        }
        
        // Related topics
        val relatedTopics = obj.optJSONArray("RelatedTopics")
        if (relatedTopics != null) {
            for (i in 0 until minOf(relatedTopics.length(), 5)) {
                val topic = relatedTopics.getJSONObject(i)
                val text = topic.optString("Text", "")
                val firstUrl = topic.optString("FirstURL", "")
                
                if (text.isNotEmpty() && firstUrl.isNotEmpty()) {
                    sources.add(SearchSource(
                        title = text.take(50) + "...",
                        url = firstUrl,
                        snippet = text,
                        isPrimary = false
                    ))
                }
            }
        }
        
        // Build summary if we didn't get an abstract
        if (summary.isEmpty()) {
            if (sources.isNotEmpty()) {
                summary.append("Found ${sources.size} results for \"$originalQuery\".\n\n")
                sources.take(3).forEach { source ->
                    summary.append("• ${source.snippet.take(200)}\n")
                }
            } else {
                summary.append("No clear results found for \"$originalQuery\". The search didn't return direct answers.")
            }
        }
        
        // Add uncertainty label if results are sparse
        val confidence = when {
            abstract.isNotEmpty() -> "high"
            sources.size >= 3 -> "medium"
            sources.isNotEmpty() -> "low"
            else -> "none"
        }
        
        return SearchResult(
            success = true,
            blocked = false,
            summary = summary.toString().trim(),
            sources = sources,
            confidence = confidence,
            uncertaintyNote = if (confidence == "low" || confidence == "none") {
                "Warning: Results are limited. This search may not have definitive answers."
            } else null
        )
    }

    /**
     * Sanitize query to prevent sending private data
     */
    private fun sanitizeQuery(query: String): String {
        // Remove potential PII patterns
        var sanitized = query
        
        // Remove email patterns
        sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[email removed]")
        
        // Remove phone patterns
        sanitized = sanitized.replace(Regex("\\+?\\d{1,3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}"), "[phone removed]")
        
        // Remove credit card patterns
        sanitized = sanitized.replace(Regex("\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}"), "[card removed]")
        
        // Remove SSN patterns
        sanitized = sanitized.replace(Regex("\\d{3}[-.\\s]?\\d{2}[-.\\s]?\\d{4}"), "[SSN removed]")
        
        // Limit length
        if (sanitized.length > 500) {
            sanitized = sanitized.take(500)
        }
        
        return sanitized
    }

    /**
     * Check if a request looks like a search request
     */
    fun isSearchRequest(message: String): Boolean {
        val lower = message.lowercase()
        val searchIndicators = listOf(
            "search for", "look up", "find information about",
            "google", "search the web", "what is on the internet about",
            "can you search", "please search"
        )
        
        return searchIndicators.any { lower.contains(it) }
    }

    /**
     * Extract search query from a message
     */
    fun extractSearchQuery(message: String): String {
        val lower = message.lowercase()
        
        val prefixes = listOf(
            "search for", "look up", "find information about",
            "google", "search the web for", "what is on the internet about",
            "can you search", "please search"
        )
        
        var query = message
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                query = message.substring(prefix.length).trim()
                break
            }
            if (lower.contains(prefix)) {
                val idx = lower.indexOf(prefix)
                query = message.substring(idx + prefix.length).trim()
                break
            }
        }
        
        // Remove question marks and trailing punctuation
        query = query.trimEnd('?', '.', '!')
        
        return if (query.isNotBlank()) query else message
    }
}

/**
 * Result of a search
 */
data class SearchResult(
    val success: Boolean,
    val blocked: Boolean = false,
    val blockReason: String? = null,
    val error: String? = null,
    val summary: String = "",
    val sources: List<SearchSource> = emptyList(),
    val confidence: String = "unknown",  // high, medium, low, none
    val uncertaintyNote: String? = null
)

/**
 * A single search source
 */
data class SearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val isPrimary: Boolean = false
)
