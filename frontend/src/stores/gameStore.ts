import { computed, ref, watch } from "vue";
import { defineStore } from "pinia";
import type {
  BoardCellDto,
  CandidateTile,
  ChatHistory,
  ChatMessage,
  DeviceType,
  GameMode,
  GameSnapshot,
  GridValidationResponse,
  LetterValidationState,
  MixmoValidationState,
  Orientation,
  PlacementPreviewResult,
  PlacementSuggestion,
  PlayerBoardView,
  RackTile,
  RequestedTile,
  RoomBootstrapData,
  SessionRecord,
  StaleGameStateDto,
  SocketEventEnvelope,
  ThemeOption,
  ToastState,
  ViewportState
} from "@/models/types";
import { fetchChatHistory, fetchPlayerBoard, validateMixmoGrid } from "@/services/api/rooms";
import { detectDeviceType } from "@/utils/device";
import { readChatReadSequence, saveChatReadSequence, saveSession } from "@/utils/storage";
import { RealtimeClient } from "@/services/realtime/client";

const realtimeClient = new RealtimeClient();

interface DraftPlacement {
  start: { x: number; y: number } | null;
  orientation: Orientation | null;
  usesBoardLetters: boolean;
  feedback: string | null;
}

function defaultViewport(): ViewportState {
  return { centerX: 0, centerY: 0, zoom: 1 };
}

function boardSignature(cells: BoardCellDto[]): string {
  return cells
    .map((cell) => `${cell.x}:${cell.y}:${cell.resolvedLetter}:${cell.tileId}`)
    .sort()
    .join("|");
}

function boardCellKey(x: number, y: number): string {
  return `${x}:${y}`;
}

function orientationDelta(orientation: Orientation): { x: number; y: number } {
  return orientation === "HORIZONTAL" ? { x: 1, y: 0 } : { x: 0, y: 1 };
}

function invalidReasonMessage(reason: string | null): string {
  switch (reason) {
    case "missing_anchor":
      return "Pick a board position before placing the word.";
    case "first_word_must_cross_origin":
      return "Your first word has to cross the center cell.";
    case "disconnected_placement":
      return "This word must stay connected to your existing grid.";
    case "collision_with_different_letter":
      return "One of the reused board letters does not match this word.";
    case "invalid_tile_usage":
      return "The selected rack tiles do not line up with this word yet.";
    case "invalid_orientation":
      return "Choose a horizontal or vertical direction.";
    case "invalid_bandit_letter":
      return "Bandit tiles can only stand in for K, W, X, Y, or Z.";
    default:
      return "Keep building the word until the preview turns valid.";
  }
}

