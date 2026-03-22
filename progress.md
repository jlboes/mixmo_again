Original prompt: Build the MIXMO MVP end-to-end from `TECHNICAL_REQUIREMENTS_UPDATED.md`, `mixo_ui_requirements_UPDATED.md`, and `MIXMO_CODEX_IMPLEMENTATION_HANDOFF.md`, using the handoff as authority while preserving stricter coherent technical/UI rules. Deliver a runnable full-stack MVP with Spring Boot + PostgreSQL backend, Vue 3 + TypeScript frontend, Docker Compose, realtime gameplay, authoritative rule enforcement, reconnect, suggestions, Bandit flow, Mixmo flow, tests, and README.

## 2026-03-13

- Read and reconciled all three source-of-truth docs.
- Confirmed the repo started empty except for documentation, so the implementation is greenfield.
- Chosen architecture: Spring Boot modular monolith backend, native WebSocket JSON protocol, Vue 3 + Pinia frontend, PostgreSQL persistence with normalized current-state tables plus snapshot/event log.
- Open question noted for later decisions file: the docs mention board reorganization between placements, but the authoritative confirm contract is additive-only. The current plan is to implement the contract-defined additive placement model and record the rationale.
- Implemented the backend app, PostgreSQL/Flyway schema, room lifecycle, websocket protocol, authoritative rule services, reconnect flow, and Dockerfiles.
- Implemented the Vue frontend with a waiting room, mobile-first game surface, board viewport, rack composition, suggestions, Mixmo button states, wildcard selection, and Bandit theme sheet.
- Verified: `mvn test`, `npm test`, `npm run build`, and `docker-compose config`.
- TODO for next iteration: add a richer board-assisted composer for overlap-heavy words that require interleaving existing board letters into the final whole-word payload.

## 2026-03-18

- Added a debug-gated demo room flow: backend `mixmo.debug.demo-room.enabled` and frontend `VITE_DEBUG_DEMO_ROOM` now expose a one-click seeded room for testing.
- Seeded the demo room with an active crossword-style board containing intersecting horizontal and vertical words, then dealt a live rack on top.
- Removed the explicit Place Mode step from the UI and store flow so word building stays in a single composing path.
- Reworked drafting so the candidate word can mix rack tiles with letters already on the board, infer orientation from reused letters, and support extending from the front or back.
- Improved board/rack feedback with clearer reused-letter highlighting, anchor visualization, used-rack states, and live composer guidance text.
- Verified: `npm run build`, `npm test -- --run`, and `mvn -q test`.
- TODO for next iteration: add a lightweight end-to-end browser check around the demo-room flow so UI regressions are caught without relying only on unit/integration tests.

## 2026-03-19

- Verified the stale-game timer logic in store tests, then traced a live failure mode to the auto-Mixmo path clearing stale state even when validation rejects the board.
- Updated stale auto-triggering so timer-driven Mixmo attempts no longer count as player activity; stale state now stays visible if the auto attempt fails validation or the validator errors.
- Replaced the demo-room seed board with validator-valid French crossings (`AIMER`, `ANCRE`, `AMOUR`, `MICRO`, `RIEUR`) while preserving the same sparse 19-cell layout and bag count.
- Added tests for stale auto-trigger invalid-grid behavior, updated backend demo-room assertions, and added a Python lexicon guard for the demo seed words.
- Refined the active play screen into a denser board-first layout: compact header, dominant board area, and a single unified controls panel that now contains suggestions, word builder, actions, rack, and players.
- Reworked board rendering to remove the fragile line grid, introduce softer dot/textured guides plus origin/anchor cues, and derive tile frames from shared viewport metrics so zoom/pan feels more precise.
- Embedded the candidate builder inside the unified controls surface, added a view-level layout test, expanded board component coverage, and verified `npm test` plus `npm run build`.
- Removed the remaining suggestion UI affordances from the play screen: no suggestions header/copy in the controls panel and no placement suggestion buttons on the board.
- Reorganized the action area into a single sequential workflow block: build (builder + rack), adjust (placement controls), confirm (primary action then MIXMO), then status/messages below the actions.
- Tightened the mobile draft flow further: orientation now sits directly under the drafted letters inside the builder block, `Place Word` follows immediately after, the rack moves below the core actions, and helper copy is quieter on small screens.
- Residual limitation: true browser-level idle verification is still blocked in this environment because Playwright/Chromium launch is denied by the sandbox on this machine.
- Residual limitation remains for this UI pass as well: a direct Playwright browser check against the local Vite server still fails on this machine with Chromium Mach port permission errors, so manual browser verification is still pending outside the sandbox.
