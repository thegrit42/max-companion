# Max - Local-First AI Companion

Max is a local-first AI companion for Samsung Galaxy S25. He is loyal to you alone, speaks honestly, and acts only within your rules.

## Core Architecture

Max operates on a three-state model:

```
┌──────────┐     Proposal      ┌──────────┐    Approval    ┌─────────┐
│ THINKING │ ───────────────► │ PROPOSING │ ─────────────► │ ACTING  │
│          │                   │           │                 │         │
│ Analyze  │                   │ Waiting   │                 │ Execute │
│ Listen   │                   │ for       │                 │ (only   │
│ Remember │                   │ approval  │                 │ state   │
└──────────┘                   └──────────┘                 │ that    │
     ▲                                                       │ changes │
     │                        Denied                         │ anything)│
     └───────────────────────────────────────────────────────└─────────┘
```

## The 12 Non-Negotiable Rules

These rules are hard-coded into Max's core. They outrank convenience.

| Rule | Name | What It Means |
|------|------|---------------|
| 1 | Owner Lock | Max recognizes only you. No one else can issue commands. |
| 2 | Default is NO | If permission is unclear, Max does nothing. |
| 3 | Clear Permission Required | Before action, Max shows: what, why, risk level. |
| 4 | Double Confirmation | High-risk actions need two approvals. |
| 5 | Sandbox First | Self-modification requires sandbox testing. |
| 6 | Full Transparency Log | Every action is logged. Log cannot be silently erased. |
| 7 | Immediate Stop Control | "STOP NOW" halts everything. Lockdown mode available. |
| 8 | Permission Expiration | Approvals expire after 2 minutes. |
| 9 | No Silent Background Power | Max cannot grant itself new permissions. |
| 10 | Data Loyalty | Your data stays with Max only. No sharing without approval. |
| 11 | Anti-Impersonation | Voice alone is not enough for critical actions. |
| 12 | Safety Boundary | Even with approval, illegal/dangerous actions are blocked. |

## Project Structure

```
max/
├── app/
│   ├── src/main/
│   │   ├── java/com/max/
│   │   │   ├── core/
│   │   │   │   ├── MaxCore.kt      # Central coordinator
│   │   │   │   └── MaxState.kt     # States, actions, proposals
│   │   │   ├── rules/
│   │   │   │   ├── NonNegotiableRules.kt  # The 12 rules
│   │   │   │   └── RuleEngine.kt          # Rule enforcement
│   │   │   ├── memory/
│   │   │   │   └── MemoryBank.kt   # Short-term + long-term memory
│   │   │   ├── log/
│   │   │   │   └── ActionLog.kt    # Full transparency logging
│   │   │   ├── ai/
│   │   │   │   └── AIBackend.kt    # AI backend interface
│   │   │   ├── ui/
│   │   │   │   ├── MaxChatScreen.kt  # Main interface
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt      # Dark, calm theme
│   │   │   ├── MainActivity.kt
│   │   │   └── MaxApplication.kt
│   │   └── res/
│   │       └── values/
│   │           ├── strings.xml
│   │           ├── colors.xml
│   │           └── themes.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## How It Works

### 1. Companion Core
Max talks with you locally. He has a stable, honest voice. He explains what he can and cannot do.

### 2. Rule Enforcement
Every action passes through `RuleEngine.checkAction()`. If any rule blocks it, Max explains:
- Which rule blocked it
- Why it was blocked
- What would unblock it

### 3. Approval Gate
For actions needing approval:
1. Max creates a `MaxProposal` with: description, reason, risk level
2. You see the proposal and choose: Approve / Deny / Ask Questions
3. High-risk actions need double confirmation
4. Approvals expire in 2 minutes (Rule 8)

### 4. Memory
- **Short-term**: Current conversation context. Auto-cleared on new session.
- **Long-term**: Approved preferences. Requires explicit approval to store.
- **Forget command**: "forget [query]" removes matching memories.

### 5. Action Log
Every action is logged with:
- Timestamp
- Action type
- What happened
- Approved or denied
- Owner verification status

You can view the log anytime. It cannot be silently erased (Rule 6).

### 6. STOP NOW and Lockdown
- "STOP NOW" halts all pending actions immediately (Rule 7)
- Lockdown mode disables all automation until you exit it

## Building

Requires:
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

```bash
cd max
./gradlew assembleDebug
```

## Definition of Done

Max is finished when he is:
- ✅ Fully local
- ✅ Loyal and honest
- ✅ Capable of drafting code and tools
- ✅ Able to detect and explain failures
- ✅ Blocked from acting without approval
- ✅ Following the 12 non-negotiable rules
- 🔄 Reliable enough to trust day to day

## First Build Status

This is the first build pass. The core architecture is complete:
- [x] Companion core with honest voice
- [x] 12 non-negotiable rules embedded
- [x] Rule engine for enforcement
- [x] Approval gate with double confirmation
- [x] Memory system (short-term + approved long-term)
- [x] Action log with full transparency
- [x] STOP NOW and Lockdown controls
- [ ] AI backend integration (placeholder for Gemini Nano)
- [ ] Sandbox testing for self-modification
- [ ] Internet search integration

## The Core Policy (Hard-Coded)

> "This AI executes sensitive actions only after verified owner authorization, explicit approval, and auditable confirmation."
