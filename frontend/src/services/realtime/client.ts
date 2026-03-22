import type { Orientation, RequestedTile, SocketEventEnvelope } from "@/models/types";

const defaultBaseUrl = import.meta.env.VITE_WS_BASE_URL ?? "ws://localhost:8080/ws";

function requestId(): string {
  return crypto.randomUUID();
}

export class RealtimeClient {
  private socket: WebSocket | null = null;

  constructor(private readonly baseUrl = defaultBaseUrl) {}

  connect(params: { roomId: string; playerId: string; sessionToken: string; onEvent: (event: SocketEventEnvelope) => void; onClose: () => void }) {
    this.close();
    const query = new URLSearchParams({
      roomId: params.roomId,
      playerId: params.playerId,
      sessionToken: params.sessionToken
    });
    this.socket = new WebSocket(`${this.baseUrl}?${query.toString()}`);
    this.socket.addEventListener("message", (message) => {
      params.onEvent(JSON.parse(message.data) as SocketEventEnvelope);
    });
    this.socket.addEventListener("close", () => params.onClose());
  }

  close() {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  send(type: string, context: { roomId: string; playerId: string; roomVersion: number }, payload: object) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return null;
    }
    const id = requestId();
    this.socket.send(JSON.stringify({
      type,
      requestId: id,
      roomId: context.roomId,
      playerId: context.playerId,
      expectedRoomVersion: context.roomVersion,
      payload
    }));
  return id;
}

  requestSync(context: { roomId: string; playerId: string; roomVersion: number }, lastKnownEventSequence: number) {
    return this.send("game.sync.request", context, { lastKnownEventSequence });
  }

  requestPreview(context: { roomId: string; playerId: string; roomVersion: number }, payload: { candidateWord: string; orientation: Orientation; start: { x: number; y: number }; tiles: RequestedTile[] }) {
    return this.send("placement.preview.request", context, payload);
  }

  requestSuggestions(context: { roomId: string; playerId: string; roomVersion: number }, payload: { candidateWord: string; deviceType: string }) {
    return this.send("placement.suggestions.request", context, payload);
  }

  confirmPlacement(context: { roomId: string; playerId: string; roomVersion: number }, payload: { candidateWord: string; orientation: Orientation; start: { x: number; y: number }; tiles: RequestedTile[]; banditTheme: string | null }) {
    return this.send("placement.confirm.request", context, payload);
  }

  returnBoardTiles(context: { roomId: string; playerId: string; roomVersion: number }, payload: { tileIds: string[] }) {
  return this.send("board.tiles.return.request", context, payload);
  }

  sendChatMessage(context: { roomId: string; playerId: string; roomVersion: number }, text: string) {
    return this.send("chat.message.send", context, { text });
  }

  triggerMixmo(context: { roomId: string; playerId: string; roomVersion: number }) {
    return this.send("mixmo.trigger", context, {});
  }

  selectBanditTheme(context: { roomId: string; playerId: string; roomVersion: number }, theme: string) {
    return this.send("bandit.theme.select", context, { theme });
  }
}
