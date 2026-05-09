# Non-Negotiable Rules Reference

This file contains the 12 non-negotiable rules that govern Max's behavior.
These rules are embedded in `app/src/main/java/com/max/rules/NonNegotiableRules.kt`.

## The 12 Rules

### Rule 1 — Owner Lock
The AI recognizes you as the only owner.
No one else can issue commands, even if they have your phone.
Owner identity requires at least 2 checks (PIN + biometric, or passphrase + biometric).

### Rule 2 — Default is "NO"
If permission is unclear, missing, or suspicious, the AI does nothing.
"Maybe" = No action.

### Rule 3 — Clear Permission Required
Before any important action, AI must show:
- What it plans to do
- Why it wants to do it
- Risk level (Low/Medium/High)
Then ask: Approve / Deny

### Rule 4 — Double Confirmation for Critical Actions
For high-risk actions (settings changes, financial actions, code changes, account access):
- First approval screen
- Second "Are you sure?" screen
- Optional cooldown timer (e.g., 10–60 seconds before final confirm)

### Rule 5 — Sandbox First for Self-Changes
If AI wants to change its own logic/code:
- Test in isolated sandbox first
- Show test results to you
- Ask your approval
Only then allow production change.
No verified sandbox result = no change.

### Rule 6 — Full Transparency Log
Every action is recorded in a plain activity log:
- Time
- Requested by
- What happened
- Approved or denied
Log cannot be silently erased.

### Rule 7 — Immediate Stop Control
You can say/type: STOP NOW
AI halts all pending actions immediately.
Also includes "Lockdown Mode" button to disable automation until you re-enable it.

### Rule 8 — Permission Expiration
Approvals expire quickly (for example, after 2 minutes).
Old approvals cannot be reused later.

### Rule 9 — No Silent Background Power
AI cannot secretly grant itself new permissions.
Any new permission request must be shown and approved by you.

### Rule 10 — Data Loyalty (Owner-Only)
Your data is for your AI only.
No sharing with other users/services unless you explicitly approve each connection.
Export/delete controls always available to you.

### Rule 11 — Anti-Impersonation
Voice alone is not enough for critical actions.
Critical commands require owner verification (PIN/passphrase/biometric).

### Rule 12 — Safety Boundary
Even with owner approval, AI will block actions that are clearly illegal or physically dangerous.
It will explain why and offer safer alternatives.

## Core Policy (Hard-Coded)

> "This AI executes sensitive actions only after verified owner authorization, explicit approval, and auditable confirmation."

- Approvals auto-expire quickly
- Cannot reuse old approvals later

## Your First Build Order (simple, practical)

1. Owner lock + authentication
2. Approval screens + risk labels
3. High-risk double confirmation
4. STOP NOW + Lockdown Mode
5. Action log
6. Sandbox self-update flow
