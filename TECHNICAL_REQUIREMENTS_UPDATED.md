# TECHNICAL_REQUIREMENTS.md

# Mixmo MVP — Technical Requirements

## 1. Document Purpose

This document defines the repo-ready technical requirements for implementing the **Mixmo MVP**.

It is intended as the engineering handoff for a full-stack implementation using:

- **Frontend:** Vue 3 + TypeScript
- **Backend:** Spring Boot
- **Database:** PostgreSQL
- **Realtime transport:** WebSocket

This document is derived from:

- the supplied Mixmo game rules
- the uploaded UI requirements and UX constraints, especially the **mobile-first**, **compose first / place second**, **unbounded board**, **Mixmo gating**, **Bandit pause**, and **placement suggestions** requirements

---

## 2. Product Goal

Build a playable MVP of **Mixmo**, a simultaneous multiplayer word game in which each player manages a personal crossword-like board and can trigger shared game-state changes via **MIXMO**.

The implementation must prioritize:

- mobile usability first
- low-friction touch interaction
- server-authoritative game logic
- fast realtime synchronization
- clear separation between game rules and frontend interaction state

The frontend must preserve the UI contract that players **compose a candidate word first, then place the full word as a whole** rather than dragging letters one by one. The board must stay visible throughout gameplay, and the UI must not depend on mobile drag-and-drop or mobile keyboard entry.

---

## 3. MVP Scope

### 3.1 Included in MVP

- create and join multiplayer rooms
- start a game session
- draw initial tiles
- manage a shared tile reserve
- manage one personal board per player
- compose a word from rack tiles
- place a full word on the board with preview and validation
- support classic jokers
- support Bandit tile restrictions
- pause the game for Bandit theme selection
- persist and display the chosen Bandit theme
- compute and display multiple placement suggestions
- support MIXMO triggering and shared draw resolution
- detect the final MIXMO and end the game
- support reconnect and room state reload

### 3.2 Explicitly Out of Scope for MVP

- French dictionary validation
- semantic validation of theme correctness
- authentication and identity federation
- matchmaking
- rankings or leaderboards
- bots
- anti-cheat beyond authoritative backend validation
- advanced moderation/admin tools

The UI requirements explicitly confirm that MVP does **not** require word validation and does **not** require theme validation tied to Mixmo.

---

## 4. Stack Decision

## 4.1 Frontend Stack

Use:

- **Vue 3**
- **TypeScript**
- **Vite**
- **Pinia**
- **Vue Router**
- **Tailwind CSS**
- **VueUse**
- **native WebSocket client** or **STOMP client**

### Rationale

The UI requirements define a strongly stateful UX with explicit modes, persistent board visibility, a candidate-word strip, preview states, Bandit pause state, and suggestion-driven placement on an unbounded board. That fits a reactive Vue + Pinia architecture well.

## 4.2 Backend Stack

Use:

- **Java 21**
- **Spring Boot 3**
- **Spring Web**
- **Spring WebSocket**
- **Spring Validation**
- **Spring Data JPA**
- **Flyway**
- **Jackson**

Optional but recommended:

- **MapStruct**
- **Lombok**
- **Spring Security** only if protected/private rooms are added

### Rationale

The backend must own authoritative rules for tile draws, rack state, placement validity, room pause state, MIXMO resolution, and winner detection. The domain is rule-heavy and benefits from clear service boundaries and transactional integrity.

## 4.3 Database

Use:

- **PostgreSQL**

### Rationale

PostgreSQL is required for durable room state, reconnectability, event history, active game persistence, and final game outcomes. While some active session data may be cached in memory, PostgreSQL should remain the durable source of truth.

## 4.4 Realtime Transport

Use:

- **REST** for room lifecycle and bootstrap
- **WebSocket** for live game commands and state broadcasts

### Rationale

MIXMO and Bandit both affect all players immediately. The UI requirements also assume immediate, shared state transitions for pause/resume, Bandit visibility, and MIXMO outcomes.

---

## 5. Architecture

## 5.1 Architecture Style

Use a **modular monolith**.

This MVP does not require microservices. The preferred design is a single deployable backend with clean module boundaries around core game domains.

