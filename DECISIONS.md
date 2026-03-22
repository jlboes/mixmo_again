# MIXMO MVP Decisions

This file records implementation choices where the docs leave room for interpretation.

## Active decisions

1. The backend uses a native JSON WebSocket envelope on `/ws` instead of STOMP because the handoff defines a command/event protocol directly and does not require broker semantics.
2. The authoritative game state is persisted in normalized tables (`room`, `player`, `tile`, `rack_entry`, `board_cell`) and mirrored into `game_snapshot` plus `game_event` after each committed mutation.
3. Room mutations are serialized with a JVM room lock keyed by `roomId` and persisted with a monotonically increasing `roomVersion`.
4. Suggestion `victoryCount` is implemented as a deterministic overlap-oriented heuristic: the number of existing board cells reused by the candidate placement. The docs require ranking by `victoryCount` but do not define the formula.
5. The MVP implements additive word placement through the authoritative `placement.confirm.request` contract and also exposes an authoritative `board.tiles.return.request` workflow so players can move confirmed board tiles back to the rack without relying on local-only state.
6. When MIXMO resolves with fewer than `2 * playerCount` tiles left in the reserve, the backend performs a best-effort round-robin draw until the reserve is exhausted. The docs define the empty-reserve final MIXMO case, but not partial depletion during a shared draw.
7. The backend validation supports overlap-heavy placements when the client submits the full final word. The current mobile-first UI composes candidate letters from the rack plus wildcard letter pickers, so advanced overlap insertion that would require board-assisted word assembly remains a follow-up UX enhancement.
