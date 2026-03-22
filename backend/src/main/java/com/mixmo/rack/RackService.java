package com.mixmo.rack;

import com.mixmo.persistence.RackEntryEntity;
import com.mixmo.persistence.RackEntryRepository;
import com.mixmo.room.RoomAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RackService {

  private final RackEntryRepository rackEntryRepository;

  public RackService(RackEntryRepository rackEntryRepository) {
    this.rackEntryRepository = rackEntryRepository;
  }

  public boolean isRackEmpty(RoomAggregate aggregate, String playerId) {
    return aggregate.rackEntriesFor(playerId).isEmpty();
  }

  public void removeTilesFromRack(String playerId, List<String> tileIds) {
    rackEntryRepository.deleteByTileIdIn(tileIds);
    List<RackEntryEntity> remaining = rackEntryRepository.findByPlayerIdOrderByPositionAsc(playerId);
    for (int index = 0; index < remaining.size(); index++) {
      RackEntryEntity entry = remaining.get(index);
      entry.setPosition(index);
    }
    rackEntryRepository.saveAll(remaining);
  }

  public void appendTilesToRack(String playerId, List<String> tileIds) {
    List<RackEntryEntity> currentEntries = rackEntryRepository.findByPlayerIdOrderByPositionAsc(playerId);
    List<RackEntryEntity> newEntries = new ArrayList<>();
    int nextPosition = currentEntries.size();
    for (String tileId : tileIds) {
      RackEntryEntity entry = new RackEntryEntity();
      entry.setId(UUID.randomUUID().toString());
      entry.setPlayerId(playerId);
      entry.setTileId(tileId);
      entry.setPosition(nextPosition++);
      newEntries.add(entry);
    }
    rackEntryRepository.saveAll(newEntries);
  }
}
