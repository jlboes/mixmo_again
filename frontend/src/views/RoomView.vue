<template>
  <main class="mixmo-page-shell min-h-screen px-3 py-3 md:px-6 md:py-4">
    <ToastBanner :toast="game.toast" @dismiss="game.dismissToast()" />

    <section v-if="loading" class="mixmo-panel mx-auto max-w-xl p-6 text-center">
      Loading room…
    </section>

    <LobbyPanel
      v-else-if="game.status === 'WAITING'"
      :room-code="game.roomCode ?? ''"
      :players="game.players"
      :host-player-id="game.hostPlayerId"
      :can-start="game.self?.playerId === game.hostPlayerId"
      :self-player-id="game.self?.playerId ?? null"
      @start="startCurrentRoom"
    />

    <section v-else class="mixmo-play-shell mx-auto grid max-w-7xl gap-3 xl:grid-cols-[minmax(0,1fr)_24rem] 2xl:grid-cols-[minmax(0,1fr)_26rem]">
      <div class="grid gap-3">
        <header class="mixmo-panel mixmo-play-header flex flex-wrap items-start justify-between gap-3 px-4 py-3 md:px-5">
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <p class="mixmo-kicker">{{ modeLabel }}</p>
            </div>
            <div class="mt-2 flex flex-wrap items-end gap-x-3 gap-y-2">
              <h1 class="text-2xl leading-none md:text-3xl">Room {{ game.roomCode }}</h1>
              <p class="max-w-2xl text-sm leading-6 text-[var(--color-text-secondary)]">{{ headerHint }}</p>
            </div>
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2 text-sm text-[var(--color-text-secondary)]">
            <span class="mixmo-chip">Bag {{ game.bagRemaining }}</span>
            <span v-if="game.currentBanditTheme" class="mixmo-chip mixmo-chip-active">Bandit: {{ game.currentBanditTheme }}</span>
            <span class="mixmo-chip">{{ game.players.length }} players</span>

            <div v-if="!isWideDesktop" class="flex w-full justify-end gap-2">
              <button
                v-if="game.hasSpectatablePlayers"
                class="utility-button mixmo-compact-button mixmo-companion-button"
                @click="openPlayersPanel()"
              >
                Players
              </button>
              <button
                class="utility-button mixmo-compact-button mixmo-companion-button"
                @click="openChatPanel()"
              >
                <span>Chat</span>
                <span v-if="game.chatUnreadCount > 0" class="mixmo-unread-badge">{{ unreadBadgeLabel }}</span>
              </button>
            </div>
          </div>
        </header>

        <GameBoard
          class="mixmo-play-board h-[50vh] min-h-[340px] md:h-[58vh] xl:h-[calc(100vh-10.5rem)] xl:min-h-[38rem] xl:max-h-[46rem]"
          :board-cells="game.validatedBoard"
          :preview-cells="game.preview?.previewCells ?? []"
          :suggestions="game.suggestions"
          :selected-suggestion-id="game.selectedSuggestionId"
          :selected-board-cells="game.selectedBoardCells"
          :selected-return-cells="game.selectedReturnCells"
          :anchor="game.anchor"
          :remove-mode-active="game.boardEditMode"
          :suggestions-locked="game.isComposeLocked"
          :viewport="game.viewport"
          @select-cell="game.setAnchorCell"
          @select-suggestion="game.selectSuggestion"
          @update:viewport="game.setViewport"
        />
      </div>

      <aside class="mixmo-panel mixmo-play-controls xl:sticky xl:top-4">
        <section class="mixmo-play-section mixmo-workflow-section">
          <div class="mixmo-workflow-block">
            <div class="mt-3">
              <CandidateStrip
                embedded
                :candidate="game.candidate"
                :disabled="game.isComposeLocked"
                @remove-last="game.removeLastCandidateTile()"
                @clear="game.clearCandidate()"
              >
                <template #after-draft>
                  <div class="mixmo-inline-controls">
                    <div class="min-w-0 flex-1">
                      <div class="flex flex-wrap items-center justify-between gap-3">
                        <p class="mixmo-kicker">Place</p>
                        <div class="mixmo-play-placement-actions">
                          <button
                            class="utility-button mixmo-compact-button mixmo-mode-toggle"
                            :class="game.boardEditMode ? 'mixmo-mode-toggle-active' : ''"
                            :aria-pressed="game.boardEditMode"
                            :disabled="game.boardEditMode ? game.boardReturnSubmitting : !game.canEnterBoardEditMode"
                            @click="game.boardEditMode ? game.cancelBoardEditMode() : game.enterBoardEditMode()"
                          >
                            {{ game.boardEditMode ? "Remove mode on" : "Remove mode" }}
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </CandidateStrip>
            </div>

            <div v-if="!game.boardEditMode" class="mixmo-placement-action-row mt-3">
              <button class="primary-button" :disabled="game.isComposeLocked || !game.preview?.valid" @click="game.confirmPlacement()">Place Word</button>
              <button
                class="utility-button mixmo-orientation-toggle"
                :class="game.orientation === 'VERTICAL' ? 'mixmo-orientation-toggle-vertical' : 'mixmo-orientation-toggle-horizontal'"
                :aria-label="game.orientation === 'HORIZONTAL' ? 'Current orientation horizontal. Switch to vertical.' : 'Current orientation vertical. Switch to horizontal.'"
                :disabled="game.isComposeLocked || !game.canToggleOrientation"
                @click="game.toggleOrientation()"
              >
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  class="mixmo-orientation-icon"
                  :class="game.orientation === 'VERTICAL' ? 'mixmo-orientation-icon-vertical' : ''"
                >
                  <path
                    d="M4 12h16m-3.5-3.5L20 12l-3.5 3.5M7.5 8.5L4 12l3.5 3.5"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.8"
                  />
                </svg>
                <span class="sr-only">
                  {{ game.orientation === "HORIZONTAL" ? "Horizontal placement selected. Tap to switch to vertical." : "Vertical placement selected. Tap to switch to horizontal." }}
                </span>
              </button>
            </div>

            <div v-else class="mixmo-play-primary-action mt-3">
              <button
                class="primary-button"
                :disabled="!game.canSubmitBoardReturn"
                @click="game.returnSelectedBoardTiles()"
              >
                {{ game.boardReturnSubmitting ? "Removing..." : "Return selected letters" }}
              </button>
            </div>

            <p v-if="game.boardEditMode && game.boardEditFeedback" class="mixmo-remove-mode-feedback mt-2">
              {{ game.boardEditFeedback }}
            </p>

            <div class="mixmo-play-secondary-action mt-2 md:mt-3">
              <button class="special-button" :disabled="!game.canMixmo" @click="game.triggerMixmo()">
                {{ game.mixmoValidationState === "validating" ? "Validating..." : "MIXMO" }}
              </button>
            </div>

            <div class="mt-3">
              <RackPanel :rack="sortedRack" :disabled="game.isComposeLocked" @toggle-tile="game.toggleRackTile" />
            </div>

            <div class="mt-3 flex items-center justify-between gap-3">
              <p class="mixmo-kicker">Sort Letters</p>
              <div class="mixmo-segment mixmo-sort-toggle">
                <button
                  class="mixmo-segment-button mixmo-sort-toggle-button"
                  :class="rackSortMode === 'default' ? 'mixmo-segment-button-active' : ''"
                  :aria-pressed="rackSortMode === 'default'"
                  @click="rackSortMode = 'default'"
                >
                  Default
                </button>
                <button
                  class="mixmo-segment-button mixmo-sort-toggle-button"
                  :class="rackSortMode === 'alphabetical' ? 'mixmo-segment-button-active' : ''"
                  :aria-pressed="rackSortMode === 'alphabetical'"
                  @click="rackSortMode = 'alphabetical'"
                >
                  A-Z
                </button>
              </div>
            </div>

            <div class="mt-2 flex">
              <button class="utility-button mixmo-compact-button w-full" :disabled="!game.canValidateGridWords" @click="game.validateGridWords()">
                {{ game.mixmoValidationState === "validating" ? "Validating..." : "Validate grid" }}
              </button>
            </div>
          </div>

          <div class="mixmo-workflow-block">
            <div class="max-w-[28rem]">
              <p class="mixmo-kicker">Status</p>
            </div>

            <div class="mt-3 grid gap-2">
              <p v-if="game.staleGameMessage" class="mixmo-play-message mixmo-play-message-alert">
                {{ game.staleGameMessage }}
              </p>
              <p v-if="game.preview && !game.preview.valid" class="mixmo-play-message mixmo-play-message-alert">
                {{ invalidReasonLabel(game.preview.invalidReason) }}
              </p>
            </div>

            <div v-if="game.invalidMixmoWords.length > 0" class="mixmo-play-alert-panel mt-4">
              <p class="mixmo-kicker">Invalid Grid Words</p>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  v-for="word in game.invalidMixmoWords"
                  :key="word"
                  class="rounded-full border border-[rgba(229,72,77,0.22)] bg-[rgba(229,72,77,0.12)] px-3 py-1 text-sm font-semibold text-[var(--color-preview-conflict)]"
                >
                  {{ word }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <section v-if="isWideDesktop" class="mixmo-play-section">
          <div class="flex items-center justify-between gap-3">
            <p class="mixmo-kicker">Companion</p>
            <div class="mixmo-segment mixmo-sort-toggle">
              <button
                class="mixmo-segment-button mixmo-sort-toggle-button"
                :class="desktopCompanionPanel === 'players' ? 'mixmo-segment-button-active' : ''"
                :aria-pressed="desktopCompanionPanel === 'players'"
                @click="setDesktopPanel('players')"
              >
                Players
              </button>
              <button
                class="mixmo-segment-button mixmo-sort-toggle-button"
                :class="desktopCompanionPanel === 'chat' ? 'mixmo-segment-button-active' : ''"
                :aria-pressed="desktopCompanionPanel === 'chat'"
                @click="setDesktopPanel('chat')"
              >
                Chat
                <span v-if="game.chatUnreadCount > 0" class="mixmo-unread-badge ml-2">{{ unreadBadgeLabel }}</span>
              </button>
            </div>
          </div>

          <div v-if="desktopCompanionPanel === 'players'" class="mt-3 grid gap-2">
            <div
              v-for="player in game.players"
              :key="player.playerId"
              class="rounded-[20px] border border-[rgba(16,17,20,0.08)] bg-[rgba(255,255,255,0.6)] px-4 py-3"
            >
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="flex flex-wrap items-center gap-2">
                    <p class="font-semibold text-[var(--color-text-primary)]">{{ player.playerName }}</p>
                    <span
                      v-if="player.playerId === game.self?.playerId"
                      class="mixmo-badge mixmo-badge-primary"
                    >
                      You
                    </span>
                  </div>
                  <p class="text-sm text-[var(--color-text-muted)]">Seat {{ player.seatOrder }} · Rack {{ player.rackCount ?? 0 }} · Cells {{ player.boardCellCount ?? 0 }}</p>
                </div>

                <div class="flex items-center gap-2">
                  <button
                    v-if="player.playerId !== game.self?.playerId"
                    class="utility-button mixmo-compact-button"
                    @click="openPlayersPanel(player.playerId)"
                  >
                    Spectate
                  </button>
                  <span class="mixmo-badge" :class="player.connected ? 'mixmo-badge-success' : 'mixmo-badge-muted'">
                    {{ player.connected ? "On" : "Off" }}
                  </span>
                </div>
              </div>
            </div>

            <div v-if="game.winnerPlayerId" class="mt-2 rounded-[24px] px-4 py-4 text-white shadow-[0_18px_30px_rgba(239,61,154,0.18)]" style="background: var(--mx-gradient-primary)">
              Winner: {{ winnerName }}
            </div>
          </div>

          <div v-else class="mt-3 overflow-hidden rounded-[26px] border border-[rgba(16,17,20,0.08)] bg-[rgba(255,255,255,0.72)]">
            <RoomChatPanel
              class="h-[30rem]"
              :messages="game.chatMessages"
              :self-player-id="game.self?.playerId ?? null"
              :loading="game.chatLoading"
              :error="game.chatError"
              @send="game.sendChatMessage"
              @dismiss-error="game.dismissChatError()"
            />
          </div>
        </section>
      </aside>
    </section>

    <div
      v-if="showMobileChat"
      class="mixmo-companion-overlay"
      @click.self="closeChatPanel"
    >
      <div class="mixmo-panel mixmo-companion-surface">
        <RoomChatPanel
          :messages="game.chatMessages"
          :self-player-id="game.self?.playerId ?? null"
          :loading="game.chatLoading"
          :error="game.chatError"
          show-back-button
          @back="closeChatPanel"
          @send="game.sendChatMessage"
          @dismiss-error="game.dismissChatError()"
        />
      </div>
    </div>

    <div
      v-if="showPlayerViewer"
      class="mixmo-companion-overlay"
      :class="isWideDesktop ? 'mixmo-companion-overlay-desktop' : ''"
      @click.self="closePlayersPanel"
    >
      <div class="mixmo-panel mixmo-companion-surface" :class="isWideDesktop ? 'mixmo-companion-surface-desktop' : ''">
        <PlayerBoardViewer
          :players="game.spectatablePlayers"
          :selected-player-id="selectedViewerPlayerId"
          :boards="game.playerBoardCache"
          :loading-by-player-id="game.playerBoardLoading"
          :viewport="selectedViewerViewport"
          :error="game.viewerBoardError"
          :close-label="isWideDesktop ? 'Close viewer' : 'Back to game'"
          @close="closePlayersPanel"
          @select-player="selectViewerPlayer"
          @update-viewport="updateViewerViewport"
        />
      </div>
    </div>

    <WildcardPicker
      :open="game.wildcardPicker.open"
      :letters="wildcardLetters"
      @choose="game.chooseWildcardLetter"
      @close="game.closeWildcardPicker()"
    />

    <BanditThemeSheet
      :open="game.isBanditThemeOpen"
      :is-owner="game.isBanditSelectorOwner"
      :options="game.themeOptions"
      :selected-theme="selectedTheme"
      @select="selectedTheme = $event"
      @close="game.dismissToast()"
      @confirm="confirmBanditTheme"
    />
  </main>
</template>

<script setup lang="ts">
import { useWindowSize } from "@vueuse/core";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { fetchState, leaveRoom, reconnect, startRoom } from "@/services/api/rooms";
import { clearSession, readSession } from "@/utils/storage";
import { useGameStore } from "@/stores/gameStore";
import LobbyPanel from "@/components/room/LobbyPanel.vue";
import GameBoard from "@/components/board/GameBoard.vue";
import RackPanel from "@/components/rack/RackPanel.vue";
import CandidateStrip from "@/components/candidate-word/CandidateStrip.vue";
import WildcardPicker from "@/components/common/WildcardPicker.vue";
import BanditThemeSheet from "@/components/bandit/BanditThemeSheet.vue";
import ToastBanner from "@/components/common/ToastBanner.vue";
import PlayerBoardViewer from "@/components/room/PlayerBoardViewer.vue";
import RoomChatPanel from "@/components/room/RoomChatPanel.vue";
import type { RackTile, ThemeOption, ViewportState } from "@/models/types";

type CompanionPanel = "chat" | "players" | null;

const route = useRoute();
const router = useRouter();
const game = useGameStore();
const { width } = useWindowSize();
const loading = ref(true);
const selectedTheme = ref<ThemeOption | null>(null);
const rackSortMode = ref<"default" | "alphabetical">("default");
let pendingLobbyLeave: Promise<void> | null = null;

const wildcardLetters = computed(() => {
  return game.wildcardPicker.kind === "BANDIT" ? ["K", "W", "X", "Y", "Z"] : "ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
});

const sortedRack = computed<RackTile[]>(() => {
  if (rackSortMode.value === "default") {
    return game.rack;
  }

  return [...game.rack].sort((left, right) => {
    const leftLabel = `${left.face}${left.kind === "NORMAL" ? "" : `-${left.kind}`}`;
    const rightLabel = `${right.face}${right.kind === "NORMAL" ? "" : `-${right.kind}`}`;
    return leftLabel.localeCompare(rightLabel);
  });
});

const winnerName = computed(() => game.players.find((player) => player.playerId === game.winnerPlayerId)?.playerName ?? "Unknown");
const isWideDesktop = computed(() => width.value >= 1280);
const companionPanel = computed<CompanionPanel>(() => {
  const panel = route.query.panel;
  return panel === "chat" || panel === "players" ? panel : null;
});
const viewerPlayerIdQuery = computed(() => typeof route.query.viewerPlayerId === "string" ? route.query.viewerPlayerId : null);
const selectedViewerPlayerId = computed(() => {
  if (!viewerPlayerIdQuery.value) {
    return null;
  }
  return game.spectatablePlayers.some((player) => player.playerId === viewerPlayerIdQuery.value)
    ? viewerPlayerIdQuery.value
    : null;
});
const desktopCompanionPanel = computed<"players" | "chat">(() => companionPanel.value === "chat" ? "chat" : "players");
const showMobileChat = computed(() => companionPanel.value === "chat" && !isWideDesktop.value);
const showPlayerViewer = computed(() => companionPanel.value === "players" && selectedViewerPlayerId.value != null);
const unreadBadgeLabel = computed(() => game.chatUnreadCount > 99 ? "99+" : String(game.chatUnreadCount));
const selectedViewerViewport = computed(() => game.playerBoardViewport(selectedViewerPlayerId.value));

const modeLabel = computed(() => {
  if (game.boardEditMode) {
    return "Remove Letters";
  }
  if (game.status === "PAUSED") {
    return "Bandit Pause";
  }
  if (game.status === "FINISHED") {
    return "Match Finished";
  }
  switch (game.mode) {
    case "composing":
      return "Building Word";
    case "confirmed":
      return "Word Placed";
    default:
      return "Build Your Next Word";
  }
});

const headerHint = computed(() => {
  if (game.boardEditMode) {
    return "Mark placed letters for removal, then return them to your rack.";
  }
  if (game.status === "PAUSED") {
    return game.isBanditSelectorOwner ? "Choose a bandit theme to resume play." : "Waiting for the bandit theme selection.";
  }
  if (game.status === "FINISHED") {
    return `${winnerName.value} cleared their rack and finished the match.`;
  }
  switch (game.mode) {
    case "composing":
      return "Compose first, then place the full word in one move.";
    case "confirmed":
      return "The latest placement is locked in and synced.";
    default:
      return "";
  }
});

onMounted(async () => {
  const roomId = String(route.params.roomId);
  const session = readSession(roomId);
  if (!session) {
    await router.push({ name: "home" });
    return;
  }
  loading.value = true;
  try {
    game.hydrateBootstrap({
      room: {
        roomId: session.roomId,
        roomCode: session.roomCode,
        status: "WAITING",
        hostPlayerId: session.seatOrder === 1 ? session.playerId : null,
        players: [],
        currentBanditTheme: null,
        pauseReason: null,
        roomVersion: 0
      },
      self: {
        playerId: session.playerId,
        playerName: session.playerName,
        seatOrder: session.seatOrder,
        sessionToken: session.sessionToken
      }
    });
    const state = await reconnect(roomId, {
      playerId: session.playerId,
      sessionToken: session.sessionToken,
      lastKnownRoomVersion: game.roomVersion,
      lastKnownEventSequence: game.lastResolvedEventSequence
    });
    game.syncAfterReconnect(state.data.gameSnapshot);
    await game.loadChatHistory();
  } catch {
    try {
      const state = await fetchState(roomId, session.playerId, session.sessionToken);
      game.applySnapshot(state.data);
      game.connectRealtime();
      await game.loadChatHistory();
    } catch {
      clearSession(roomId);
      game.disconnectRealtime();
      await router.push({ name: "home" });
      return;
    }
  } finally {
    loading.value = false;
  }
});

watch(
  () => [companionPanel.value, viewerPlayerIdQuery.value, game.spectatablePlayers.map((player) => player.playerId).join(","), isWideDesktop.value],
  async () => {
    await normalizeCompanionRoute();
  },
  { immediate: true }
);

watch(
  () => [companionPanel.value, game.chatLatestSequence],
  () => {
    if (companionPanel.value === "chat") {
      game.markChatRead();
    }
  },
  { immediate: true }
);

watch(
  () => selectedViewerPlayerId.value,
  (playerId) => {
    if (!playerId) {
      return;
    }
    void game.loadPlayerBoard(playerId);
  },
  { immediate: true }
);

watch(
  () => [game.roomVersion, companionPanel.value, selectedViewerPlayerId.value] as const,
  ([, panel, playerId]) => {
    if (panel !== "players" || !playerId) {
      return;
    }
    void game.loadPlayerBoard(playerId, { silent: true, force: true });
  }
);

onBeforeRouteLeave(async () => {
  await leaveWaitingRoomForNavigation();
});

onUnmounted(() => {
  game.disconnectRealtime();
});

async function normalizeCompanionRoute(): Promise<void> {
  if (loading.value) {
    return;
  }

  if (companionPanel.value !== "players") {
    if (viewerPlayerIdQuery.value) {
      await updateCompanionQuery(companionPanel.value, null);
    }
    return;
  }

  if (!game.hasSpectatablePlayers) {
    await updateCompanionQuery(null, null);
    return;
  }

  if (!selectedViewerPlayerId.value && !isWideDesktop.value) {
    await updateCompanionQuery("players", game.spectatablePlayers[0]?.playerId ?? null);
    return;
  }

  if (!selectedViewerPlayerId.value && viewerPlayerIdQuery.value) {
    await updateCompanionQuery("players", isWideDesktop.value ? null : game.spectatablePlayers[0]?.playerId ?? null);
  }
}

async function updateCompanionQuery(panel: CompanionPanel, viewerPlayerId: string | null): Promise<void> {
  const nextQuery = { ...route.query } as Record<string, string>;

  if (panel) {
    nextQuery.panel = panel;
  } else {
    delete nextQuery.panel;
  }

  if (viewerPlayerId) {
    nextQuery.viewerPlayerId = viewerPlayerId;
  } else {
    delete nextQuery.viewerPlayerId;
  }

  await router.replace({ query: nextQuery });
}

async function openPlayersPanel(playerId?: string): Promise<void> {
  const targetPlayerId = playerId ?? game.spectatablePlayers[0]?.playerId ?? null;
  if (!targetPlayerId) {
    return;
  }
  await updateCompanionQuery("players", targetPlayerId);
}

async function selectViewerPlayer(playerId: string): Promise<void> {
  await updateCompanionQuery("players", playerId);
}

function updateViewerViewport(nextViewport: ViewportState): void {
  if (!selectedViewerPlayerId.value) {
    return;
  }
  game.setPlayerBoardViewport(selectedViewerPlayerId.value, nextViewport);
}

async function closePlayersPanel(): Promise<void> {
  if (isWideDesktop.value) {
    await updateCompanionQuery("players", null);
    return;
  }
  await updateCompanionQuery(null, null);
}

async function openChatPanel(): Promise<void> {
  await updateCompanionQuery("chat", null);
}

async function closeChatPanel(): Promise<void> {
  await updateCompanionQuery(null, null);
}

async function setDesktopPanel(panel: "players" | "chat"): Promise<void> {
  await updateCompanionQuery(panel, null);
}

async function startCurrentRoom() {
  if (!game.roomId || !game.self) {
    return;
  }
  try {
    const response = await startRoom(game.roomId, game.self.playerId);
    game.applySnapshot(response.data.gameSnapshot);
  } catch (error) {
    game.toast = {
      type: "error",
      message: error instanceof Error ? error.message : "Match could not be started."
    };
  }
}

async function leaveWaitingRoomForNavigation() {
  if (pendingLobbyLeave) {
    await pendingLobbyLeave;
    return;
  }
  const routeRoomId = String(route.params.roomId);
  if (game.status !== "WAITING" || game.roomId !== routeRoomId || !game.self) {
    return;
  }

  pendingLobbyLeave = (async () => {
    try {
      await leaveRoom(routeRoomId, {
        playerId: game.self?.playerId ?? "",
        sessionToken: game.self?.sessionToken ?? ""
      });
      clearSession(routeRoomId);
    } catch {
      // Preserve the local session if the room already started or the leave request failed.
    } finally {
      game.disconnectRealtime();
    }
  })();

  await pendingLobbyLeave;
}

function confirmBanditTheme() {
  if (selectedTheme.value) {
    game.selectBanditTheme(selectedTheme.value);
    selectedTheme.value = null;
  }
}

function invalidReasonLabel(reason: string | null) {
  switch (reason) {
    case "missing_anchor":
      return "Tap an empty board cell to place the word, or reuse letters already on the board.";
    case "first_word_must_cross_origin":
      return "The first word must cross the origin.";
    case "disconnected_placement":
      return "This placement must connect to your existing grid.";
    case "collision_with_different_letter":
      return "One or more overlapping letters do not match.";
    case "invalid_tile_usage":
      return "The rack letters and reused board letters do not line up yet.";
    case "invalid_bandit_letter":
      return "Bandit can only be used as K, W, X, Y, or Z.";
    default:
      return "This placement is invalid.";
  }
}
</script>
