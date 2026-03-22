# MIXMO MVP

MIXMO is a full-stack multiplayer word-game MVP built from the repository handoff documents. The project delivers:

- Spring Boot 3 backend with authoritative room/game rules
- FastAPI validator service for French lexical normalization and Morphalou-based dictionary checks
- PostgreSQL persistence with normalized state tables, event log, and snapshot mirror
- Native WebSocket JSON protocol for gameplay commands and realtime state updates
- Vue 3 + TypeScript + Pinia + Vue Router + Tailwind frontend
- Local Docker Compose setup for `postgres`, `backend`, `python_api`, and `frontend`

## Repository Layout

- `backend/`: Spring Boot application, Flyway migrations, tests
- `frontend/`: Vite Vue application, Pinia state, realtime client, tests
- `python_api/`: FastAPI French word validation service, lexicon builder, tests
- `docker-compose.yml`: local multi-service startup
- `DECISIONS.md`: recorded implementation choices where the docs were ambiguous

## Local Development

### Backend

Requirements:

- Java 21+
- Maven 3.9+
- PostgreSQL 16+ running on `localhost:5432` with database `mixmo`, user `mixmo`, password `mixmo`
- Python validation API running on `http://localhost:8000` or another URL passed through `MIXMO_VALIDATOR_BASE_URL`

Run:

```bash
cd backend
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

### Python Validator

Requirements:

- Python 3.12+

Run:

```bash
cd python_api
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Validator URL: `http://localhost:8000`

### Frontend

Requirements:

- Node 23+
- npm 10+

Run:

```bash
cd frontend
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

### Docker Compose

Use the available Compose binary in this environment:

```bash
docker-compose up --build
```

Services:

- Frontend: `http://localhost:4173`
- Backend: `http://localhost:8080`
- Python validator: `http://localhost:8000`
- PostgreSQL: `localhost:5432`

### Docker Compose Development

For a bind-mounted development stack that avoids rebuilding containers on every code change, use:

```bash
docker-compose -f docker-compose.dev.yml up
```

Development behavior:

- Frontend runs `npm run dev` on `http://localhost:5173`
- Backend runs `mvn spring-boot:run` with the local `backend/` folder mounted into the container
- Python validator runs `uvicorn ... --reload` with source directories mounted into the container
- PostgreSQL still persists data in the `postgres-data` Docker volume
- Browser testing remains opt-in through the Compose `browser` profile

Notes:

- The frontend container keeps `node_modules` in a Docker volume so the bind mount does not overwrite installed packages
- The backend container keeps the Maven cache in a Docker volume to speed up repeated starts

### Docker Compose Browser Testing

For a Codex-controlled browser in Docker, start the dev stack with the optional browser profile:

```bash
docker-compose -f docker-compose.dev.yml --profile browser up --build
```

This adds:

- Host frontend for manual use: `http://localhost:5173`
- Browser-testing frontend for remote browsers: `http://localhost:5174`
- Playwright server websocket endpoint: `ws://127.0.0.1:3000/`

The browser-testing path is separate from the manual host path. Humans should keep using `http://localhost:5173`. A remote Playwright client should connect to `ws://127.0.0.1:3000/`, then browse `http://frontend-browser:5174` from inside the Docker network so the app can reach `backend:8080` and `ws://backend:8080/ws`.

Playwright clients that connect to the remote server must match the server's major and minor Playwright version. This image uses Playwright `1.58.2`.

If local `npx` runs into npm cache permission issues, you can use a disposable cache for the host-side client:

```bash
export npm_config_cache=/tmp/codex-npm-cache
```

## Core API Surface

REST:

- `POST /api/rooms`
- `POST /api/rooms/{roomId}/join`
- `POST /api/rooms/{roomId}/start`
- `GET /api/rooms/{roomId}`
- `GET /api/rooms/{roomId}/state`
- `POST /api/rooms/{roomId}/reconnect`
- `POST /api/rooms/{roomId}/mixmo/validate`
- `POST /api/rooms/{roomId}/mixmo/manual`

WebSocket:

- Endpoint: `/ws?roomId=...&playerId=...&sessionToken=...`
- Commands:
  - `game.sync.request`
  - `placement.preview.request`
  - `placement.suggestions.request`
  - `placement.confirm.request`
  - `mixmo.trigger`
  - `bandit.theme.select`

## Gameplay Coverage

Implemented rules include:

- official 120-tile bag and initial draw of 6
- sparse unbounded personal board
- first placement crosses origin
- later placements stay connected
- overlap-match enforcement
- Classic Joker wildcard resolution
- Bandit restricted-letter validation and pause/theme flow
- server-authoritative Mixmo grid extraction, deduplicated batch dictionary lookup, per-letter validity aggregation, and guarded MIXMO dispatch
- MIXMO empty-rack gating, shared draw, and final win condition
- reconnect snapshot replacement of authoritative rack/board state

## Mixmo Validation Flow

When the player clicks `MIXMO`, the frontend sends the current board cells to `POST /api/rooms/{roomId}/mixmo/validate`. Spring extracts every contiguous horizontal and vertical word, ignores isolated single-letter sequences by default, deduplicates repeated strings, and delegates only lexical validation to the FastAPI service. The response returns:

- `gridValid`
- extracted word occurrences with direction, coordinates, and validity
- `invalidWords`
- a per-cell `letterStatuses` map using `NEUTRAL`, `VALID`, and `INVALID`

The frontend renders those statuses directly on the existing board. Only when `gridValid` is `true` does it send the existing realtime `mixmo.trigger` command. Spring validates the authoritative persisted board again inside the trigger path before resolving MIXMO, so the backend remains the final authority.

## Tests

Backend:

- `mvn test`

Frontend:

- `npm test`
- `npm run build`

Python validator:

- `pytest`

## Known Follow-Up

The backend accepts full whole-word payloads for overlap-heavy placements, but the current mobile-first UI primarily composes rack-driven candidate letters and wildcard picks. A future board-assisted composer can expose more advanced interleaved overlap authoring without changing the backend contract.
