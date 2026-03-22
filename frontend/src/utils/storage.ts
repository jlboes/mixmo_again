import type { SessionRecord } from "@/models/types";

const prefix = "mixmo.session.";
const chatReadPrefix = "mixmo.chat-read.";

export function saveSession(session: SessionRecord): void {
  localStorage.setItem(`${prefix}${session.roomId}`, JSON.stringify(session));
  localStorage.setItem(`${prefix}latest`, session.roomId);
}

export function readSession(roomId: string): SessionRecord | null {
  const raw = localStorage.getItem(`${prefix}${roomId}`);
  return raw ? (JSON.parse(raw) as SessionRecord) : null;
}

export function clearSession(roomId: string): void {
  localStorage.removeItem(`${prefix}${roomId}`);
}

export function saveChatReadSequence(roomId: string, playerId: string, sequenceNumber: number): void {
  localStorage.setItem(`${chatReadPrefix}${roomId}.${playerId}`, String(sequenceNumber));
}

export function readChatReadSequence(roomId: string, playerId: string): number {
  const raw = localStorage.getItem(`${chatReadPrefix}${roomId}.${playerId}`);
  const parsed = raw == null ? NaN : Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}
