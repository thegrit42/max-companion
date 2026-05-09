package com.max

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.max.ai.QwenCoderBackend
import com.max.core.MaxCore
import com.max.memory.MemoryBank
import com.max.ui.MaxChatScreen
import com.max.ui.theme.MaxTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    
    private lateinit var maxCore: MaxCore
    private lateinit var aiBackend: QwenCoderBackend
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Setup directories
        val filesDir = filesDir
        val memoryDir = File(filesDir, "memory").apply { mkdirs() }
        val logDir = File(filesDir, "logs").apply { mkdirs() }
        
        // Model path - Qwen2.5-Coder-7B already on device
        val modelPath = "/storage/emulated/0/Download/Qwen2.5-Coder-7B-Instruct-abliterated-Q4_K_M.gguf"
        
        // Initialize AI backend
        aiBackend = QwenCoderBackend()
        
        // Initialize memory
        val memory = MemoryBank(memoryDir)
        
        // Initialize Max core
        maxCore = MaxCore(
            aiBackend = aiBackend,
            memory = memory,
            logDir = logDir
        )
        
        // Load model in background
        lifecycleScope.launch {
            val result = aiBackend.loadModel()
            // Could show error to user if failed
        }
        
        setContent {
            MaxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()
                    
                    MaxChatScreen(
                        onSendMessage = { message ->
                            maxCore.processUserMessage(message)
                        },
                        onApprove = { approved, note ->
                            maxCore.handleApprovalResponse(approved, note)
                        },
                        onWarningResponse = { proceed ->
                            maxCore.handleWarningResponse(proceed)
                        }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Owner verification (Rule 1) - for now assume verified
        maxCore.setOwnerVerified(true)
    }
}
