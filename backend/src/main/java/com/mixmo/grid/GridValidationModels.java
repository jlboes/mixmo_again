package com.mixmo.grid;

import com.mixmo.common.LetterValidationStatus;
import com.mixmo.common.Orientation;
import com.mixmo.placement.PlacementModels.Coordinate;
import java.util.List;
import java.util.Map;

public final class GridValidationModels {
  private GridValidationModels() {
  }

  public record GridCell(
      int x,
      int y,
      String letter
  ) {
  }

  public record ExtractedWord(
      String text,
      Orientation direction,
      Coordinate start,
      List<Coordinate> occupiedCoordinates,
      boolean valid
  ) {
  }

  public record GridValidationResult(
      boolean gridValid,
      List<ExtractedWord> extractedWords,
      List<String> invalidWords,
      Map<String, LetterValidationStatus> letterStatuses
  ) {
  }

  public record PythonBatchRequest(
      List<String> words
  ) {
  }

  public record PythonValidationResult(
      String input,
      String normalized,
      boolean valid
  ) {
  }
}
