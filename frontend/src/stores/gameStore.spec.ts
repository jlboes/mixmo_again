import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { GameSnapshot, ThemeOption } from "@/models/types";

const { realtimeClientMock, validateMixmoGridMock, fetchPlayerBoardMock, fetchChatHistoryMock } = vi.hoisted(() => ({
  realtimeClientMock: {
    connect: vi.fn(),
    close: vi.fn(),
    requestSync: vi.fn(),
    requestPreview: vi.fn(),
    requestSuggestions: vi.fn(),
    confirmPlacement: vi.fn(),
    returnBoardTiles: vi.fn(),
    sendChatMessage: vi.fn(),
    triggerMixmo: vi.fn(),
    selectBanditTheme: vi.fn()
  },
  validateMixmoGridMock: vi.fn(),
  fetchPlayerBoardMock: vi.fn(),
  fetchChatHistoryMock: vi.fn()
}));

vi.mock("@/services/realtime/client", () => ({
  RealtimeClient: vi.fn().mockImplementation(() => realtimeClientMock)
}));

vi.mock("@/services/api/rooms", async () => {
  const actual = await vi.importActual<typeof import("@/services/api/rooms")>("@/services/api/rooms");
  return {
    ...actual,
    fetchPlayerBoard: fetchPlayerBoardMock,
    fetchChatHistory: fetchChatHistoryMock,
    validateMixmoGrid: validateMixmoGridMock
  };
});

import { useGameStore } from "./gameStore";

function snapshot(partial: Partial<GameSnapshot> = {}): GameSnapshot {
  return {
    roomId: "room-1",
    roomCode: "ABCD12",
    status: "ACTIVE",
    roomVersion: 3,
    bagRemaining: 80,
    pauseReason: null,
    currentBanditTheme: null,
    winnerPlayerId: null,
    selfPlayerId: "player-1",
    hostPlayerId: "player-1",
    players: [
      { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 2, boardCellCount: 0 }
    ],
    selfRack: [
      { tileId: "a", kind: "NORMAL", face: "A", assignedLetter: null },
      { tileId: "r", kind: "NORMAL", face: "R", assignedLetter: null }
    ],
    selfBoard: [],
    candidateWordState: null,
    actionState: {
      canTriggerMixmo: false,
      canConfirmPlacement: true,
      canSelectBanditTheme: false,
      mixmoReason: "Mixmo is available only when all rack letters are placed"
    },
    themeState: {
      themeOptions: [
        "Animals",
        "Food & Drinks",
        "Countries & Cities",
        "Nature",
        "Jobs / Professions",
        "Sports",
        "Technology",
        "Movies & Entertainment",
        "Transportation",
        "Household Objects"
      ] as ThemeOption[],
      currentBanditTheme: null,
      paused: false,
      pauseTriggeringPlayerId: null
    },
    staleGameState: {
      warningActive: false,
      message: null,
      automaticMixmoAt: null
    },
    lastResolvedEventSequence: 5,
    updatedAt: new Date().toISOString(),
    ...partial
  };
}

function hydrateActiveSelf(store: ReturnType<typeof useGameStore>, roomVersion = 3) {
  store.hydrateBootstrap({
    room: {
      roomId: "room-1",
      roomCode: "ABCD12",
      status: "ACTIVE",
      hostPlayerId: "player-1",
      players: [],
      currentBanditTheme: null,
      pauseReason: null,
      roomVersion
    },
    self: {
      playerId: "player-1",
      playerName: "Jean-Luc",
      seatOrder: 1,
      sessionToken: "token-1"
    }
  });
}

