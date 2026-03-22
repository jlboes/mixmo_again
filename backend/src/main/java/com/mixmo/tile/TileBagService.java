package com.mixmo.tile;

import com.mixmo.common.TileKind;
import com.mixmo.common.TileLocation;
import com.mixmo.persistence.RackEntryEntity;
import com.mixmo.persistence.RackEntryRepository;
import com.mixmo.persistence.TileEntity;
import com.mixmo.persistence.TileRepository;
import com.mixmo.room.RoomAggregate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TileBagService {

  private static final Map<String, Integer> DISTRIBUTION = new LinkedHashMap<>();

  static {
    DISTRIBUTION.put("A", 10);
    DISTRIBUTION.put("B", 2);
    DISTRIBUTION.put("C", 3);
    DISTRIBUTION.put("D", 4);
    DISTRIBUTION.put("E", 17);
    DISTRIBUTION.put("F", 2);
    DISTRIBUTION.put("G", 3);
    DISTRIBUTION.put("H", 3);
    DISTRIBUTION.put("I", 9);
    DISTRIBUTION.put("J", 2);
    DISTRIBUTION.put("K", 1);
    DISTRIBUTION.put("L", 6);
    DISTRIBUTION.put("M", 3);
    DISTRIBUTION.put("N", 7);
    DISTRIBUTION.put("O", 7);
    DISTRIBUTION.put("P", 3);
    DISTRIBUTION.put("Q", 2);
    DISTRIBUTION.put("R", 6);
    DISTRIBUTION.put("S", 7);
    DISTRIBUTION.put("T", 7);
    DISTRIBUTION.put("U", 6);
    DISTRIBUTION.put("V", 3);
    DISTRIBUTION.put("W", 1);
    DISTRIBUTION.put("X", 1);
    DISTRIBUTION.put("Y", 1);
    DISTRIBUTION.put("Z", 1);
  }

  private final TileRepository tileRepository;
  private final RackEntryRepository rackEntryRepository;

  public TileBagService(TileRepository tileRepository, RackEntryRepository rackEntryRepository) {
    this.tileRepository = tileRepository;
    this.rackEntryRepository = rackEntryRepository;
  }

  public void initializeBag(RoomAggregate aggregate) {
    if (!aggregate.tiles().isEmpty()) {
      return;
    }
    List<TileEntity> bag = new ArrayList<>(120);
    DISTRIBUTION.forEach((letter, quantity) -> {
      for (int i = 0; i < quantity; i++) {
        bag.add(newTile(aggregate.room().getId(), TileKind.NORMAL, letter));
      }
    });
    bag.add(newTile(aggregate.room().getId(), TileKind.JOKER, null));
    bag.add(newTile(aggregate.room().getId(), TileKind.JOKER, null));
    bag.add(newTile(aggregate.room().getId(), TileKind.BANDIT, null));
    Random random = new Random(aggregate.room().getId().hashCode());
    for (int index = bag.size() - 1; index > 0; index--) {
      int swapIndex = random.nextInt(index + 1);
      TileEntity temp = bag.get(index);
      bag.set(index, bag.get(swapIndex));
      bag.set(swapIndex, temp);
    }
    for (int index = 0; index < bag.size(); index++) {
      bag.get(index).setBagPosition(index);
    }
    tileRepository.saveAll(bag);
  }

  public List<TileEntity> drawTiles(RoomAggregate aggregate, String playerId, int count) {
    List<TileEntity> bagTiles = tileRepository.findByRoomIdAndLocationOrderByBagPositionAsc(aggregate.room().getId(), TileLocation.BAG);
    List<RackEntryEntity> currentRack = rackEntryRepository.findByPlayerIdOrderByPositionAsc(playerId);
    List<TileEntity> drawn = new ArrayList<>();
    for (int index = 0; index < count && index < bagTiles.size(); index++) {
      TileEntity tile = bagTiles.get(index);
      tile.setLocation(TileLocation.RACK);
      tile.setOwnerPlayerId(playerId);
      tile.setBagPosition(null);
      tileRepository.save(tile);

      RackEntryEntity rackEntry = new RackEntryEntity();
      rackEntry.setId(UUID.randomUUID().toString());
      rackEntry.setPlayerId(playerId);
      rackEntry.setTileId(tile.getId());
      rackEntry.setPosition(currentRack.size() + index);
      rackEntryRepository.save(rackEntry);
      drawn.add(tile);
    }
    return drawn;
  }

  private TileEntity newTile(String roomId, TileKind kind, String faceValue) {
    TileEntity tile = new TileEntity();
    tile.setId(UUID.randomUUID().toString());
    tile.setRoomId(roomId);
    tile.setKind(kind);
    tile.setFaceValue(faceValue);
    tile.setAssignedLetter(null);
    tile.setOwnerPlayerId(null);
    tile.setLocation(TileLocation.BAG);
    return tile;
  }
}

