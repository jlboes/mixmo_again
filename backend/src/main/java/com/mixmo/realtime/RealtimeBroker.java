package com.mixmo.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.api.RestModels.GameSnapshotDto;
import com.mixmo.room.GameStateAssembler;
import com.mixmo.room.RoomAggregate;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeBroker {

  private final ObjectMapper objectMapper;
  private final GameStateAssembler gameStateAssembler;
  private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

  public RealtimeBroker(ObjectMapper objectMapper, GameStateAssembler gameStateAssembler) {
    this.objectMapper = objectMapper;
    this.gameStateAssembler = gameStateAssembler;
  }

  public void register(WebSocketSession session, String roomId, String playerId) {
    sessions.put(session.getId(), new SessionInfo(session, roomId, playerId));
  }

  public void unregister(WebSocketSession session) {
    sessions.remove(session.getId());
  }

  public int activeSessionCount(String roomId, String playerId) {
    return (int) sessions.values().stream()
        .filter(info -> info.roomId().equals(roomId) && info.playerId().equals(playerId))
        .count();
  }

  public boolean hasActiveSession(String roomId, String playerId) {
    return activeSessionCount(roomId, playerId) > 0;
  }

  public void sendToSession(WebSocketSession session, SocketModels.EventEnvelope event) {
    send(session, event);
  }

  public void sendToRoom(String roomId, SocketModels.EventEnvelope event) {
    sessions.values().stream()
        .filter(info -> info.roomId().equals(roomId))
        .forEach(info -> send(info.session(), event));
  }

  public void sendRoomSnapshots(RoomAggregate aggregate) {
    Set<String> playerIds = aggregate.playersById().keySet();
    for (SessionInfo info : sessions.values()) {
      if (!info.roomId().equals(aggregate.room().getId()) || !playerIds.contains(info.playerId())) {
        continue;
      }
      GameSnapshotDto snapshot = gameStateAssembler.snapshot(aggregate, info.playerId());
      send(info.session(), new SocketModels.EventEnvelope("game.state.updated", null, aggregate.room().getId(), aggregate.room().getVersion(), snapshot));
    }
  }

  private void send(WebSocketSession session, SocketModels.EventEnvelope event) {
    if (!session.isOpen()) {
      return;
    }
    try {
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize websocket event.", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to write websocket event.", exception);
    }
  }

  private record SessionInfo(
      WebSocketSession session,
      String roomId,
      String playerId
  ) {
  }
}