export const useGameStore = defineStore("game", () => {
  const roomId = ref<string | null>(null);
  const roomCode = ref<string | null>(null);
  const roomVersion = ref(0);
  const hostPlayerId = ref<string | null>(null);
  const self = ref<SessionRecord | null>(null);
  const status = ref<"WAITING" | "ACTIVE" | "PAUSED" | "FINISHED">("WAITING");
  const players = ref<GameSnapshot["players"]>([]);
  const bagRemaining = ref(0);
  const winnerPlayerId = ref<string | null>(null);
  const currentBanditTheme = ref<string | null>(null);
  const pauseReason = ref<GameSnapshot["pauseReason"]>(null);
  const pauseTriggeringPlayerId = ref<string | null>(null);
  const themeOptions = ref<ThemeOption[]>([]);
  const rack = ref<RackTile[]>([]);
  const board = ref<GameSnapshot["selfBoard"]>([]);
  const mode = ref<GameMode>("idle");
  const orientation = ref<Orientation>("HORIZONTAL");
  const anchor = ref<{ x: number; y: number } | null>(null);
  const candidate = ref<CandidateTile[]>([]);
  const preview = ref<PlacementPreviewResult | null>(null);
  const suggestions = ref<PlacementSuggestion[]>([]);
  const selectedSuggestionId = ref<string | null>(null);
  const selectedSuggestionTiles = ref<RequestedTile[] | null>(null);
  const viewport = ref<ViewportState>(defaultViewport());
  const toast = ref<ToastState | null>(null);
  const wildcardPicker = ref<{ open: boolean; tileId: string | null; kind: RackTile["kind"] | null }>({
    open: false,
    tileId: null,
    kind: null
  });
  const connectionReady = ref(false);
  const lastResolvedEventSequence = ref(0);
  const candidateHistory = ref<string[]>([]);
  const mixmoValidationState = ref<MixmoValidationState>("idle");
  const mixmoValidationError = ref<string | null>(null);
  const invalidMixmoWords = ref<string[]>([]);
  const boardValidationStatuses = ref<Record<string, LetterValidationState>>({});
  const boardEditMode = ref(false);
  const selectedReturnTileIds = ref<string[]>([]);
  const boardReturnSubmitting = ref(false);
  const staleGameState = ref<StaleGameStateDto>({
    warningActive: false,
    message: null,
    automaticMixmoAt: null
  });
  const playerBoardCache = ref<Record<string, PlayerBoardView>>({});
  const playerBoardLoading = ref<Record<string, boolean>>({});
  const viewerViewportByPlayerId = ref<Record<string, ViewportState>>({});
  const viewerBoardError = ref<string | null>(null);
  const chatMessages = ref<ChatMessage[]>([]);
  const chatLatestSequence = ref(0);
  const chatLastReadSequence = ref(0);
  const chatLoading = ref(false);
  const chatError = ref<string | null>(null);
  const staleCountdownNow = ref(Date.now());
  const mixmoTriggerPending = ref(false);
  let staleGameCountdownInterval: ReturnType<typeof setInterval> | null = null;

  const candidateWord = computed(() => candidate.value.map((tile) => tile.letter).join(""));
  const canMixmo = computed(() => rack.value.length === 0 && status.value === "ACTIVE" && mixmoValidationState.value !== "validating" && !mixmoTriggerPending.value);
  const staleGameActive = computed(() => staleGameState.value.warningActive);
  const staleGameMessage = computed(() => {
    if (!staleGameState.value.warningActive || !staleGameState.value.message) {
      return null;
    }
    if (!staleGameState.value.automaticMixmoAt) {
      return staleGameState.value.message;
    }
    const automaticMixmoAt = Date.parse(staleGameState.value.automaticMixmoAt);
    if (Number.isNaN(automaticMixmoAt)) {
      return staleGameState.value.message;
    }
    const remainingSeconds = Math.max(0, Math.ceil((automaticMixmoAt - staleCountdownNow.value) / 1000));
    return `${staleGameState.value.message} - remaining ${remainingSeconds} seconds before automatic MIXMO.`;
  });
  const isBanditThemeOpen = computed(() => status.value === "PAUSED");
  const isBanditSelectorOwner = computed(() => pauseTriggeringPlayerId.value === self.value?.playerId);
  const boardCellByPosition = computed(() => {
    const index = new Map<string, BoardCellDto>();
    for (const cell of board.value) {
      index.set(boardCellKey(cell.x, cell.y), cell);
    }
    return index;
  });
  const validatedBoard = computed<BoardCellDto[]>(() => {
    return board.value.map((cell) => ({
      ...cell,
      validationStatus: boardValidationStatuses.value[boardCellKey(cell.x, cell.y)] ?? "NEUTRAL"
    }));
  });
  const boardCandidates = computed(() => candidate.value.filter(isBoardCandidate));
  const candidateUsesBoardLetters = computed(() => boardCandidates.value.length > 0);
  const selectedBoardCells = computed(() => boardCandidates.value.map((tile) => tile.boardCell));
  const selectedReturnCells = computed(() => {
    const selectedIds = new Set(selectedReturnTileIds.value);
    return board.value
      .filter((cell) => selectedIds.has(cell.tileId))
      .map((cell) => ({ x: cell.x, y: cell.y }));
  });
  const activeDraftBoardTileIds = computed(() => {
    const tileIds = new Set<string>();
    for (const tile of boardCandidates.value) {
      if (!tile.boardCell) {
        continue;
      }
      const boardCell = boardCellAt(tile.boardCell);
      if (boardCell) {
        tileIds.add(boardCell.tileId);
      }
    }
    return tileIds;
  });
  const boardEditSelectionCount = computed(() => selectedReturnTileIds.value.length);
  const canEnterBoardEditMode = computed(() => status.value === "ACTIVE" && board.value.length > 0 && !boardReturnSubmitting.value);
  const canSubmitBoardReturn = computed(() => boardEditMode.value && selectedReturnTileIds.value.length > 0 && !boardReturnSubmitting.value);
  const isComposeLocked = computed(() => boardEditMode.value || boardReturnSubmitting.value);
  const canValidateGridWords = computed(() => status.value === "ACTIVE" && board.value.length > 0 && mixmoValidationState.value !== "validating");
  const canToggleOrientation = computed(() => boardCandidates.value.length <= 1 && candidate.value.length > 0);
  const spectatablePlayers = computed(() => players.value.filter((player) => player.playerId !== self.value?.playerId));
  const hasSpectatablePlayers = computed(() => spectatablePlayers.value.length > 0);
  const chatUnreadCount = computed(() => {
    return chatMessages.value.filter((message) => {
      return message.sequenceNumber > chatLastReadSequence.value && message.playerId !== self.value?.playerId;
    }).length;
  });
  const boardEditFeedback = computed(() => {
    if (!boardEditMode.value) {
      return null;
    }
    if (boardReturnSubmitting.value) {
      return "Removing the selected letters from your board.";
    }
    if (selectedReturnTileIds.value.length === 0) {
      return "Remove mode is on. Tap placed letters to mark them for return.";
    }
    return `${selectedReturnTileIds.value.length} ${selectedReturnTileIds.value.length === 1 ? "letter" : "letters"} marked for return.`;
  });
  const composerFeedback = computed(() => {
    if (status.value === "PAUSED") {
      return isBanditSelectorOwner.value
        ? "Choose a bandit theme to resume play."
        : "Waiting for the triggering player to choose a bandit theme.";
    }
    if (status.value === "FINISHED") {
      return "The match is finished.";
    }
    if (candidate.value.length === 0) {
      return "Tap rack tiles or existing board letters to start a word.";
    }

    const placement = resolveDraftPlacement();
    if (placement.feedback) {
      return placement.feedback;
    }
    if (preview.value && !preview.value.valid && preview.value.invalidReason) {
      return invalidReasonMessage(preview.value.invalidReason);
    }
    if (preview.value?.valid) {
      return candidateUsesBoardLetters.value
        ? "The preview is ready. Place the word when the highlighted board letters look right."
        : "The preview is ready. Place the word or tap a different cell to adjust it.";
    }
    if (candidateUsesBoardLetters.value) {
      return boardCandidates.value.length === 1
        ? "Tap board letters in word order, then add rack letters to complete the word."
        : "Keep board letters in word order while you build the word.";
    }
    if (anchor.value) {
      return "Checking this placement. You can still move the anchor or choose a suggestion.";
    }
    if (suggestions.value.length > 0) {
      return "Select a suggestion or tap an empty board cell to place the word manually.";
    }
    return "Tap an empty board cell or wait for suggestions to place this word.";
  });

  function clearStaleCountdownInterval(): void {
    if (!staleGameCountdownInterval) {
      return;
    }
    clearInterval(staleGameCountdownInterval);
    staleGameCountdownInterval = null;
  }

  function syncStaleCountdown(): void {
    clearStaleCountdownInterval();
    staleCountdownNow.value = Date.now();
    if (!staleGameState.value.warningActive || !staleGameState.value.automaticMixmoAt) {
      return;
    }
    staleGameCountdownInterval = setInterval(() => {
      staleCountdownNow.value = Date.now();
    }, 1000);
  }

  watch(
    () => [staleGameState.value.warningActive, staleGameState.value.automaticMixmoAt],
    () => {
      syncStaleCountdown();
    },
    { immediate: true, flush: "sync" }
  );

  function resetCompanionState(): void {
    playerBoardCache.value = {};
    playerBoardLoading.value = {};
    viewerViewportByPlayerId.value = {};
    viewerBoardError.value = null;
    chatMessages.value = [];
    chatLatestSequence.value = 0;
    chatLastReadSequence.value = 0;
    chatLoading.value = false;
    chatError.value = null;
  }

  function syncChatReadSequence(): void {
    if (!roomId.value || !self.value) {
      chatLastReadSequence.value = 0;
      return;
    }
    chatLastReadSequence.value = readChatReadSequence(roomId.value, self.value.playerId);
  }

  function mergeChatMessages(messages: ChatMessage[]): void {
    const bySequence = new Map<number, ChatMessage>();
    for (const message of chatMessages.value) {
      bySequence.set(message.sequenceNumber, message);
    }
    for (const message of messages) {
      bySequence.set(message.sequenceNumber, message);
    }
    chatMessages.value = [...bySequence.values()].sort((left, right) => left.sequenceNumber - right.sequenceNumber);
    chatLatestSequence.value = chatMessages.value.length === 0 ? 0 : chatMessages.value[chatMessages.value.length - 1].sequenceNumber;
  }

  function syncSpectatorBoardCache(previousPlayers: GameSnapshot["players"], nextPlayers: GameSnapshot["players"]): void {
    const nextPlayerIds = new Set(nextPlayers.map((player) => player.playerId));
    playerBoardCache.value = Object.fromEntries(
      Object.entries(playerBoardCache.value).filter(([playerId]) => nextPlayerIds.has(playerId))
    );
    playerBoardLoading.value = Object.fromEntries(
      Object.entries(playerBoardLoading.value).filter(([playerId]) => nextPlayerIds.has(playerId))
    );
    viewerViewportByPlayerId.value = Object.fromEntries(
      Object.entries(viewerViewportByPlayerId.value).filter(([playerId]) => nextPlayerIds.has(playerId))
    );

    const previousCounts = new Map(previousPlayers.map((player) => [player.playerId, player.boardCellCount ?? 0]));
    for (const player of nextPlayers) {
      if (!playerBoardCache.value[player.playerId]) {
        continue;
      }
      const previousCount = previousCounts.get(player.playerId);
      const nextCount = player.boardCellCount ?? 0;
      if (previousCount !== nextCount) {
        void loadPlayerBoard(player.playerId, { silent: true, force: true });
      }
    }
  }

  function draftSurvivesSnapshot(nextRack: GameSnapshot["selfRack"], nextBoard: GameSnapshot["selfBoard"]): boolean {
    if (candidate.value.length === 0) {
      return false;
    }
    const nextRackTileIds = new Set(nextRack.map((tile) => tile.tileId));
    const nextBoardTileIds = new Set(nextBoard.map((cell) => cell.tileId));
    return candidate.value.every((tile) => {
      if (tile.source === "rack") {
        return nextRackTileIds.has(tile.tileId);
      }
      if (!tile.boardCell) {
        return false;
      }
      const boardCell = boardCellAt(tile.boardCell);
      return boardCell ? nextBoardTileIds.has(boardCell.tileId) : false;
    });
  }

  function syncBoardEditAfterSnapshot(snapshot: GameSnapshot, preserveEditMode: boolean): void {
    if (!preserveEditMode || snapshot.status !== "ACTIVE" || snapshot.selfBoard.length === 0) {
      boardEditMode.value = false;
      selectedReturnTileIds.value = [];
      boardReturnSubmitting.value = false;
      return;
    }
    const nextBoardTileIds = new Set(snapshot.selfBoard.map((cell) => cell.tileId));
    selectedReturnTileIds.value = selectedReturnTileIds.value.filter((tileId) => nextBoardTileIds.has(tileId));
    boardReturnSubmitting.value = false;
  }

  function hydrateBootstrap(data: RoomBootstrapData): void {
    if (roomId.value && roomId.value !== data.room.roomId) {
      resetCompanionState();
    }
    roomId.value = data.room.roomId;
    roomCode.value = data.room.roomCode;
    roomVersion.value = data.room.roomVersion;
    hostPlayerId.value = data.room.hostPlayerId;
    status.value = data.room.status;
    players.value = data.room.players;
    currentBanditTheme.value = data.room.currentBanditTheme;
    pauseReason.value = data.room.pauseReason;
    self.value = {
      roomId: data.room.roomId,
      roomCode: data.room.roomCode,
      playerId: data.self.playerId,
      playerName: data.self.playerName,
      seatOrder: data.self.seatOrder,
      sessionToken: data.self.sessionToken
    };
    saveSession(self.value);
    syncChatReadSequence();
  }

  function applySnapshot(snapshot: GameSnapshot): void {
    if (roomId.value && roomId.value !== snapshot.roomId) {
      resetCompanionState();
    }
    const previousBoardState = boardSignature(board.value);
    const shouldPreserveDraft = draftSurvivesSnapshot(snapshot.selfRack, snapshot.selfBoard);
    const preserveBoardEditMode = boardEditMode.value && !boardReturnSubmitting.value;
    const previousPlayers = [...players.value];
    roomId.value = snapshot.roomId;
    roomCode.value = snapshot.roomCode;
    roomVersion.value = snapshot.roomVersion;
    hostPlayerId.value = snapshot.hostPlayerId;
    status.value = snapshot.status;
    bagRemaining.value = snapshot.bagRemaining;
    winnerPlayerId.value = snapshot.winnerPlayerId;
    players.value = snapshot.players;
    currentBanditTheme.value = snapshot.currentBanditTheme;
    pauseReason.value = snapshot.pauseReason;
    pauseTriggeringPlayerId.value = snapshot.themeState.pauseTriggeringPlayerId;
    themeOptions.value = snapshot.themeState.themeOptions;
    staleGameState.value = snapshot.staleGameState;
    board.value = snapshot.selfBoard;
    syncChatReadSequence();
    rack.value = snapshot.selfRack.map((tile) => ({
      ...tile,
      selected: candidate.value.some((selected) => selected.source === "rack" && selected.tileId === tile.tileId)
    }));
    lastResolvedEventSequence.value = snapshot.lastResolvedEventSequence;
    syncSpectatorBoardCache(previousPlayers, snapshot.players);
    syncBoardEditAfterSnapshot(snapshot, preserveBoardEditMode);

    if (previousBoardState !== boardSignature(board.value) || status.value !== "ACTIVE") {
      clearMixmoValidationFeedback();
    }

    if (candidate.value.length > 0 && !shouldPreserveDraft) {
      resetDraft();
    }

    if (boardEditMode.value) {
      syncSelection();
      mode.value = candidate.value.length === 0
        ? status.value === "FINISHED" ? "confirmed" : "idle"
        : "composing";
      return;
    }
    if (status.value === "PAUSED") {
      mode.value = "banditTheme";
    } else if (candidate.value.length === 0) {
      mode.value = status.value === "FINISHED" ? "confirmed" : "idle";
    } else {
      refreshDraftState();
    }
  }

  function connectRealtime(): void {
    if (!roomId.value || !self.value) {
      return;
    }
    realtimeClient.connect({
      roomId: roomId.value,
      playerId: self.value.playerId,
      sessionToken: self.value.sessionToken,
      onEvent: handleSocketEvent,
      onClose: () => {
        connectionReady.value = false;
      }
    });
  }

  function disconnectRealtime(): void {
    realtimeClient.close();
    connectionReady.value = false;
  }

  function handleSocketEvent(event: SocketEventEnvelope): void {
    connectionReady.value = true;
    if (event.type === "game.state.updated") {
      applySnapshot(event.payload as GameSnapshot);
      return;
    }
    if (event.type === "chat.message.created") {
      mergeChatMessages([event.payload as ChatMessage]);
      return;
    }
    if (event.type === "placement.preview.result") {
      preview.value = event.payload as PlacementPreviewResult;
      if (candidate.value.length > 0 && status.value === "ACTIVE") {
        mode.value = "composing";
      }
      return;
    }
    if (event.type === "placement.suggestions.result") {
      if (candidateUsesBoardLetters.value || anchor.value) {
        return;
      }
      suggestions.value = ((event.payload as { suggestions: PlacementSuggestion[] }).suggestions ?? []) as PlacementSuggestion[];
      if (suggestions.value.length > 0 && !selectedSuggestionId.value) {
        selectSuggestion(suggestions.value[0]);
      } else if (suggestions.value.length === 0 && !selectedSuggestionId.value) {
        preview.value = null;
      }
      return;
    }
    if (event.type === "game.paused") {
      mode.value = "banditTheme";
      toast.value = { type: "info", message: "Bandit theme selection is blocking the game." };
      return;
    }
    if (event.type === "bandit.theme.selected") {
      toast.value = { type: "success", message: `Bandit theme set to ${(event.payload as { theme: string }).theme}.` };
      return;
    }
    if (event.type === "mixmo.resolved") {
      clearMixmoValidationFeedback();
      mixmoTriggerPending.value = false;
      const payload = event.payload as { finalMixmo: boolean; triggerReason?: "PLAYER" | "MANUAL" | "AUTOMATIC" };
      toast.value = payload.finalMixmo
        ? { type: "success", message: "Final MIXMO resolved the game." }
        : payload.triggerReason === "AUTOMATIC"
          ? { type: "info", message: "Automatic MIXMO resolved. Everyone drew two tiles." }
          : { type: "info", message: "MIXMO resolved. Everyone drew two tiles." };
      return;
    }
    if (event.type === "game.finished") {
      clearMixmoValidationFeedback();
      mixmoTriggerPending.value = false;
      mode.value = "confirmed";
      toast.value = { type: "success", message: "Game finished." };
      return;
    }
    if (event.type === "error") {
      boardReturnSubmitting.value = false;
      mixmoTriggerPending.value = false;
      const payload = event.payload as { message: string };
      toast.value = { type: "error", message: payload.message };
    }
  }

  async function loadPlayerBoard(playerId: string, options: { silent?: boolean; force?: boolean } = {}): Promise<PlayerBoardView | null> {
    if (!roomId.value || !self.value || !playerId) {
      return null;
    }
    if (!options.force && playerBoardCache.value[playerId]) {
      return playerBoardCache.value[playerId];
    }
    if (playerBoardLoading.value[playerId]) {
      return playerBoardCache.value[playerId] ?? null;
    }
    playerBoardLoading.value = { ...playerBoardLoading.value, [playerId]: true };
    if (!options.silent) {
      viewerBoardError.value = null;
    }
    try {
      const response = await fetchPlayerBoard(roomId.value, playerId, self.value.playerId, self.value.sessionToken);
      playerBoardCache.value = { ...playerBoardCache.value, [playerId]: response.data };
      viewerBoardError.value = null;
      return response.data;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Player board could not be loaded.";
      if (!options.silent) {
        viewerBoardError.value = message;
      }
      return null;
    } finally {
      playerBoardLoading.value = { ...playerBoardLoading.value, [playerId]: false };
    }
  }

  function playerBoardViewport(playerId: string | null): ViewportState {
    if (!playerId) {
      return defaultViewport();
    }
    return viewerViewportByPlayerId.value[playerId] ?? defaultViewport();
  }

  function setPlayerBoardViewport(playerId: string, nextViewport: ViewportState): void {
    const currentViewport = viewerViewportByPlayerId.value[playerId];
    if (
      currentViewport &&
      currentViewport.centerX === nextViewport.centerX &&
      currentViewport.centerY === nextViewport.centerY &&
      currentViewport.zoom === nextViewport.zoom
    ) {
      return;
    }
    viewerViewportByPlayerId.value = {
      ...viewerViewportByPlayerId.value,
      [playerId]: nextViewport
    };
  }

  async function loadChatHistory(limit = 50): Promise<void> {
    if (!roomId.value || !self.value) {
      return;
    }
    chatLoading.value = true;
    chatError.value = null;
    syncChatReadSequence();
    try {
      const response = await fetchChatHistory(roomId.value, self.value.playerId, self.value.sessionToken, limit);
      applyChatHistory(response.data);
    } catch (error) {
      chatError.value = error instanceof Error ? error.message : "Chat history could not be loaded.";
    } finally {
      chatLoading.value = false;
    }
  }

  function applyChatHistory(history: ChatHistory): void {
    mergeChatMessages(history.messages ?? []);
    chatLatestSequence.value = Math.max(chatLatestSequence.value, history.latestSequence ?? 0);
  }

  function markChatRead(): void {
    if (!roomId.value || !self.value) {
      return;
    }
    chatLastReadSequence.value = Math.max(chatLastReadSequence.value, chatLatestSequence.value);
    saveChatReadSequence(roomId.value, self.value.playerId, chatLastReadSequence.value);
  }

  function dismissChatError(): void {
    chatError.value = null;
  }

  function sendChatMessage(text: string): boolean {
    if (!roomId.value || !self.value) {
      return false;
    }
    const normalized = text.trim();
    if (normalized.length === 0) {
      return false;
    }
    const requestId = realtimeClient.sendChatMessage(
      { roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value },
      normalized
    );
    if (!requestId) {
      toast.value = { type: "error", message: "Realtime connection is unavailable. Chat message was not sent." };
      return false;
    }
    return true;
  }

  function toggleRackTile(tileId: string): void {
    if (isComposeLocked.value) {
      return;
    }
    const tile = rack.value.find((rackTile) => rackTile.tileId === tileId);
    if (!tile) {
      return;
    }
    if (removeCandidateByTileId(tileId)) {
      refreshDraftState();
      return;
    }
    if (tile.kind === "JOKER" || tile.kind === "BANDIT") {
      wildcardPicker.value = { open: true, tileId, kind: tile.kind };
      return;
    }
    insertCandidate(createRackCandidate(tile, tile.face));
    refreshDraftState();
  }

  function chooseWildcardLetter(letter: string): void {
    if (isComposeLocked.value) {
      return;
    }
    if (!wildcardPicker.value.tileId) {
      return;
    }
    const tile = rack.value.find((rackTile) => rackTile.tileId === wildcardPicker.value.tileId);
    if (!tile) {
      return;
    }
    wildcardPicker.value = { open: false, tileId: null, kind: null };
    insertCandidate(createRackCandidate(tile, letter));
    refreshDraftState();
  }

  function closeWildcardPicker(): void {
    wildcardPicker.value = { open: false, tileId: null, kind: null };
  }

  function removeLastCandidateTile(): void {
    if (isComposeLocked.value) {
      return;
    }
    const lastTileId = candidateHistory.value.pop();
    if (!lastTileId) {
      return;
    }
    removeCandidateByTileId(lastTileId, false);
    refreshDraftState();
  }

  function clearCandidate(): void {
    if (isComposeLocked.value) {
      return;
    }
    if (candidate.value.length === 0) {
      return;
    }
    resetDraft();
  }

  function enterBoardEditMode(): void {
    if (!canEnterBoardEditMode.value || boardEditMode.value) {
      return;
    }
    wildcardPicker.value = { open: false, tileId: null, kind: null };
    boardEditMode.value = true;
    selectedReturnTileIds.value = [];
  }

  function cancelBoardEditMode(): void {
    if (!boardEditMode.value || boardReturnSubmitting.value) {
      return;
    }
    boardEditMode.value = false;
    selectedReturnTileIds.value = [];
  }

  function toggleBoardReturnSelection(cell: BoardCellDto): void {
    if (!boardEditMode.value || boardReturnSubmitting.value) {
      return;
    }
    if (activeDraftBoardTileIds.value.has(cell.tileId)) {
      toast.value = { type: "info", message: "Remove it from the current draft first." };
      return;
    }
    if (selectedReturnTileIds.value.includes(cell.tileId)) {
      selectedReturnTileIds.value = selectedReturnTileIds.value.filter((tileId) => tileId !== cell.tileId);
    } else {
      selectedReturnTileIds.value = [...selectedReturnTileIds.value, cell.tileId];
    }
  }

  function setAnchorCell(nextAnchor: { x: number; y: number }): void {
    const boardCell = boardCellAt(nextAnchor);
    if (boardEditMode.value) {
      if (boardCell) {
        toggleBoardReturnSelection(boardCell);
      }
      return;
    }
    if (boardCell) {
      toggleBoardCell(boardCell);
      return;
    }

    if (candidateUsesBoardLetters.value) {
      return;
    }

    anchor.value = nextAnchor;
    suggestions.value = [];
    clearSuggestionSelection();
    preview.value = buildLocalPreview(nextAnchor, orientation.value);
    requestPreviewAt(nextAnchor, orientation.value);
  }

  function toggleOrientation(): void {
    if (isComposeLocked.value) {
      return;
    }
    if (!canToggleOrientation.value) {
      return;
    }
    orientation.value = orientation.value === "HORIZONTAL" ? "VERTICAL" : "HORIZONTAL";
    clearSuggestionSelection();
    refreshDraftState();
  }

  function selectSuggestion(suggestion: PlacementSuggestion): void {
    if (isComposeLocked.value) {
      return;
    }
    if (candidateUsesBoardLetters.value) {
      return;
    }
    selectedSuggestionId.value = suggestion.suggestionId;
    selectedSuggestionTiles.value = suggestion.tileAssignments;
    orientation.value = suggestion.orientation;
    anchor.value = { ...suggestion.start };
    preview.value = {
      valid: true,
      invalidReason: null,
      previewCells: suggestion.previewCells,
      crossesOrigin: suggestion.previewCells.some((cell) => cell.x === 0 && cell.y === 0),
      connectedToCluster: true,
      usesBandit: candidate.value.some((tile) => tile.kind === "BANDIT")
    };
    viewport.value = { centerX: suggestion.start.x, centerY: suggestion.start.y, zoom: viewport.value.zoom };
    mode.value = "composing";
  }

  function setViewport(nextViewport: ViewportState): void {
    if (
      viewport.value.centerX === nextViewport.centerX &&
      viewport.value.centerY === nextViewport.centerY &&
      viewport.value.zoom === nextViewport.zoom
    ) {
      return;
    }
    viewport.value = nextViewport;
  }

  function requestPreview(): void {
    const placement = resolveDraftPlacement();
    const nextStart = placement.usesBoardLetters ? placement.start : anchor.value;
    const nextOrientation = placement.usesBoardLetters ? placement.orientation : orientation.value;
    if (!nextStart || !nextOrientation) {
      return;
    }
    requestPreviewAt(nextStart, nextOrientation);
  }

  function refreshSuggestions(): void {
    if (!self.value || !roomId.value || candidateWord.value.length === 0 || status.value !== "ACTIVE" || candidateUsesBoardLetters.value) {
      suggestions.value = [];
      return;
    }
    realtimeClient.requestSuggestions(
      { roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value },
      {
        candidateWord: candidateWord.value,
        deviceType: detectDeviceType() as DeviceType
      }
    );
  }

  function confirmPlacement(): void {
    if (isComposeLocked.value) {
      return;
    }
    if (!self.value || !roomId.value || candidateWord.value.length === 0) {
      return;
    }
    const placement = resolveDraftPlacement();
    const nextStart = placement.usesBoardLetters ? placement.start : anchor.value;
    const nextOrientation = placement.usesBoardLetters ? placement.orientation : orientation.value;
    if (!nextStart || !nextOrientation) {
      return;
    }
    const tiles = selectedSuggestionTiles.value ?? selectedTileAssignments();
    realtimeClient.confirmPlacement(
      { roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value },
      {
        candidateWord: candidateWord.value,
        orientation: nextOrientation,
        start: nextStart,
        tiles,
        banditTheme: null
      }
    );
  }

  function returnSelectedBoardTiles(): void {
    if (!self.value || !roomId.value || !canSubmitBoardReturn.value) {
      return;
    }
    boardReturnSubmitting.value = true;
    const requestId = realtimeClient.returnBoardTiles(
      { roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value },
      { tileIds: [...selectedReturnTileIds.value] }
    );
    if (!requestId) {
      boardReturnSubmitting.value = false;
      toast.value = { type: "error", message: "Realtime connection is unavailable. Board letters were not returned." };
    }
  }

  async function runGridValidation(): Promise<GridValidationResponse | null> {
    if (!self.value || !roomId.value) {
      return null;
    }

    mixmoValidationState.value = "validating";
    mixmoValidationError.value = null;
    invalidMixmoWords.value = [];

    try {
      const response = await validateMixmoGrid(roomId.value, {
        playerId: self.value.playerId,
        sessionToken: self.value.sessionToken,
        expectedRoomVersion: roomVersion.value,
        boardCells: board.value.map((cell) => ({
          x: cell.x,
          y: cell.y,
          letter: cell.resolvedLetter
        }))
      });

      applyMixmoValidation(response.data);
      mixmoValidationState.value = "success";
      return response.data;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Grid validation could not be completed.";
      mixmoTriggerPending.value = false;
      mixmoValidationState.value = "error";
      mixmoValidationError.value = message;
      toast.value = { type: "error", message };
      return null;
    }
  }

  async function validateGridWords(): Promise<void> {
    if (!canValidateGridWords.value) {
      return;
    }

    const validation = await runGridValidation();
    if (!validation) {
      return;
    }

    toast.value = validation.gridValid
      ? { type: "success", message: "All grid words are valid." }
      : { type: "info", message: `${validation.invalidWords.length} invalid grid ${validation.invalidWords.length === 1 ? "word" : "words"} found.` };
  }

  async function triggerMixmo(): Promise<void> {
    if (!self.value || !roomId.value || !canMixmo.value) {
      return;
    }

    const validation = await runGridValidation();
    if (!validation || !validation.gridValid) {
      return;
    }

    mixmoTriggerPending.value = true;
    const requestId = realtimeClient.triggerMixmo({ roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value });
    if (!requestId) {
      mixmoTriggerPending.value = false;
      mixmoValidationState.value = "error";
      mixmoValidationError.value = "Realtime connection is unavailable. MIXMO was not triggered.";
      toast.value = { type: "error", message: mixmoValidationError.value };
    }
  }

  function selectBanditTheme(theme: ThemeOption): void {
    if (!self.value || !roomId.value) {
      return;
    }
    realtimeClient.selectBanditTheme({ roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value }, theme);
  }

  function syncAfterReconnect(snapshot: GameSnapshot): void {
    applySnapshot(snapshot);
    connectRealtime();
  }

  function selectedTileAssignments(): RequestedTile[] {
    return candidate.value
      .filter((tile) => tile.source === "rack")
      .map((tile) => ({
        tileId: tile.tileId,
        resolvedLetter: tile.letter
      }));
  }

  function dismissToast(): void {
    toast.value = null;
  }

  function applyMixmoValidation(validation: GridValidationResponse): void {
    boardValidationStatuses.value = { ...(validation.letterStatuses ?? {}) };
    invalidMixmoWords.value = [...(validation.invalidWords ?? [])];
  }

  function clearMixmoValidationFeedback(): void {
    mixmoValidationState.value = "idle";
    mixmoValidationError.value = null;
    invalidMixmoWords.value = [];
    boardValidationStatuses.value = {};
  }

  function boardCellAt(position: { x: number; y: number }): BoardCellDto | undefined {
    return boardCellByPosition.value.get(boardCellKey(position.x, position.y));
  }

  function createBoardCandidate(cell: BoardCellDto): CandidateTile {
    return {
      tileId: `board:${cell.x}:${cell.y}`,
      kind: cell.tileKind,
      face: cell.resolvedLetter,
      letter: cell.resolvedLetter,
      source: "board",
      boardCell: { x: cell.x, y: cell.y }
    };
  }

  function createRackCandidate(tile: RackTile, letter: string): CandidateTile {
    return {
      tileId: tile.tileId,
      kind: tile.kind,
      face: tile.face,
      letter,
      source: "rack"
    };
  }

  function isBoardCandidate(tile: CandidateTile): tile is CandidateTile & { source: "board"; boardCell: { x: number; y: number } } {
    return tile.source === "board" && tile.boardCell != null;
  }

  function lineSpanLength(start: { x: number; y: number }, delta: { x: number; y: number }): number {
    let length = 1;
    let step = 1;
    while (boardCellAt({ x: start.x + delta.x * step, y: start.y + delta.y * step })) {
      length += 1;
      step += 1;
    }
    step = 1;
    while (boardCellAt({ x: start.x - delta.x * step, y: start.y - delta.y * step })) {
      length += 1;
      step += 1;
    }
    return length;
  }

  function inferOrientationFromBoardCell(position: { x: number; y: number }): Orientation {
    const horizontalSpan = lineSpanLength(position, { x: 1, y: 0 });
    const verticalSpan = lineSpanLength(position, { x: 0, y: 1 });
    if (horizontalSpan === verticalSpan) {
      return orientation.value;
    }
    return horizontalSpan > verticalSpan ? "HORIZONTAL" : "VERTICAL";
  }

  // A mixed draft is valid when every reused board letter falls on one consistent line.
  function resolveDraftPlacement(): DraftPlacement {
    if (candidate.value.length === 0) {
      return {
        start: null,
        orientation: orientation.value,
        usesBoardLetters: false,
        feedback: null
      };
    }

    const boardEntries = candidate.value
      .map((tile, index) => ({ tile, index }))
      .filter((entry): entry is { tile: CandidateTile & { source: "board"; boardCell: { x: number; y: number } }; index: number } => isBoardCandidate(entry.tile));

    if (boardEntries.length === 0) {
      return {
        start: anchor.value,
        orientation: orientation.value,
        usesBoardLetters: false,
        feedback: anchor.value ? null : null
      };
    }

    const derivedOrientation = boardEntries.length === 1
      ? orientation.value
      : boardEntries.every((entry) => entry.tile.boardCell.x === boardEntries[0].tile.boardCell.x)
        ? "VERTICAL"
        : boardEntries.every((entry) => entry.tile.boardCell.y === boardEntries[0].tile.boardCell.y)
          ? "HORIZONTAL"
          : null;

    if (!derivedOrientation) {
      return {
        start: null,
        orientation: null,
        usesBoardLetters: true,
        feedback: "Board letters must stay in a single row or column."
      };
    }

    const delta = orientationDelta(derivedOrientation);
    let start: { x: number; y: number } | null = null;
    for (const entry of boardEntries) {
      const nextStart = {
        x: entry.tile.boardCell.x - delta.x * entry.index,
        y: entry.tile.boardCell.y - delta.y * entry.index
      };
      if (!start) {
        start = nextStart;
        continue;
      }
      if (start.x !== nextStart.x || start.y !== nextStart.y) {
        return {
          start: null,
          orientation: derivedOrientation,
          usesBoardLetters: true,
          feedback: "Tap board letters in word order so the draft stays aligned."
        };
      }
    }

    return {
      start,
      orientation: derivedOrientation,
      usesBoardLetters: true,
      feedback: null
    };
  }

  function buildLocalPreview(nextStart: { x: number; y: number } | null, nextOrientation: Orientation | null): PlacementPreviewResult | null {
    if (!nextStart || !nextOrientation || candidateWord.value.length === 0) {
      return null;
    }

    const delta = orientationDelta(nextOrientation);
    const previewCells = candidate.value.map((tile, index) => {
      const x = nextStart.x + delta.x * index;
      const y = nextStart.y + delta.y * index;
      const boardCell = boardCellAt({ x, y });
      if (boardCell && boardCell.resolvedLetter.toUpperCase() !== tile.letter.toUpperCase()) {
        return { x, y, letter: tile.letter, state: "CONFLICT" as const };
      }
      if (boardCell) {
        return { x, y, letter: tile.letter, state: "OVERLAP_MATCH" as const };
      }
      return { x, y, letter: tile.letter, state: "NEW" as const };
    });

    const hasConflict = previewCells.some((cell) => cell.state === "CONFLICT");
    return {
      valid: false,
      invalidReason: hasConflict ? "collision_with_different_letter" : null,
      previewCells,
      crossesOrigin: previewCells.some((cell) => cell.x === 0 && cell.y === 0),
      connectedToCluster: board.value.length === 0 || previewCells.some((cell) => cell.state === "OVERLAP_MATCH"),
      usesBandit: candidate.value.some((tile) => tile.kind === "BANDIT")
    };
  }

  function clearSuggestionSelection(): void {
    selectedSuggestionId.value = null;
    selectedSuggestionTiles.value = null;
  }

  function insertCandidate(nextTile: CandidateTile): void {
    candidate.value.push(nextTile);
    candidateHistory.value.push(nextTile.tileId);
    suggestions.value = [];
    clearSuggestionSelection();
  }

  function removeCandidateByTileId(tileId: string, pruneHistory = true): boolean {
    const index = candidate.value.findIndex((tile) => tile.tileId === tileId);
    if (index < 0) {
      return false;
    }
    candidate.value.splice(index, 1);
    if (pruneHistory) {
      candidateHistory.value = candidateHistory.value.filter((entry) => entry !== tileId);
    }
    suggestions.value = [];
    clearSuggestionSelection();
    return true;
  }

  function toggleBoardCell(cell: BoardCellDto): void {
    const tileId = `board:${cell.x}:${cell.y}`;
    if (removeCandidateByTileId(tileId)) {
      refreshDraftState();
      return;
    }
    if (candidate.value.length === 0) {
      orientation.value = inferOrientationFromBoardCell({ x: cell.x, y: cell.y });
    }
    insertCandidate(createBoardCandidate(cell));
    refreshDraftState();
  }

  function requestPreviewAt(nextStart: { x: number; y: number }, nextOrientation: Orientation): void {
    if (!self.value || !roomId.value || candidateWord.value.length === 0) {
      return;
    }
    preview.value = buildLocalPreview(nextStart, nextOrientation);
    realtimeClient.requestPreview(
      { roomId: roomId.value, playerId: self.value.playerId, roomVersion: roomVersion.value },
      {
        candidateWord: candidateWord.value,
        orientation: nextOrientation,
        start: nextStart,
        tiles: selectedTileAssignments()
      }
    );
  }

  function refreshDraftState(): void {
    syncSelection();
    if (candidate.value.length === 0) {
      preview.value = null;
      suggestions.value = [];
      clearSuggestionSelection();
      anchor.value = null;
      mode.value = status.value === "PAUSED" ? "banditTheme" : status.value === "FINISHED" ? "confirmed" : "idle";
      return;
    }
    if (status.value !== "ACTIVE") {
      mode.value = status.value === "PAUSED" ? "banditTheme" : mode.value;
      return;
    }

    mode.value = "composing";
    const placement = resolveDraftPlacement();
    if (placement.usesBoardLetters) {
      suggestions.value = [];
      clearSuggestionSelection();
      anchor.value = placement.start;
      if (placement.orientation) {
        orientation.value = placement.orientation;
      }
      preview.value = buildLocalPreview(placement.start, placement.orientation);
      if (!placement.start || !placement.orientation || placement.feedback) {
        return;
      }
      requestPreviewAt(placement.start, placement.orientation);
      return;
    }

    preview.value = anchor.value ? buildLocalPreview(anchor.value, orientation.value) : null;
    if (anchor.value) {
      requestPreviewAt(anchor.value, orientation.value);
      return;
    }
    refreshSuggestions();
  }

  function syncSelection(): void {
    const selectedIds = new Set(candidate.value.filter((tile) => tile.source === "rack").map((tile) => tile.tileId));
    rack.value = rack.value.map((tile) => ({
      ...tile,
      selected: selectedIds.has(tile.tileId)
    }));
  }

  function resetDraft(): void {
    candidate.value = [];
    preview.value = null;
    suggestions.value = [];
    clearSuggestionSelection();
    anchor.value = null;
    orientation.value = "HORIZONTAL";
    candidateHistory.value = [];
    mode.value = status.value === "PAUSED" ? "banditTheme" : status.value === "FINISHED" ? "confirmed" : "idle";
    syncSelection();
  }

  return {
    roomId,
    roomCode,
    roomVersion,
    hostPlayerId,
    self,
    status,
    players,
    bagRemaining,
    winnerPlayerId,
    currentBanditTheme,
    pauseReason,
    pauseTriggeringPlayerId,
    themeOptions,
    rack,
    board,
    validatedBoard,
    mode,
    orientation,
    anchor,
    candidate,
    candidateWord,
    preview,
    suggestions,
    selectedSuggestionId,
    viewport,
    toast,
    staleGameState,
    staleGameActive,
    staleGameMessage,
    spectatablePlayers,
    hasSpectatablePlayers,
    mixmoValidationState,
    mixmoValidationError,
    invalidMixmoWords,
    wildcardPicker,
    connectionReady,
    lastResolvedEventSequence,
    boardEditMode,
    boardEditSelectionCount,
    boardEditFeedback,
    boardReturnSubmitting,
    selectedReturnCells,
    candidateUsesBoardLetters,
    selectedBoardCells,
    playerBoardCache,
    playerBoardLoading,
    viewerBoardError,
    chatMessages,
    chatLatestSequence,
    chatLastReadSequence,
    chatUnreadCount,
    chatLoading,
    chatError,
    canEnterBoardEditMode,
    canSubmitBoardReturn,
    canValidateGridWords,
    isComposeLocked,
    canToggleOrientation,
    canMixmo,
    isBanditThemeOpen,
    isBanditSelectorOwner,
    composerFeedback,
    hydrateBootstrap,
    applySnapshot,
    connectRealtime,
    disconnectRealtime,
    toggleRackTile,
    chooseWildcardLetter,
    closeWildcardPicker,
    removeLastCandidateTile,
    clearCandidate,
    enterBoardEditMode,
    cancelBoardEditMode,
    setAnchorCell,
    toggleOrientation,
    selectSuggestion,
    setViewport,
    loadPlayerBoard,
    playerBoardViewport,
    setPlayerBoardViewport,
    requestPreview,
    refreshSuggestions,
    loadChatHistory,
    markChatRead,
    dismissChatError,
    sendChatMessage,
    validateGridWords,
    confirmPlacement,
    returnSelectedBoardTiles,
    triggerMixmo,
    selectBanditTheme,
    syncAfterReconnect,
    dismissToast
  };
});
