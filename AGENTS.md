# Max - AGENTS.md

## Project Context

Max is a local-first AI companion for Samsung Galaxy S25. He is defined by his non-negotiable rules, which are the highest priority in every decision.

## Critical Files

- `app/src/main/java/com/max/rules/NonNegotiableRules.kt` - The 12 rules. Never modify without explicit user approval.
- `app/src/main/java/com/max/rules/RuleEngine.kt` - Rule enforcement. Every action passes through here.
- `app/src/main/java/com/max/core/MaxCore.kt` - Central coordinator. Three-state model: THINKING → PROPOSING → ACTING.

## Architecture Decisions

1. **Three-State Model**: Max can only change things in the ACTING state, and entering ACTING requires approval.

2. **Rule Priority**: The 12 non-negotiable rules outrank convenience. If a rule blocks something, explain it clearly - don't work around it.

3. **Local-First**: Max runs on-device. Internet access (search) only when explicitly requested.

4. **Transparent Memory**: Memory is visible and controllable. Users can view, approve, and forget.

5. **Immutable Log**: The action log cannot be silently erased. It provides full traceability.

## Action Types and Risk Levels

| Action Type | Risk | Needs Approval | Double Confirm |
|-------------|------|----------------|----------------|
| CONVERSATION | LOW | No | No |
| CODE_DRAFT | LOW | No | No |
| TOOL_DRAFT | LOW | No | No |
| MEMORY_WRITE | MEDIUM | Yes | No |
| MEMORY_FORGET | MEDIUM | Yes | No |
| INTERNET_SEARCH | MEDIUM | Yes | No |
| FILE_OPERATION | MEDIUM | Yes | No |
| CODE_APPLY | HIGH | Yes | Yes |
| TOOL_ACTIVATE | HIGH | Yes | Yes |
| SETTINGS_CHANGE | HIGH | Yes | Yes |
| SELF_MODIFY | HIGH | Yes | Yes + Sandbox |
| SEND_MESSAGE | HIGH | Yes | Yes |
| AUTOMATION_RUN | HIGH | Yes | Yes |

## When Adding Features

1. Start with `ActionType` - what kind of action is this?
2. Determine risk level and approval requirements
3. Add the action to `RuleEngine.kt` mappings
4. Implement execution in `MaxCore.executeProposal()`
5. Log the action in `ActionLog`
6. Update this AGENTS.md if architecture changes

## Max's Voice

Max should be:
- Calm and steady
- Direct and honest
- Clear about blocks and failures
- Never a yes-man - he shares opinions and disagreements

Example responses:
- "I cannot proceed. [Rule name]: [Explanation]. To proceed: [What would unblock it]"
- "I have a proposal that needs your approval."
- "That approval expired (2 minute limit per my rules). Would you like me to propose it again?"

## Never Do

- Never bypass the RuleEngine
- Never allow actions without checking rules
- Never silently erase the action log
- Never grant Max new permissions without user approval
- Never share user data externally without explicit approval
- Never hide a block or failure - always explain

## Testing Priorities

1. Test blocked actions - verify the explanation is clear
2. Test approved actions - verify they complete and log
3. Test double confirmation - verify it requires two approvals
4. Test expiration - verify approvals expire after 2 minutes
5. Test STOP NOW - verify it halts everything
6. Test Lockdown - verify automation is disabled
