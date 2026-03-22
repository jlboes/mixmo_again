package com.mixmo.mixmo;

import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.common.RoomStatus;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.rack.RackService;
import com.mixmo.room.RoomAggregate;
import com.mixmo.tile.TileBagService;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MixmoService {

  private final RackService rackService;
  private final TileBagService tileBagService;
  private final GameRoomRepository roomRepository;
  private final Clock clock;

  public MixmoService(
      RackService rackService,
      TileBagService tileBagService,
      GameRoomRepository roomRepository,
      Clock clock
  ) {
    this.rackService = rackService;
    this.tileBagService = tileBagService;
    this.roomRepository = roomRepository;
    this.clock = clock;
  }

  public MixmoOutcome triggerPlayer(RoomAggregate aggregate, String playerId) {
    return trigger(aggregate, playerId, true);
  }

  public MixmoOutcome triggerManual(RoomAggregate aggregate, String playerId) {
    return trigger(aggregate, playerId, false);
  }

  public MixmoOutcome triggerAutomatic(RoomAggregate aggregate) {
    if (aggregate.room().getStatus() != RoomStatus.ACTIVE) {
      throw new MixmoException(ErrorCode.ROOM_NOT_ACTIVE, "Room is not active.", HttpStatus.CONFLICT);
    }
    if (aggregate.bagRemaining() == 0) {
      throw new MixmoException(ErrorCode.MIXMO_NOT_ALLOWED, "Automatic Mixmo is unavailable when no bag letters remain.", HttpStatus.CONFLICT);
    }
    return sharedDraw(aggregate);
  }

  private MixmoOutcome trigger(RoomAggregate aggregate, String playerId, boolean requireEmptyRack) {
    if (aggregate.room().getStatus() != RoomStatus.ACTIVE) {
      throw new MixmoException(ErrorCode.ROOM_NOT_ACTIVE, "Room is not active.", HttpStatus.CONFLICT);
    }
    if (requireEmptyRack && !rackService.isRackEmpty(aggregate, playerId)) {
      throw new MixmoException(ErrorCode.MIXMO_NOT_ALLOWED, "Mixmo is available only when the rack is empty.", HttpStatus.BAD_REQUEST);
    }

    if (aggregate.bagRemaining() == 0 && rackService.isRackEmpty(aggregate, playerId)) {
      aggregate.room().setStatus(RoomStatus.FINISHED);
      aggregate.room().setWinnerPlayerId(playerId);
      aggregate.room().setFinishedAt(java.time.Instant.now(clock));
      aggregate.room().setVersion(aggregate.room().getVersion() + 1);
      roomRepository.save(aggregate.room());
      return new MixmoOutcome(true, 0, 0);
    }

    return sharedDraw(aggregate);
  }

  private MixmoOutcome sharedDraw(RoomAggregate aggregate) {
    int totalDraws = 0;
    for (int round = 0; round < 2; round++) {
      for (var player : aggregate.playersInSeatOrder()) {
        totalDraws += tileBagService.drawTiles(aggregate, player.getId(), 1).size();
      }
    }
    aggregate.room().setVersion(aggregate.room().getVersion() + 1);
    roomRepository.save(aggregate.room());
    return new MixmoOutcome(false, 2, totalDraws);
  }

  public record MixmoOutcome(
      boolean finalMixmo,
      int drawCountPerPlayer,
      int totalDraws
  ) {
  }
}
