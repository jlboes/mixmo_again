package com.mixmo.room;

import com.mixmo.api.RestModels;
import com.mixmo.bandit.BanditService;
import com.mixmo.board.BoardReorganizationService;
import com.mixmo.common.DeviceType;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.EventType;
import com.mixmo.common.MixmoException;
import com.mixmo.common.MixmoTriggerReason;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.VersionedResult;
import com.mixmo.grid.GridValidationModels;
import com.mixmo.grid.GridValidationService;
import com.mixmo.mixmo.MixmoService;
import com.mixmo.persistence.BoardCellEntity;
import com.mixmo.persistence.BoardCellRepository;
import com.mixmo.persistence.GameRoomEntity;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.PlayerRepository;
import com.mixmo.persistence.RoomChatMessageEntity;
import com.mixmo.persistence.RoomChatMessageRepository;
import com.mixmo.persistence.TileEntity;
import com.mixmo.persistence.TileRepository;
import com.mixmo.placement.PlacementModels.PlacementOutcome;
import com.mixmo.placement.PlacementModels.PlacementOutcomeType;
import com.mixmo.placement.PlacementModels.PlacementRequest;
import com.mixmo.placement.PlacementModels.PlacementSuggestion;
import com.mixmo.placement.PlacementModels.PlacementValidationResult;
import com.mixmo.placement.PlacementService;
import com.mixmo.realtime.RealtimeBroker;
import com.mixmo.realtime.SocketModels;
import com.mixmo.suggestion.SuggestionService;
import com.mixmo.tile.TileBagService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

  private final GameRoomRepository roomRepository;
  private final PlayerRepository playerRepository;
  private final RoomStateLoader roomStateLoader;
  private final RoomLockManager roomLockManager;
  private final RoomCodeGenerator roomCodeGenerator;
  private final TileBagService tileBagService;
  private final TileRepository tileRepository;
  private final BoardCellRepository boardCellRepository;
  private final RoomChatMessageRepository roomChatMessageRepository;
  private final GameStateAssembler gameStateAssembler;
  private final GameEventStore gameEventStore;
  private final PlacementService placementService;
  private final BoardReorganizationService boardReorganizationService;
  private final SuggestionService suggestionService;
  private final GridValidationService gridValidationService;
  private final MixmoService mixmoService;
  private final BanditService banditService;
  private final RealtimeBroker realtimeBroker;
  private final Clock clock;
  private final Duration staleWarningDelay;
  private final Duration staleAutoDelay;
  private final boolean demoRoomEnabled;

  public RoomService(
      GameRoomRepository roomRepository,
      PlayerRepository playerRepository,
      RoomStateLoader roomStateLoader,
      RoomLockManager roomLockManager,
      RoomCodeGenerator roomCodeGenerator,
      TileBagService tileBagService,
      TileRepository tileRepository,
      BoardCellRepository boardCellRepository,
      RoomChatMessageRepository roomChatMessageRepository,
      GameStateAssembler gameStateAssembler,
      GameEventStore gameEventStore,
      PlacementService placementService,
      BoardReorganizationService boardReorganizationService,
      SuggestionService suggestionService,
      GridValidationService gridValidationService,
      MixmoService mixmoService,
      BanditService banditService,
      RealtimeBroker realtimeBroker,
      Clock clock,
      @Value("${mixmo.stale.warning-delay:90s}") Duration staleWarningDelay,
      @Value("${mixmo.stale.auto-delay:30s}") Duration staleAutoDelay,
      @Value("${mixmo.debug.demo-room.enabled:false}") boolean demoRoomEnabled
  ) {
    this.roomRepository = roomRepository;
    this.playerRepository = playerRepository;
    this.roomStateLoader = roomStateLoader;
    this.roomLockManager = roomLockManager;
    this.roomCodeGenerator = roomCodeGenerator;
    this.tileBagService = tileBagService;
    this.tileRepository = tileRepository;
    this.boardCellRepository = boardCellRepository;
    this.roomChatMessageRepository = roomChatMessageRepository;
    this.gameStateAssembler = gameStateAssembler;
    this.gameEventStore = gameEventStore;
    this.placementService = placementService;
    this.boardReorganizationService = boardReorganizationService;
    this.suggestionService = suggestionService;
    this.gridValidationService = gridValidationService;
    this.mixmoService = mixmoService;
    this.banditService = banditService;
    this.realtimeBroker = realtimeBroker;
    this.clock = clock;
    this.staleWarningDelay = staleWarningDelay;
    this.staleAutoDelay = staleAutoDelay;
    this.demoRoomEnabled = demoRoomEnabled;
  }

  @Transactional
  public VersionedResult<RestModels.RoomBootstrapData> createRoom(RestModels.CreateRoomRequest request) {
    CreatedRoomContext context = createRoomBase(request);
    return new VersionedResult<>(gameStateAssembler.bootstrap(context.aggregate(), context.player()), context.aggregate().room().getVersion());
  }

  @Transactional
  public VersionedResult<RestModels.RoomBootstrapData> createDemoRoom(RestModels.CreateRoomRequest request) {
    if (!demoRoomEnabled) {
      throw new MixmoException(ErrorCode.ROOM_NOT_FOUND, "Demo room is disabled.", HttpStatus.NOT_FOUND);
    }
    RestModels.CreateRoomRequest normalizedRequest = new RestModels.CreateRoomRequest(
        request == null || request.playerName() == null || request.playerName().isBlank() ? "Demo Host" : request.playerName(),
        request == null ? null : request.deviceType()
    );
    CreatedRoomContext context = createRoomBase(normalizedRequest);
    String roomId = context.aggregate().room().getId();
    String playerId = context.player().getId();
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    tileBagService.initializeBag(aggregate);
    RoomAggregate seeded = roomStateLoader.loadByRoomId(roomId);
    seedDemoGrid(seeded, playerId);
    seeded = roomStateLoader.loadByRoomId(roomId);
    tileBagService.drawTiles(seeded, playerId, 6);
    Instant now = Instant.now(clock);
    seeded.room().setStatus(RoomStatus.ACTIVE);
    seeded.room().setStartedAt(now);
    armStaleSchedule(seeded.room(), now);
    seeded.room().setVersion(seeded.room().getVersion() + 1);
    roomRepository.save(seeded.room());
    gameEventStore.appendEvent(roomId, EventType.GAME_STARTED, Map.of("roomId", roomId, "demoRoom", true), now);
    RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
    gameEventStore.saveSnapshot(updated, now);
    realtimeBroker.sendRoomSnapshots(updated);
    return new VersionedResult<>(gameStateAssembler.bootstrap(updated, updated.playersById().get(playerId)), updated.room().getVersion());
  }

  private CreatedRoomContext createRoomBase(RestModels.CreateRoomRequest request) {
    Instant now = Instant.now(clock);
    GameRoomEntity room = new GameRoomEntity();
    room.setId(UUID.randomUUID().toString());
    room.setCode(roomCodeGenerator.nextCode());
    room.setStatus(RoomStatus.WAITING);
    room.setCreatedAt(now);
    room.setVersion(0);
    PlayerEntity player = newPlayer(room.getId(), request.playerName(), request.deviceType(), 1, null, now);
    room.setHostPlayerId(player.getId());
    roomRepository.save(room);
    playerRepository.save(player);

    RoomAggregate aggregate = roomStateLoader.loadByRoomId(room.getId());
    gameEventStore.appendEvent(room.getId(), EventType.ROOM_CREATED, Map.of("playerId", player.getId()), now);
    aggregate = roomStateLoader.loadByRoomId(room.getId());
    gameEventStore.saveSnapshot(aggregate, now);
    return new CreatedRoomContext(aggregate, player);
  }

  @Transactional
  public VersionedResult<RestModels.RoomBootstrapData> joinRoom(String roomIdOrCode, RestModels.JoinRoomRequest request) {
    String roomId = roomStateLoader.load(roomIdOrCode).room().getId();
    return roomLockManager.withRoomLock(roomId, () -> doJoinRoom(roomId, request));
  }

  @Transactional
  public VersionedResult<Map<String, Object>> leaveRoom(String roomIdOrCode, RestModels.LeaveRoomRequest request) {
    String roomId = roomStateLoader.load(roomIdOrCode).room().getId();
    return roomLockManager.withRoomLock(roomId, () -> doLeaveRoom(roomId, request));
  }

  @Transactional
  public VersionedResult<Map<String, Object>> startRoom(String roomIdOrCode, RestModels.StartRoomRequest request) {
    String roomId = roomStateLoader.load(roomIdOrCode).room().getId();
    return roomLockManager.withRoomLock(roomId, () -> doStartRoom(roomId, request));
  }

  public VersionedResult<RestModels.RoomDto> getRoom(String roomIdOrCode, String playerId, String sessionToken) {
    RoomAggregate aggregate = authenticate(roomIdOrCode, playerId, sessionToken);
    return new VersionedResult<>(gameStateAssembler.roomSummary(aggregate), aggregate.room().getVersion());
  }

  public VersionedResult<RestModels.GameSnapshotDto> getState(String roomIdOrCode, String playerId, String sessionToken) {
    RoomAggregate aggregate = authenticate(roomIdOrCode, playerId, sessionToken);
    return new VersionedResult<>(gameStateAssembler.snapshot(aggregate, playerId), aggregate.room().getVersion());
  }

  public VersionedResult<RestModels.PlayerBoardViewDto> getPlayerBoard(String roomIdOrCode, String targetPlayerId, String playerId, String sessionToken) {
    RoomAggregate aggregate = authenticate(roomIdOrCode, playerId, sessionToken);
    requirePlayerInRoom(aggregate, targetPlayerId);
    return new VersionedResult<>(gameStateAssembler.playerBoardView(aggregate, targetPlayerId), aggregate.room().getVersion());
  }

  public VersionedResult<RestModels.ChatHistoryDto> getChatHistory(String roomIdOrCode, String playerId, String sessionToken, Integer limit) {
    RoomAggregate aggregate = authenticate(roomIdOrCode, playerId, sessionToken);
    int normalizedLimit = Math.max(1, Math.min(50, limit == null ? 50 : limit));
    List<RoomChatMessageEntity> recentMessages = roomChatMessageRepository.findTop50ByRoomIdOrderBySequenceNumberDesc(aggregate.room().getId());
    List<RoomChatMessageEntity> limitedMessages = recentMessages.stream()
        .sorted(Comparator.comparingLong(RoomChatMessageEntity::getSequenceNumber).reversed())
        .limit(normalizedLimit)
        .toList();
    return new VersionedResult<>(gameStateAssembler.chatHistory(aggregate, limitedMessages), aggregate.room().getVersion());
  }

  public VersionedResult<RestModels.GridValidationResponse> validateMixmoGrid(String roomIdOrCode, RestModels.GridValidationRequest request) {
    RoomAggregate aggregate = authenticate(roomIdOrCode, request.playerId(), request.sessionToken());
    assertVersion(aggregate, request.expectedRoomVersion());
    requireGameplayAllowed(aggregate, request.playerId());

    GridValidationModels.GridValidationResult validationResult = gridValidationService.validateSubmittedGrid(
        request.boardCells() == null
            ? List.of()
            : request.boardCells().stream()
                .map(cell -> new GridValidationModels.GridCell(cell.x(), cell.y(), cell.letter()))
                .toList()
    );
    return new VersionedResult<>(toGridValidationResponse(validationResult), aggregate.room().getVersion());
  }

  @Transactional
  public VersionedResult<RestModels.ReconnectData> reconnect(String roomIdOrCode, RestModels.ReconnectRequest request) {
    String roomId = roomStateLoader.load(roomIdOrCode).room().getId();
    return roomLockManager.withRoomLock(roomId, () -> doReconnect(roomId, request));
  }

  @Transactional(noRollbackFor = MixmoException.class)
  public VersionedResult<Map<String, Object>> triggerManualMixmo(String roomIdOrCode, RestModels.ManualMixmoRequest request) {
    String roomId = roomStateLoader.load(roomIdOrCode).room().getId();
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = authenticate(roomId, request.playerId(), request.sessionToken());
      resetStaleScheduleAfterAuthenticatedMixmo(aggregate, Instant.now(clock), "MANUAL_REQUEST_RECEIVED");
      return resolveMixmo(aggregate, request.playerId(), MixmoTriggerReason.MANUAL, true);
    });
  }

  public PlacementValidationResult preview(String roomId, String playerId, PlacementRequest request, Long expectedRoomVersion) {
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    assertVersion(aggregate, expectedRoomVersion);
    return placementService.validate(aggregate, playerId, request);
  }

  public List<PlacementSuggestion> suggestions(String roomId, String playerId, String candidateWord, DeviceType deviceType, Long expectedRoomVersion) {
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    assertVersion(aggregate, expectedRoomVersion);
    return suggestionService.suggestions(aggregate, playerId, candidateWord, deviceType);
  }

  @Transactional
  public VersionedResult<RestModels.ChatMessageDto> sendChatMessage(String roomId, String playerId, String text) {
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      PlayerEntity player = requirePlayerInRoom(aggregate, playerId);
      RoomChatMessageEntity message = new RoomChatMessageEntity();
      message.setRoomId(aggregate.room().getId());
      message.setPlayerId(player.getId());
      message.setMessageText(normalizeChatMessage(text));
      message.setCreatedAt(Instant.now(clock));
      message.setSequenceNumber(roomChatMessageRepository.findMaxSequenceNumber(aggregate.room().getId()).orElse(0L) + 1);
      roomChatMessageRepository.save(message);
      pruneChatHistory(aggregate.room().getId());
      RestModels.ChatMessageDto payload = gameStateAssembler.chatMessage(aggregate, message);
      realtimeBroker.sendToRoom(aggregate.room().getId(), new SocketModels.EventEnvelope(
          "chat.message.created",
          null,
          aggregate.room().getId(),
          aggregate.room().getVersion(),
          payload
      ));
      return new VersionedResult<>(payload, aggregate.room().getVersion());
    });
  }

  @Transactional
  public VersionedResult<Map<String, Object>> confirmPlacement(String roomId, String playerId, PlacementRequest request, Long expectedRoomVersion) {
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      assertVersion(aggregate, expectedRoomVersion);
      requireGameplayAllowed(aggregate, playerId);
      PlacementOutcome outcome = placementService.commitOrPause(aggregate, playerId, request);
      RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
      Instant now = Instant.now(clock);
      if (outcome.type() == PlacementOutcomeType.PAUSED_FOR_BANDIT) {
        boolean warningWasActive = isStaleWarningActive(updated.room());
        if (clearStaleSchedule(updated.room())) {
          roomRepository.save(updated.room());
        }
        if (warningWasActive) {
          appendStaleWarningClearedEvent(roomId, "GAME_PAUSED", now);
        }
        Map<String, Object> pausePayload = mapOf(
            "reason", "BANDIT_THEME_REQUIRED",
            "triggeringPlayerId", playerId
        );
        gameEventStore.appendEvent(roomId, EventType.GAME_PAUSED, pausePayload, now);
        updated = roomStateLoader.loadByRoomId(roomId);
        gameEventStore.saveSnapshot(updated, now);
        realtimeBroker.sendToRoom(roomId, new SocketModels.EventEnvelope("game.paused", null, roomId, updated.room().getVersion(), pausePayload));
        realtimeBroker.sendRoomSnapshots(updated);
        return new VersionedResult<>(Map.of("pausedForBandit", true), updated.room().getVersion());
      }
      Map<String, Object> placementPayload = mapOf(
          "playerId", playerId,
          "previewCells", outcome.validationResult().previewCells()
      );
      gameEventStore.appendEvent(roomId, EventType.PLACEMENT_CONFIRMED, placementPayload, now);
      updated = roomStateLoader.loadByRoomId(roomId);
      gameEventStore.saveSnapshot(updated, now);
      realtimeBroker.sendRoomSnapshots(updated);
      return new VersionedResult<>(Map.of("committed", true), updated.room().getVersion());
    });
  }

  @Transactional
  public VersionedResult<Map<String, Object>> returnBoardTiles(String roomId, String playerId, List<String> tileIds, Long expectedRoomVersion) {
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      assertVersion(aggregate, expectedRoomVersion);
      requireGameplayAllowed(aggregate, playerId);
      var result = boardReorganizationService.returnTiles(aggregate, playerId, tileIds);
      RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
      Instant now = Instant.now(clock);
      gameEventStore.appendEvent(roomId, EventType.BOARD_TILES_RETURNED, mapOf(
          "playerId", playerId,
          "tileIds", result.tileIds(),
          "returnedCount", result.returnedCount()
      ), now);
      updated = roomStateLoader.loadByRoomId(roomId);
      gameEventStore.saveSnapshot(updated, now);
      realtimeBroker.sendRoomSnapshots(updated);
      return new VersionedResult<>(Map.of(
          "tileIds", result.tileIds(),
          "returnedCount", result.returnedCount()
      ), updated.room().getVersion());
    });
  }

  @Transactional
  public VersionedResult<Map<String, Object>> selectBanditTheme(String roomId, String playerId, String theme, Long expectedRoomVersion) {
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      assertVersion(aggregate, expectedRoomVersion);
      var selection = banditService.selectTheme(aggregate, playerId, theme);
      RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
      Instant now = Instant.now(clock);
      if (staleScheduleEligible(updated)) {
        armStaleSchedule(updated.room(), now);
        roomRepository.save(updated.room());
      }
      gameEventStore.appendEvent(roomId, EventType.BANDIT_THEME_SELECTED, mapOf("selectedByPlayerId", playerId, "theme", selection.getSelectedTheme()), now);
      gameEventStore.appendEvent(roomId, EventType.GAME_RESUMED, mapOf("reason", "BANDIT_THEME_SELECTED"), now);
      updated = roomStateLoader.loadByRoomId(roomId);
      gameEventStore.saveSnapshot(updated, now);
      realtimeBroker.sendToRoom(roomId, new SocketModels.EventEnvelope("bandit.theme.selected", null, roomId, updated.room().getVersion(), mapOf("selectedByPlayerId", playerId, "theme", selection.getSelectedTheme())));
      realtimeBroker.sendRoomSnapshots(updated);
      realtimeBroker.sendToRoom(roomId, new SocketModels.EventEnvelope("game.resumed", null, roomId, updated.room().getVersion(), Map.of("reason", "BANDIT_THEME_SELECTED")));
      return new VersionedResult<>(Map.of("theme", selection.getSelectedTheme()), updated.room().getVersion());
    });
  }

  @Transactional(noRollbackFor = MixmoException.class)
  public VersionedResult<Map<String, Object>> triggerMixmo(String roomId, String playerId, Long expectedRoomVersion) {
    return roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      resetStaleScheduleAfterAuthenticatedMixmo(aggregate, Instant.now(clock), "PLAYER_REQUEST_RECEIVED");
      assertVersion(aggregate, expectedRoomVersion);
      requireGameplayAllowed(aggregate, playerId);
      return resolveMixmo(aggregate, playerId, MixmoTriggerReason.PLAYER, true);
    });
  }

  public List<String> dueStaleRoomIds() {
    return roomRepository.findDueStaleRoomIds(RoomStatus.ACTIVE, Instant.now(clock));
  }

  @Transactional
  public void processDueStaleRoom(String roomId) {
    roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
      Instant now = Instant.now(clock);

      if (!staleScheduleEligible(aggregate)) {
        boolean warningWasActive = isStaleWarningActive(aggregate.room());
        if (clearStaleSchedule(aggregate.room())) {
          roomRepository.save(aggregate.room());
        }
        if (warningWasActive) {
          appendStaleWarningClearedEvent(roomId, "STALE_DISABLED", now);
          RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
          gameEventStore.saveSnapshot(updated, now);
          realtimeBroker.sendRoomSnapshots(updated);
        }
        return;
      }

      if (isStaleWarningDue(aggregate.room(), now)) {
        activateStaleWarning(aggregate.room(), now);
        roomRepository.save(aggregate.room());
        appendStaleWarningStartedEvent(roomId, aggregate.room().getAutomaticMixmoAt(), now);
        RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
        gameEventStore.saveSnapshot(updated, now);
        realtimeBroker.sendRoomSnapshots(updated);
        return;
      }

      if (isAutomaticMixmoDue(aggregate.room(), now)) {
        resolveAutomaticMixmo(aggregate);
      }
    });
  }

  @Transactional
  public void onSocketConnected(String roomId, String playerId, String sessionToken) {
    roomLockManager.withRoomLock(roomId, () -> {
      RoomAggregate aggregate = authenticate(roomId, playerId, sessionToken);
      PlayerEntity player = aggregate.playersById().get(playerId);
      if (!player.isConnected() && realtimeBroker.hasActiveSession(roomId, playerId)) {
        player.setConnected(true);
        aggregate.room().setVersion(aggregate.room().getVersion() + 1);
        playerRepository.save(player);
        roomRepository.save(aggregate.room());
        Instant now = Instant.now(clock);
        RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
        gameEventStore.appendEvent(roomId, EventType.GAME_STATE_UPDATED, Map.of("playerId", playerId, "connected", true), now);
        updated = roomStateLoader.loadByRoomId(roomId);
        gameEventStore.saveSnapshot(updated, now);
        realtimeBroker.sendRoomSnapshots(updated);
      }
    });
  }

  @Transactional
  public void onSocketDisconnected(String roomId, String playerId) {
    roomLockManager.withRoomLock(roomId, () -> {
      if (realtimeBroker.hasActiveSession(roomId, playerId)) {
        return;
      }
      RoomAggregate aggregate;
      try {
        aggregate = roomStateLoader.loadByRoomId(roomId);
      } catch (MixmoException exception) {
        if (exception.getCode() == ErrorCode.ROOM_NOT_FOUND) {
          return;
        }
        throw exception;
      }
      PlayerEntity player = aggregate.playersById().get(playerId);
      if (player != null && player.isConnected()) {
        Instant now = Instant.now(clock);
        player.setConnected(false);
        aggregate.room().setVersion(aggregate.room().getVersion() + 1);
        String previousHostPlayerId = aggregate.room().getHostPlayerId();
        String nextHostPlayerId = maybeTransferWaitingHost(aggregate, playerId);
        playerRepository.save(player);
        roomRepository.save(aggregate.room());
        RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
        Map<String, Object> payload = mapOf(
            "playerId", playerId,
            "connected", false,
            "previousHostPlayerId", previousHostPlayerId,
            "hostPlayerId", nextHostPlayerId
        );
        gameEventStore.appendEvent(roomId, EventType.GAME_STATE_UPDATED, payload, now);
        updated = roomStateLoader.loadByRoomId(roomId);
        gameEventStore.saveSnapshot(updated, now);
        realtimeBroker.sendRoomSnapshots(updated);
      }
    });
  }

  public RoomAggregate authenticate(String roomIdOrCode, String playerId, String sessionToken) {
    RoomAggregate aggregate = roomStateLoader.load(roomIdOrCode);
    PlayerEntity player = aggregate.playersById().get(playerId);
    if (player == null || !player.getSessionToken().equals(sessionToken)) {
      throw new MixmoException(ErrorCode.UNAUTHORIZED_SESSION, "The provided session is invalid.", HttpStatus.UNAUTHORIZED);
    }
    return aggregate;
  }

  public RoomAggregate authenticateSocket(String roomIdOrCode, String playerId, String sessionToken) {
    return authenticate(roomIdOrCode, playerId, sessionToken);
  }

  private PlayerEntity requirePlayerInRoom(RoomAggregate aggregate, String playerId) {
    PlayerEntity player = aggregate.playersById().get(playerId);
    if (player == null) {
      throw new MixmoException(ErrorCode.PLAYER_NOT_FOUND, "The requested player was not found in this room.", HttpStatus.NOT_FOUND);
    }
    return player;
  }

  private VersionedResult<RestModels.RoomBootstrapData> doJoinRoom(String roomId, RestModels.JoinRoomRequest request) {
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    if (aggregate.room().getStatus() != RoomStatus.WAITING) {
      throw new MixmoException(ErrorCode.ROOM_NOT_WAITING, "Room is no longer accepting players.", HttpStatus.CONFLICT);
    }
    PlayerEntity player = null;
    if (request.sessionToken() != null) {
      player = aggregate.players().stream()
          .filter(candidate -> candidate.getSessionToken().equals(request.sessionToken()))
          .findFirst()
          .orElse(null);
    }
    if (player == null) {
      player = newPlayer(
          roomId,
          request.playerName(),
          request.deviceType(),
          nextAvailableSeatOrder(aggregate.players()),
          request.sessionToken(),
          Instant.now(clock)
      );
      playerRepository.save(player);
      aggregate.room().setVersion(aggregate.room().getVersion() + 1);
      roomRepository.save(aggregate.room());
      gameEventStore.appendEvent(roomId, EventType.PLAYER_JOINED, Map.of("playerId", player.getId()), Instant.now(clock));
    } else {
      player.setConnected(true);
      playerRepository.save(player);
    }
    RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
    gameEventStore.saveSnapshot(updated, Instant.now(clock));
    realtimeBroker.sendRoomSnapshots(updated);
    return new VersionedResult<>(gameStateAssembler.bootstrap(updated, player), updated.room().getVersion());
  }

  private VersionedResult<Map<String, Object>> doLeaveRoom(String roomId, RestModels.LeaveRoomRequest request) {
    RoomAggregate aggregate = authenticate(roomId, request.playerId(), request.sessionToken());
    if (aggregate.room().getStatus() != RoomStatus.WAITING) {
      throw new MixmoException(ErrorCode.ROOM_NOT_WAITING, "Only waiting rooms support lobby leave.", HttpStatus.CONFLICT);
    }

    PlayerEntity leavingPlayer = aggregate.playersById().get(request.playerId());
    Instant now = Instant.now(clock);
    String previousHostPlayerId = aggregate.room().getHostPlayerId();

    playerRepository.delete(leavingPlayer);

    List<PlayerEntity> remainingPlayers = aggregate.players().stream()
        .filter(candidate -> !candidate.getId().equals(leavingPlayer.getId()))
        .toList();
    if (remainingPlayers.isEmpty()) {
      roomRepository.delete(aggregate.room());
      return new VersionedResult<>(Map.of("left", true, "roomDeleted", true), aggregate.room().getVersion() + 1);
    }

    aggregate.room().setHostPlayerId(nextWaitingHostId(previousHostPlayerId, leavingPlayer.getId(), remainingPlayers));
    aggregate.room().setVersion(aggregate.room().getVersion() + 1);
    roomRepository.save(aggregate.room());

    RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
    Map<String, Object> payload = mapOf(
        "playerId", leavingPlayer.getId(),
        "leftWaitingRoom", true,
        "previousHostPlayerId", previousHostPlayerId,
        "hostPlayerId", updated.room().getHostPlayerId()
    );
    gameEventStore.appendEvent(roomId, EventType.GAME_STATE_UPDATED, payload, now);
    updated = roomStateLoader.loadByRoomId(roomId);
    gameEventStore.saveSnapshot(updated, now);
    realtimeBroker.sendRoomSnapshots(updated);
    return new VersionedResult<>(Map.of("left", true, "roomDeleted", false), updated.room().getVersion());
  }

  private VersionedResult<Map<String, Object>> doStartRoom(String roomId, RestModels.StartRoomRequest request) {
    RoomAggregate aggregate = roomStateLoader.loadByRoomId(roomId);
    if (aggregate.room().getStatus() != RoomStatus.WAITING) {
      throw new MixmoException(ErrorCode.ROOM_ALREADY_STARTED, "Room has already started.", HttpStatus.CONFLICT);
    }
    PlayerEntity host = aggregate.playersById().get(request.requestedByPlayerId());
    if (host == null || !Objects.equals(aggregate.room().getHostPlayerId(), request.requestedByPlayerId())) {
      throw new MixmoException(ErrorCode.UNAUTHORIZED_SESSION, "Only the room creator can start the room.", HttpStatus.FORBIDDEN);
    }
    if (aggregate.players().stream().anyMatch(player -> !player.isConnected())) {
      throw new MixmoException(
          ErrorCode.ROOM_HAS_OFFLINE_PLAYERS,
          "All lobby players must be connected before starting.",
          HttpStatus.CONFLICT
      );
    }
    tileBagService.initializeBag(aggregate);
    RoomAggregate initialized = roomStateLoader.loadByRoomId(roomId);
    for (PlayerEntity player : initialized.playersInSeatOrder()) {
      tileBagService.drawTiles(initialized, player.getId(), 6);
    }
    Instant now = Instant.now(clock);
    initialized.room().setStatus(RoomStatus.ACTIVE);
    initialized.room().setStartedAt(now);
    armStaleSchedule(initialized.room(), now);
    initialized.room().setVersion(initialized.room().getVersion() + 1);
    roomRepository.save(initialized.room());
    RoomAggregate updated = roomStateLoader.loadByRoomId(roomId);
    gameEventStore.appendEvent(roomId, EventType.GAME_STARTED, Map.of("roomId", roomId), now);
    updated = roomStateLoader.loadByRoomId(roomId);
    gameEventStore.saveSnapshot(updated, now);
    realtimeBroker.sendRoomSnapshots(updated);
    return new VersionedResult<>(Map.of("gameSnapshot", gameStateAssembler.snapshot(updated, host.getId())), updated.room().getVersion());
  }

  private VersionedResult<RestModels.ReconnectData> doReconnect(String roomId, RestModels.ReconnectRequest request) {
    RoomAggregate aggregate = authenticate(roomId, request.playerId(), request.sessionToken());
    PlayerEntity player = aggregate.playersById().get(request.playerId());
    boolean changed = !player.isConnected();
    if (changed) {
      player.setConnected(true);
      playerRepository.save(player);
      aggregate.room().setVersion(aggregate.room().getVersion() + 1);
      roomRepository.save(aggregate.room());
      gameEventStore.appendEvent(roomId, EventType.PLAYER_RECONNECTED, Map.of("playerId", player.getId()), Instant.now(clock));
      aggregate = roomStateLoader.loadByRoomId(roomId);
      gameEventStore.saveSnapshot(aggregate, Instant.now(clock));
      realtimeBroker.sendRoomSnapshots(aggregate);
    }
    long fromExclusive = request.lastKnownEventSequence() == null ? aggregate.lastEventSequence() : request.lastKnownEventSequence();
    RestModels.MissedEventWindowDto missedWindow = new RestModels.MissedEventWindowDto(fromExclusive, aggregate.lastEventSequence());
    return new VersionedResult<>(new RestModels.ReconnectData(
        true,
        gameStateAssembler.snapshot(aggregate, request.playerId()),
        missedWindow
    ), aggregate.room().getVersion());
  }

  private VersionedResult<Map<String, Object>> resolveMixmo(
      RoomAggregate aggregate,
      String playerId,
      MixmoTriggerReason triggerReason,
      boolean validateGrid
  ) {
    if (validateGrid) {
      GridValidationModels.GridValidationResult validationResult = gridValidationService.validateAuthoritativeGrid(aggregate, playerId);
      if (!validationResult.gridValid()) {
        throw new MixmoException(
            ErrorCode.MIXMO_GRID_INVALID,
            mixmoGridInvalidMessage(validationResult.invalidWords()),
            HttpStatus.CONFLICT
        );
      }
    }

    MixmoService.MixmoOutcome outcome = switch (triggerReason) {
      case PLAYER -> mixmoService.triggerPlayer(aggregate, playerId);
      case MANUAL -> mixmoService.triggerManual(aggregate, playerId);
      case AUTOMATIC -> mixmoService.triggerAutomatic(aggregate);
    };
    RoomAggregate updated = roomStateLoader.loadByRoomId(aggregate.room().getId());
    Instant now = Instant.now(clock);

    if (!staleScheduleEligible(updated)) {
      boolean warningWasActive = isStaleWarningActive(updated.room());
      if (clearStaleSchedule(updated.room())) {
        roomRepository.save(updated.room());
      }
      if (warningWasActive) {
        appendStaleWarningClearedEvent(updated.room().getId(), "STALE_DISABLED", now);
      }
    } else if (triggerReason == MixmoTriggerReason.AUTOMATIC) {
      boolean warningWasActive = isStaleWarningActive(updated.room());
      armStaleSchedule(updated.room(), now);
      roomRepository.save(updated.room());
      if (warningWasActive) {
        appendStaleWarningClearedEvent(updated.room().getId(), "AUTOMATIC_MIXMO", now);
      }
    }

    Map<String, Object> mixmoResolvedPayload = mixmoResolvedPayload(
        triggerReason == MixmoTriggerReason.AUTOMATIC ? null : playerId,
        outcome,
        roomStateLoader.loadByRoomId(aggregate.room().getId()),
        triggerReason
    );
    gameEventStore.appendEvent(aggregate.room().getId(), EventType.MIXMO_RESOLVED, mixmoResolvedPayload, now);
    if (outcome.finalMixmo()) {
      gameEventStore.appendEvent(aggregate.room().getId(), EventType.GAME_FINISHED, mapOf(
          "winnerPlayerId", playerId,
          "finalMixmo", true,
          "bagRemaining", roomStateLoader.loadByRoomId(aggregate.room().getId()).bagRemaining(),
          "currentBanditTheme", roomStateLoader.loadByRoomId(aggregate.room().getId()).room().getCurrentBanditTheme()
      ), now);
    }
    updated = roomStateLoader.loadByRoomId(aggregate.room().getId());
    gameEventStore.saveSnapshot(updated, now);
    realtimeBroker.sendToRoom(updated.room().getId(), new SocketModels.EventEnvelope("mixmo.resolved", null, updated.room().getId(), updated.room().getVersion(), mixmoResolvedPayload));
    realtimeBroker.sendRoomSnapshots(updated);
    if (outcome.finalMixmo()) {
      realtimeBroker.sendToRoom(updated.room().getId(), new SocketModels.EventEnvelope("game.finished", null, updated.room().getId(), updated.room().getVersion(), mapOf(
          "winnerPlayerId", playerId,
          "finalMixmo", true,
          "bagRemaining", updated.bagRemaining(),
          "currentBanditTheme", updated.room().getCurrentBanditTheme()
      )));
    }
    return new VersionedResult<>(Map.of(
        "finalMixmo", outcome.finalMixmo(),
        "drawCountPerPlayer", outcome.drawCountPerPlayer(),
        "bagRemaining", updated.bagRemaining()
    ), updated.room().getVersion());
  }

  private void resolveAutomaticMixmo(RoomAggregate aggregate) {
    resolveMixmo(aggregate, null, MixmoTriggerReason.AUTOMATIC, false);
  }

  private boolean staleScheduleEligible(RoomAggregate aggregate) {
    return aggregate.room().getStatus() == RoomStatus.ACTIVE && aggregate.bagRemaining() > 0;
  }

  private void armStaleSchedule(GameRoomEntity room, Instant now) {
    room.setNextStaleWarningAt(now.plus(staleWarningDelay));
    room.setAutomaticMixmoAt(null);
  }

  private boolean clearStaleSchedule(GameRoomEntity room) {
    boolean changed = room.getNextStaleWarningAt() != null || room.getAutomaticMixmoAt() != null;
    room.setNextStaleWarningAt(null);
    room.setAutomaticMixmoAt(null);
    return changed;
  }

  private boolean isStaleWarningActive(GameRoomEntity room) {
    return room.getAutomaticMixmoAt() != null;
  }

  private boolean isStaleWarningDue(GameRoomEntity room, Instant now) {
    return room.getNextStaleWarningAt() != null && !room.getNextStaleWarningAt().isAfter(now);
  }

  private boolean isAutomaticMixmoDue(GameRoomEntity room, Instant now) {
    return room.getAutomaticMixmoAt() != null && !room.getAutomaticMixmoAt().isAfter(now);
  }

  private void activateStaleWarning(GameRoomEntity room, Instant now) {
    room.setNextStaleWarningAt(null);
    room.setAutomaticMixmoAt(now.plus(staleAutoDelay));
  }

  private void resetStaleScheduleAfterAuthenticatedMixmo(RoomAggregate aggregate, Instant now, String reason) {
    boolean warningWasActive = isStaleWarningActive(aggregate.room());
    if (staleScheduleEligible(aggregate)) {
      armStaleSchedule(aggregate.room(), now);
    } else {
      clearStaleSchedule(aggregate.room());
    }
    roomRepository.save(aggregate.room());
    if (!warningWasActive) {
      return;
    }
    appendStaleWarningClearedEvent(aggregate.room().getId(), reason, now);
    RoomAggregate updated = roomStateLoader.loadByRoomId(aggregate.room().getId());
    gameEventStore.saveSnapshot(updated, now);
    realtimeBroker.sendRoomSnapshots(updated);
  }

  private void appendStaleWarningStartedEvent(String roomId, Instant automaticMixmoAt, Instant now) {
    gameEventStore.appendEvent(roomId, EventType.STALE_GAME_WARNING_STARTED, mapOf(
        "automaticMixmoAt", automaticMixmoAt
    ), now);
  }

  private void appendStaleWarningClearedEvent(String roomId, String reason, Instant now) {
    gameEventStore.appendEvent(roomId, EventType.STALE_GAME_WARNING_CLEARED, mapOf(
        "reason", reason
    ), now);
  }

  private Map<String, Object> mixmoResolvedPayload(
      String triggeredByPlayerId,
      MixmoService.MixmoOutcome outcome,
      RoomAggregate updated,
      MixmoTriggerReason triggerReason
  ) {
    return mapOf(
        "triggeredByPlayerId", triggeredByPlayerId,
        "drawCountPerPlayer", outcome.drawCountPerPlayer(),
        "bagRemaining", updated.bagRemaining(),
        "finalMixmo", outcome.finalMixmo(),
        "triggerReason", triggerReason.name()
    );
  }

  private void requireGameplayAllowed(RoomAggregate aggregate, String playerId) {
    if (aggregate.room().getStatus() == RoomStatus.PAUSED) {
      throw new MixmoException(ErrorCode.ROOM_PAUSED, "Bandit theme selection is required before gameplay can continue.", HttpStatus.CONFLICT);
    }
    if (aggregate.room().getStatus() == RoomStatus.FINISHED) {
      throw new MixmoException(ErrorCode.ROOM_FINISHED, "The room is finished.", HttpStatus.CONFLICT);
    }
    if (aggregate.room().getStatus() != RoomStatus.ACTIVE) {
      throw new MixmoException(ErrorCode.ROOM_NOT_ACTIVE, "The room is not active.", HttpStatus.CONFLICT);
    }
  }

  private void assertVersion(RoomAggregate aggregate, Long expectedRoomVersion) {
    if (expectedRoomVersion != null && aggregate.room().getVersion() != expectedRoomVersion) {
      throw new MixmoException(ErrorCode.ROOM_VERSION_MISMATCH, "Room version mismatch.", HttpStatus.CONFLICT, true);
    }
  }

  private String maybeTransferWaitingHost(RoomAggregate aggregate, String disconnectedPlayerId) {
    if (aggregate.room().getStatus() != RoomStatus.WAITING || !Objects.equals(aggregate.room().getHostPlayerId(), disconnectedPlayerId)) {
      return aggregate.room().getHostPlayerId();
    }

    return aggregate.players().stream()
        .filter(candidate -> candidate.isConnected() && !candidate.getId().equals(disconnectedPlayerId))
        .min(Comparator.comparingInt(PlayerEntity::getSeatOrder))
        .map(PlayerEntity::getId)
        .map(nextHostPlayerId -> {
          aggregate.room().setHostPlayerId(nextHostPlayerId);
          return nextHostPlayerId;
        })
        .orElse(aggregate.room().getHostPlayerId());
  }

  private String nextWaitingHostId(String currentHostPlayerId, String leavingPlayerId, List<PlayerEntity> remainingPlayers) {
    if (!Objects.equals(currentHostPlayerId, leavingPlayerId)) {
      return currentHostPlayerId;
    }
    return remainingPlayers.stream()
        .min(Comparator.comparingInt(PlayerEntity::getSeatOrder))
        .map(PlayerEntity::getId)
        .orElseThrow();
  }

  private int nextAvailableSeatOrder(List<PlayerEntity> players) {
    Set<Integer> usedSeatOrders = new HashSet<>();
    for (PlayerEntity player : players) {
      usedSeatOrders.add(player.getSeatOrder());
    }
    int nextSeatOrder = 1;
    while (usedSeatOrders.contains(nextSeatOrder)) {
      nextSeatOrder += 1;
    }
    return nextSeatOrder;
  }

  private PlayerEntity newPlayer(String roomId, String playerName, DeviceType deviceType, int seatOrder, String sessionToken, Instant now) {
    PlayerEntity player = new PlayerEntity();
    player.setId(UUID.randomUUID().toString());
    player.setRoomId(roomId);
    player.setName(playerName == null || playerName.isBlank() ? "Player " + seatOrder : playerName.trim());
    player.setSeatOrder(seatOrder);
    player.setConnected(true);
    player.setCreatedAt(now);
    player.setSessionToken(sessionToken == null ? UUID.randomUUID().toString() : sessionToken);
    player.setDeviceType(deviceType == null ? DeviceType.MOBILE : deviceType);
    return player;
  }

  private String normalizeChatMessage(String text) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isEmpty()) {
      throw new MixmoException(ErrorCode.INVALID_CHAT_MESSAGE, "Chat messages cannot be empty.", HttpStatus.BAD_REQUEST);
    }
    if (normalized.length() > 280) {
      throw new MixmoException(ErrorCode.INVALID_CHAT_MESSAGE, "Chat messages must be 280 characters or fewer.", HttpStatus.BAD_REQUEST);
    }
    return normalized;
  }

  private void pruneChatHistory(String roomId) {
    List<RoomChatMessageEntity> messages = roomChatMessageRepository.findByRoomIdOrderBySequenceNumberDesc(roomId);
    if (messages.size() <= 50) {
      return;
    }
    roomChatMessageRepository.deleteAll(messages.subList(50, messages.size()));
  }

  private void seedDemoGrid(RoomAggregate aggregate, String playerId) {
    List<DemoCellPlacement> placements = List.of(
        new DemoCellPlacement(0, 0, "A"),
        new DemoCellPlacement(1, 0, "I"),
        new DemoCellPlacement(2, 0, "M"),
        new DemoCellPlacement(3, 0, "E"),
        new DemoCellPlacement(4, 0, "R"),
        new DemoCellPlacement(2, 1, "I"),
        new DemoCellPlacement(4, 1, "I"),
        new DemoCellPlacement(0, 2, "A"),
        new DemoCellPlacement(1, 2, "N"),
        new DemoCellPlacement(2, 2, "C"),
        new DemoCellPlacement(3, 2, "R"),
        new DemoCellPlacement(4, 2, "E"),
        new DemoCellPlacement(2, 3, "R"),
        new DemoCellPlacement(4, 3, "U"),
        new DemoCellPlacement(0, 4, "A"),
        new DemoCellPlacement(1, 4, "M"),
        new DemoCellPlacement(2, 4, "O"),
        new DemoCellPlacement(3, 4, "U"),
        new DemoCellPlacement(4, 4, "R")
    );
    List<TileEntity> availableTiles = new ArrayList<>(aggregate.tiles().stream()
        .filter(tile -> tile.getLocation() == com.mixmo.common.TileLocation.BAG)
        .filter(tile -> tile.getKind() == com.mixmo.common.TileKind.NORMAL)
        .toList());
    for (DemoCellPlacement placement : placements) {
      TileEntity tile = takeDemoTile(availableTiles, placement.letter());
      tile.setLocation(com.mixmo.common.TileLocation.BOARD);
      tile.setOwnerPlayerId(playerId);
      tile.setAssignedLetter(placement.letter());
      tile.setBagPosition(null);
      tileRepository.save(tile);

      BoardCellEntity boardCell = new BoardCellEntity();
      boardCell.setId(UUID.randomUUID().toString());
      boardCell.setPlayerId(playerId);
      boardCell.setTileId(tile.getId());
      boardCell.setResolvedLetter(placement.letter());
      boardCell.setX(placement.x());
      boardCell.setY(placement.y());
      boardCellRepository.save(boardCell);
    }
  }

  private TileEntity takeDemoTile(List<TileEntity> availableTiles, String letter) {
    for (int index = 0; index < availableTiles.size(); index++) {
      TileEntity candidate = availableTiles.get(index);
      if (letter.equals(candidate.getFaceValue())) {
        availableTiles.remove(index);
        return candidate;
      }
    }
    throw new MixmoException(ErrorCode.INVALID_TILE_USAGE, "Demo room seed could not find a tile for letter " + letter + ".", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private record CreatedRoomContext(RoomAggregate aggregate, PlayerEntity player) {
  }

  private record DemoCellPlacement(int x, int y, String letter) {
  }

  private RestModels.GridValidationResponse toGridValidationResponse(GridValidationModels.GridValidationResult validationResult) {
    return new RestModels.GridValidationResponse(
        validationResult.gridValid(),
        validationResult.extractedWords().stream()
            .map(word -> new RestModels.ExtractedWordDto(
                word.text(),
                word.direction(),
                word.start(),
                word.occupiedCoordinates(),
                word.valid()
            ))
            .toList(),
        validationResult.invalidWords(),
        validationResult.letterStatuses()
    );
  }

  private String mixmoGridInvalidMessage(List<String> invalidWords) {
    if (invalidWords == null || invalidWords.isEmpty()) {
      return "Current grid contains invalid words.";
    }
    return "Current grid contains invalid words: " + String.join(", ", invalidWords) + ".";
  }

  private Map<String, Object> mapOf(Object... entries) {
    Map<String, Object> result = new HashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }
}
