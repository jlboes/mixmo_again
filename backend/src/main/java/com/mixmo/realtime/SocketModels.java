package com.mixmo.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.mixmo.common.DeviceType;
import com.mixmo.common.Orientation;
import com.mixmo.placement.PlacementModels.Coordinate;
import com.mixmo.placement.PlacementModels.RequestedTile;
import java.util.List;

public final class SocketModels {
  private SocketModels() {
  }

  public record CommandEnvelope(
      String type,
      String requestId,
      String roomId,
      String playerId,
      Long expectedRoomVersion,
      JsonNode payload
  ) {
  }

  public record EventEnvelope(
      String type,
      String requestId,
      String roomId,
      Long roomVersion,
      Object payload
  ) {
  }

  public record GameSyncPayload(
      Long lastKnownEventSequence
  ) {
  }

  public record PlacementPreviewPayload(
      String candidateWord,
      Orientation orientation,
      Coordinate start,
      List<RequestedTile> tiles
  ) {
  }

  public record PlacementSuggestionsPayload(
      String candidateWord,
      DeviceType deviceType
  ) {
  }

  public record PlacementConfirmPayload(
      String candidateWord,
      Orientation orientation,
      Coordinate start,
      List<RequestedTile> tiles,
      String banditTheme
  ) {
  }

  public record BoardTilesReturnPayload(
      List<String> tileIds
  ) {
  }

  public record ChatMessagePayload(
      String text
  ) {
  }

  public record BanditThemePayload(
      String theme
  ) {
  }
}
