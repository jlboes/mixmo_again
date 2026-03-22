package com.mixmo.grid;

import com.mixmo.common.ErrorCode;
import com.mixmo.common.LetterValidationStatus;
import com.mixmo.common.MixmoException;
import com.mixmo.common.Orientation;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.placement.PlacementModels.Coordinate;
import com.mixmo.room.RoomAggregate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GridValidationService {

  private final PythonWordValidatorClient pythonWordValidatorClient;
  private final int minWordLength;

  public GridValidationService(
      PythonWordValidatorClient pythonWordValidatorClient,
      @Value("${mixmo.grid-validation.min-word-length:2}") int minWordLength
  ) {
    this.pythonWordValidatorClient = pythonWordValidatorClient;
    this.minWordLength = Math.max(1, minWordLength);
  }

  public GridValidationModels.GridValidationResult validateSubmittedGrid(List<GridValidationModels.GridCell> submittedCells) {
    return validateCells(submittedCells == null ? List.of() : submittedCells);
  }

  public GridValidationModels.GridValidationResult validateAuthoritativeGrid(RoomAggregate aggregate, String playerId) {
    return validateCells(authoritativeGrid(aggregate, playerId));
  }

  public List<GridValidationModels.GridCell> authoritativeGrid(RoomAggregate aggregate, String playerId) {
    return aggregate.boardFor(playerId).stream()
        .map(this::toGridCell)
        .toList();
  }

  private GridValidationModels.GridValidationResult validateCells(List<GridValidationModels.GridCell> cells) {
    LinkedHashMap<String, GridValidationModels.GridCell> cellByKey = new LinkedHashMap<>();
    for (GridValidationModels.GridCell cell : cells) {
      String letter = normalizeLetter(cell.letter());
      if (letter == null) {
        continue;
      }
      cellByKey.put(key(cell.x(), cell.y()), new GridValidationModels.GridCell(cell.x(), cell.y(), letter));
    }

    List<GridValidationModels.ExtractedWord> extractedWords = new ArrayList<>();
    extractedWords.addAll(extractWords(cellByKey, Orientation.HORIZONTAL));
    extractedWords.addAll(extractWords(cellByKey, Orientation.VERTICAL));

    Map<String, Boolean> validityByText = lookupWordValidity(extractedWords);
    List<GridValidationModels.ExtractedWord> validatedWords = extractedWords.stream()
        .map(word -> new GridValidationModels.ExtractedWord(
            word.text(),
            word.direction(),
            word.start(),
            word.occupiedCoordinates(),
            validityByText.getOrDefault(word.text(), false)
        ))
        .toList();

    List<String> invalidWords = validatedWords.stream()
        .filter(word -> !word.valid())
        .map(GridValidationModels.ExtractedWord::text)
        .distinct()
        .toList();

    return new GridValidationModels.GridValidationResult(
        invalidWords.isEmpty(),
        validatedWords,
        invalidWords,
        computeLetterStatuses(cellByKey.values(), validatedWords)
    );
  }

  private List<GridValidationModels.ExtractedWord> extractWords(
      Map<String, GridValidationModels.GridCell> cellByKey,
      Orientation direction
  ) {
    Comparator<GridValidationModels.GridCell> comparator = direction == Orientation.HORIZONTAL
        ? Comparator.comparingInt(GridValidationModels.GridCell::y).thenComparingInt(GridValidationModels.GridCell::x)
        : Comparator.comparingInt(GridValidationModels.GridCell::x).thenComparingInt(GridValidationModels.GridCell::y);

    int deltaX = direction == Orientation.HORIZONTAL ? 1 : 0;
    int deltaY = direction == Orientation.VERTICAL ? 1 : 0;

    List<GridValidationModels.ExtractedWord> words = new ArrayList<>();
    for (GridValidationModels.GridCell cell : cellByKey.values().stream().sorted(comparator).toList()) {
      if (cellByKey.containsKey(key(cell.x() - deltaX, cell.y() - deltaY))) {
        continue;
      }

      List<GridValidationModels.GridCell> sequence = walkSequence(cellByKey, cell, deltaX, deltaY);
      if (sequence.size() < minWordLength) {
        continue;
      }

      List<Coordinate> coordinates = sequence.stream()
          .map(entry -> new Coordinate(entry.x(), entry.y()))
          .toList();
      words.add(new GridValidationModels.ExtractedWord(
          sequence.stream().map(GridValidationModels.GridCell::letter).collect(Collectors.joining()),
          direction,
          coordinates.getFirst(),
          coordinates,
          false
      ));
    }
    return words;
  }

  private List<GridValidationModels.GridCell> walkSequence(
      Map<String, GridValidationModels.GridCell> cellByKey,
      GridValidationModels.GridCell start,
      int deltaX,
      int deltaY
  ) {
    List<GridValidationModels.GridCell> sequence = new ArrayList<>();
    GridValidationModels.GridCell current = start;
    while (current != null) {
      sequence.add(current);
      current = cellByKey.get(key(current.x() + deltaX, current.y() + deltaY));
    }
    return sequence;
  }

  private Map<String, Boolean> lookupWordValidity(List<GridValidationModels.ExtractedWord> extractedWords) {
    List<String> uniqueWords = extractedWords.stream()
        .map(GridValidationModels.ExtractedWord::text)
        .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    if (uniqueWords.isEmpty()) {
      return Map.of();
    }

    List<GridValidationModels.PythonValidationResult> responses = pythonWordValidatorClient.validateBatch(uniqueWords);
    return validateValidatorResponses(uniqueWords, responses);
  }

  private Map<String, Boolean> validateValidatorResponses(
      final List<String> uniqueWords,
      final List<GridValidationModels.PythonValidationResult> responses
  ) {
    if (responses.size() != uniqueWords.size()) {
      throw new MixmoException(
          ErrorCode.VALIDATION_SERVICE_UNAVAILABLE,
          "Word validator returned an unexpected response size.",
          HttpStatus.BAD_GATEWAY,
          true
      );
    }

    Set<String> expectedWords = new LinkedHashSet<>(uniqueWords);
    LinkedHashMap<String, Boolean> validityByText = new LinkedHashMap<>();
    for (GridValidationModels.PythonValidationResult response : responses) {
      if (response.input() == null || !expectedWords.remove(response.input())) {
        throw new MixmoException(
            ErrorCode.VALIDATION_SERVICE_UNAVAILABLE,
            "Word validator returned an unexpected word payload.",
            HttpStatus.BAD_GATEWAY,
            true
        );
      }
      validityByText.put(response.input(), response.valid());
    }

    if (!expectedWords.isEmpty()) {
      throw new MixmoException(
          ErrorCode.VALIDATION_SERVICE_UNAVAILABLE,
          "Word validator response omitted one or more requested words.",
          HttpStatus.BAD_GATEWAY,
          true
      );
    }
    return validityByText;
  }

  private Map<String, LetterValidationStatus> computeLetterStatuses(
      Collection<GridValidationModels.GridCell> cells,
      List<GridValidationModels.ExtractedWord> extractedWords
  ) {
    Map<String, List<Boolean>> validityByCoordinate = new LinkedHashMap<>();
    for (GridValidationModels.ExtractedWord word : extractedWords) {
      for (Coordinate coordinate : word.occupiedCoordinates()) {
        validityByCoordinate.computeIfAbsent(key(coordinate.x(), coordinate.y()), ignored -> new ArrayList<>()).add(word.valid());
      }
    }

    LinkedHashMap<String, LetterValidationStatus> statuses = new LinkedHashMap<>();
    for (GridValidationModels.GridCell cell : cells) {
      List<Boolean> wordValidity = validityByCoordinate.get(key(cell.x(), cell.y()));
      if (wordValidity == null || wordValidity.isEmpty()) {
        statuses.put(key(cell.x(), cell.y()), LetterValidationStatus.NEUTRAL);
      } else if (wordValidity.stream().anyMatch(valid -> !valid)) {
        statuses.put(key(cell.x(), cell.y()), LetterValidationStatus.INVALID);
      } else {
        statuses.put(key(cell.x(), cell.y()), LetterValidationStatus.VALID);
      }
    }
    return statuses;
  }

  private GridValidationModels.GridCell toGridCell(BoardCellEntity cell) {
    return new GridValidationModels.GridCell(cell.getX(), cell.getY(), normalizeLetter(cell.getResolvedLetter()));
  }

  private String normalizeLetter(String letter) {
    if (letter == null || letter.isBlank()) {
      return null;
    }
    return letter.trim().toUpperCase(Locale.ROOT);
  }

  private String key(int x, int y) {
    return x + ":" + y;
  }
}
