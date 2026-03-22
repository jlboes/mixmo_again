package com.mixmo.suggestion;

import com.mixmo.common.DeviceType;
import com.mixmo.common.Orientation;
import com.mixmo.common.TileKind;
import com.mixmo.persistence.TileEntity;
import com.mixmo.placement.PlacementModels.PlacementRequest;
import com.mixmo.placement.PlacementModels.PlacementSuggestion;
import com.mixmo.placement.PlacementModels.PlacementValidationResult;
import com.mixmo.placement.PlacementModels.RequestedTile;
import com.mixmo.placement.PlacementService;
import com.mixmo.room.RoomAggregate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

import static com.mixmo.placement.PlacementModels.Coordinate;

@Service
public class SuggestionService {

  private final PlacementService placementService;

  public SuggestionService(PlacementService placementService) {
    this.placementService = placementService;
  }

  public List<PlacementSuggestion> suggestions(RoomAggregate aggregate, String playerId, String candidateWord, DeviceType deviceType) {
    String normalizedWord = candidateWord == null ? "" : candidateWord.trim().toUpperCase(Locale.ROOT);
    if (normalizedWord.isEmpty()) {
      return List.of();
    }

    Bounds bounds = bounds(aggregate, playerId, normalizedWord.length());
    List<PlacementSuggestion> suggestions = new ArrayList<>();
    for (Orientation orientation : Orientation.values()) {
      for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
          List<RequestedTile> tileAssignments = assignTiles(aggregate, playerId, normalizedWord, x, y, orientation);
          if (tileAssignments == null) {
            continue;
          }
          PlacementRequest request = new PlacementRequest(normalizedWord, orientation, new Coordinate(x, y), tileAssignments, null);
          PlacementValidationResult validation = placementService.validate(aggregate, playerId, request);
          if (!validation.valid()) {
            continue;
          }
          suggestions.add(new PlacementSuggestion(
              UUID.randomUUID().toString(),
              new Coordinate(x, y),
              orientation,
              validation.victoryCount(),
              validation.previewCells(),
              tileAssignments,
              validation.victoryCount() > 0 ? "Best overlap" : "Valid placement"
          ));
        }
      }
    }

    return suggestions.stream()
        .sorted(Comparator
            .comparingInt(PlacementSuggestion::victoryCount).reversed()
            .thenComparingInt(suggestion -> suggestion.previewCells().stream().filter(cell -> "NEW".equals(cell.state())).toList().size())
            .thenComparingInt(suggestion -> suggestion.start().x())
            .thenComparingInt(suggestion -> suggestion.start().y())
            .thenComparing(suggestion -> suggestion.orientation().name()))
        .limit(deviceType == null ? DeviceType.MOBILE.suggestionLimit() : deviceType.suggestionLimit())
        .toList();
  }

  private List<RequestedTile> assignTiles(RoomAggregate aggregate, String playerId, String word, int startX, int startY, Orientation orientation) {
    Map<String, Integer> normalCounts = new HashMap<>();
    List<TileEntity> jokers = new ArrayList<>();
    List<TileEntity> bandits = new ArrayList<>();
    for (TileEntity tile : aggregate.rackTilesFor(playerId)) {
      if (tile.getKind() == TileKind.NORMAL) {
        normalCounts.merge(tile.getFaceValue(), 1, Integer::sum);
      } else if (tile.getKind() == TileKind.JOKER) {
        jokers.add(tile);
      } else {
        bandits.add(tile);
      }
    }

    List<RequestedTile> result = new ArrayList<>();
    Map<String, Integer> consumedNormals = new HashMap<>();
    int jokerIndex = 0;
    int banditIndex = 0;
    Map<String, String> board = aggregate.boardByCoordinate(playerId).entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getResolvedLetter()));

    for (int index = 0; index < word.length(); index++) {
      int x = orientation == Orientation.HORIZONTAL ? startX + index : startX;
      int y = orientation == Orientation.VERTICAL ? startY + index : startY;
      String letter = String.valueOf(word.charAt(index));
      String existing = board.get(RoomAggregate.key(x, y));
      if (existing != null) {
        if (!existing.equals(letter)) {
          return null;
        }
        continue;
      }

      int availableNormal = normalCounts.getOrDefault(letter, 0) - consumedNormals.getOrDefault(letter, 0);
      if (availableNormal > 0) {
        TileEntity tile = aggregate.rackTilesFor(playerId).stream()
            .filter(candidate -> candidate.getKind() == TileKind.NORMAL && letter.equals(candidate.getFaceValue()) && !result.stream().map(RequestedTile::tileId).toList().contains(candidate.getId()))
            .findFirst()
            .orElse(null);
        if (tile != null) {
          result.add(new RequestedTile(tile.getId(), letter));
          consumedNormals.merge(letter, 1, Integer::sum);
          continue;
        }
      }
      if ("KWXYZ".contains(letter) && banditIndex < bandits.size()) {
        result.add(new RequestedTile(bandits.get(banditIndex++).getId(), letter));
        continue;
      }
      if (jokerIndex < jokers.size()) {
        result.add(new RequestedTile(jokers.get(jokerIndex++).getId(), letter));
        continue;
      }
      return null;
    }
    return result;
  }

  private Bounds bounds(RoomAggregate aggregate, String playerId, int wordLength) {
    List<com.mixmo.persistence.BoardCellEntity> board = aggregate.boardFor(playerId);
    if (board.isEmpty()) {
      return new Bounds(-wordLength, -wordLength, wordLength, wordLength);
    }
    int minX = board.stream().mapToInt(com.mixmo.persistence.BoardCellEntity::getX).min().orElse(0) - wordLength;
    int maxX = board.stream().mapToInt(com.mixmo.persistence.BoardCellEntity::getX).max().orElse(0) + wordLength;
    int minY = board.stream().mapToInt(com.mixmo.persistence.BoardCellEntity::getY).min().orElse(0) - wordLength;
    int maxY = board.stream().mapToInt(com.mixmo.persistence.BoardCellEntity::getY).max().orElse(0) + wordLength;
    return new Bounds(minX, minY, maxX, maxY);
  }

  private record Bounds(int minX, int minY, int maxX, int maxY) {
  }
}

