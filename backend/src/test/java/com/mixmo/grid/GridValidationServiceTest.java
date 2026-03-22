package com.mixmo.grid;

import com.mixmo.common.LetterValidationStatus;
import com.mixmo.common.Orientation;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridValidationServiceTest {

  private RecordingPythonWordValidatorClient pythonWordValidatorClient;
  private GridValidationService gridValidationService;

  @BeforeEach
  void setUp() {
    pythonWordValidatorClient = new RecordingPythonWordValidatorClient();
    gridValidationService = new GridValidationService(pythonWordValidatorClient, 2);
  }

  @Test
  void extractsHorizontalWords() {
    pythonWordValidatorClient.define("MER", true);

    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "M"),
        cell(1, 0, "E"),
        cell(2, 0, "R")
    ));

    assertTrue(result.gridValid());
    assertEquals(1, result.extractedWords().size());
    assertEquals("MER", result.extractedWords().getFirst().text());
    assertEquals(Orientation.HORIZONTAL, result.extractedWords().getFirst().direction());
    assertEquals(0, result.extractedWords().getFirst().start().x());
    assertEquals(0, result.extractedWords().getFirst().start().y());
  }

  @Test
  void extractsVerticalWords() {
    pythonWordValidatorClient.define("MER", true);

    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "M"),
        cell(0, 1, "E"),
        cell(0, 2, "R")
    ));

    assertTrue(result.gridValid());
    assertEquals(1, result.extractedWords().size());
    assertEquals("MER", result.extractedWords().getFirst().text());
    assertEquals(Orientation.VERTICAL, result.extractedWords().getFirst().direction());
    assertEquals(0, result.extractedWords().getFirst().start().x());
    assertEquals(0, result.extractedWords().getFirst().start().y());
  }

  @Test
  void keepsSharedLetterInvalidWhenAnyAttachedWordIsInvalid() {
    pythonWordValidatorClient.define("CAT", true);
    pythonWordValidatorClient.define("RAZ", false);

    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "C"),
        cell(1, 0, "A"),
        cell(2, 0, "T"),
        cell(1, -1, "R"),
        cell(1, 1, "Z")
    ));

    assertFalse(result.gridValid());
    assertEquals(List.of("RAZ"), result.invalidWords());
    assertEquals(LetterValidationStatus.VALID, result.letterStatuses().get("0:0"));
    assertEquals(LetterValidationStatus.INVALID, result.letterStatuses().get("1:0"));
    assertEquals(LetterValidationStatus.VALID, result.letterStatuses().get("2:0"));
    assertEquals(LetterValidationStatus.INVALID, result.letterStatuses().get("1:-1"));
    assertEquals(LetterValidationStatus.INVALID, result.letterStatuses().get("1:1"));
  }

  @Test
  void deduplicatesPythonLookupsWhilePreservingMultipleOccurrences() {
    pythonWordValidatorClient.define("MOT", true);

    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "M"),
        cell(1, 0, "O"),
        cell(2, 0, "T"),
        cell(0, 2, "M"),
        cell(1, 2, "O"),
        cell(2, 2, "T")
    ));

    assertEquals(List.of("MOT"), pythonWordValidatorClient.lastBatch());
    assertEquals(2, result.extractedWords().size());
    assertTrue(result.extractedWords().stream().allMatch(GridValidationModels.ExtractedWord::valid));
  }

  @Test
  void ignoresSingleLetterSequencesByDefault() {
    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "A"),
        cell(2, 2, "B")
    ));

    assertTrue(result.gridValid());
    assertTrue(result.extractedWords().isEmpty());
    assertTrue(pythonWordValidatorClient.lastBatch().isEmpty());
    assertEquals(LetterValidationStatus.NEUTRAL, result.letterStatuses().get("0:0"));
    assertEquals(LetterValidationStatus.NEUTRAL, result.letterStatuses().get("2:2"));
  }

  @Test
  void reportsInvalidGridWhenAnyExtractedWordIsRejected() {
    pythonWordValidatorClient.define("MAU", false);

    GridValidationModels.GridValidationResult result = gridValidationService.validateSubmittedGrid(List.of(
        cell(0, 0, "M"),
        cell(1, 0, "A"),
        cell(2, 0, "U")
    ));

    assertFalse(result.gridValid());
    assertEquals(List.of("MAU"), result.invalidWords());
  }

  @Test
  void rejectsValidatorResponseSizeMismatch() {
    pythonWordValidatorClient.respondWith(words -> List.of(
        new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true)
    ));

    MixmoException exception = assertValidationServiceUnavailable(() -> gridValidationService.validateSubmittedGrid(twoWordGrid()));
    assertEquals("Word validator returned an unexpected response size.", exception.getMessage());
  }

  @Test
  void rejectsUnexpectedValidatorInputs() {
    pythonWordValidatorClient.respondWith(words -> List.of(
        new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true),
        new GridValidationModels.PythonValidationResult("ZZ", "ZZ", true)
    ));

    MixmoException exception = assertValidationServiceUnavailable(() -> gridValidationService.validateSubmittedGrid(twoWordGrid()));
    assertEquals("Word validator returned an unexpected word payload.", exception.getMessage());
  }

  @Test
  void rejectsNullValidatorInputs() {
    pythonWordValidatorClient.respondWith(words -> List.of(
        new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true),
        new GridValidationModels.PythonValidationResult(null, "CD", true)
    ));

    MixmoException exception = assertValidationServiceUnavailable(() -> gridValidationService.validateSubmittedGrid(twoWordGrid()));
    assertEquals("Word validator returned an unexpected word payload.", exception.getMessage());
  }

  @Test
  void rejectsDuplicateValidatorInputs() {
    pythonWordValidatorClient.respondWith(words -> List.of(
        new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true),
        new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true)
    ));

    MixmoException exception = assertValidationServiceUnavailable(() -> gridValidationService.validateSubmittedGrid(twoWordGrid()));
    assertEquals("Word validator returned an unexpected word payload.", exception.getMessage());
  }

  @Test
  void rejectsOmittedValidatorWords() {
    pythonWordValidatorClient.respondWith(words -> new AbstractList<>() {
      private final List<GridValidationModels.PythonValidationResult> backing = List.of(
          new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true),
          new GridValidationModels.PythonValidationResult(words.getFirst(), words.getFirst(), true)
      );

      @Override
      public GridValidationModels.PythonValidationResult get(int index) {
        return backing.get(index);
      }

      @Override
      public int size() {
        return backing.size();
      }

      @Override
      public java.util.Iterator<GridValidationModels.PythonValidationResult> iterator() {
        return backing.subList(0, 1).iterator();
      }
    });

    MixmoException exception = assertValidationServiceUnavailable(() -> gridValidationService.validateSubmittedGrid(twoWordGrid()));
    assertEquals("Word validator response omitted one or more requested words.", exception.getMessage());
  }

  private GridValidationModels.GridCell cell(int x, int y, String letter) {
    return new GridValidationModels.GridCell(x, y, letter);
  }

  private List<GridValidationModels.GridCell> twoWordGrid() {
    return List.of(
        cell(0, 0, "A"),
        cell(1, 0, "B"),
        cell(0, 2, "C"),
        cell(1, 2, "D")
    );
  }

  private MixmoException assertValidationServiceUnavailable(org.junit.jupiter.api.function.Executable executable) {
    MixmoException exception = assertThrows(MixmoException.class, executable);
    assertEquals(ErrorCode.VALIDATION_SERVICE_UNAVAILABLE, exception.getCode());
    assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    assertTrue(exception.isRetryable());
    return exception;
  }

  private static final class RecordingPythonWordValidatorClient implements PythonWordValidatorClient {
    private final Map<String, Boolean> validityByWord = new LinkedHashMap<>();
    private List<String> lastBatch = List.of();
    private Function<List<String>, List<GridValidationModels.PythonValidationResult>> responseFactory = this::defaultResponses;

    void define(String word, boolean valid) {
      validityByWord.put(word, valid);
    }

    void respondWith(Function<List<String>, List<GridValidationModels.PythonValidationResult>> responseFactory) {
      this.responseFactory = responseFactory;
    }

    List<String> lastBatch() {
      return lastBatch;
    }

    @Override
    public List<GridValidationModels.PythonValidationResult> validateBatch(List<String> words) {
      lastBatch = new ArrayList<>(words);
      return responseFactory.apply(words);
    }

    private List<GridValidationModels.PythonValidationResult> defaultResponses(List<String> words) {
      return words.stream()
          .map(word -> new GridValidationModels.PythonValidationResult(word, word, validityByWord.getOrDefault(word, false)))
          .toList();
    }
  }
}