## 5.2 Architectural Principles

- backend is the single source of truth for gameplay state
- frontend owns presentation and interaction state only
- all room mutations must be serialized per room
- game state transitions must be deterministic
- the board model must support unbounded growth
- state snapshots must be reloadable after reconnect or restart

## 5.3 Suggested Backend Module Boundaries

```text
com.mixmo
  config
  common
  room
  player
  tile
  rack
  board
  placement
  suggestion
  mixmo
  bandit
  realtime
  persistence
```

## 5.4 Suggested Frontend Structure

```text
src/
  app/
  router/
  views/
  components/
    board/
    rack/
    candidate-word/
    placement/
    suggestions/
    mixmo/
    bandit/
    room/
    common/
  stores/
  services/
    api/
    realtime/
  models/
  utils/
  styles/
```

---

## 6. Core Gameplay Requirements

## 6.1 Tile Distribution

The implementation must use the exact official tile distribution:

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

The backend must initialize, shuffle, and draw from this tile bag deterministically.

## 6.2 Game Start

At game start:

- a room is in `WAITING`
- once started, the shared bag is initialized and shuffled
- each player receives **6 tiles**
- each player starts with an empty personal board
- room status moves to `ACTIVE`

## 6.3 Player Board Rules

Each player has a personal board with these properties:

- effectively **unbounded**
- words can be placed only horizontally or vertically
- diagonal placement is not allowed
- cells may intersect if resolved letters match
- board navigation must support growth in all directions

The UI requirements explicitly define the board as unbounded and require the interface to prioritize the relevant local play area rather than imply hard limits.

## 6.4 Word Composition and Placement Model

The product interaction model is mandatory:

1. player composes a candidate word from rack tiles
2. player selects a start cell
3. player sees an immediate whole-word preview
4. player may switch orientation
5. player confirms placement

The player must **not** place letters tile-by-tile. The frontend should expose explicit compose mode and place mode.

## 6.5 Placement Validation Rules

The backend must validate placement before commit.

Validation must include at least:

- anchor position present
- orientation present
- horizontal or vertical only
- no conflicting letters on overlap
- overlap allowed only when letters match
- used tiles must belong to the player rack
- resolved joker/bandit letter must be valid
- for non-first placements, placement must connect to the player’s current board cluster
- preview response must indicate whether placement is valid

The frontend must receive lightweight invalid reasons suitable for immediate correction, such as:

- `collision_with_different_letter`
- `disconnected_placement`
- `invalid_bandit_letter`
- `invalid_tile_usage`
- `missing_anchor`
- `empty_candidate_word`

The UI requirements explicitly require live preview, invalid feedback, and understandable correction paths.

## 6.6 Placement Suggestions

Placement suggestions are mandatory in MVP.

Requirements:

- suggestions are computed when a candidate word is ready
- suggestions must surface **multiple valid placements simultaneously**
- selected suggestion must update preview immediately
- suggestions must be understandable and visually linked to the board preview
- mobile should show up to **3 suggestions**
- desktop should show up to **5 suggestions**

These requirements come directly from the uploaded UI spec.

### Recommendation

Suggestion generation should be **server-side** so that validation and suggestion logic remain consistent across clients.

## 6.7 Classic Joker Rules

Classic Joker behavior:

- can represent any letter `A-Z`
- replacement letter is fixed once placement is confirmed
- if it is used at a crossing, the resolved letter must be consistent for both words

## 6.8 Bandit Rules

Bandit behavior:

- can represent only `K`, `W`, `X`, `Y`, or `Z`
- when Bandit is used, the player must select a theme from a predefined list
- while Bandit theme selection is pending, the game is globally paused
- after theme selection, the game resumes
- the selected Bandit theme remains visible for the rest of the game

Theme options for MVP:

- Animals
- Food & Drinks
- Countries & Cities
- Nature
- Jobs / Professions
- Sports
- Technology
- Movies & Entertainment
- Transportation
- Household Objects

These are explicitly required by the UI spec.

## 6.9 MIXMO Rules

MIXMO is a special gated action.

Rules:

