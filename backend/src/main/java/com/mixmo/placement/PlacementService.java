package com.mixmo.placement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.InvalidReason;
import com.mixmo.common.MixmoException;
import com.mixmo.common.Orientation;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.TileKind;
import com.mixmo.common.TileLocation;
import com.mixmo.persistence.BanditThemeSelectionEntity;
import com.mixmo.persistence.BanditThemeSelectionRepository;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.persistence.BoardCellRepository;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.persistence.TileEntity;
import com.mixmo.persistence.TileRepository;
import com.mixmo.rack.RackService;
import com.mixmo.room.RoomAggregate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static com.mixmo.placement.PlacementModels.Coordinate;
import static com.mixmo.placement.PlacementModels.PlacementOutcome;
import static com.mixmo.placement.PlacementModels.PlacementOutcomeType;
import static com.mixmo.placement.PlacementModels.PlacementRequest;
import static com.mixmo.placement.PlacementModels.PlacementValidationResult;
import static com.mixmo.placement.PlacementModels.PreviewCell;
import static com.mixmo.placement.PlacementModels.RequestedTile;
import static com.mixmo.placement.PlacementModels.ResolvedPlacementTile;

@Service
public class PlacementService {

  private final TileRepository tileRepository;
  private final BoardCellRepository boardCellRepository;
  private final BanditThemeSelectionRepository banditThemeSelectionRepository;
  private final RackService rackService;
  private final GameRoomRepository roomRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PlacementService(
      TileRepository tileRepository,
      BoardCellRepository boardCellRepository,
      BanditThemeSelectionRepository banditThemeSelectionRepository,
      RackService rackService,
      GameRoomRepository roomRepository,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.tileRepository = tileRepository;
    this.boardCellRepository = boardCellRepository;
    this.banditThemeSelectionRepository = banditThemeSelectionRepository;
    this.rackService = rackService;
    this.roomRepository = roomRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public PlacementValidationResult validate(RoomAggregate aggregate, String playerId, PlacementRequest request) {
    if (request == null || request.start() == null) {
      return invalid(InvalidReason.MISSING_ANCHOR);
    }
    if (request.orientation() == null) {
      return invalid(InvalidReason.INVALID_ORIENTATION);
    }
    String candidateWord = normalizeCandidateWord(request.candidateWord());
    if (candidateWord.isEmpty()) {
      return invalid(InvalidReason.EMPTY_CANDIDATE_WORD);
    }

    Map<String, BoardCellEntity> boardByCoordinate = aggregate.boardByCoordinate(playerId);
    Map<String, TileEntity> rackTiles = new HashMap<>();
    for (TileEntity tile : aggregate.rackTilesFor(playerId)) {
      rackTiles.put(tile.getId(), tile);
    }

    List<PreviewCell> previewCells = new ArrayList<>();
    List<ResolvedPlacementTile> resolvedTiles = new ArrayList<>();
    Set<String> usedTileIds = new HashSet<>();
    int tileCursor = 0;
    int overlapCount = 0;

    for (int index = 0; index < candidateWord.length(); index++) {
      int x = request.orientation() == Orientation.HORIZONTAL ? request.start().x() + index : request.start().x();
      int y = request.orientation() == Orientation.VERTICAL ? request.start().y() + index : request.start().y();
      String letter = String.valueOf(candidateWord.charAt(index));
      BoardCellEntity existing = boardByCoordinate.get(RoomAggregate.key(x, y));
      if (existing != null) {
        if (!letter.equals(existing.getResolvedLetter())) {
          previewCells.add(new PreviewCell(x, y, letter, "CONFLICT"));
          return new PlacementValidationResult(false, InvalidReason.COLLISION_WITH_DIFFERENT_LETTER, previewCells, false, false, false, 0, List.of());
        }
        overlapCount++;
        previewCells.add(new PreviewCell(x, y, letter, "OVERLAP_MATCH"));
        continue;
      }

      if (request.tiles() == null || tileCursor >= request.tiles().size()) {
        return new PlacementValidationResult(false, InvalidReason.INVALID_TILE_USAGE, previewCells, false, false, false, 0, List.of());
      }

      RequestedTile requestedTile = request.tiles().get(tileCursor++);
      if (!usedTileIds.add(requestedTile.tileId())) {
        return invalid(InvalidReason.INVALID_TILE_USAGE);
      }
      TileEntity tile = rackTiles.get(requestedTile.tileId());
      if (tile == null) {
        return invalid(InvalidReason.INVALID_TILE_USAGE);
      }
      String resolvedLetter;
      try {
        resolvedLetter = resolveRequestedLetter(tile, requestedTile.resolvedLetter(), letter);
      } catch (MixmoException exception) {
        InvalidReason reason = exception.getCode() == ErrorCode.INVALID_BANDIT_LETTER
            ? InvalidReason.INVALID_BANDIT_LETTER
            : InvalidReason.INVALID_TILE_USAGE;
        return new PlacementValidationResult(false, reason, previewCells, false, false, false, 0, List.of());
      }
      resolvedTiles.add(new ResolvedPlacementTile(tile.getId(), resolvedLetter, tile.getKind(), x, y));
      previewCells.add(new PreviewCell(x, y, letter, "NEW"));
    }

    if (request.tiles() != null && tileCursor != request.tiles().size()) {
      return invalid(InvalidReason.INVALID_TILE_USAGE);
    }
    if (resolvedTiles.isEmpty()) {
      return invalid(InvalidReason.INVALID_TILE_USAGE);
    }

    boolean crossesOrigin = previewCells.stream().anyMatch(cell -> cell.x() == 0 && cell.y() == 0);
    List<BoardCellEntity> currentBoard = aggregate.boardFor(playerId);
    if (currentBoard.isEmpty() && !crossesOrigin) {
      return new PlacementValidationResult(false, InvalidReason.FIRST_WORD_MUST_CROSS_ORIGIN, previewCells, false, false, usesBandit(resolvedTiles), overlapCount, resolvedTiles);
    }

    Set<String> occupied = new HashSet<>();
    for (BoardCellEntity boardCell : currentBoard) {
      occupied.add(RoomAggregate.key(boardCell.getX(), boardCell.getY()));
    }
    for (ResolvedPlacementTile resolvedTile : resolvedTiles) {
      occupied.add(RoomAggregate.key(resolvedTile.x(), resolvedTile.y()));
    }
    boolean connected = currentBoard.isEmpty() || isConnected(occupied);
    if (!connected) {
      return new PlacementValidationResult(false, InvalidReason.DISCONNECTED_PLACEMENT, previewCells, crossesOrigin, false, usesBandit(resolvedTiles), overlapCount, resolvedTiles);
    }

    boolean touchesExisting = currentBoard.isEmpty() || touchesCluster(currentBoard, resolvedTiles, boardByCoordinate);
    if (!currentBoard.isEmpty() && !touchesExisting) {
      return new PlacementValidationResult(false, InvalidReason.DISCONNECTED_PLACEMENT, previewCells, crossesOrigin, false, usesBandit(resolvedTiles), overlapCount, resolvedTiles);
    }

    return new PlacementValidationResult(true, null, previewCells, crossesOrigin, true, usesBandit(resolvedTiles), overlapCount, resolvedTiles);
  }

  public PlacementOutcome commitOrPause(RoomAggregate aggregate, String playerId, PlacementRequest request) {
    PlacementValidationResult validation = validate(aggregate, playerId, request);
    if (!validation.valid()) {
      throw validationException(validation.invalidReason());
    }
    if (validation.usesBandit()) {
      pauseForBandit(aggregate, playerId, request);
      return new PlacementOutcome(PlacementOutcomeType.PAUSED_FOR_BANDIT, validation);
    }
    commitPlacement(aggregate, playerId, validation.resolvedTiles());
    return new PlacementOutcome(PlacementOutcomeType.COMMITTED, validation);
  }

  public void commitPlacement(RoomAggregate aggregate, String playerId, List<ResolvedPlacementTile> resolvedTiles) {
    for (ResolvedPlacementTile resolvedTile : resolvedTiles) {
      TileEntity tile = tileRepository.findById(resolvedTile.tileId())
          .orElseThrow(() -> new MixmoException(ErrorCode.INVALID_TILE_USAGE, "Tile not found.", HttpStatus.BAD_REQUEST));
      tile.setAssignedLetter(resolvedTile.resolvedLetter());
      tile.setLocation(TileLocation.BOARD);
      tileRepository.save(tile);

      BoardCellEntity boardCell = new BoardCellEntity();
      boardCell.setId(UUID.randomUUID().toString());
      boardCell.setPlayerId(playerId);
      boardCell.setTileId(tile.getId());
      boardCell.setResolvedLetter(resolvedTile.resolvedLetter());
      boardCell.setX(resolvedTile.x());
      boardCell.setY(resolvedTile.y());
      boardCellRepository.save(boardCell);
    }
    rackService.removeTilesFromRack(playerId, resolvedTiles.stream().map(ResolvedPlacementTile::tileId).toList());
    aggregate.room().setVersion(aggregate.room().getVersion() + 1);
    roomRepository.save(aggregate.room());
  }

  public void pauseForBandit(RoomAggregate aggregate, String playerId, PlacementRequest request) {
    if (aggregate.pendingBanditSelection().isPresent()) {
      throw new MixmoException(ErrorCode.ROOM_PAUSED, "Bandit theme selection is already pending.", HttpStatus.CONFLICT);
    }
    BanditThemeSelectionEntity selection = new BanditThemeSelectionEntity();
    selection.setId(UUID.randomUUID().toString());
    selection.setRoomId(aggregate.room().getId());
    selection.setTriggeringPlayerId(playerId);
    selection.setStatus("PENDING");
    selection.setCreatedAt(Instant.now(clock));
    selection.setPendingPlacementJson(writeJson(request));
    banditThemeSelectionRepository.save(selection);
    aggregate.room().setStatus(RoomStatus.PAUSED);
    aggregate.room().setPauseReason(com.mixmo.common.PauseReason.BANDIT_THEME_REQUIRED);
    aggregate.room().setVersion(aggregate.room().getVersion() + 1);
    roomRepository.save(aggregate.room());
  }

  public PlacementRequest readPendingPlacement(BanditThemeSelectionEntity selection) {
    try {
      return objectMapper.readValue(selection.getPendingPlacementJson(), PlacementRequest.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read pending placement.", exception);
    }
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize pending placement.", exception);
    }
  }

  private PlacementValidationResult invalid(InvalidReason reason) {
    return new PlacementValidationResult(false, reason, List.of(), false, false, false, 0, List.of());
  }

  private MixmoException validationException(InvalidReason reason) {
    ErrorCode code = switch (reason) {
      case EMPTY_CANDIDATE_WORD -> ErrorCode.EMPTY_CANDIDATE_WORD;
      case MISSING_ANCHOR -> ErrorCode.MISSING_ANCHOR;
      case INVALID_ORIENTATION -> ErrorCode.INVALID_ORIENTATION;
      case FIRST_WORD_MUST_CROSS_ORIGIN -> ErrorCode.FIRST_WORD_MUST_CROSS_ORIGIN;
      case DISCONNECTED_PLACEMENT -> ErrorCode.DISCONNECTED_PLACEMENT;
      case COLLISION_WITH_DIFFERENT_LETTER -> ErrorCode.COLLISION_WITH_DIFFERENT_LETTER;
      case INVALID_BANDIT_LETTER -> ErrorCode.INVALID_BANDIT_LETTER;
      case INVALID_TILE_USAGE -> ErrorCode.INVALID_TILE_USAGE;
    };
    return new MixmoException(code, reason.code(), HttpStatus.BAD_REQUEST);
  }

  private String normalizeCandidateWord(String candidateWord) {
    return candidateWord == null ? "" : candidateWord.trim().toUpperCase(Locale.ROOT);
  }

  private boolean usesBandit(List<ResolvedPlacementTile> resolvedTiles) {
    return resolvedTiles.stream().anyMatch(tile -> tile.tileKind() == TileKind.BANDIT);
  }

  private String resolveRequestedLetter(TileEntity tile, String requestedLetter, String wordLetter) {
    return switch (tile.getKind()) {
      case NORMAL -> {
        String expected = Objects.requireNonNullElse(tile.getFaceValue(), "");
        String resolved = Objects.requireNonNullElse(requestedLetter, expected).toUpperCase(Locale.ROOT);
        if (!resolved.equals(expected) || !resolved.equals(wordLetter)) {
          throw validationException(InvalidReason.INVALID_TILE_USAGE);
        }
        yield resolved;
      }
      case JOKER -> {
        String resolved = Objects.requireNonNullElse(requestedLetter, wordLetter).toUpperCase(Locale.ROOT);
        if (!resolved.equals(wordLetter)) {
          throw validationException(InvalidReason.INVALID_TILE_USAGE);
        }
        yield resolved;
      }
      case BANDIT -> {
        String resolved = Objects.requireNonNullElse(requestedLetter, wordLetter).toUpperCase(Locale.ROOT);
        if (!resolved.equals(wordLetter) || "KWXYZ".indexOf(resolved.charAt(0)) < 0) {
          throw validationException(InvalidReason.INVALID_BANDIT_LETTER);
        }
        yield resolved;
      }
    };
  }

  private boolean touchesCluster(
      List<BoardCellEntity> currentBoard,
      List<ResolvedPlacementTile> resolvedTiles,
      Map<String, BoardCellEntity> boardByCoordinate
  ) {
    if (currentBoard.isEmpty()) {
      return true;
    }
    for (ResolvedPlacementTile resolvedTile : resolvedTiles) {
      String ownCoordinate = RoomAggregate.key(resolvedTile.x(), resolvedTile.y());
      if (boardByCoordinate.containsKey(ownCoordinate)) {
        return true;
      }
      for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        if (boardByCoordinate.containsKey(RoomAggregate.key(resolvedTile.x() + delta[0], resolvedTile.y() + delta[1]))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isConnected(Set<String> occupiedCoordinates) {
    if (occupiedCoordinates.isEmpty()) {
      return true;
    }
    Set<String> visited = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    String first = occupiedCoordinates.iterator().next();
    visited.add(first);
    queue.add(first);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      String[] parts = current.split(":");
      int x = Integer.parseInt(parts[0]);
      int y = Integer.parseInt(parts[1]);
      for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        String neighbor = RoomAggregate.key(x + delta[0], y + delta[1]);
        if (occupiedCoordinates.contains(neighbor) && visited.add(neighbor)) {
          queue.addLast(neighbor);
        }
      }
    }
    return visited.size() == occupiedCoordinates.size();
  }
}
