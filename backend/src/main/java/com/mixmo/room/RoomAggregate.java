package com.mixmo.room;

import com.mixmo.common.TileLocation;
import com.mixmo.persistence.BanditThemeSelectionEntity;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.persistence.GameRoomEntity;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.RackEntryEntity;
import com.mixmo.persistence.TileEntity;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RoomAggregate(
    GameRoomEntity room,
    List<PlayerEntity> players,
    List<TileEntity> tiles,
    List<RackEntryEntity> rackEntries,
    List<BoardCellEntity> boardCells,
    Optional<BanditThemeSelectionEntity> pendingBanditSelection,
    long lastEventSequence
) {

  public Map<String, PlayerEntity> playersById() {
    return players.stream().collect(Collectors.toMap(PlayerEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
  }

  public Map<String, TileEntity> tilesById() {
    return tiles.stream().collect(Collectors.toMap(TileEntity::getId, Function.identity()));
  }

  public List<PlayerEntity> playersInSeatOrder() {
    return players.stream().sorted(Comparator.comparingInt(PlayerEntity::getSeatOrder)).toList();
  }

  public List<RackEntryEntity> rackEntriesFor(String playerId) {
    return rackEntries.stream()
        .filter(entry -> entry.getPlayerId().equals(playerId))
        .sorted(Comparator.comparingInt(RackEntryEntity::getPosition))
        .toList();
  }

  public List<TileEntity> rackTilesFor(String playerId) {
    Map<String, TileEntity> tileById = tilesById();
    return rackEntriesFor(playerId).stream()
        .map(entry -> tileById.get(entry.getTileId()))
        .toList();
  }

  public Map<String, BoardCellEntity> boardByCoordinate(String playerId) {
    return boardCells.stream()
        .filter(cell -> cell.getPlayerId().equals(playerId))
        .collect(Collectors.toMap(cell -> key(cell.getX(), cell.getY()), Function.identity()));
  }

  public List<BoardCellEntity> boardFor(String playerId) {
    return boardCells.stream().filter(cell -> cell.getPlayerId().equals(playerId)).toList();
  }

  public List<TileEntity> bagTiles() {
    return tiles.stream()
        .filter(tile -> tile.getLocation() == TileLocation.BAG)
        .sorted(Comparator.comparing(TileEntity::getBagPosition))
        .toList();
  }

  public long bagRemaining() {
    return bagTiles().size();
  }

  public static String key(int x, int y) {
    return x + ":" + y;
  }
}