- a player may trigger MIXMO only when their current rack is empty
- triggering MIXMO causes **all players** to draw **2 tiles**
- simultaneous MIXMO calls do **not** stack
- if no tiles remain and the triggering player has placed all current letters, the final MIXMO ends the game and the triggering player wins

The UI requirements explicitly require the Mixmo button to be disabled until all letters are placed and require the outcome to clearly communicate shared draw or final win.

## 6.10 Game End Rules

The backend must end the game when:

1. the tile reserve is empty
2. a player with an empty rack triggers final MIXMO
3. room status becomes `FINISHED`
4. winner is recorded
5. all clients receive immediate finish notification

---

## 7. Frontend Requirements

## 7.1 UX Model

The frontend must implement these explicit modes:

- `idle`
- `composing`
- `placing`
- `banditTheme`
- `confirmed`

These modes align with the UI state model in the uploaded requirements.

## 7.2 Mobile-First Rules

The frontend must preserve these constraints:

- mobile portrait is the primary target
- no mobile drag-and-drop-first interaction
- no mobile keyboard-based word entry
- tap-based rack interaction
- board remains visible during play
- candidate word strip is central to gameplay
- place mode visually emphasizes the board
- Bandit selection should use a bottom sheet on mobile

These constraints are explicitly defined in the UI document.

## 7.3 Board Interaction Requirements

The board must support:

- free pan
- pinch-to-zoom on touch
- wheel/trackpad zoom on desktop
- tap-to-select anchor cell
- gesture thresholding so taps win over tiny drags
- focus tools:
  - recenter on current placement
  - recenter on active cluster
  - fit suggestions

These behaviors are explicitly required by the UI handoff.

## 7.4 Board Rendering Strategy

The board must be modeled and rendered as an **unbounded sparse grid**.

Recommendation:

- frontend renders from sparse `BoardCell[]`
- viewport state contains `centerX`, `centerY`, `zoom`
- do not build a giant fixed DOM grid
- use CSS transforms or canvas-like rendering patterns for efficient viewport movement

## 7.5 Responsive Layout Requirements

Breakpoints:

- mobile portrait: `< 768px`
- tablet / small landscape: `768px - 1023px`
- desktop: `>= 1024px`
- wide desktop: `>= 1440px`

The mobile portrait layout should keep the following visible with minimal scrolling:

1. compact header
2. board area
3. candidate word strip
4. rack and primary actions

These layout rules are stated in the UI spec.

## 7.6 Frontend State Domains

Suggested Pinia stores:

- `roomStore`
- `boardStore`
- `rackStore`
- `actionStore`
- `suggestionStore`
- `themeStore`
- `connectionStore`

## 7.7 UI Action Hierarchy

The action area must clearly separate:

- **Primary:** Validate / Confirm placement
- **Secondary:** Clear / Cancel, Orientation switch
- **Special:** MIXMO

MIXMO must remain visible but isolated from Validate to avoid accidental activation.

---

## 8. Backend Requirements

## 8.1 Authoritative Rules

The backend is authoritative for:

- room state
- tile bag and draws
- rack state
- placement validation
- suggestion validity
- Bandit pause state
- theme persistence
- MIXMO eligibility and resolution
- winner detection

## 8.2 Concurrency Requirements

All room-mutating commands must be serialized at room level.

Recommended approach:

- one logical lock or serialized command queue per room
- optimistic room version checks for persistence
- idempotent resolution for simultaneous MIXMO events

## 8.3 Persistence Strategy

Use PostgreSQL as source of truth with two storage layers:

1. **current-state tables** for fast reads
2. **event log** for audit/debug/rebuild support

Additionally:

- persist a room snapshot after every committed gameplay action
- allow reconnect and room state reconstruction from current snapshot

## 8.4 In-Memory Cache

Use in-memory caching only for active sessions, such as:

- active room snapshot cache
- transient suggestion results
- websocket session mapping

Persistent correctness must not depend on memory-only state.

---

## 9. Domain Model Requirements

## 9.1 Core Entities

### GameRoom

Fields:

- `id`
- `code`
- `status` (`WAITING`, `ACTIVE`, `PAUSED`, `FINISHED`)
- `createdAt`
- `startedAt`
- `finishedAt`
- `currentBanditTheme`
- `pauseReason`
- `winnerPlayerId`
- `version`

