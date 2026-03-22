package com.mixmo.integration;

import com.mixmo.api.RestModels;
import com.mixmo.common.DeviceType;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.common.Orientation;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.TileKind;
import com.mixmo.common.TileLocation;
import com.mixmo.grid.GridValidationModels;
import com.mixmo.grid.PythonWordValidatorClient;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.persistence.BoardCellRepository;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.RackEntryEntity;
import com.mixmo.persistence.RackEntryRepository;
import com.mixmo.persistence.TileEntity;
import com.mixmo.persistence.TileRepository;
import com.mixmo.placement.PlacementModels.PlacementRequest;
import com.mixmo.placement.PlacementModels.RequestedTile;
import com.mixmo.realtime.RealtimeBroker;
import com.mixmo.room.RoomAggregate;
import com.mixmo.room.RoomService;
import com.mixmo.room.RoomStateLoader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.mixmo.placement.PlacementModels.Coordinate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "mixmo.debug.demo-room.enabled=true",
    "spring.main.allow-bean-definition-overriding=true"
})
class RoomServiceIntegrationTest {

  @TestConfiguration
  static class TestClockConfiguration {

    @Bean
    @Primary
    Clock clock() {
      return new MutableClock(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC);
    }
  }

  static final class MutableClock extends Clock {
    private Instant currentInstant;
    private final ZoneOffset zone;

    MutableClock(Instant currentInstant, ZoneOffset zone) {
      this.currentInstant = currentInstant;
      this.zone = zone;
    }

    void set(Instant instant) {
      currentInstant = instant;
    }

    void advance(Duration duration) {
      currentInstant = currentInstant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return zone;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return Clock.fixed(currentInstant, zone);
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }
  }

  @Autowired
  private RoomService roomService;

  @Autowired
  private RoomStateLoader roomStateLoader;

  @Autowired
  private TileRepository tileRepository;

  @Autowired
  private RackEntryRepository rackEntryRepository;

  @Autowired
  private BoardCellRepository boardCellRepository;

  @MockBean
  private PythonWordValidatorClient pythonWordValidatorClient;

  @Autowired
  private Clock clock;

  @Autowired
  private RealtimeBroker realtimeBroker;

  @BeforeEach
  void stubWordValidator() {
    mutableClock().set(Instant.parse("2026-03-19T10:00:00Z"));
    when(pythonWordValidatorClient.validateBatch(anyList())).thenAnswer(invocation -> {
      List<String> words = invocation.getArgument(0);
      return words.stream()
          .map(word -> new GridValidationModels.PythonValidationResult(word, word, true))
          .toList();
    });
  }

  @Test
  void startInitializesExactBagAndDrawsSixPerPlayer() {
    RoomFixture fixture = createStartedRoom();
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());

