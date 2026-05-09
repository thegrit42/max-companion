package com.max.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import com.max.core.MaxProposal
import com.max.core.MaxResponse
import com.max.core.MaxState
import com.max.core.RiskLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxChatScreen(
    onSendMessage: suspend (String) -> MaxResponse,
    onApprove: suspend (Boolean, String?) -> MaxResponse,
    onWarningResponse: suspend (Boolean) -> MaxResponse,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var currentProposal by remember { mutableStateOf<MaxProposal?>(null) }
    var currentWarning by remember { mutableStateOf<String?>(null) }
    var canProceedWarning by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentState by remember { mutableStateOf(MaxState.THINKING) }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()  // Fix input box behind nav bar
    ) {
        // Status bar
        StatusBar(
            state = currentState,
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (currentProposal != null) {
                item {
                    ApprovalPanel(
                        proposal = currentProposal!!,
                        isLoading = isLoading,
                        onApprove = { note ->
                            isLoading = true
                            scope.launch {
                                val response = onApprove(true, note)
                                messages = messages + ChatMessage(
                                    content = if (note != null) "Approved: $note" else "Approved",
                                    isFromUser = true,
                                    timestamp = System.currentTimeMillis()
                                )
                                messages = messages + ChatMessage(
                                    content = response.message,
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentState = response.state
                                currentProposal = null
                                isLoading = false
                            }
                        },
                        onDeny = {
                            isLoading = true
                            scope.launch {
                                val response = onApprove(false, null)
                                messages = messages + ChatMessage(
                                    content = "Denied",
                                    isFromUser = true,
                                    timestamp = System.currentTimeMillis()
                                )
                                messages = messages + ChatMessage(
                                    content = response.message,
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentState = response.state
                                currentProposal = null
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (currentWarning != null) {
                item {
                    WarningPanel(
                        warning = currentWarning!!,
                        canProceed = canProceedWarning,
                        isLoading = isLoading,
                        onProceed = {
                            isLoading = true
                            scope.launch {
                                val response = onWarningResponse(true)
                                messages = messages + ChatMessage(
                                    content = "Proceeding despite warning",
                                    isFromUser = true,
                                    timestamp = System.currentTimeMillis()
                                )
                                messages = messages + ChatMessage(
                                    content = response.message,
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentState = response.state
                                currentWarning = null
                                isLoading = false
                            }
                        },
                        onAbort = {
                            scope.launch {
                                val response = onWarningResponse(false)
                                messages = messages + ChatMessage(
                                    content = "Aborted",
                                    isFromUser = true,
                                    timestamp = System.currentTimeMillis()
                                )
                                messages = messages + ChatMessage(
                                    content = response.message,
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentState = response.state
                                currentWarning = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Input area - properly inset from system navigation bar
        InputArea(
            text = inputText,
            onTextChange = { inputText = it },
            isLoading = isLoading,
            onSend = {
                if (inputText.isNotBlank()) {
                    val userMessage = inputText
                    inputText = ""
                    messages = messages + ChatMessage(
                        content = userMessage,
                        isFromUser = true,
                        timestamp = System.currentTimeMillis()
                    )
                    isLoading = true
                    
                    scope.launch {
                        val response = onSendMessage(userMessage)
                        messages = messages + ChatMessage(
                            content = response.message,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        currentState = response.state
                        
                        if (response.proposal != null) {
                            currentProposal = response.proposal
                        }
                        
                        if (response.isWarning) {
                            currentWarning = response.message
                            canProceedWarning = response.canProceed
                        }
                        
                        isLoading = false
                        
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.ime)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
        )
    }
}

@Composable
private fun StatusBar(
    state: MaxState,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val stateColor = when (state) {
        MaxState.THINKING -> Color(0xFF4CAF50)
        MaxState.PROPOSING -> Color(0xFFFFA726)
        MaxState.ACTING -> Color(0xFF42A5F5)
    }

    val stateLabel = when (state) {
        MaxState.THINKING -> "Listening"
        MaxState.PROPOSING -> "Awaiting Approval"
        MaxState.ACTING -> "Acting"
    }

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(stateColor, RoundedCornerShape(50))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stateLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Qwen 7B",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isFromUser

    Column(
        modifier = modifier,
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(0.85f)
        ) {
            Text(
                text = message.content,
                fontSize = 15.sp,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ApprovalPanel(
    proposal: MaxProposal,
    isLoading: Boolean,
    onApprove: (String?) -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    val riskColor = when (proposal.riskAssessment) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MEDIUM -> Color(0xFFFFA726)
        RiskLevel.HIGH -> Color(0xFFF44336)
        RiskLevel.CRITICAL -> Color(0xFFD32F2F)
    }

    var noteText by remember { mutableStateOf("") }
    var showNoteField by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Approval Required",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            if (proposal.requiresDoubleConfirm) {
                Text(
                    text = "⚠️ This action requires double confirmation",
                    fontSize = 12.sp,
                    color = riskColor
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        if (proposal.requiresDoubleConfirm) {
                            showNoteField = true
                        } else {
                            onApprove(null)
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Approve")
                }

                OutlinedButton(
                    onClick = onDeny,
                    enabled = !isLoading
                ) {
                    Text("Deny")
                }
            }

            if (showNoteField) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Confirmation note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { onApprove(noteText.ifBlank { null }) },
                    enabled = !isLoading && noteText.isNotBlank()
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
private fun WarningPanel(
    warning: String,
    canProceed: Boolean,
    isLoading: Boolean,
    onProceed: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x33F44336)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rule 12 Warning",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }

            Text(
                text = warning,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (canProceed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onProceed,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Text("Proceed Anyway")
                    }

                    OutlinedButton(
                        onClick = onAbort,
                        enabled = !isLoading
                    ) {
                        Text("Abort")
                    }
                }
            } else {
                Button(
                    onClick = onAbort,
                    enabled = !isLoading
                ) {
                    Text("Understood")
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = !isLoading,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Message Max...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        FilledIconButton(
            onClick = onSend,
            enabled = !isLoading && text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send"
            )
        }
    }
}

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