### Player

Fields:

- `id`
- `roomId`
- `name`
- `seatOrder`
- `connected`
- `createdAt`

### Tile

Fields:

- `id`
- `kind` (`NORMAL`, `JOKER`, `BANDIT`)
- `faceValue`
- `assignedLetter`
- `ownerPlayerId`
- `location` (`BAG`, `RACK`, `BOARD`)

### RackEntry

Fields:

- `id`
- `playerId`
- `tileId`
- `position`

### BoardCell

Fields:

- `id`
- `playerId`
- `x`
- `y`
- `tileId`
- `resolvedLetter`

Constraint:

- unique `(player_id, x, y)`

### GameEvent

Fields:

- `id`
- `roomId`
- `eventType`
- `payloadJson`
- `createdAt`
- `sequenceNumber`

### GameSnapshot

Fields:

- `roomId`
- `payloadJson`
- `updatedAt`
- `version`

### BanditThemeSelection

Fields:

- `id`
- `roomId`
- `triggeringPlayerId`
- `selectedTheme`
- `status`
- `createdAt`
- `resolvedAt`

---

## 10. Service Responsibilities

### RoomService

- create room
- join room
- start room
- fetch room state
- reconnect state
- finish room

### TileBagService

- initialize bag
- shuffle bag
- draw N tiles
- detect reserve exhaustion

### RackService

- initial draw of 6
- post-MIXMO draw of 2
- rack reconciliation after placement
- empty-rack detection
- MIXMO eligibility check

### BoardService

- read sparse board
- place tiles
- query cluster bounds
- compute placement overlays

### PlacementService

- validate preview
- resolve intersections
- detect conflicts
- enforce joker and bandit letter rules
- commit valid placement

### SuggestionService

- generate valid placements for candidate word
- rank suggestions
- return top suggestions per device context

### MixmoService

- validate MIXMO eligibility
- atomically resolve room-wide draw
- handle final MIXMO
- set winner and finish room

### BanditService

- transition room to `PAUSED`
- validate selected theme
- persist theme
- resume room

### GameStateAssembler

- build frontend-facing room snapshot DTO
- emit websocket update payloads

---

## 11. API Requirements

## 11.1 REST Endpoints

Required:

```text
POST   /api/rooms
POST   /api/rooms/{roomId}/join
POST   /api/rooms/{roomId}/start
GET    /api/rooms/{roomId}
GET    /api/rooms/{roomId}/state
POST   /api/rooms/{roomId}/reconnect
```

Optional:

```text
POST   /api/rooms/{roomId}/leave
```

## 11.2 WebSocket Commands

Client -> server:

- `game.sync.request`
- `placement.preview.request`
- `placement.suggestions.request`
- `placement.confirm.request`
- `mixmo.trigger`
- `bandit.theme.select`

## 11.3 WebSocket Events

Server -> client:

- `game.state.updated`
- `placement.preview.result`
- `placement.suggestions.result`
- `mixmo.resolved`
- `game.paused`
- `game.resumed`
- `bandit.theme.selected`
- `game.finished`
- `error`

---

## 12. DTO Requirements

The backend should expose explicit DTOs, not entities, to the frontend.

Suggested UI-facing data shape should include at least:

- room status
- player id
- rack tiles
- sparse board cells
- candidate word
- placement preview
- placement suggestions
- theme state
- action availability
- viewport state
- winner id if applicable

The uploaded UI requirements already define a frontend-oriented shape that can guide DTO design, including `RackTile`, `BoardCell`, `CandidateWord`, `PlacementSuggestion`, `PlacementPreview`, `ThemeState`, `ActionState`, and `GameUiState`.

---

## 13. Non-Functional Requirements

## 13.1 Performance

Target nominal behavior:

- command acknowledgment under 200 ms in dev/staging-like conditions
- websocket propagation feels immediate to players
- placement preview and suggestion refresh is low-latency

## 13.2 Reliability

The system must support:

- browser refresh recovery
- reconnect to active game
- fetch latest room snapshot
- consistent room state after backend restart

## 13.3 Testability

The codebase must be structured for:

