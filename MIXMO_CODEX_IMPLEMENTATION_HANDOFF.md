# MIXMO_CODEX_IMPLEMENTATION_HANDOFF.md

# Mixmo MVP — Final Codex Handoff

This document is the authoritative implementation handoff combining:

- official game rules
- clarified product decisions
- finalized backend contracts
- finalized realtime protocol
- MVP simplifications

It is intended to remove ambiguity before implementation.

---

## 1. Final verdict on coherence

The uploaded technical and UI documents are broadly coherent with the official Mixmo rules, but they needed several clarifications to become implementation-safe.

The main ambiguities were:

- confusion between Classic Joker and Bandit theme flow
- missing first-word origin rule
- missing explicit statement that multiple crossings are allowed
- missing explicit reconnect/bootstrap contract details
- missing detailed WebSocket command and event payloads
- undefined suggestion ranking rule

These are now resolved by this handoff and by the updated technical and UI documents.

---

## 2. Authoritative gameplay rules for MVP

### 2.1 Tile distribution

| Tile | Qty | Tile | Qty |
|---|---:|---|---:|
| A | 10 | P | 3 |
| B | 2 | Q | 2 |
| C | 3 | R | 6 |
| D | 4 | S | 7 |
| E | 17 | T | 7 |
| F | 2 | U | 6 |
| G | 3 | V | 3 |
| H | 3 | W | 1 |
| I | 9 | X | 1 |
| J | 2 | Y | 1 |
| K | 1 | Z | 1 |
| L | 6 | Classic Joker | 2 |
| M | 3 | Bandit | 1 |
| N | 7 |  |  |
| O | 7 | Total | 120 |

### 2.2 Start of game

- each player draws 6 tiles
- each player starts with an empty personal board
- words are placed only horizontally or vertically
- no diagonal placement is allowed

### 2.3 Board constraints

- each player has a personal crossword-like board
- the board is effectively unbounded
- the **first committed placement must cross the conceptual origin `(0,0)`**
- after the first move, every committed placement must connect to the current board cluster
- disconnected islands are forbidden
- a word may cross one or more existing words if all overlapping letters match

### 2.4 Jokers and Bandit

- **Classic Joker** can represent any letter A-Z
- **Bandit** can represent only K, W, X, Y, or Z
- if Classic Joker or Bandit is used at a crossing, the resolved letter must be the same for both words
- **only Bandit requires theme selection**
- drawing a Bandit tile does not pause the game by itself
- the game pauses only when a player attempts to use Bandit and must choose the theme
- the selected Bandit theme remains visible until the end of the match

### 2.5 MIXMO

- a player may trigger MIXMO only when their rack is empty
- all players then draw 2 tiles
- simultaneous MIXMO triggers do not stack
- players may completely reorganize their own board between placements, as long as each committed state remains valid under the connectivity rules
- if the reserve is empty and a player with an empty rack triggers MIXMO, that final MIXMO ends the game and that player wins

### 2.6 Blocked game case

- if players are collectively blocked, the system may support a manual shared MIXMO that draws 2 tiles for everyone
- this does not stack with other simultaneous MIXMO requests

---

## 3. Explicit MVP simplifications

These remain out of scope for MVP:

- dictionary validation
- final semantic verification that a word actually matches the selected Bandit theme
- authentication
- bots
- matchmaking
- ranking system outside the per-suggestion `victoryCount` metric

---

## 4. Suggestion engine rule

Suggestion ranking is based **only on victory count**.

Each suggestion must expose:

- start position
- orientation
- preview cells
- `victoryCount`

Higher `victoryCount` ranks first.

---

## 5. REST bootstrap contract

Use REST for room lifecycle and snapshot bootstrap.

Required endpoints:

```text
POST   /api/rooms
POST   /api/rooms/{roomId}/join
POST   /api/rooms/{roomId}/start
GET    /api/rooms/{roomId}
GET    /api/rooms/{roomId}/state
POST   /api/rooms/{roomId}/reconnect
POST   /api/rooms/{roomId}/mixmo/manual
```

Core rule:

- `/state` and `/reconnect` return the **full authoritative snapshot**, not just deltas
- after reconnect, the player's rack must exactly match the server snapshot, including any missed shared draws

---

## 6. WebSocket contract

Use WebSocket for gameplay commands and broadcasts.

Client commands:

- `game.sync.request`
- `placement.preview.request`
- `placement.suggestions.request`
- `placement.confirm.request`
- `mixmo.trigger`
- `bandit.theme.select`

Server events:

- `command.ack`
- `placement.preview.result`
- `placement.suggestions.result`
- `game.state.updated`
- `mixmo.resolved`
- `game.paused`
- `bandit.theme.selected`
- `game.resumed`
- `game.finished`
- `error`

Every command must include:

- `requestId`
- `roomId`
- `playerId`
- `expectedRoomVersion`
- `payload`

---

## 7. Reconnect rule

Reconnect must behave like a hard resync.

If the client refreshes the page:

- it requests the latest snapshot
- it receives all tiles it should currently own
- local stale rack state is discarded
- the latest pause/theme/winner state is restored

---

## 8. Frontend interaction rule

The mandatory UX model remains:

1. compose a candidate word from rack tiles
2. choose anchor/start position
3. preview whole-word placement
4. switch orientation if needed
5. confirm placement
6. if Bandit is used, choose theme

This is mobile-first and must not depend on drag-and-drop or keyboard word entry on mobile.

---

## 9. Implementation recommendation

For Codex or any engineering handoff, the safe implementation order is:

1. room lifecycle and bootstrap snapshot
2. authoritative tile bag and initial draw
3. sparse personal board model
4. preview validation including origin/connectivity/multi-crossing rules
5. placement commit
6. MIXMO shared draw resolution
7. Bandit pause/theme flow
8. suggestion generation and ranking by victory count
9. reconnect hard resync
10. UI polish after MVP correctness

---

## 10. Files to trust

Use these three files together as the implementation source of truth:

- `TECHNICAL_REQUIREMENTS_UPDATED.md`
- `mixo_ui_requirements_UPDATED.md`
- `MIXMO_CODEX_IMPLEMENTATION_HANDOFF.md`