describe("gameStore", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setActivePinia(createPinia());
    realtimeClientMock.connect.mockReset();
    realtimeClientMock.close.mockReset();
    realtimeClientMock.requestSync.mockReset();
    realtimeClientMock.requestPreview.mockReset();
    realtimeClientMock.requestSuggestions.mockReset();
    realtimeClientMock.confirmPlacement.mockReset();
    realtimeClientMock.returnBoardTiles.mockReset();
    realtimeClientMock.sendChatMessage.mockReset();
    realtimeClientMock.triggerMixmo.mockReset();
    realtimeClientMock.selectBanditTheme.mockReset();
    realtimeClientMock.returnBoardTiles.mockReturnValue("board-return-request");
    realtimeClientMock.sendChatMessage.mockReturnValue("chat-request");
    realtimeClientMock.triggerMixmo.mockReturnValue("mixmo-request");
    validateMixmoGridMock.mockReset();
    fetchPlayerBoardMock.mockReset();
    fetchChatHistoryMock.mockReset();
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps word building in composing mode without a separate placement step", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot());

    store.toggleRackTile("a");
    store.toggleRackTile("r");

    expect(store.mode).toBe("composing");
    expect(store.candidateWord).toBe("AR");
  });

  it("appends board letters in selection order while building from the rack", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      selfRack: [
        { tileId: "c", kind: "NORMAL", face: "C", assignedLetter: null }
      ],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" },
        { x: 1, y: 0, resolvedLetter: "T", tileId: "board-t", tileKind: "NORMAL" }
      ]
    }));

    store.toggleRackTile("c");
    store.setAnchorCell({ x: 0, y: 0 });
    store.setAnchorCell({ x: 1, y: 0 });

    expect(store.candidateWord).toBe("CAT");
    expect(store.candidateUsesBoardLetters).toBe(true);
    expect(store.canToggleOrientation).toBe(false);
  });

  it("reflects mixmo disabled and enabled states from the rack state", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot());

    expect(store.canMixmo).toBe(false);

    store.applySnapshot(snapshot({
      roomVersion: 4,
      selfRack: [],
      players: [
        { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 0, boardCellCount: 3 }
      ],
      actionState: {
        canTriggerMixmo: true,
        canConfirmPlacement: false,
        canSelectBanditTheme: false,
        mixmoReason: null
      }
    }));

    expect(store.canMixmo).toBe(true);
  });

  it("uses board edit mode to select placed letters for return without changing the draft", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      selfRack: [
        { tileId: "c", kind: "NORMAL", face: "C", assignedLetter: null }
      ],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" }
      ]
    }));

    store.enterBoardEditMode();
    store.setAnchorCell({ x: 0, y: 0 });

    expect(store.boardEditMode).toBe(true);
    expect(store.boardEditSelectionCount).toBe(1);
    expect(store.selectedReturnCells).toEqual([{ x: 0, y: 0 }]);
    expect(store.candidateWord).toBe("");

    store.setAnchorCell({ x: 0, y: 0 });
    expect(store.boardEditSelectionCount).toBe(0);
  });

  it("blocks returning board letters that are already part of the current draft", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" }
      ]
    }));

    store.setAnchorCell({ x: 0, y: 0 });
    expect(store.candidateWord).toBe("A");

    store.enterBoardEditMode();
    store.setAnchorCell({ x: 0, y: 0 });

    expect(store.boardEditSelectionCount).toBe(0);
    expect(store.toast).toEqual({ type: "info", message: "Remove it from the current draft first." });
  });

  it("submits selected board tiles for authoritative return and preserves an unaffected draft", () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 9);
    store.applySnapshot(snapshot({
      roomVersion: 9,
      selfRack: [
        { tileId: "c", kind: "NORMAL", face: "C", assignedLetter: null }
      ],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" },
        { x: 1, y: 0, resolvedLetter: "T", tileId: "board-t", tileKind: "NORMAL" }
      ]
    }));

    store.toggleRackTile("c");
    expect(store.candidateWord).toBe("C");

    store.enterBoardEditMode();
    store.setAnchorCell({ x: 1, y: 0 });
    store.returnSelectedBoardTiles();

    expect(realtimeClientMock.returnBoardTiles).toHaveBeenCalledWith({
      roomId: "room-1",
      playerId: "player-1",
      roomVersion: 9
    }, {
      tileIds: ["board-t"]
    });
    expect(store.boardReturnSubmitting).toBe(true);

    store.applySnapshot(snapshot({
      roomVersion: 10,
      selfRack: [
        { tileId: "c", kind: "NORMAL", face: "C", assignedLetter: null },
        { tileId: "board-t", kind: "NORMAL", face: "T", assignedLetter: null }
      ],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" }
      ]
    }));

    expect(store.boardEditMode).toBe(false);
    expect(store.boardEditSelectionCount).toBe(0);
    expect(store.boardReturnSubmitting).toBe(false);
    expect(store.candidateWord).toBe("C");
    expect(store.rack.find((tile) => tile.tileId === "c")?.selected).toBe(true);
  });

  it("clears the draft after a snapshot removes a drafted board letter", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "A", tileId: "board-a", tileKind: "NORMAL" }
      ]
    }));

    store.setAnchorCell({ x: 0, y: 0 });
    expect(store.candidateWord).toBe("A");

    store.enterBoardEditMode();
    store.applySnapshot(snapshot({
      roomVersion: 4,
      selfBoard: []
    }));

    expect(store.boardEditMode).toBe(false);
    expect(store.candidateWord).toBe("");
    expect(store.mode).toBe("idle");
  });

  it("enters the bandit blocking state when the room is paused", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      status: "PAUSED",
      pauseReason: "BANDIT_THEME_REQUIRED",
      roomVersion: 6,
      themeState: {
        ...snapshot().themeState,
        paused: true,
        pauseTriggeringPlayerId: "player-2"
      }
    }));

    expect(store.isBanditThemeOpen).toBe(true);
    expect(store.mode).toBe("banditTheme");
    expect(store.isBanditSelectorOwner).toBe(false);
  });

  it("validates the board before dispatching realtime mixmo", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 4);
    store.applySnapshot(snapshot({
      roomVersion: 4,
      selfRack: [],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "M", tileId: "m", tileKind: "NORMAL" },
        { x: 1, y: 0, resolvedLetter: "O", tileId: "o", tileKind: "NORMAL" }
      ],
      players: [
        { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 0, boardCellCount: 2 }
      ]
    }));

    let resolveValidation: ((value: { data: { gridValid: boolean; extractedWords: []; invalidWords: []; letterStatuses: { "0:0": "VALID"; "1:0": "VALID" } } }) => void) | null = null;
    validateMixmoGridMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveValidation = resolve;
        })
    );

    const pending = store.triggerMixmo();

    expect(validateMixmoGridMock).toHaveBeenCalledWith("room-1", {
      playerId: "player-1",
      sessionToken: "token-1",
      expectedRoomVersion: 4,
      boardCells: [
        { x: 0, y: 0, letter: "M" },
        { x: 1, y: 0, letter: "O" }
      ]
    });
    expect(realtimeClientMock.triggerMixmo).not.toHaveBeenCalled();

    expect(resolveValidation).not.toBeNull();
    resolveValidation!({
      data: {
        gridValid: true,
        extractedWords: [],
        invalidWords: [],
        letterStatuses: { "0:0": "VALID", "1:0": "VALID" }
      }
    });
    await pending;

    expect(realtimeClientMock.triggerMixmo).toHaveBeenCalledWith({
      roomId: "room-1",
      playerId: "player-1",
      roomVersion: 4
    });
    expect(store.mixmoValidationState).toBe("success");
  });

  it("can validate the grid manually without triggering realtime mixmo", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 7);
    store.applySnapshot(snapshot({
      roomVersion: 7,
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "M", tileId: "m", tileKind: "NORMAL" },
        { x: 1, y: 0, resolvedLetter: "O", tileId: "o", tileKind: "NORMAL" }
      ]
    }));

    validateMixmoGridMock.mockResolvedValueOnce({
      data: {
        gridValid: true,
        extractedWords: [],
        invalidWords: [],
        letterStatuses: {
          "0:0": "VALID",
          "1:0": "VALID"
        }
      }
    });

    await store.validateGridWords();

    expect(validateMixmoGridMock).toHaveBeenCalledWith("room-1", {
      playerId: "player-1",
      sessionToken: "token-1",
      expectedRoomVersion: 7,
      boardCells: [
        { x: 0, y: 0, letter: "M" },
        { x: 1, y: 0, letter: "O" }
      ]
    });
    expect(realtimeClientMock.triggerMixmo).not.toHaveBeenCalled();
    expect(store.toast).toEqual({ type: "success", message: "All grid words are valid." });
  });

  it("blocks realtime mixmo when backend reports an invalid grid", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 5);
    store.applySnapshot(snapshot({
      roomVersion: 5,
      selfRack: [],
      selfBoard: [
        { x: 0, y: 0, resolvedLetter: "M", tileId: "m", tileKind: "NORMAL" },
        { x: 1, y: 0, resolvedLetter: "A", tileId: "a", tileKind: "NORMAL" },
        { x: 2, y: 0, resolvedLetter: "U", tileId: "u", tileKind: "NORMAL" }
      ],
      players: [
        { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 0, boardCellCount: 3 }
      ]
    }));

    validateMixmoGridMock.mockResolvedValueOnce({
      data: {
        gridValid: false,
        extractedWords: [],
        invalidWords: ["MAU"],
        letterStatuses: {
          "0:0": "INVALID",
          "1:0": "INVALID",
          "2:0": "INVALID"
        }
      }
    });

    await store.triggerMixmo();

    expect(realtimeClientMock.triggerMixmo).not.toHaveBeenCalled();
    expect(store.invalidMixmoWords).toEqual(["MAU"]);
    expect(store.validatedBoard.map((cell) => cell.validationStatus)).toEqual(["INVALID", "INVALID", "INVALID"]);
  });

  it("shows an error state when grid validation fails", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 5);
    store.applySnapshot(snapshot({
      roomVersion: 5,
      selfRack: [],
      players: [
        { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 0, boardCellCount: 0 }
      ]
    }));

    validateMixmoGridMock.mockRejectedValueOnce(new Error("Word validator is unavailable."));

    await store.triggerMixmo();

    expect(realtimeClientMock.triggerMixmo).not.toHaveBeenCalled();
    expect(store.mixmoValidationState).toBe("error");
    expect(store.mixmoValidationError).toBe("Word validator is unavailable.");
    expect(store.toast).toEqual({ type: "error", message: "Word validator is unavailable." });
  });

  it("renders the stale warning countdown from the snapshot state", async () => {
    const store = useGameStore();
    vi.setSystemTime(new Date("2026-03-19T10:00:00Z"));

    store.applySnapshot(snapshot({
      staleGameState: {
        warningActive: true,
        message: "Stale game detected",
        automaticMixmoAt: "2026-03-19T10:00:30Z"
      }
    }));

    expect(store.staleGameActive).toBe(true);
    expect(store.staleGameMessage).toBe("Stale game detected - remaining 30 seconds before automatic MIXMO.");

    await vi.advanceTimersByTimeAsync(1_000);

    expect(store.staleGameMessage).toBe("Stale game detected - remaining 29 seconds before automatic MIXMO.");
  });

  it("clears the stale warning when a new snapshot removes it", () => {
    const store = useGameStore();
    store.applySnapshot(snapshot({
      staleGameState: {
        warningActive: true,
        message: "Stale game detected",
        automaticMixmoAt: "2026-03-19T10:00:30Z"
      }
    }));

    expect(store.staleGameActive).toBe(true);
    expect(store.staleGameMessage).toBeTruthy();

    store.applySnapshot(snapshot({
      roomVersion: 10,
      staleGameState: {
        warningActive: false,
        message: null,
        automaticMixmoAt: null
      }
    }));

    expect(store.staleGameActive).toBe(false);
    expect(store.staleGameMessage).toBeNull();
  });

  it("does not locally auto-trigger mixmo from the stale countdown", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 7);
    store.applySnapshot(snapshot({
      roomVersion: 7,
      selfRack: [],
      staleGameState: {
        warningActive: true,
        message: "Stale game detected",
        automaticMixmoAt: "2026-03-19T10:00:30Z"
      }
    }));

    await vi.advanceTimersByTimeAsync(35_000);

    expect(validateMixmoGridMock).not.toHaveBeenCalled();
    expect(realtimeClientMock.triggerMixmo).not.toHaveBeenCalled();
  });

  it("shows automatic mixmo feedback when the realtime event is backend-triggered", () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 8);
    store.connectRealtime();

    const params = realtimeClientMock.connect.mock.calls[0]?.[0];
    expect(params).toBeTruthy();

    params.onEvent({
      type: "mixmo.resolved",
      requestId: null,
      roomId: "room-1",
      roomVersion: 8,
      payload: {
        triggeredByPlayerId: null,
        drawCountPerPlayer: 2,
        bagRemaining: 12,
        finalMixmo: false,
        triggerReason: "AUTOMATIC"
      }
    });

    expect(store.toast).toEqual({ type: "info", message: "Automatic MIXMO resolved. Everyone drew two tiles." });
  });

  it("restores per-player viewer viewport without affecting the gameplay viewport", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 8);
    store.applySnapshot(snapshot({
      players: [
        { playerId: "player-1", playerName: "Jean-Luc", seatOrder: 1, connected: true, rackCount: 2, boardCellCount: 0 },
        { playerId: "player-2", playerName: "Alice", seatOrder: 2, connected: true, rackCount: 3, boardCellCount: 4 },
        { playerId: "player-3", playerName: "Maya", seatOrder: 3, connected: true, rackCount: 4, boardCellCount: 6 }
      ]
    }));
    fetchPlayerBoardMock
      .mockResolvedValueOnce({
        data: {
          player: { playerId: "player-2", playerName: "Alice", seatOrder: 2, connected: true, rackCount: 3, boardCellCount: 4 },
          boardCells: [],
          roomVersion: 8,
          updatedAt: new Date().toISOString()
        }
      })
      .mockResolvedValueOnce({
        data: {
          player: { playerId: "player-3", playerName: "Maya", seatOrder: 3, connected: true, rackCount: 4, boardCellCount: 6 },
          boardCells: [],
          roomVersion: 8,
          updatedAt: new Date().toISOString()
        }
      });

    store.setViewport({ centerX: 5, centerY: -2, zoom: 1.6 });
    await store.loadPlayerBoard("player-2");
    store.setPlayerBoardViewport("player-2", { centerX: 11, centerY: 3, zoom: 2.1 });
    await store.loadPlayerBoard("player-3");
    store.setPlayerBoardViewport("player-3", { centerX: -4, centerY: 9, zoom: 1.2 });

    expect(store.playerBoardViewport("player-2")).toEqual({ centerX: 11, centerY: 3, zoom: 2.1 });
    expect(store.playerBoardViewport("player-3")).toEqual({ centerX: -4, centerY: 9, zoom: 1.2 });
    expect(store.viewport).toEqual({ centerX: 5, centerY: -2, zoom: 1.6 });
  });

  it("updates chat messages and unread count from realtime events", () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 8);
    store.connectRealtime();

    const params = realtimeClientMock.connect.mock.calls[0]?.[0];
    params.onEvent({
      type: "chat.message.created",
      requestId: null,
      roomId: "room-1",
      roomVersion: 8,
      payload: {
        sequenceNumber: 6,
        playerId: "player-2",
        playerName: "Alice",
        seatOrder: 2,
        text: "Bonjour",
        createdAt: new Date().toISOString()
      }
    });

    expect(store.chatMessages).toHaveLength(1);
    expect(store.chatUnreadCount).toBe(1);

    store.markChatRead();

    expect(store.chatUnreadCount).toBe(0);
  });

  it("uses the persisted chat read watermark when loading recent history", async () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 8);
    localStorage.setItem("mixmo.chat-read.room-1.player-1", "10");
    fetchChatHistoryMock.mockResolvedValueOnce({
      data: {
        latestSequence: 11,
        messages: [
          {
            sequenceNumber: 10,
            playerId: "player-2",
            playerName: "Alice",
            seatOrder: 2,
            text: "Seen",
            createdAt: new Date().toISOString()
          },
          {
            sequenceNumber: 11,
            playerId: "player-2",
            playerName: "Alice",
            seatOrder: 2,
            text: "Unread",
            createdAt: new Date().toISOString()
          }
        ]
      }
    });

    await store.loadChatHistory();

    expect(fetchChatHistoryMock).toHaveBeenCalledWith("room-1", "player-1", "token-1", 50);
    expect(store.chatLastReadSequence).toBe(10);
    expect(store.chatUnreadCount).toBe(1);

    store.markChatRead();

    expect(localStorage.getItem("mixmo.chat-read.room-1.player-1")).toBe("11");
  });

  it("disconnects realtime and resets the connection flag", () => {
    const store = useGameStore();
    hydrateActiveSelf(store, 8);
    store.connectRealtime();

    const params = realtimeClientMock.connect.mock.calls[0]?.[0];
    params.onEvent({
      type: "game.state.updated",
      requestId: null,
      roomId: "room-1",
      roomVersion: 8,
      payload: snapshot({ roomVersion: 8 })
    });

    expect(store.connectionReady).toBe(true);

    store.disconnectRealtime();

    expect(realtimeClientMock.close).toHaveBeenCalledTimes(1);
    expect(store.connectionReady).toBe(false);
  });
});