- unit tests of game rules
- integration tests of REST, websocket, and persistence
- frontend component/store tests for critical UX flows

## 13.4 Extensibility

The code must leave room for later integration of:

- dictionary validation
- semantic theme validation
- auth/private rooms
- rankings and profiles

---

## 14. Testing Requirements

## 14.1 Backend Unit Tests

Must cover:

- tile bag distribution count
- initial draw of 6 tiles
- MIXMO allowed only when rack empty
- MIXMO draws 2 for all players
- final MIXMO ends game when reserve empty
- classic joker accepts any letter
- Bandit accepts only K/W/X/Y/Z
- overlap conflict rejection
- valid overlap acceptance
- disconnected placement rejection
- paused room rejects gameplay actions except Bandit selection

## 14.2 Backend Integration Tests

Must cover:

- create/join/start flow
- websocket state update after placement
- websocket update after MIXMO
- websocket pause/resume around Bandit
- reconnect returns latest state

## 14.3 Frontend Tests

Must cover:

- rack tile selection builds candidate word
- place mode changes emphasis correctly
- suggestion selection updates preview
- invalid placement shows reason
- MIXMO disabled/enabled states render correctly
- Bandit sheet blocks normal actions
- persisted Bandit theme remains visible after selection

---

## 15. Deployment Requirements

## 15.1 Local Development

The repository should include:

- frontend app
- backend app
- PostgreSQL service
- Docker Compose for local bootstrapping

## 15.2 Expected Services

- `frontend`
- `backend`
- `postgres`

## 15.3 Reverse Proxy

Optional for MVP locally, but production deployment must support websocket upgrade correctly.

---

## 16. Recommended Implementation Order

1. scaffold Spring Boot backend and Vue frontend
2. add PostgreSQL + Flyway
3. implement room lifecycle and player join
4. implement tile bag and initial draw
5. implement sparse board persistence
6. implement preview validation
7. implement placement confirmation
8. implement websocket state sync
9. implement MIXMO
10. implement Bandit pause/theme flow
11. implement suggestion engine
12. harden reconnect and snapshot loading
13. add tests and cleanup

---

## 17. Final Engineering Decisions

The definitive MVP stack is:

- **Vue 3 + TypeScript + Vite + Pinia + Tailwind** on the frontend
- **Spring Boot 3 + Java 21 + Spring WebSocket + Spring Data JPA** on the backend
- **PostgreSQL** for persistence
- **REST + WebSocket** for transport
- **modular monolith** architecture
- **backend-authoritative game engine**
- **sparse unbounded board model**
- **persistent snapshots + event log**

The most important product-driven implementation constraints are:

- compose first / place second
- mobile-first touch UX
- unbounded board
- multiple placement suggestions in MVP
- MIXMO as a gated shared action
- Bandit theme selection as a global pause flow
- persistent Bandit theme visibility after selection



---

# 18. Clarifications and Authoritative Decisions (2026-03-13)

This section supersedes any earlier ambiguous wording in this document.

## 18.1 Authoritative game-rule clarifications

- **Classic Joker** can replace **any letter A-Z**.
- **Bandit** can replace **only K, W, X, Y, or Z**.
- **Theme selection is required only for Bandit**, never for the classic Joker.
- When a joker or Bandit is used at a crossing, the resolved letter must be identical for both crossing words.
- The **first placement must pass through the conceptual origin `(0,0)`**.
- After the first placement, **disconnected islands are forbidden**. Every committed placement must connect to the player's existing board cluster.
- A placement **may cross multiple existing words** as long as all overlaps are letter-consistent.
- Simultaneous `MIXMO` calls **do not stack**; only one shared draw of 2 tiles is resolved.
- If players decide collectively to unblock the game, the system may support a **manual shared MIXMO** that also draws 2 tiles for every player.
- On reconnect or page refresh, the client must receive the full authoritative snapshot, including **all tiles the player should currently hold** after any previously resolved draw events.

## 18.2 Suggestion ranking rule

Suggestion ranking is **only based on victory count**.

Interpretation for MVP implementation:

