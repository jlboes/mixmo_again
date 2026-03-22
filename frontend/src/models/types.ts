export type DeviceType = "MOBILE" | "TABLET" | "DESKTOP";
export type RoomStatus = "WAITING" | "ACTIVE" | "PAUSED" | "FINISHED";
export type PauseReason = "BANDIT_THEME_REQUIRED" | null;
export type TileKind = "NORMAL" | "JOKER" | "BANDIT";
export type Orientation = "HORIZONTAL" | "VERTICAL";
export type GameMode = "idle" | "composing" | "banditTheme" | "confirmed";
export type LetterValidationState = "NEUTRAL" | "VALID" | "INVALID";
export type MixmoValidationState = "idle" | "validating" | "success" | "error";
export type MixmoTriggerReason = "PLAYER" | "MANUAL" | "AUTOMATIC";
export type ThemeOption =
  | "Animals"
  | "Food & Drinks"
  | "Countries & Cities"
  | "Nature"
  | "Jobs / Professions"
  | "Sports"
  | "Technology"
  | "Movies & Entertainment"
  | "Transportation"
  | "Household Objects";

export interface ApiEnvelope<T> {
  requestId: string;
  serverTime: string;
  roomVersion: number;
  data: T;
}

export interface PlayerSummary {
  playerId: string;
  playerName: string;
  seatOrder: number;
  connected: boolean;
  rackCount?: number;
  boardCellCount?: number;
}

export interface RoomSummary {
  roomId: string;
  roomCode: string;
  status: RoomStatus;
  hostPlayerId: string | null;
  players: PlayerSummary[];
  currentBanditTheme: string | null;
  pauseReason: PauseReason;
  roomVersion: number;
}

export interface SelfInfo {
  playerId: string;
  playerName: string;
  seatOrder: number;
  sessionToken: string;
}

export interface RoomBootstrapData {
  room: RoomSummary;
  self: SelfInfo;
}

export interface RackTileDto {
  tileId: string;
  kind: TileKind;
  face: string;
  assignedLetter: string | null;
}

export interface BoardCellDto {
  x: number;
  y: number;
  resolvedLetter: string;
  tileId: string;
  tileKind: TileKind;
  validationStatus?: LetterValidationState;
}

export interface PlayerBoardView {
  player: PlayerSummary;
  boardCells: BoardCellDto[];
  roomVersion: number;
  updatedAt: string;
}

export interface ChatMessage {
  sequenceNumber: number;
  playerId: string;
  playerName: string;
  seatOrder: number;
  text: string;
  createdAt: string;
}

export interface ChatHistory {
  messages: ChatMessage[];
  latestSequence: number;
}

export interface ActionStateDto {
  canTriggerMixmo: boolean;
  canConfirmPlacement: boolean;
  canSelectBanditTheme: boolean;
  mixmoReason: string | null;
}

export interface ThemeStateDto {
  themeOptions: ThemeOption[];
  currentBanditTheme: string | null;
  paused: boolean;
  pauseTriggeringPlayerId: string | null;
}

export interface StaleGameStateDto {
  warningActive: boolean;
  message: string | null;
  automaticMixmoAt: string | null;
}

export interface GameSnapshot {
  roomId: string;
  roomCode: string;
  status: RoomStatus;
  roomVersion: number;
  bagRemaining: number;
  pauseReason: PauseReason;
  currentBanditTheme: string | null;
  winnerPlayerId: string | null;
  selfPlayerId: string;
  hostPlayerId: string | null;
  players: PlayerSummary[];
  selfRack: RackTileDto[];
  selfBoard: BoardCellDto[];
  candidateWordState: unknown;
  actionState: ActionStateDto;
  themeState: ThemeStateDto;
  staleGameState: StaleGameStateDto;
  lastResolvedEventSequence: number;
  updatedAt: string;
}

export interface ReconnectData {
  resyncRequired: boolean;
  gameSnapshot: GameSnapshot;
  missedEventWindow: {
    fromExclusive: number;
    toInclusive: number;
  };
}

export interface RequestedTile {
  tileId: string;
  resolvedLetter: string | null;
}

export interface PreviewCell {
  x: number;
  y: number;
  letter: string;
  state: "NEW" | "OVERLAP_MATCH" | "CONFLICT";
}

export interface PlacementPreviewResult {
  valid: boolean;
  invalidReason: string | null;
  previewCells: PreviewCell[];
  crossesOrigin: boolean;
  connectedToCluster: boolean;
  usesBandit: boolean;
}

export interface PlacementSuggestion {
  suggestionId: string;
  start: { x: number; y: number };
  orientation: Orientation;
  victoryCount: number;
  previewCells: PreviewCell[];
  tileAssignments: RequestedTile[];
  label: string;
}

export interface GridValidationCellDto {
  x: number;
  y: number;
  letter: string;
}

export interface ExtractedWordDto {
  text: string;
  direction: Orientation;
  start: { x: number; y: number };
  occupiedCoordinates: Array<{ x: number; y: number }>;
  valid: boolean;
}

export interface GridValidationResponse {
  gridValid: boolean;
  extractedWords: ExtractedWordDto[];
  invalidWords: string[];
  letterStatuses: Record<string, LetterValidationState>;
}

export interface SocketEventEnvelope<T = unknown> {
  type: string;
  requestId: string | null;
  roomId: string;
  roomVersion: number;
  payload: T;
}

export interface CandidateTile {
  tileId: string;
  kind: TileKind;
  face: string;
  letter: string;
  source: "board" | "rack";
  boardCell?: {
    x: number;
    y: number;
  };
}

export interface RackTile extends RackTileDto {
  selected: boolean;
}

export interface ToastState {
  type: "success" | "error" | "info";
  message: string;
}

export interface ViewportState {
  centerX: number;
  centerY: number;
  zoom: number;
}

export interface GridValidationRequest {
  playerId: string;
  sessionToken: string;
  expectedRoomVersion: number;
  boardCells: GridValidationCellDto[];
}

export interface SessionRecord {
  roomId: string;
  roomCode: string;
  playerId: string;
  playerName: string;
  seatOrder: number;
  sessionToken: string;
}
