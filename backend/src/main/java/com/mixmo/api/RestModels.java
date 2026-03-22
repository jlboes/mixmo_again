package com.mixmo.api;

import com.mixmo.common.DeviceType;
import com.mixmo.common.LetterValidationStatus;
import com.mixmo.common.Orientation;
import com.mixmo.common.PauseReason;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.ThemeOption;
import com.mixmo.common.TileKind;
import com.mixmo.placement.PlacementModels.Coordinate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RestModels {
  private RestModels() {
  }

  public record CreateRoomRequest(
      String playerName,
      DeviceType deviceType
  ) {
  }

  public record JoinRoomRequest(
      String playerName,
      String sessionToken,
      DeviceType deviceType
  ) {
  }

  public record LeaveRoomRequest(
      String playerId,
      String sessionToken
  ) {
  }

  public record StartRoomRequest(
      String requestedByPlayerId
  ) {
  }

  public record ManualMixmoRequest(
      String playerId,
      String sessionToken
  ) {
  }

  public record GridValidationCellDto(
      int x,
      int y,
      String letter
  ) {
  }

  public record GridValidationRequest(
      String playerId,
      String sessionToken,
      Long expectedRoomVersion,
      List<GridValidationCellDto> boardCells
  ) {
  }

  public record ExtractedWordDto(
      String text,
      Orientation direction,
      Coordinate start,
      List<Coordinate> occupiedCoordinates,
      boolean valid
  ) {
  }

  public record GridValidationResponse(
      boolean gridValid,
      List<ExtractedWordDto> extractedWords,
      List<String> invalidWords,
      Map<String, LetterValidationStatus> letterStatuses
  ) {
  }

  public record ReconnectRequest(
      String playerId,
      String sessionToken,
      Long lastKnownRoomVersion,
      Long lastKnownEventSequence
  ) {
  }

  public record RoomQuery(
      String playerId,
      String sessionToken
  ) {
  }

  public record RoomBootstrapData(
      RoomDto room,
      SelfDto self
  ) {
  }

  public record RoomDto(
      String roomId,
      String roomCode,
      RoomStatus status,
      String hostPlayerId,
      List<PlayerSummaryDto> players,
      String currentBanditTheme,
      PauseReason pauseReason,
      Long roomVersion
  ) {
  }

  public record SelfDto(
      String playerId,
      String playerName,
      int seatOrder,
      String sessionToken
  ) {
  }

  public record PlayerSummaryDto(
      String playerId,
      String playerName,
      int seatOrder,
      boolean connected,
      Integer rackCount,
      Integer boardCellCount
  ) {
  }

  public record RackTileDto(
      String tileId,
      TileKind kind,
      String face,
      String assignedLetter
  ) {
  }

  public record BoardCellDto(
      int x,
      int y,
      String resolvedLetter,
      String tileId,
      TileKind tileKind
  ) {
  }

  public record PlayerBoardViewDto(
      PlayerSummaryDto player,
      List<BoardCellDto> boardCells,
      long roomVersion,
      Instant updatedAt
  ) {
  }

  public record ChatMessageDto(
      long sequenceNumber,
      String playerId,
      String playerName,
      int seatOrder,
      String text,
      Instant createdAt
  ) {
  }

  public record ChatHistoryDto(
      List<ChatMessageDto> messages,
      long latestSequence
  ) {
  }

  public record ActionStateDto(
      boolean canTriggerMixmo,
      boolean canConfirmPlacement,
      boolean canSelectBanditTheme,
      String mixmoReason
  ) {
  }

  public record ThemeStateDto(
      List<String> themeOptions,
      String currentBanditTheme,
      boolean paused,
      String pauseTriggeringPlayerId
  ) {
  }

  public record StaleGameStateDto(
      boolean warningActive,
      String message,
      Instant automaticMixmoAt
  ) {
  }

  public record GameSnapshotDto(
      String roomId,
      String roomCode,
      RoomStatus status,
      long roomVersion,
      long bagRemaining,
      PauseReason pauseReason,
      String currentBanditTheme,
      String winnerPlayerId,
      String selfPlayerId,
      String hostPlayerId,
      List<PlayerSummaryDto> players,
      List<RackTileDto> selfRack,
      List<BoardCellDto> selfBoard,
      Object candidateWordState,
      ActionStateDto actionState,
      ThemeStateDto themeState,
      StaleGameStateDto staleGameState,
      long lastResolvedEventSequence,
      Instant updatedAt
  ) {
  }

  public record ReconnectData(
      boolean resyncRequired,
      GameSnapshotDto gameSnapshot,
      MissedEventWindowDto missedEventWindow
  ) {
  }

  public record MissedEventWindowDto(
      long fromExclusive,
      long toInclusive
  ) {
  }

  public static List<String> themeLabels() {
    return List.of(
        ThemeOption.ANIMALS.label(),
        ThemeOption.FOOD_AND_DRINKS.label(),
        ThemeOption.COUNTRIES_AND_CITIES.label(),
        ThemeOption.NATURE.label(),
        ThemeOption.JOBS_AND_PROFESSIONS.label(),
        ThemeOption.SPORTS.label(),
        ThemeOption.TECHNOLOGY.label(),
        ThemeOption.MOVIES_AND_ENTERTAINMENT.label(),
        ThemeOption.TRANSPORTATION.label(),
        ThemeOption.HOUSEHOLD_OBJECTS.label()
    );
  }
}
