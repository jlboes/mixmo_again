package com.mixmo.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.common.EventType;
import com.mixmo.persistence.GameEventEntity;
import com.mixmo.persistence.GameEventRepository;
import com.mixmo.persistence.GameSnapshotEntity;
import com.mixmo.persistence.GameSnapshotRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class GameEventStore {

  private final GameEventRepository eventRepository;
  private final GameSnapshotRepository snapshotRepository;
  private final ObjectMapper objectMapper;

  public GameEventStore(
      GameEventRepository eventRepository,
      GameSnapshotRepository snapshotRepository,
      ObjectMapper objectMapper
  ) {
    this.eventRepository = eventRepository;
    this.snapshotRepository = snapshotRepository;
    this.objectMapper = objectMapper;
  }

  public long appendEvent(String roomId, EventType eventType, Object payload, Instant now) {
    long nextSequence = eventRepository.findMaxSequenceNumber(roomId).orElse(0L) + 1L;
    GameEventEntity event = new GameEventEntity();
    event.setRoomId(roomId);
    event.setEventType(eventType);
    event.setPayloadJson(writeJson(payload));
    event.setCreatedAt(now);
    event.setSequenceNumber(nextSequence);
    eventRepository.save(event);
    return nextSequence;
  }

  public void saveSnapshot(RoomAggregate aggregate, Instant now) {
    GameSnapshotEntity snapshot = snapshotRepository.findById(aggregate.room().getId()).orElseGet(GameSnapshotEntity::new);
    snapshot.setRoomId(aggregate.room().getId());
    snapshot.setVersion(aggregate.room().getVersion());
    snapshot.setUpdatedAt(now);
    snapshot.setPayloadJson(writeJson(new PersistedSnapshot(
        aggregate.room().getId(),
        aggregate.room().getCode(),
        aggregate.room().getStatus(),
        aggregate.room().getVersion(),
        aggregate.players(),
        aggregate.tiles(),
        aggregate.rackEntries(),
        aggregate.boardCells(),
        aggregate.pendingBanditSelection().orElse(null),
        aggregate.lastEventSequence()
    )));
    snapshotRepository.save(snapshot);
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize JSON payload.", exception);
    }
  }

  private record PersistedSnapshot(
      String roomId,
      String roomCode,
      Object status,
      long roomVersion,
      Object players,
      Object tiles,
      Object rackEntries,
      Object boardCells,
      Object pendingBanditSelection,
      long lastEventSequence
  ) {
  }
}

