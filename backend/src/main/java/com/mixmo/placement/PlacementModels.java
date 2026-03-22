package com.mixmo.placement;

import com.mixmo.common.InvalidReason;
import com.mixmo.common.Orientation;
import com.mixmo.common.TileKind;
import java.util.List;

public final class PlacementModels {
  private PlacementModels() {
  }

  public record Coordinate(
      int x,
      int y
  ) {
  }

  public record RequestedTile(
      String tileId,
      String resolvedLetter
  ) {
  }

  public record PlacementRequest(
      String candidateWord,
      Orientation orientation,
      Coordinate start,
      List<RequestedTile> tiles,
      String banditTheme
  ) {
  }

  public record PreviewCell(
      int x,
      int y,
      String letter,
      String state
  ) {
  }

  public record ResolvedPlacementTile(
      String tileId,
      String resolvedLetter,
      TileKind tileKind,
      int x,
      int y
  ) {
  }

  public record PlacementValidationResult(
      boolean valid,
      InvalidReason invalidReason,
      List<PreviewCell> previewCells,
      boolean crossesOrigin,
      boolean connectedToCluster,
      boolean usesBandit,
      int victoryCount,
      List<ResolvedPlacementTile> resolvedTiles
  ) {
  }

  public record PlacementSuggestion(
      String suggestionId,
      Coordinate start,
      Orientation orientation,
      int victoryCount,
      List<PreviewCell> previewCells,
      List<RequestedTile> tileAssignments,
      String label
  ) {
  }

  public record PlacementOutcome(
      PlacementOutcomeType type,
      PlacementValidationResult validationResult
  ) {
  }

  public enum PlacementOutcomeType {
    COMMITTED,
    PAUSED_FOR_BANDIT
  }
}

