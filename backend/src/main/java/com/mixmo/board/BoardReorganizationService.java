package com.mixmo.board;

import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.common.TileLocation;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.persistence.BoardCellRepository;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.persistence.TileEntity;
import com.mixmo.persistence.TileRepository;
import com.mixmo.rack.RackService;
import com.mixmo.room.RoomAggregate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BoardReorganizationService {

  private final BoardCellRepository boardCellRepository;
  private final TileRepository tileRepository;
  private final RackService rackService;
  private final GameRoomRepository roomRepository;

  public BoardReorganizationService(
      BoardCellRepository boardCellRepository,
      TileRepository tileRepository,
      RackService rackService,
      GameRoomRepository roomRepository
  ) {
    this.boardCellRepository = boardCellRepository;
    this.tileRepository = tileRepository;
    this.rackService = rackService;
    this.roomRepository = roomRepository;
  }

  public BoardReturnResult returnTiles(RoomAggregate aggregate, String playerId, List<String> tileIds) {
    if (tileIds == null || tileIds.isEmpty()) {
      throw invalidSelection();
    }

    LinkedHashSet<String> orderedTileIds = new LinkedHashSet<>(tileIds);
    if (orderedTileIds.size() != tileIds.size()) {
      throw invalidSelection();
    }

    Map<String, BoardCellEntity> boardCellByTileId = aggregate.boardFor(playerId).stream()
        .collect(Collectors.toMap(BoardCellEntity::getTileId, Function.identity()));
    Map<String, TileEntity> tilesById = aggregate.tilesById();
    List<BoardCellEntity> selectedCells = new ArrayList<>();
    List<String> orderedIds = new ArrayList<>(orderedTileIds);

    for (String tileId : orderedIds) {
      BoardCellEntity boardCell = boardCellByTileId.get(tileId);
      TileEntity tile = tilesById.get(tileId);
      if (boardCell == null || tile == null || tile.getLocation() != TileLocation.BOARD) {
        throw invalidSelection();
      }
      selectedCells.add(boardCell);
    }

    boardCellRepository.deleteAll(selectedCells);
    for (String tileId : orderedIds) {
      TileEntity tile = tilesById.get(tileId);
      tile.setLocation(TileLocation.RACK);
      tile.setAssignedLetter(null);
      tileRepository.save(tile);
    }
    rackService.appendTilesToRack(playerId, orderedIds);
    aggregate.room().setVersion(aggregate.room().getVersion() + 1);
    roomRepository.save(aggregate.room());
    return new BoardReturnResult(orderedIds, orderedIds.size());
  }

  private MixmoException invalidSelection() {
    return new MixmoException(ErrorCode.INVALID_TILE_USAGE, "Selected board tiles are invalid.", HttpStatus.BAD_REQUEST);
  }

  public record BoardReturnResult(
      List<String> tileIds,
      int returnedCount
  ) {
  }
}
