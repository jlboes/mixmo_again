import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RoomView from "./RoomView.vue";

const shared = vi.hoisted(() => {
  return {
    push: vi.fn(),
    reconnect: vi.fn(),
    fetchState: vi.fn(),
    leaveRoom: vi.fn(),
    startRoom: vi.fn(),
    replace: vi.fn(),
    readSession: vi.fn(),
    clearSession: vi.fn(),
    routeLeaveGuard: null as null | (() => unknown | Promise<unknown>),
    width: 375,
    route: {
      params: { roomId: "room-1" },
      query: {} as Record<string, string>
    },
    game: {} as Record<string, unknown>
  };
});

vi.mock("vue-router", () => ({
  onBeforeRouteLeave: (guard: () => unknown | Promise<unknown>) => {
    shared.routeLeaveGuard = guard;
  },
  useRoute: () => shared.route,
  useRouter: () => ({ push: shared.push, replace: shared.replace })
}));

vi.mock("@vueuse/core", () => ({
  useWindowSize: () => ({ width: { value: shared.width } })
}));

vi.mock("@/services/api/rooms", () => ({
  fetchState: shared.fetchState,
  leaveRoom: shared.leaveRoom,
  reconnect: shared.reconnect,
  startRoom: shared.startRoom
}));

vi.mock("@/utils/storage", () => ({
  clearSession: shared.clearSession,
  readSession: shared.readSession
}));

vi.mock("@/stores/gameStore", () => ({
  useGameStore: () => shared.game
}));

function createGameState() {
  return {
    toast: null,
    dismissToast: vi.fn(),
    status: "ACTIVE",
    roomCode: "ABCD12",
    roomId: "room-1",
    roomVersion: 1,
    hostPlayerId: "player-1",
    lastResolvedEventSequence: 0,
    self: {
      roomId: "room-1",
      roomCode: "ABCD12",
      playerId: "player-1",
      playerName: "Jean",
      seatOrder: 1,
      sessionToken: "session-token"
    },
    players: [
      { playerId: "player-1", playerName: "Jean", seatOrder: 1, connected: true, rackCount: 6, boardCellCount: 4 },
      { playerId: "player-2", playerName: "Alice", seatOrder: 2, connected: true, rackCount: 5, boardCellCount: 7 }
    ],
    bagRemaining: 18,
    currentBanditTheme: null,
    validatedBoard: [],
    preview: { valid: true, invalidReason: null, previewCells: [], crossesOrigin: true, connectedToCluster: true, usesBandit: false },
    suggestions: [
      {
        suggestionId: "sg-1",
        start: { x: 1, y: 2 },
        orientation: "HORIZONTAL",
        victoryCount: 4,
        previewCells: [],
        tileAssignments: [],
        label: "AIMER"
      }
    ],
    selectedSuggestionId: null,
    selectedBoardCells: [],
    selectedReturnCells: [],
    anchor: null,
    isComposeLocked: false,
    viewport: { centerX: 0, centerY: 0, zoom: 1 },
    setAnchorCell: vi.fn(),
    selectSuggestion: vi.fn(),
    setViewport: vi.fn(),
    candidate: [{ tileId: "tile-1", kind: "NORMAL", face: "A", letter: "A", source: "rack" }],
    removeLastCandidateTile: vi.fn(),
    clearCandidate: vi.fn(),
    boardEditMode: false,
    canEnterBoardEditMode: true,
    enterBoardEditMode: vi.fn(),
    boardReturnSubmitting: false,
    cancelBoardEditMode: vi.fn(),
    canValidateGridWords: true,
    canToggleOrientation: true,
    toggleOrientation: vi.fn(),
    orientation: "HORIZONTAL",
    canSubmitBoardReturn: false,
    validateGridWords: vi.fn(),
    returnSelectedBoardTiles: vi.fn(),
    canMixmo: false,
    triggerMixmo: vi.fn(),
    rack: [{ tileId: "tile-1", kind: "NORMAL", face: "A", assignedLetter: null, selected: false }],
    toggleRackTile: vi.fn(),
    boardEditFeedback: null,
    staleGameMessage: null,
    composerFeedback: "The preview is ready. Place the word when the highlighted board letters look right.",
    mixmoValidationState: "idle",
    mixmoValidationError: null,
    invalidMixmoWords: [],
    candidateUsesBoardLetters: false,
    wildcardPicker: { open: false, tileId: null, kind: null },
    chooseWildcardLetter: vi.fn(),
    closeWildcardPicker: vi.fn(),
    isBanditThemeOpen: false,
    isBanditSelectorOwner: false,
    themeOptions: [],
    selectBanditTheme: vi.fn(),
    hydrateBootstrap: vi.fn(),
    syncAfterReconnect: vi.fn(),
    applySnapshot: vi.fn(),
    connectRealtime: vi.fn(),
    disconnectRealtime: vi.fn(),
    winnerPlayerId: null,
    spectatablePlayers: [
      { playerId: "player-2", playerName: "Alice", seatOrder: 2, connected: true, rackCount: 5, boardCellCount: 7 }
    ],
    hasSpectatablePlayers: true,
    playerBoardCache: {},
    playerBoardLoading: {},
    viewerBoardError: null,
    loadPlayerBoard: vi.fn(),
    playerBoardViewport: vi.fn(() => ({ centerX: 0, centerY: 0, zoom: 1 })),
    setPlayerBoardViewport: vi.fn(),
    chatMessages: [],
    chatLatestSequence: 0,
    chatLastReadSequence: 0,
    chatUnreadCount: 2,
    chatLoading: false,
    chatError: null,
    loadChatHistory: vi.fn().mockResolvedValue(undefined),
    markChatRead: vi.fn(),
    dismissChatError: vi.fn(),
    sendChatMessage: vi.fn()
  };
}

