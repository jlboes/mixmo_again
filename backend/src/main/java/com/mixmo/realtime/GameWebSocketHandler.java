package com.mixmo.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.placement.PlacementModels.PlacementRequest;
import com.mixmo.realtime.SocketModels.BoardTilesReturnPayload;
import com.mixmo.realtime.SocketModels.BanditThemePayload;
import com.mixmo.realtime.SocketModels.ChatMessagePayload;
import com.mixmo.realtime.SocketModels.CommandEnvelope;
import com.mixmo.realtime.SocketModels.EventEnvelope;
import com.mixmo.realtime.SocketModels.GameSyncPayload;
import com.mixmo.realtime.SocketModels.PlacementConfirmPayload;
import com.mixmo.realtime.SocketModels.PlacementPreviewPayload;
import com.mixmo.realtime.SocketModels.PlacementSuggestionsPayload;
import com.mixmo.room.RoomAggregate;
import com.mixmo.room.RoomService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;
  private final RoomService roomService;
  private final RealtimeBroker realtimeBroker;

  public GameWebSocketHandler(
      ObjectMapper objectMapper,
      RoomService roomService,
      RealtimeBroker realtimeBroker
  ) {
    this.objectMapper = objectMapper;
    this.roomService = roomService;
    this.realtimeBroker = realtimeBroker;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Map<String, String> query = parseQuery(session.getUri());
    String roomId = query.get("roomId");
    String playerId = query.get("playerId");
    String sessionToken = query.get("sessionToken");
    if (roomId == null || playerId == null || sessionToken == null) {
      throw new MixmoException(ErrorCode.UNAUTHORIZED_SESSION, "Missing websocket authentication parameters.", HttpStatus.UNAUTHORIZED);
    }
    RoomAggregate aggregate = roomService.authenticateSocket(roomId, playerId, sessionToken);
    session.getAttributes().put("roomId", aggregate.room().getId());
    session.getAttributes().put("playerId", playerId);
    realtimeBroker.register(session, aggregate.room().getId(), playerId);
    roomService.onSocketConnected(aggregate.room().getId(), playerId, sessionToken);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    try {
      CommandEnvelope command = objectMapper.readValue(message.getPayload(), CommandEnvelope.class);
      String roomId = (String) session.getAttributes().get("roomId");
      String playerId = (String) session.getAttributes().get("playerId");
      if (!roomId.equals(command.roomId()) || !playerId.equals(command.playerId())) {
        throw new MixmoException(ErrorCode.UNAUTHORIZED_SESSION, "Socket player mismatch.", HttpStatus.UNAUTHORIZED);
      }
      switch (command.type()) {
        case "game.sync.request" -> handleSync(session, command);
        case "placement.preview.request" -> handlePreview(session, command);
        case "placement.suggestions.request" -> handleSuggestions(session, command);
        case "placement.confirm.request" -> handleConfirm(session, command);
        case "board.tiles.return.request" -> handleBoardTilesReturn(session, command);
        case "chat.message.send" -> handleChatMessage(session, command);
        case "mixmo.trigger" -> handleMixmo(session, command);
        case "bandit.theme.select" -> handleBandit(session, command);
        default -> throw new MixmoException(ErrorCode.INVALID_COMMAND, "Unsupported websocket command.", HttpStatus.BAD_REQUEST);
      }
    } catch (MixmoException exception) {
      sendError(session, null, (String) session.getAttributes().get("roomId"), (Long) null, exception);
    } catch (Exception exception) {
      sendError(session, null, (String) session.getAttributes().get("roomId"), null, new MixmoException(ErrorCode.INVALID_COMMAND, exception.getMessage(), HttpStatus.BAD_REQUEST));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = (String) session.getAttributes().get("roomId");
    String playerId = (String) session.getAttributes().get("playerId");
    realtimeBroker.unregister(session);
    if (roomId != null && playerId != null) {
      roomService.onSocketDisconnected(roomId, playerId);
    }
  }

  private void handleSync(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    objectMapper.treeToValue(command.payload(), GameSyncPayload.class);
    var state = roomService.getState(command.roomId(), command.playerId(), ((Map<String, String>) parseQuery(session.getUri())).get("sessionToken"));
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), state.roomVersion(), Map.of("accepted", true)));
    realtimeBroker.sendToSession(session, new EventEnvelope("game.state.updated", command.requestId(), command.roomId(), state.roomVersion(), state.data()));
  }

  private void handlePreview(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    PlacementPreviewPayload payload = objectMapper.treeToValue(command.payload(), PlacementPreviewPayload.class);
    PlacementRequest request = new PlacementRequest(payload.candidateWord(), payload.orientation(), payload.start(), payload.tiles(), null);
    var preview = roomService.preview(command.roomId(), command.playerId(), request, command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), command.expectedRoomVersion(), Map.of("accepted", true)));
    realtimeBroker.sendToSession(session, new EventEnvelope("placement.preview.result", command.requestId(), command.roomId(), command.expectedRoomVersion(), mapOf(
        "valid", preview.valid(),
        "invalidReason", preview.invalidReason() == null ? null : preview.invalidReason().code(),
        "previewCells", preview.previewCells(),
        "crossesOrigin", preview.crossesOrigin(),
        "connectedToCluster", preview.connectedToCluster(),
        "usesBandit", preview.usesBandit()
    )));
  }

  private void handleSuggestions(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    PlacementSuggestionsPayload payload = objectMapper.treeToValue(command.payload(), PlacementSuggestionsPayload.class);
    var suggestions = roomService.suggestions(command.roomId(), command.playerId(), payload.candidateWord(), payload.deviceType(), command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), command.expectedRoomVersion(), Map.of("accepted", true)));
    realtimeBroker.sendToSession(session, new EventEnvelope("placement.suggestions.result", command.requestId(), command.roomId(), command.expectedRoomVersion(), Map.of("suggestions", suggestions)));
  }

  private void handleConfirm(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    PlacementConfirmPayload payload = objectMapper.treeToValue(command.payload(), PlacementConfirmPayload.class);
    PlacementRequest request = new PlacementRequest(payload.candidateWord(), payload.orientation(), payload.start(), payload.tiles(), payload.banditTheme());
    var result = roomService.confirmPlacement(command.roomId(), command.playerId(), request, command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), result.roomVersion(), Map.of("accepted", true)));
  }

  private void handleBoardTilesReturn(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    BoardTilesReturnPayload payload = objectMapper.treeToValue(command.payload(), BoardTilesReturnPayload.class);
    var result = roomService.returnBoardTiles(command.roomId(), command.playerId(), payload.tileIds(), command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), result.roomVersion(), Map.of("accepted", true)));
  }

  private void handleChatMessage(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    ChatMessagePayload payload = objectMapper.treeToValue(command.payload(), ChatMessagePayload.class);
    var result = roomService.sendChatMessage(command.roomId(), command.playerId(), payload.text());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), result.roomVersion(), Map.of("accepted", true)));
  }

  private void handleMixmo(WebSocketSession session, CommandEnvelope command) {
    var result = roomService.triggerMixmo(command.roomId(), command.playerId(), command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), result.roomVersion(), Map.of("accepted", true)));
  }

  private void handleBandit(WebSocketSession session, CommandEnvelope command) throws JsonProcessingException {
    BanditThemePayload payload = objectMapper.treeToValue(command.payload(), BanditThemePayload.class);
    var result = roomService.selectBanditTheme(command.roomId(), command.playerId(), payload.theme(), command.expectedRoomVersion());
    realtimeBroker.sendToSession(session, new EventEnvelope("command.ack", command.requestId(), command.roomId(), result.roomVersion(), Map.of("accepted", true)));
  }

  private void sendError(WebSocketSession session, String requestId, String roomId, Long roomVersion, MixmoException exception) {
    realtimeBroker.sendToSession(session, new EventEnvelope("error", requestId, roomId, roomVersion, mapOf(
        "code", exception.getCode().name(),
        "message", exception.getMessage(),
        "retryable", exception.isRetryable()
    )));
  }

  private Map<String, Object> mapOf(Object... entries) {
    Map<String, Object> result = new HashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }

  private Map<String, String> parseQuery(URI uri) {
    Map<String, String> query = new HashMap<>();
    if (uri == null || uri.getQuery() == null) {
      return query;
    }
    for (String pair : uri.getQuery().split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2) {
        query.put(parts[0], parts[1]);
      }
    }
    return query;
  }
}