- each suggestion payload must include a numeric `victoryCount`
- higher `victoryCount` ranks first
- tie-breakers may be deterministic but must not change business meaning; recommended order is:
  1. higher `victoryCount`
  2. fewer newly occupied cells
  3. lexicographic `(startX, startY, orientation)`

## 18.3 Placement validation additions

The backend must additionally validate:

- first placement crosses conceptual origin `(0,0)`
- later placements remain connected to the current board cluster
- multiple overlaps are allowed when every overlapped letter matches
- the committed board remains a single connected component for that player

Add the following invalid reasons:

- `first_word_must_cross_origin`
- `disconnected_island`

## 18.4 Bandit and pause behavior

Bandit draw and usage are distinct moments:

- drawing a Bandit tile does **not** immediately pause the room
- the room pauses only when a player attempts to **use** the Bandit in a placement that requires theme selection
- while the Bandit theme is pending, gameplay commands are rejected except theme-selection and sync/reconnect
- once the theme is selected, the game resumes and the selected theme remains visible for the rest of the match

## 18.5 Room/bootstrap contract details

The REST layer is responsible for room lifecycle and authoritative bootstrap.

### 18.5.1 REST endpoints

```text
POST   /api/rooms
POST   /api/rooms/{roomId}/join
POST   /api/rooms/{roomId}/start
GET    /api/rooms/{roomId}
GET    /api/rooms/{roomId}/state
POST   /api/rooms/{roomId}/reconnect
POST   /api/rooms/{roomId}/mixmo/manual
```

### 18.5.2 Common response envelope

```json
{
  "requestId": "uuid",
  "serverTime": "2026-03-13T10:15:30Z",
  "roomVersion": 12,
  "data": {}
}
```

### 18.5.3 Create room

**Request**

```json
{
  "playerName": "Jean-Luc",
  "deviceType": "mobile"
}
```

**Response**

```json
{
  "requestId": "uuid",
  "serverTime": "2026-03-13T10:15:30Z",
  "roomVersion": 0,
  "data": {
    "room": {
      "roomId": "r_123",
      "roomCode": "ABCD12",
      "status": "WAITING"
    },
    "self": {
      "playerId": "p_1",
      "playerName": "Jean-Luc",
      "seatOrder": 1,
      "sessionToken": "opaque-session-token"
    }
  }
}
```

### 18.5.4 Join room

**Request**

```json
{
  "playerName": "Alice",
  "sessionToken": null,
  "deviceType": "mobile"
}
```

**Response**

```json
{
  "requestId": "uuid",
  "serverTime": "2026-03-13T10:16:00Z",
  "roomVersion": 1,
  "data": {
    "room": {
      "roomId": "r_123",
      "roomCode": "ABCD12",
      "status": "WAITING",
      "players": [
        { "playerId": "p_1", "playerName": "Jean-Luc", "seatOrder": 1, "connected": true },
        { "playerId": "p_2", "playerName": "Alice", "seatOrder": 2, "connected": true }
      ]
    },
    "self": {
      "playerId": "p_2",
      "playerName": "Alice",
      "seatOrder": 2,
      "sessionToken": "opaque-session-token"
    }
  }
}
```

### 18.5.5 Start room

**Request**

```json
{
  "requestedByPlayerId": "p_1"
}
```

**Response**

```json
{
  "requestId": "uuid",
  "serverTime": "2026-03-13T10:16:15Z",
  "roomVersion": 2,
  "data": {
    "gameSnapshot": {
      "roomId": "r_123",
      "status": "ACTIVE",
      "bagRemaining": 108,
      "pauseReason": null,
      "currentBanditTheme": null,
      "players": [
        {
          "playerId": "p_1",
          "playerName": "Jean-Luc",
          "rack": [
            { "tileId": "t1", "kind": "NORMAL", "face": "A" }
          ],
          "board": []
        }
      ]
    }
  }
}
```

### 18.5.6 Get room state

Returns the full authoritative room snapshot for initial page load.

**Response `data.gameSnapshot` must include at least:**

