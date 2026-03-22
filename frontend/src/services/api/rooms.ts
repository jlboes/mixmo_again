import { apiRequest } from "./client";
import type {
  DeviceType,
  ChatHistory,
  GameSnapshot,
  GridValidationRequest,
  GridValidationResponse,
  PlayerBoardView,
  ReconnectData,
  RoomBootstrapData,
  RoomSummary
} from "@/models/types";

export function createRoom(playerName: string, deviceType: DeviceType) {
  return apiRequest<RoomBootstrapData>("/api/rooms", {
    method: "POST",
    body: JSON.stringify({ playerName, deviceType })
  });
}

export function createDemoRoom(deviceType: DeviceType) {
  return apiRequest<RoomBootstrapData>("/api/rooms/demo", {
    method: "POST",
    body: JSON.stringify({ playerName: "Demo Host", deviceType })
  });
}

export function joinRoom(roomId: string, playerName: string, deviceType: DeviceType, sessionToken?: string | null) {
  return apiRequest<RoomBootstrapData>(`/api/rooms/${roomId}/join`, {
    method: "POST",
    body: JSON.stringify({ playerName, sessionToken: sessionToken ?? null, deviceType })
  });
}

export function leaveRoom(roomId: string, payload: { playerId: string; sessionToken: string }) {
  return apiRequest<{ left: boolean; roomDeleted: boolean }>(`/api/rooms/${roomId}/leave`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function startRoom(roomId: string, requestedByPlayerId: string) {
  return apiRequest<{ gameSnapshot: GameSnapshot }>(`/api/rooms/${roomId}/start`, {
    method: "POST",
    body: JSON.stringify({ requestedByPlayerId })
  });
}

export function fetchRoom(roomId: string, playerId: string, sessionToken: string) {
  const query = new URLSearchParams({ playerId, sessionToken });
  return apiRequest<RoomSummary>(`/api/rooms/${roomId}?${query.toString()}`);
}

export function fetchState(roomId: string, playerId: string, sessionToken: string) {
  const query = new URLSearchParams({ playerId, sessionToken });
  return apiRequest<GameSnapshot>(`/api/rooms/${roomId}/state?${query.toString()}`);
}

export function fetchPlayerBoard(roomId: string, targetPlayerId: string, playerId: string, sessionToken: string) {
  const query = new URLSearchParams({ playerId, sessionToken });
  return apiRequest<PlayerBoardView>(`/api/rooms/${roomId}/players/${targetPlayerId}/board?${query.toString()}`);
}

export function fetchChatHistory(roomId: string, playerId: string, sessionToken: string, limit = 50) {
  const query = new URLSearchParams({ playerId, sessionToken, limit: String(limit) });
  return apiRequest<ChatHistory>(`/api/rooms/${roomId}/chat?${query.toString()}`);
}

export function reconnect(roomId: string, payload: { playerId: string; sessionToken: string; lastKnownRoomVersion?: number; lastKnownEventSequence?: number }) {
  return apiRequest<ReconnectData>(`/api/rooms/${roomId}/reconnect`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function manualMixmo(roomId: string, payload: { playerId: string; sessionToken: string }) {
  return apiRequest<{ finalMixmo: boolean; drawCountPerPlayer: number; bagRemaining: number }>(`/api/rooms/${roomId}/mixmo/manual`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function validateMixmoGrid(roomId: string, payload: GridValidationRequest) {
  return apiRequest<GridValidationResponse>(`/api/rooms/${roomId}/mixmo/validate`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