    assertEquals(120, aggregate.tiles().size());
    assertEquals(6, aggregate.rackEntriesFor(fixture.hostId()).size());
    assertEquals(6, aggregate.rackEntriesFor(fixture.guestId()).size());
    assertEquals(108, aggregate.bagRemaining());
    assertEquals(17, countNormalTiles(aggregate, "E"));
    assertEquals(2, countKind(aggregate, TileKind.JOKER));
    assertEquals(1, countKind(aggregate, TileKind.BANDIT));
  }

  @Test
  void mixmoRequiresEmptyRackAndDrawsTwoForAllPlayers() {
    RoomFixture fixture = createStartedRoom();
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());

    MixmoException exception = assertThrows(MixmoException.class, () -> roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), aggregate.room().getVersion()));
    assertEquals(ErrorCode.MIXMO_NOT_ALLOWED, exception.getCode());

    emptyRack(fixture.roomId(), fixture.hostId());
    RoomAggregate beforeMixmo = roomStateLoader.loadByRoomId(fixture.roomId());
    roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), beforeMixmo.room().getVersion());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(2, updated.rackEntriesFor(fixture.hostId()).size());
    assertEquals(8, updated.rackEntriesFor(fixture.guestId()).size());
  }

  @Test
  void finalMixmoEndsGameImmediatelyWhenReserveIsEmpty() {
    RoomFixture fixture = createStartedRoom();
    emptyRack(fixture.roomId(), fixture.hostId());
    exhaustBag(fixture.roomId());
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());

    roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), aggregate.room().getVersion());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals("FINISHED", updated.room().getStatus().name());
    assertEquals(fixture.hostId(), updated.room().getWinnerPlayerId());
  }

  @Test
  void banditUsagePausesThenResumesWithSelectedTheme() {
    RoomFixture fixture = createStartedRoom();
    rewriteRack(fixture.roomId(), fixture.hostId(), List.of(tileSpec(TileKind.BANDIT, null)));
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    TileEntity bandit = aggregate.rackTilesFor(fixture.hostId()).getFirst();

    roomService.confirmPlacement(
        fixture.roomId(),
        fixture.hostId(),
        new PlacementRequest("K", Orientation.HORIZONTAL, new Coordinate(0, 0), List.of(new RequestedTile(bandit.getId(), "K")), null),
        aggregate.room().getVersion()
    );

    RoomAggregate paused = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals("PAUSED", paused.room().getStatus().name());

    roomService.selectBanditTheme(fixture.roomId(), fixture.hostId(), "Animals", paused.room().getVersion());

    RoomAggregate resumed = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals("ACTIVE", resumed.room().getStatus().name());
    assertEquals("Animals", resumed.room().getCurrentBanditTheme());
    assertEquals(1, resumed.boardFor(fixture.hostId()).size());
  }

  @Test
  void joinRoomWithExistingSessionTokenIsIdempotent() {
    RestModels.RoomBootstrapData created = roomService.createRoom(new RestModels.CreateRoomRequest("Host", DeviceType.MOBILE)).data();
    String joinSessionToken = UUID.randomUUID().toString();

    RestModels.RoomBootstrapData firstJoin = roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("GuestSpam", joinSessionToken, DeviceType.MOBILE)
    ).data();
    RestModels.RoomBootstrapData secondJoin = roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("GuestSpam", joinSessionToken, DeviceType.MOBILE)
    ).data();

    RoomAggregate aggregate = roomStateLoader.loadByRoomId(created.room().roomId());

    assertEquals(2, aggregate.players().size());
    assertEquals(firstJoin.self().playerId(), secondJoin.self().playerId());
    assertEquals(joinSessionToken, firstJoin.self().sessionToken());
    assertEquals(joinSessionToken, secondJoin.self().sessionToken());
  }

  @Test
  void multipleLiveSocketsKeepPlayerConnectedUntilTheLastSocketCloses() {
    RoomFixture fixture = createStartedRoom();
    WebSocketSession firstSocket = mock(WebSocketSession.class);
    WebSocketSession secondSocket = mock(WebSocketSession.class);
    when(firstSocket.getId()).thenReturn("socket-1");
    when(firstSocket.isOpen()).thenReturn(true);
    when(secondSocket.getId()).thenReturn("socket-2");
    when(secondSocket.isOpen()).thenReturn(true);

    realtimeBroker.register(firstSocket, fixture.roomId(), fixture.hostId());
    realtimeBroker.register(secondSocket, fixture.roomId(), fixture.hostId());

    realtimeBroker.unregister(firstSocket);
    roomService.onSocketDisconnected(fixture.roomId(), fixture.hostId());
    assertTrue(roomStateLoader.loadByRoomId(fixture.roomId()).playersById().get(fixture.hostId()).isConnected());

    realtimeBroker.unregister(secondSocket);
    roomService.onSocketDisconnected(fixture.roomId(), fixture.hostId());
    assertFalse(roomStateLoader.loadByRoomId(fixture.roomId()).playersById().get(fixture.hostId()).isConnected());
  }

  @Test
  void leaveWaitingRoomRemovesPlayerAndRejoinDoesNotCreateAnOfflineThirdSeat() {
    RestModels.RoomBootstrapData created = roomService.createRoom(new RestModels.CreateRoomRequest("Host", DeviceType.MOBILE)).data();
    String joinSessionToken = UUID.randomUUID().toString();
    RestModels.RoomBootstrapData joined = roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("Guest", joinSessionToken, DeviceType.MOBILE)
    ).data();

    roomService.leaveRoom(
        created.room().roomId(),
        new RestModels.LeaveRoomRequest(joined.self().playerId(), joined.self().sessionToken())
    );

    RoomAggregate afterLeave = roomStateLoader.loadByRoomId(created.room().roomId());
    assertEquals(1, afterLeave.players().size());
    assertFalse(afterLeave.playersById().containsKey(joined.self().playerId()));

    roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("Guest", joinSessionToken, DeviceType.MOBILE)
    );

    RoomAggregate afterRejoin = roomStateLoader.loadByRoomId(created.room().roomId());
    assertEquals(2, afterRejoin.players().size());
    assertEquals(2, afterRejoin.players().stream().filter(PlayerEntity::isConnected).count());
    assertEquals(0, afterRejoin.players().stream().filter(player -> !player.isConnected()).count());
  }

  @Test
  void leaveWaitingRoomTransfersHostAndAllowsTheRemainingPlayerToStart() {
    RestModels.RoomBootstrapData created = roomService.createRoom(new RestModels.CreateRoomRequest("Host", DeviceType.MOBILE)).data();
    RestModels.RoomBootstrapData joined = roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("Guest", null, DeviceType.MOBILE)
    ).data();

    roomService.leaveRoom(
        created.room().roomId(),
        new RestModels.LeaveRoomRequest(created.self().playerId(), created.self().sessionToken())
    );

    RoomAggregate transferred = roomStateLoader.loadByRoomId(created.room().roomId());
    assertEquals(joined.self().playerId(), transferred.room().getHostPlayerId());
    assertEquals(1, transferred.players().size());

    roomService.startRoom(created.room().roomId(), new RestModels.StartRoomRequest(joined.self().playerId()));
    assertEquals(RoomStatus.ACTIVE, roomStateLoader.loadByRoomId(created.room().roomId()).room().getStatus());
  }

  @Test
  void hostDisconnectTransfersLobbyOwnershipAndReconnectDoesNotReclaimIt() {
    RestModels.RoomBootstrapData created = roomService.createRoom(new RestModels.CreateRoomRequest("Host", DeviceType.MOBILE)).data();
    RestModels.RoomBootstrapData joined = roomService.joinRoom(
        created.room().roomId(),
        new RestModels.JoinRoomRequest("Guest", null, DeviceType.MOBILE)
    ).data();

    roomService.onSocketDisconnected(created.room().roomId(), created.self().playerId());

    RoomAggregate transferred = roomStateLoader.loadByRoomId(created.room().roomId());
    assertEquals(joined.self().playerId(), transferred.room().getHostPlayerId());
    assertFalse(transferred.playersById().get(created.self().playerId()).isConnected());

    MixmoException cannotStartWithOfflinePlayer = assertThrows(
        MixmoException.class,
        () -> roomService.startRoom(created.room().roomId(), new RestModels.StartRoomRequest(joined.self().playerId()))
    );
    assertEquals(ErrorCode.ROOM_HAS_OFFLINE_PLAYERS, cannotStartWithOfflinePlayer.getCode());

    roomService.reconnect(
        created.room().roomId(),
        new RestModels.ReconnectRequest(created.self().playerId(), created.self().sessionToken(), 0L, 0L)
    );

    RoomAggregate reconnected = roomStateLoader.loadByRoomId(created.room().roomId());
    assertEquals(joined.self().playerId(), reconnected.room().getHostPlayerId());
    assertTrue(reconnected.playersById().get(created.self().playerId()).isConnected());

    roomService.startRoom(created.room().roomId(), new RestModels.StartRoomRequest(joined.self().playerId()));
    assertEquals(RoomStatus.ACTIVE, roomStateLoader.loadByRoomId(created.room().roomId()).room().getStatus());
  }

  @Test
  void leavingTheLastWaitingPlayerDeletesTheRoom() {
    RestModels.RoomBootstrapData created = roomService.createRoom(new RestModels.CreateRoomRequest("Solo", DeviceType.MOBILE)).data();

    roomService.leaveRoom(
        created.room().roomId(),
        new RestModels.LeaveRoomRequest(created.self().playerId(), created.self().sessionToken())
    );

    MixmoException exception = assertThrows(
        MixmoException.class,
        () -> roomStateLoader.loadByRoomId(created.room().roomId())
    );
    assertEquals(ErrorCode.ROOM_NOT_FOUND, exception.getCode());
  }

  @Test
  void reconnectReturnsAuthoritativeRackAfterMissedSharedDraw() {
    RoomFixture fixture = createStartedRoom();
    roomService.onSocketDisconnected(fixture.roomId(), fixture.guestId());
    emptyRack(fixture.roomId(), fixture.hostId());
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), aggregate.room().getVersion());

    RestModels.ReconnectData reconnectData = roomService.reconnect(
        fixture.roomId(),
        new RestModels.ReconnectRequest(fixture.guestId(), fixture.guestToken(), 0L, 0L)
    ).data();

    assertEquals(8, reconnectData.gameSnapshot().selfRack().size());
    assertTrue(reconnectData.missedEventWindow().toInclusive() >= reconnectData.missedEventWindow().fromExclusive());
  }

  @Test
  void roomMembersCanFetchAnotherPlayersBoardView() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.guestId(), "DOG", Orientation.HORIZONTAL, 0, 0);

    RestModels.PlayerBoardViewDto boardView = roomService.getPlayerBoard(
        fixture.roomId(),
        fixture.guestId(),
        fixture.hostId(),
        fixture.hostToken()
    ).data();

    assertEquals(fixture.guestId(), boardView.player().playerId());
    assertEquals(3, boardView.boardCells().size());
    assertEquals(0, boardView.boardCells().getFirst().x());
  }

  @Test
  void nonMembersCannotFetchAnotherPlayersBoardView() {
    RoomFixture fixture = createStartedRoom();
    RestModels.RoomBootstrapData outsider = roomService.createRoom(new RestModels.CreateRoomRequest("Outsider", DeviceType.MOBILE)).data();

    MixmoException exception = assertThrows(
        MixmoException.class,
        () -> roomService.getPlayerBoard(fixture.roomId(), fixture.guestId(), outsider.self().playerId(), outsider.self().sessionToken())
    );

    assertEquals(ErrorCode.UNAUTHORIZED_SESSION, exception.getCode());
  }

  @Test
  void chatHistoryReturnsNewestMessagesAndBroadcastsNewOnes() throws Exception {
    RoomFixture fixture = createStartedRoom();
    WebSocketSession chatSession = mock(WebSocketSession.class);
    when(chatSession.getId()).thenReturn("chat-session");
    when(chatSession.isOpen()).thenReturn(true);
    realtimeBroker.register(chatSession, fixture.roomId(), fixture.guestId());

    for (int index = 1; index <= 55; index += 1) {
      roomService.sendChatMessage(fixture.roomId(), fixture.hostId(), "message " + index);
    }

    RestModels.ChatHistoryDto history = roomService.getChatHistory(
        fixture.roomId(),
        fixture.guestId(),
        fixture.guestToken(),
        50
    ).data();

    assertEquals(50, history.messages().size());
    assertEquals(55L, history.latestSequence());
    assertEquals(6L, history.messages().getFirst().sequenceNumber());
    assertEquals("message 6", history.messages().getFirst().text());
    assertEquals(55L, history.messages().getLast().sequenceNumber());

    verify(chatSession, atLeastOnce()).sendMessage(any(TextMessage.class));
    realtimeBroker.unregister(chatSession);
  }

  @Test
  void chatMessagesRejectBlankAndOversizedPayloads() {
    RoomFixture fixture = createStartedRoom();

    MixmoException blankException = assertThrows(
        MixmoException.class,
        () -> roomService.sendChatMessage(fixture.roomId(), fixture.hostId(), "   ")
    );
    assertEquals(ErrorCode.INVALID_CHAT_MESSAGE, blankException.getCode());

    MixmoException oversizedException = assertThrows(
        MixmoException.class,
        () -> roomService.sendChatMessage(fixture.roomId(), fixture.hostId(), "a".repeat(281))
    );
    assertEquals(ErrorCode.INVALID_CHAT_MESSAGE, oversizedException.getCode());
  }

  @Test
  void staleWatchdogWarnsAfterNinetySecondsAndAutoMixmosThirtySecondsLater() {
    RoomFixture fixture = createStartedRoom();
    RoomAggregate started = roomStateLoader.loadByRoomId(fixture.roomId());

    assertNotNull(started.room().getNextStaleWarningAt());
    assertEquals(null, started.room().getAutomaticMixmoAt());

    mutableClock().advance(Duration.ofSeconds(90));
    roomService.processDueStaleRoom(fixture.roomId());

    RestModels.GameSnapshotDto warnedSnapshot = roomService.getState(fixture.roomId(), fixture.hostId(), fixture.hostToken()).data();
    assertTrue(warnedSnapshot.staleGameState().warningActive());
    assertEquals("Stale game detected", warnedSnapshot.staleGameState().message());
    assertEquals(Instant.parse("2026-03-19T10:02:00Z"), warnedSnapshot.staleGameState().automaticMixmoAt());

    RoomAggregate warned = roomStateLoader.loadByRoomId(fixture.roomId());
    int hostRackBefore = warned.rackEntriesFor(fixture.hostId()).size();
    int guestRackBefore = warned.rackEntriesFor(fixture.guestId()).size();

    mutableClock().advance(Duration.ofSeconds(30));
    roomService.processDueStaleRoom(fixture.roomId());

    RoomAggregate afterAutoMixmo = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(hostRackBefore + 2, afterAutoMixmo.rackEntriesFor(fixture.hostId()).size());
    assertEquals(guestRackBefore + 2, afterAutoMixmo.rackEntriesFor(fixture.guestId()).size());
    assertEquals(Instant.parse("2026-03-19T10:03:30Z"), afterAutoMixmo.room().getNextStaleWarningAt());
    assertEquals(null, afterAutoMixmo.room().getAutomaticMixmoAt());
  }

  @Test
  void automaticMixmoDispatchesEvenWhenTheBoardWouldFailValidation() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "MAU", Orientation.HORIZONTAL, 0, 0);
    when(pythonWordValidatorClient.validateBatch(anyList())).thenAnswer(invocation -> {
      List<String> words = invocation.getArgument(0);
      return words.stream()
          .map(word -> new GridValidationModels.PythonValidationResult(word, word, false))
          .toList();
    });

    mutableClock().advance(Duration.ofSeconds(90));
    roomService.processDueStaleRoom(fixture.roomId());

    RoomAggregate warned = roomStateLoader.loadByRoomId(fixture.roomId());
    int hostRackBefore = warned.rackEntriesFor(fixture.hostId()).size();
    int guestRackBefore = warned.rackEntriesFor(fixture.guestId()).size();

    mutableClock().advance(Duration.ofSeconds(30));
    roomService.processDueStaleRoom(fixture.roomId());

    RoomAggregate afterAutoMixmo = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(hostRackBefore + 2, afterAutoMixmo.rackEntriesFor(fixture.hostId()).size());
    assertEquals(guestRackBefore + 2, afterAutoMixmo.rackEntriesFor(fixture.guestId()).size());
  }

  @Test
  void staleWatchdogDoesNotWarnOrAutoMixmoWhenTheBagIsEmpty() {
    RoomFixture fixture = createStartedRoom();
    exhaustBag(fixture.roomId());
    RoomAggregate beforePoll = roomStateLoader.loadByRoomId(fixture.roomId());
    int hostRackBefore = beforePoll.rackEntriesFor(fixture.hostId()).size();
    int guestRackBefore = beforePoll.rackEntriesFor(fixture.guestId()).size();

    assertEquals(0, beforePoll.bagRemaining());

    mutableClock().advance(Duration.ofSeconds(90));
    roomService.processDueStaleRoom(fixture.roomId());

    RoomAggregate afterPoll = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(hostRackBefore, afterPoll.rackEntriesFor(fixture.hostId()).size());
    assertEquals(guestRackBefore, afterPoll.rackEntriesFor(fixture.guestId()).size());
    assertEquals(null, afterPoll.room().getNextStaleWarningAt());
    assertEquals(null, afterPoll.room().getAutomaticMixmoAt());
  }

  @Test
  void rejectedAuthenticatedMixmoRequestsResetAndClearTheStaleWarning() {
    RoomFixture fixture = createStartedRoom();

    mutableClock().advance(Duration.ofSeconds(90));
    roomService.processDueStaleRoom(fixture.roomId());

    RoomAggregate warned = roomStateLoader.loadByRoomId(fixture.roomId());
    assertNotNull(warned.room().getAutomaticMixmoAt());

    MixmoException exception = assertThrows(
        MixmoException.class,
        () -> roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), warned.room().getVersion())
    );

    assertEquals(ErrorCode.MIXMO_NOT_ALLOWED, exception.getCode());

    RoomAggregate reset = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(null, reset.room().getAutomaticMixmoAt());
    assertEquals(Instant.parse("2026-03-19T10:03:00Z"), reset.room().getNextStaleWarningAt());

    RestModels.GameSnapshotDto snapshot = roomService.getState(fixture.roomId(), fixture.hostId(), fixture.hostToken()).data();
    assertFalse(snapshot.staleGameState().warningActive());
  }

  @Test
  void returnBoardTilesMovesSelectedCellsBackToRackAndClearsAssignedLetters() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "CAT", Orientation.HORIZONTAL, 0, 0);
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    String returnedTileId = aggregate.boardFor(fixture.hostId()).stream()
        .filter(cell -> cell.getX() == 1 && cell.getY() == 0)
        .map(BoardCellEntity::getTileId)
        .findFirst()
        .orElseThrow();

    roomService.returnBoardTiles(fixture.roomId(), fixture.hostId(), List.of(returnedTileId), aggregate.room().getVersion());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(2, updated.boardFor(fixture.hostId()).size());
    assertEquals(1, updated.rackEntriesFor(fixture.hostId()).size());
    assertEquals(returnedTileId, updated.rackEntriesFor(fixture.hostId()).getFirst().getTileId());
    TileEntity tile = tileRepository.findById(returnedTileId).orElseThrow();
    assertEquals(TileLocation.RACK, tile.getLocation());
    assertEquals(null, tile.getAssignedLetter());
    assertEquals(aggregate.room().getVersion() + 1, updated.room().getVersion());
  }

  @Test
  void returnBoardTilesAppendsMultipleTilesInRequestOrder() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "DOG", Orientation.HORIZONTAL, 0, 0);
    rewriteRack(
        fixture.roomId(),
        fixture.hostId(),
        List.of(tileSpec(TileKind.NORMAL, "A"), tileSpec(TileKind.NORMAL, "E"))
    );
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    List<String> existingRackTileIds = aggregate.rackEntriesFor(fixture.hostId()).stream().map(RackEntryEntity::getTileId).toList();
    List<BoardCellEntity> boardCells = aggregate.boardFor(fixture.hostId()).stream()
        .sorted(Comparator.comparingInt(BoardCellEntity::getX))
        .toList();
    String firstReturned = boardCells.get(2).getTileId();
    String secondReturned = boardCells.get(0).getTileId();

    roomService.returnBoardTiles(fixture.roomId(), fixture.hostId(), List.of(firstReturned, secondReturned), aggregate.room().getVersion());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    List<String> rackTileIds = updated.rackEntriesFor(fixture.hostId()).stream().map(RackEntryEntity::getTileId).toList();
    assertEquals(List.of(
        existingRackTileIds.get(0),
        existingRackTileIds.get(1),
        firstReturned,
        secondReturned
    ), rackTileIds);
  }

  @Test
  void returnBoardTilesAllowsDisconnectedBoards() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "DOG", Orientation.HORIZONTAL, 0, 0);
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    String middleTileId = aggregate.boardFor(fixture.hostId()).stream()
        .filter(cell -> cell.getX() == 1 && cell.getY() == 0)
        .map(BoardCellEntity::getTileId)
        .findFirst()
        .orElseThrow();

    roomService.returnBoardTiles(fixture.roomId(), fixture.hostId(), List.of(middleTileId), aggregate.room().getVersion());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(2, updated.boardFor(fixture.hostId()).size());
    assertFalse(updated.boardFor(fixture.hostId()).stream().anyMatch(cell -> cell.getX() == 1 && cell.getY() == 0));
  }

  @Test
  void returnBoardTilesRejectsDuplicateAndForeignTileIdsWithoutMutation() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "CAT", Orientation.HORIZONTAL, 0, 0);
    placeWord(fixture.roomId(), fixture.guestId(), "DOG", Orientation.HORIZONTAL, 0, 0);
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    String hostTileId = aggregate.boardFor(fixture.hostId()).getFirst().getTileId();
    String guestTileId = aggregate.boardFor(fixture.guestId()).getFirst().getTileId();
    int initialBoardCount = aggregate.boardFor(fixture.hostId()).size();
    int initialRackCount = aggregate.rackEntriesFor(fixture.hostId()).size();

    MixmoException duplicateException = assertThrows(
        MixmoException.class,
        () -> roomService.returnBoardTiles(fixture.roomId(), fixture.hostId(), List.of(hostTileId, hostTileId), aggregate.room().getVersion())
    );
    assertEquals(ErrorCode.INVALID_TILE_USAGE, duplicateException.getCode());

    MixmoException foreignException = assertThrows(
        MixmoException.class,
        () -> roomService.returnBoardTiles(fixture.roomId(), fixture.hostId(), List.of(guestTileId), aggregate.room().getVersion())
    );
    assertEquals(ErrorCode.INVALID_TILE_USAGE, foreignException.getCode());

    RoomAggregate updated = roomStateLoader.loadByRoomId(fixture.roomId());
    assertEquals(initialBoardCount, updated.boardFor(fixture.hostId()).size());
    assertEquals(initialRackCount, updated.rackEntriesFor(fixture.hostId()).size());
  }

  @Test
  void returnBoardTilesRejectsPausedFinishedAndVersionMismatch() {
    RoomFixture pausedFixture = createStartedRoom();
    rewriteRack(pausedFixture.roomId(), pausedFixture.hostId(), List.of(tileSpec(TileKind.BANDIT, null)));
    RoomAggregate pausedAggregate = roomStateLoader.loadByRoomId(pausedFixture.roomId());
    TileEntity bandit = pausedAggregate.rackTilesFor(pausedFixture.hostId()).getFirst();
    roomService.confirmPlacement(
        pausedFixture.roomId(),
        pausedFixture.hostId(),
        new PlacementRequest("K", Orientation.HORIZONTAL, new Coordinate(0, 0), List.of(new RequestedTile(bandit.getId(), "K")), null),
        pausedAggregate.room().getVersion()
    );

    MixmoException pausedException = assertThrows(
        MixmoException.class,
        () -> roomService.returnBoardTiles(pausedFixture.roomId(), pausedFixture.hostId(), List.of("missing"), pausedAggregate.room().getVersion() + 1)
    );
    assertEquals(ErrorCode.ROOM_PAUSED, pausedException.getCode());

    RoomFixture finishedFixture = createStartedRoom();
    emptyRack(finishedFixture.roomId(), finishedFixture.hostId());
    exhaustBag(finishedFixture.roomId());
    RoomAggregate finishedAggregate = roomStateLoader.loadByRoomId(finishedFixture.roomId());
    roomService.triggerMixmo(finishedFixture.roomId(), finishedFixture.hostId(), finishedAggregate.room().getVersion());
    RoomAggregate afterFinish = roomStateLoader.loadByRoomId(finishedFixture.roomId());

    MixmoException finishedException = assertThrows(
        MixmoException.class,
        () -> roomService.returnBoardTiles(finishedFixture.roomId(), finishedFixture.hostId(), List.of("missing"), afterFinish.room().getVersion())
    );
    assertEquals(ErrorCode.ROOM_FINISHED, finishedException.getCode());

    RoomFixture versionFixture = createStartedRoom();
    placeWord(versionFixture.roomId(), versionFixture.hostId(), "CAT", Orientation.HORIZONTAL, 0, 0);
    RoomAggregate versionAggregate = roomStateLoader.loadByRoomId(versionFixture.roomId());
    String tileId = versionAggregate.boardFor(versionFixture.hostId()).getFirst().getTileId();
    MixmoException versionException = assertThrows(
        MixmoException.class,
        () -> roomService.returnBoardTiles(versionFixture.roomId(), versionFixture.hostId(), List.of(tileId), versionAggregate.room().getVersion() - 1)
    );
    assertEquals(ErrorCode.ROOM_VERSION_MISMATCH, versionException.getCode());
  }

  @Test
  void demoRoomStartsActiveWithCrossingWordsSeededOnTheBoard() {
    RestModels.RoomBootstrapData bootstrap = roomService.createDemoRoom(new RestModels.CreateRoomRequest("Demo", DeviceType.MOBILE)).data();
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(bootstrap.room().roomId());
    String playerId = bootstrap.self().playerId();

    assertEquals("ACTIVE", aggregate.room().getStatus().name());
    assertEquals(19, aggregate.boardFor(playerId).size());
    assertEquals(6, aggregate.rackEntriesFor(playerId).size());
    assertEquals(95, aggregate.bagRemaining());
    assertBoardCell(aggregate, playerId, 0, 0, "A");
    assertBoardCell(aggregate, playerId, 2, 0, "M");
    assertBoardCell(aggregate, playerId, 2, 1, "I");
    assertBoardCell(aggregate, playerId, 2, 2, "C");
    assertBoardCell(aggregate, playerId, 4, 2, "E");
    assertBoardCell(aggregate, playerId, 4, 3, "U");
    assertBoardCell(aggregate, playerId, 2, 4, "O");
    assertBoardCell(aggregate, playerId, 4, 4, "R");
  }

  @Test
  void mixmoTriggerIsBlockedWhenAuthoritativeGridContainsInvalidWords() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "MAU", Orientation.HORIZONTAL, 0, 0);
    when(pythonWordValidatorClient.validateBatch(List.of("MAU")))
        .thenReturn(List.of(new GridValidationModels.PythonValidationResult("MAU", "MAU", false)));

    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    MixmoException exception = assertThrows(
        MixmoException.class,
        () -> roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), aggregate.room().getVersion())
    );

    assertEquals(ErrorCode.MIXMO_GRID_INVALID, exception.getCode());
  }

  @Test
  void gridValidationReturnsLetterStatusesAndInvalidWords() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "MAU", Orientation.HORIZONTAL, 0, 0);
    when(pythonWordValidatorClient.validateBatch(List.of("MAU")))
        .thenReturn(List.of(new GridValidationModels.PythonValidationResult("MAU", "MAU", false)));

    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    RestModels.GridValidationResponse response = roomService.validateMixmoGrid(
        fixture.roomId(),
        new RestModels.GridValidationRequest(
            fixture.hostId(),
            fixture.hostToken(),
            aggregate.room().getVersion(),
            aggregate.boardFor(fixture.hostId()).stream()
                .map(cell -> new RestModels.GridValidationCellDto(cell.getX(), cell.getY(), cell.getResolvedLetter()))
                .toList()
        )
    ).data();

    assertEquals(false, response.gridValid());
    assertEquals(List.of("MAU"), response.invalidWords());
    assertEquals("INVALID", response.letterStatuses().get("0:0").name());
  }

  @Test
  void mixmoValidationFailsCleanlyWhenPythonApiIsUnavailable() {
    RoomFixture fixture = createStartedRoom();
    placeWord(fixture.roomId(), fixture.hostId(), "MER", Orientation.HORIZONTAL, 0, 0);
    when(pythonWordValidatorClient.validateBatch(anyList()))
        .thenThrow(new MixmoException(
            ErrorCode.VALIDATION_SERVICE_UNAVAILABLE,
            "Word validator is unavailable.",
            HttpStatus.SERVICE_UNAVAILABLE,
            true
        ));

    RoomAggregate aggregate = roomStateLoader.loadByRoomId(fixture.roomId());
    MixmoException exception = assertThrows(
        MixmoException.class,
        () -> roomService.triggerMixmo(fixture.roomId(), fixture.hostId(), aggregate.room().getVersion())
    );

    assertEquals(ErrorCode.VALIDATION_SERVICE_UNAVAILABLE, exception.getCode());
    assertTrue(exception.isRetryable());
  }

  private MutableClock mutableClock() {
    return (MutableClock) clock;
  }

  private RoomFixture createStartedRoom() {
    var created = roomService.createRoom(new RestModels.CreateRoomRequest("Host", DeviceType.MOBILE)).data();
    var joined = roomService.joinRoom(created.room().roomId(), new RestModels.JoinRoomRequest("Guest", null, DeviceType.MOBILE)).data();
    roomService.startRoom(created.room().roomId(), new RestModels.StartRoomRequest(created.self().playerId()));
    return new RoomFixture(created.room().roomId(), created.self().playerId(), created.self().sessionToken(), joined.self().playerId(), joined.self().sessionToken());
  }

  private int countNormalTiles(RoomAggregate aggregate, String face) {
    return (int) aggregate.tiles().stream().filter(tile -> tile.getKind() == TileKind.NORMAL && face.equals(tile.getFaceValue())).count();
  }

  private int countKind(RoomAggregate aggregate, TileKind kind) {
    return (int) aggregate.tiles().stream().filter(tile -> tile.getKind() == kind).count();
  }

  private void emptyRack(String roomId, String playerId) {
    List<RackEntryEntity> entries = new ArrayList<>(rackEntryRepository.findByPlayerIdOrderByPositionAsc(playerId));
    rackEntryRepository.deleteAll(entries);
    int y = 40;
    for (RackEntryEntity entry : entries) {
      TileEntity tile = tileRepository.findById(entry.getTileId()).orElseThrow();
      tile.setLocation(TileLocation.BOARD);
      tile.setAssignedLetter(tile.getKind() == TileKind.NORMAL ? tile.getFaceValue() : "K");
      tileRepository.save(tile);

      BoardCellEntity boardCell = new BoardCellEntity();
      boardCell.setId(UUID.randomUUID().toString());
      boardCell.setPlayerId(playerId);
      boardCell.setTileId(tile.getId());
      boardCell.setResolvedLetter(tile.getAssignedLetter());
      boardCell.setX(boardCellRepository.findByPlayerId(playerId).size());
      boardCell.setY(y++);
      boardCellRepository.save(boardCell);
    }
  }

  private void exhaustBag(String roomId) {
    List<TileEntity> bagTiles = tileRepository.findByRoomId(roomId).stream()
        .filter(tile -> tile.getLocation() == TileLocation.BAG)
        .sorted(Comparator.comparing(tile -> tile.getBagPosition() == null ? Integer.MAX_VALUE : tile.getBagPosition()))
        .toList();
    int offset = 100;
    for (TileEntity tile : bagTiles) {
      tile.setLocation(TileLocation.BOARD);
      tile.setOwnerPlayerId("sink");
      tile.setAssignedLetter(tile.getKind() == TileKind.NORMAL ? tile.getFaceValue() : "K");
      tileRepository.save(tile);
      BoardCellEntity boardCell = new BoardCellEntity();
      boardCell.setId(UUID.randomUUID().toString());
      boardCell.setPlayerId(roomStateLoader.loadByRoomId(roomId).playersInSeatOrder().getFirst().getId());
      boardCell.setTileId(tile.getId());
      boardCell.setResolvedLetter(tile.getAssignedLetter());
      boardCell.setX(offset++);
      boardCell.setY(200);
      boardCellRepository.save(boardCell);
    }
  }

  private void rewriteRack(String roomId, String playerId, List<TileSpec> desiredTiles) {
    List<RackEntryEntity> current = rackEntryRepository.findByPlayerIdOrderByPositionAsc(playerId);
    rackEntryRepository.deleteAll(current);
    for (RackEntryEntity entry : current) {
      TileEntity tile = tileRepository.findById(entry.getTileId()).orElseThrow();
      tile.setLocation(TileLocation.BAG);
      tile.setOwnerPlayerId(null);
      tile.setAssignedLetter(null);
      tile.setBagPosition(nextBagPosition(roomId));
      tileRepository.save(tile);
    }

    for (int index = 0; index < desiredTiles.size(); index++) {
      TileSpec spec = desiredTiles.get(index);
      TileEntity tile = tileRepository.findByRoomId(roomId).stream()
          .filter(candidate -> candidate.getKind() == spec.kind())
          .filter(candidate -> spec.faceValue() == null || spec.faceValue().equals(candidate.getFaceValue()))
          .filter(candidate -> candidate.getLocation() != TileLocation.BOARD)
          .findFirst()
          .orElseThrow();
      rackEntryRepository.findAll().stream()
          .filter(entry -> entry.getTileId().equals(tile.getId()))
          .findFirst()
          .ifPresent(rackEntryRepository::delete);
      tile.setLocation(TileLocation.RACK);
      tile.setOwnerPlayerId(playerId);
      tile.setAssignedLetter(null);
      tile.setBagPosition(null);
      tileRepository.save(tile);

      RackEntryEntity entry = new RackEntryEntity();
      entry.setId(UUID.randomUUID().toString());
      entry.setPlayerId(playerId);
      entry.setTileId(tile.getId());
      entry.setPosition(index);
      rackEntryRepository.save(entry);
    }
  }

  private int nextBagPosition(String roomId) {
    return tileRepository.findByRoomId(roomId).stream()
        .map(TileEntity::getBagPosition)
        .filter(position -> position != null)
        .max(Integer::compareTo)
        .orElse(0) + 1;
  }

  private void assertBoardCell(RoomAggregate aggregate, String playerId, int x, int y, String expectedLetter) {
    BoardCellEntity cell = aggregate.boardFor(playerId).stream()
        .filter(candidate -> candidate.getX() == x && candidate.getY() == y)
        .findFirst()
        .orElseThrow();
    assertEquals(expectedLetter, cell.getResolvedLetter());
  }

  private void placeWord(String roomId, String playerId, String word, Orientation orientation, int startX, int startY) {
    rewriteRack(
        roomId,
        playerId,
        word.chars().mapToObj(character -> tileSpec(TileKind.NORMAL, String.valueOf((char) character))).toList()
    );
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    List<RequestedTile> tiles = aggregate.rackTilesFor(playerId).stream()
        .map(tile -> new RequestedTile(tile.getId(), tile.getFaceValue()))
        .toList();
    roomService.confirmPlacement(
        roomId,
        playerId,
        new PlacementRequest(word, orientation, new Coordinate(startX, startY), tiles, null),
        aggregate.room().getVersion()
    );
  }

  private TileSpec tileSpec(TileKind kind, String faceValue) {
    return new TileSpec(kind, faceValue);
  }

  private record TileSpec(TileKind kind, String faceValue) {
  }

  private record RoomFixture(String roomId, String hostId, String hostToken, String guestId, String guestToken) {
  }
}