- `roomId`, `roomCode`, `status`, `roomVersion`
- `bagRemaining`, `pauseReason`, `currentBanditTheme`, `winnerPlayerId`
- `selfPlayerId`
- `players[]` with `playerId`, `playerName`, `seatOrder`, `connected`, `rackCount`
- `selfRack[]` with tile details visible only to the owner
- `selfBoard[]` as sparse cells `{x,y,resolvedLetter,tileId,tileKind}`
- `candidateWordState` if the client asked to restore draft state locally
- `actionState` with booleans such as `canTriggerMixmo`, `canConfirmPlacement`, `canSelectBanditTheme`
- `lastResolvedEventSequence`

### 18.5.7 Reconnect

Reconnect is a bootstrap endpoint, not a diff endpoint.

**Request**

```json
{
  "playerId": "p_1",
  "sessionToken": "opaque-session-token",
  "lastKnownRoomVersion": 9,
  "lastKnownEventSequence": 41
}
```

**Response**

```json
{
  "requestId": "uuid",
  "serverTime": "2026-03-13T10:18:00Z",
  "roomVersion": 12,
  "data": {
    "resyncRequired": true,
    "gameSnapshot": {
      "roomId": "r_123",
      "status": "ACTIVE",
      "bagRemaining": 74,
      "currentBanditTheme": "Animals",
      "selfRack": [
        { "tileId": "t7", "kind": "NORMAL", "face": "R" },
        { "tileId": "t8", "kind": "NORMAL", "face": "S" }
      ],
      "selfBoard": [],
      "lastResolvedEventSequence": 44
    },
    "missedEventWindow": {
      "fromExclusive": 41,
      "toInclusive": 44
    }
  }
}
```

Reconnect rule:

- the backend must reconstruct the latest correct rack from the authoritative snapshot
- if a shared draw already happened while the player was disconnected, the response must include those drawn tiles
- the frontend must discard stale local rack data and replace it with `selfRack`

## 18.6 WebSocket protocol details

Use one authenticated WebSocket session per player per tab.

Recommended destination shape:

```text
/ws
```

Every client command must include:

```json
{
  "type": "placement.confirm.request",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {}
}
```

### 18.6.1 Client commands

#### `game.sync.request`

Used after socket open to ask for the latest authoritative snapshot.

```json
{
  "type": "game.sync.request",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {
    "lastKnownEventSequence": 44
  }
}
```

#### `placement.preview.request`

```json
{
  "type": "placement.preview.request",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {
    "candidateWord": "RADAR",
    "orientation": "HORIZONTAL",
    "start": { "x": 0, "y": 0 },
    "tiles": [
      { "tileId": "t1", "resolvedLetter": "R" },
      { "tileId": "t2", "resolvedLetter": "A" }
    ]
  }
}
```

#### `placement.suggestions.request`

```json
{
  "type": "placement.suggestions.request",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {
    "candidateWord": "RADAR",
    "deviceType": "mobile"
  }
}
```

#### `placement.confirm.request`

```json
{
  "type": "placement.confirm.request",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {
    "candidateWord": "RADAR",
    "orientation": "HORIZONTAL",
    "start": { "x": 0, "y": 0 },
    "tiles": [
      { "tileId": "t1", "resolvedLetter": "R" },
      { "tileId": "t2", "resolvedLetter": "A" }
    ],
    "banditTheme": null
  }
}
```

#### `mixmo.trigger`

```json
{
  "type": "mixmo.trigger",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 12,
  "payload": {}
}
```

#### `bandit.theme.select`

```json
{
  "type": "bandit.theme.select",
  "requestId": "uuid",
  "roomId": "r_123",
  "playerId": "p_1",
  "expectedRoomVersion": 13,
  "payload": {
    "theme": "Animals"
  }
}
```

### 18.6.2 Server acknowledgments and events

#### Command acknowledgment

Every accepted command must first emit an ack to the requesting client.

```json
{
  "type": "command.ack",
  "requestId": "uuid",
  "roomId": "r_123",
  "roomVersion": 13,
  "payload": {
    "accepted": true
  }
}
```

#### `placement.preview.result`

```json
{
  "type": "placement.preview.result",
  "requestId": "uuid",
  "roomId": "r_123",
  "roomVersion": 12,
  "payload": {
    "valid": true,
    "invalidReason": null,
    "previewCells": [
      { "x": 0, "y": 0, "letter": "R", "state": "NEW" },
      { "x": 1, "y": 0, "letter": "A", "state": "OVERLAP_MATCH" }
    ],
    "crossesOrigin": true,
    "connectedToCluster": true,
    "usesBandit": false
  }
}
```