describe("RoomView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.assign(shared.game, createGameState());
    shared.width = 375;
    shared.route.query = {};
    shared.readSession.mockReturnValue({
      roomId: "room-1",
      roomCode: "ABCD12",
      playerId: "player-1",
      playerName: "Jean",
      seatOrder: 1,
      sessionToken: "session-token"
    });
    shared.reconnect.mockResolvedValue({ data: { gameSnapshot: {} } });
    shared.fetchState.mockResolvedValue({ data: {} });
    shared.leaveRoom.mockResolvedValue({ data: { left: true, roomDeleted: false } });
    shared.startRoom.mockResolvedValue({ data: { gameSnapshot: {} } });
    shared.routeLeaveGuard = null;
    shared.replace.mockResolvedValue(undefined);
  });

  it("renders the active mobile room with compact Players and Chat entry points", async () => {
    const wrapper = shallowMount(RoomView);

    await flushPromises();

    expect(wrapper.find(".mixmo-play-header").exists()).toBe(true);
    expect(wrapper.find(".mixmo-play-board").exists()).toBe(true);
    expect(shared.game.loadChatHistory).toHaveBeenCalledTimes(1);

    const controls = wrapper.get(".mixmo-play-controls");
    expect(controls.find(".mixmo-workflow-section").exists()).toBe(true);
    expect(controls.find("candidate-strip-stub").exists()).toBe(true);
    expect(controls.find("rack-panel-stub").exists()).toBe(true);
    expect(wrapper.findAll(".mixmo-companion-button")).toHaveLength(2);
    expect(controls.text()).toContain("Place");
    expect(controls.text()).toContain("Sort Letters");
    expect(controls.text()).toContain("Default");
    expect(controls.text()).toContain("A-Z");
    expect(controls.text()).toContain("Validate grid");
    expect(controls.text()).toContain("Status");
    expect(wrapper.find("room-chat-panel-stub").exists()).toBe(false);
    expect(wrapper.find("player-board-viewer-stub").exists()).toBe(false);
  });

  it("groups place word with a single orientation toggle", async () => {
    const wrapper = shallowMount(RoomView, {
      global: {
        stubs: {
          CandidateStrip: {
            template: '<div><slot name="after-draft" /></div>'
          }
        }
      }
    });

    await flushPromises();

    const actionRow = wrapper.get(".mixmo-placement-action-row");
    expect(actionRow.text()).toContain("Place Word");
    expect(actionRow.find(".mixmo-orientation-toggle").exists()).toBe(true);
    expect(actionRow.findAll(".mixmo-segment-button")).toHaveLength(0);
    expect(wrapper.text()).toContain("Remove mode");
    expect(wrapper.text()).not.toContain("Edit");

    await actionRow.get(".mixmo-orientation-toggle").trigger("click");

    expect(shared.game.toggleOrientation).toHaveBeenCalledTimes(1);
  });

  it("renders chat as a dedicated mobile companion screen when panel=chat", async () => {
    shared.route.query = { panel: "chat" };

    const wrapper = shallowMount(RoomView);
    await flushPromises();

    expect(wrapper.find(".mixmo-companion-overlay").exists()).toBe(true);
    expect(wrapper.find("room-chat-panel-stub").exists()).toBe(true);
    expect(shared.game.markChatRead).toHaveBeenCalled();
  });

  it("renders desktop companion tabs and inline chat on wide screens", async () => {
    shared.width = 1400;
    shared.route.query = { panel: "chat" };

    const wrapper = shallowMount(RoomView);
    await flushPromises();

    expect(wrapper.findAll(".mixmo-companion-button")).toHaveLength(0);
    expect(wrapper.find(".mixmo-companion-overlay").exists()).toBe(false);
    expect(wrapper.find("room-chat-panel-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("Companion");
    expect(wrapper.text()).toContain("Chat");
  });

  it("renders the player viewer overlay when panel=players targets another player", async () => {
    shared.width = 1400;
    shared.route.query = { panel: "players", viewerPlayerId: "player-2" };

    const wrapper = shallowMount(RoomView);
    await flushPromises();

    expect(wrapper.find(".mixmo-companion-overlay").exists()).toBe(true);
    expect(wrapper.find("player-board-viewer-stub").exists()).toBe(true);
    expect(shared.game.loadPlayerBoard).toHaveBeenCalledWith("player-2");
  });

  it("lets the current host start from the lobby even after host transfer away from seat one", async () => {
    Object.assign(shared.game, createGameState(), {
      status: "WAITING",
      hostPlayerId: "player-1",
      self: {
        roomId: "room-1",
        roomCode: "ABCD12",
        playerId: "player-1",
        playerName: "Jean",
        seatOrder: 2,
        sessionToken: "session-token"
      }
    });

    const wrapper = shallowMount(RoomView);
    await flushPromises();

    expect(wrapper.getComponent({ name: "LobbyPanel" }).props("canStart")).toBe(true);
  });

  it("closes realtime when the room view unmounts", async () => {
    const wrapper = shallowMount(RoomView);

    await flushPromises();
    wrapper.unmount();

    expect(shared.game.disconnectRealtime).toHaveBeenCalledTimes(1);
  });

  it("leaves a waiting lobby on route navigation and clears the saved session", async () => {
    Object.assign(shared.game, createGameState(), {
      status: "WAITING"
    });

    shallowMount(RoomView);
    await flushPromises();
    await shared.routeLeaveGuard?.();

    expect(shared.leaveRoom).toHaveBeenCalledWith("room-1", {
      playerId: "player-1",
      sessionToken: "session-token"
    });
    expect(shared.clearSession).toHaveBeenCalledWith("room-1");
    expect(shared.game.disconnectRealtime).toHaveBeenCalledTimes(1);
  });

  it("does not call the lobby leave API when navigating away from an active game", async () => {
    shallowMount(RoomView);
    await flushPromises();
    await shared.routeLeaveGuard?.();

    expect(shared.leaveRoom).not.toHaveBeenCalled();
  });

  it("clears a stale saved session and returns home when reconnect and fetch both fail", async () => {
    shared.reconnect.mockRejectedValueOnce(new Error("stale"));
    shared.fetchState.mockRejectedValueOnce(new Error("gone"));

    shallowMount(RoomView);
    await flushPromises();

    expect(shared.clearSession).toHaveBeenCalledWith("room-1");
    expect(shared.push).toHaveBeenCalledWith({ name: "home" });
  });
});
