package com.mixmo.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.common.Orientation;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.TileKind;
import com.mixmo.common.TileLocation;
import com.mixmo.persistence.GameRoomEntity;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.RackEntryEntity;
import com.mixmo.persistence.TileEntity;
import com.mixmo.room.RoomAggregate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.mixmo.placement.PlacementModels.Coordinate;
import static com.mixmo.placement.PlacementModels.PlacementRequest;
import static com.mixmo.placement.PlacementModels.RequestedTile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementServiceTest {

  private PlacementService placementService;
  private GameRoomEntity room;
  private PlayerEntity player;

  @BeforeEach
  void setUp() {
    placementService = new PlacementService(
        Mockito.mock(com.mixmo.persistence.TileRepository.class),
        Mockito.mock(com.mixmo.persistence.BoardCellRepository.class),
        Mockito.mock(com.mixmo.persistence.BanditThemeSelectionRepository.class),
        Mockito.mock(com.mixmo.rack.RackService.class),
        Mockito.mock(com.mixmo.persistence.GameRoomRepository.class),
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-03-13T10:00:00Z"), ZoneOffset.UTC)
    );
    room = new GameRoomEntity();
    room.setId("room-1");
    room.setCode("ABCD12");
    room.setStatus(RoomStatus.ACTIVE);
    room.setCreatedAt(Instant.now());
    room.setVersion(2);

    player = new PlayerEntity();
    player.setId("player-1");
    player.setRoomId("room-1");
    player.setName("Jean-Luc");
    player.setSeatOrder(1);
    player.setConnected(true);
    player.setCreatedAt(Instant.now());
    player.setSessionToken("token");
    player.setDeviceType(com.mixmo.common.DeviceType.MOBILE);
  }

  @Test
  void firstPlacementMustCrossOrigin() {
    RoomAggregate aggregate = aggregateWithRack(List.of(normalTile("tile-a", "A")));
    var result = placementService.validate(aggregate, player.getId(), new PlacementRequest(
        "A",
        Orientation.HORIZONTAL,
        new Coordinate(1, 0),
        List.of(new RequestedTile("tile-a", "A")),
        null
    ));

    assertFalse(result.valid());
    assertEquals("first_word_must_cross_origin", result.invalidReason().code());
  }

  @Test
  void disconnectedPlacementIsRejected() {
    var boardCell = new com.mixmo.persistence.BoardCellEntity();
    boardCell.setId("board-1");
    boardCell.setPlayerId(player.getId());
    boardCell.setTileId("placed-a");
    boardCell.setResolvedLetter("A");
    boardCell.setX(0);
    boardCell.setY(0);

    RoomAggregate aggregate = aggregateWithRackAndBoard(
        List.of(normalTile("tile-b", "B")),
        List.of(boardCell)
    );

    var result = placementService.validate(aggregate, player.getId(), new PlacementRequest(
        "B",
        Orientation.HORIZONTAL,
        new Coordinate(4, 4),
        List.of(new RequestedTile("tile-b", "B")),
        null
    ));

    assertFalse(result.valid());
    assertEquals("disconnected_placement", result.invalidReason().code());
  }

  @Test
  void overlapWithMatchingLetterIsAcceptedAndDifferentLetterRejected() {
    var boardCell = new com.mixmo.persistence.BoardCellEntity();
    boardCell.setId("board-1");
    boardCell.setPlayerId(player.getId());
    boardCell.setTileId("placed-a");
    boardCell.setResolvedLetter("A");
    boardCell.setX(0);
    boardCell.setY(0);

    RoomAggregate aggregate = aggregateWithRackAndBoard(
        List.of(normalTile("tile-b", "B")),
        List.of(boardCell)
    );

    var valid = placementService.validate(aggregate, player.getId(), new PlacementRequest(
        "AB",
        Orientation.HORIZONTAL,
        new Coordinate(0, 0),
        List.of(new RequestedTile("tile-b", "B")),
        null
    ));
    assertTrue(valid.valid());

    var invalid = placementService.validate(aggregate, player.getId(), new PlacementRequest(
        "CB",
        Orientation.HORIZONTAL,
        new Coordinate(0, 0),
        List.of(new RequestedTile("tile-b", "B")),
        null
    ));
    assertFalse(invalid.valid());
    assertEquals("collision_with_different_letter", invalid.invalidReason().code());
  }

  @Test
  void jokerCanRepresentAnyLetterAndBanditIsRestricted() {
    RoomAggregate jokerAggregate = aggregateWithRack(List.of(specialTile("joker", TileKind.JOKER)));
    var jokerValid = placementService.validate(jokerAggregate, player.getId(), new PlacementRequest(
        "Q",
        Orientation.HORIZONTAL,
        new Coordinate(0, 0),
        List.of(new RequestedTile("joker", "Q")),
        null
    ));
    assertTrue(jokerValid.valid());

    RoomAggregate banditAggregate = aggregateWithRack(List.of(specialTile("bandit", TileKind.BANDIT)));
    var banditInvalid = placementService.validate(banditAggregate, player.getId(), new PlacementRequest(
        "A",
        Orientation.HORIZONTAL,
        new Coordinate(0, 0),
        List.of(new RequestedTile("bandit", "A")),
        null
    ));
    assertFalse(banditInvalid.valid());
    assertEquals("invalid_bandit_letter", banditInvalid.invalidReason().code());
  }

  private RoomAggregate aggregateWithRack(List<TileEntity> rackTiles) {
    return aggregateWithRackAndBoard(rackTiles, List.of());
  }

  private RoomAggregate aggregateWithRackAndBoard(List<TileEntity> rackTiles, List<com.mixmo.persistence.BoardCellEntity> boardCells) {
    List<RackEntryEntity> rackEntries = rackTiles.stream().map(tile -> {
      RackEntryEntity entry = new RackEntryEntity();
      entry.setId("rack-" + tile.getId());
      entry.setPlayerId(player.getId());
      entry.setTileId(tile.getId());
      entry.setPosition(rackTiles.indexOf(tile));
      return entry;
    }).toList();
    return new RoomAggregate(
        room,
        List.of(player),
        rackTiles,
        rackEntries,
        boardCells,
        Optional.empty(),
        0
    );
  }

  private TileEntity normalTile(String id, String face) {
    TileEntity tile = new TileEntity();
    tile.setId(id);
    tile.setRoomId(room.getId());
    tile.setKind(TileKind.NORMAL);
    tile.setFaceValue(face);
    tile.setOwnerPlayerId(player.getId());
    tile.setLocation(TileLocation.RACK);
    return tile;
  }

  private TileEntity specialTile(String id, TileKind kind) {
    TileEntity tile = new TileEntity();
    tile.setId(id);
    tile.setRoomId(room.getId());
    tile.setKind(kind);
    tile.setFaceValue(null);
    tile.setOwnerPlayerId(player.getId());
    tile.setLocation(TileLocation.RACK);
    return tile;
  }
}