#### `placement.suggestions.result`

```json
{
  "type": "placement.suggestions.result",
  "requestId": "uuid",
  "roomId": "r_123",
  "roomVersion": 12,
  "payload": {
    "suggestions": [
      {
        "suggestionId": "s1",
        "start": { "x": 0, "y": 0 },
        "orientation": "HORIZONTAL",
        "victoryCount": 5,
        "previewCells": [
          { "x": 0, "y": 0, "letter": "R" }
        ]
      }
    ]
  }
}
```

#### `game.state.updated`

Broadcast after any committed gameplay mutation.

```json
{
  "type": "game.state.updated",
  "roomId": "r_123",
  "roomVersion": 13,
  "payload": {
    "status": "ACTIVE",
    "bagRemaining": 72,
    "currentBanditTheme": null,
    "players": [
      { "playerId": "p_1", "rackCount": 0, "boardCellCount": 14, "connected": true }
    ]
  }
}
```

#### `mixmo.resolved`

```json
{
  "type": "mixmo.resolved",
  "roomId": "r_123",
  "roomVersion": 14,
  "payload": {
    "triggeredByPlayerId": "p_1",
    "drawCountPerPlayer": 2,
    "bagRemaining": 66,
    "finalMixmo": false
  }
}
```

#### `game.paused`

```json
{
  "type": "game.paused",
  "roomId": "r_123",
  "roomVersion": 15,
  "payload": {
    "reason": "BANDIT_THEME_REQUIRED",
    "triggeringPlayerId": "p_2"
  }
}
```

#### `bandit.theme.selected`

```json
{
  "type": "bandit.theme.selected",
  "roomId": "r_123",
  "roomVersion": 16,
  "payload": {
    "selectedByPlayerId": "p_2",
    "theme": "Animals"
  }
}
```

#### `game.resumed`

```json
{
  "type": "game.resumed",
  "roomId": "r_123",
  "roomVersion": 16,
  "payload": {
    "reason": "BANDIT_THEME_SELECTED"
  }
}
```

#### `game.finished`

```json
{
  "type": "game.finished",
  "roomId": "r_123",
  "roomVersion": 20,
  "payload": {
    "winnerPlayerId": "p_1",
    "finalMixmo": true,
    "bagRemaining": 0,
    "currentBanditTheme": "Animals"
  }
}
```

#### `error`

```json
{
  "type": "error",
  "requestId": "uuid",
  "roomId": "r_123",
  "roomVersion": 12,
  "payload": {
    "code": "ROOM_PAUSED",
    "message": "Bandit theme selection is required before gameplay can continue.",
    "retryable": false
  }
}
```

### 18.6.3 Recommended websocket error codes

- `ROOM_NOT_FOUND`
- `ROOM_NOT_ACTIVE`
- `ROOM_PAUSED`
- `ROOM_VERSION_MISMATCH`
- `INVALID_TILE_USAGE`
- `EMPTY_CANDIDATE_WORD`
- `MISSING_ANCHOR`
- `INVALID_ORIENTATION`
- `FIRST_WORD_MUST_CROSS_ORIGIN`
- `DISCONNECTED_PLACEMENT`
- `COLLISION_WITH_DIFFERENT_LETTER`
- `INVALID_BANDIT_LETTER`
- `MIXMO_NOT_ALLOWED`
- `NOT_BANDIT_TRIGGERING_PLAYER`
- `INVALID_THEME`
- `UNAUTHORIZED_SESSION`

## 18.7 Coherence check against official rules

After applying the above clarifications, the MVP technical requirements are coherent with the supplied official rules, with the following explicit MVP simplifications kept intentionally:

- dictionary validation is out of MVP
- semantic validation that the final thematic word truly matches the selected theme is out of MVP
- the UI may expose a controlled theme list for MVP instead of free-text themes
- collective manual unblock may be implemented via a dedicated action or moderator/host action, but its semantics remain identical to shared MIXMO draw of 2
