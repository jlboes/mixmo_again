package com.mixmo.room;

import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.persistence.BanditThemeSelectionEntity;
import com.mixmo.persistence.BanditThemeSelectionRepository;
import com.mixmo.persistence.BoardCellRepository;
import com.mixmo.persistence.GameEventRepository;
import com.mixmo.persistence.GameRoomEntity;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.PlayerRepository;
import com.mixmo.persistence.RackEntryRepository;
import com.mixmo.persistence.TileRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RoomStateLoader {

  private final GameRoomRepository roomRepository;
  private final PlayerRepository playerRepository;
  private final TileRepository tileRepository;
  private final RackEntryRepository rackEntryRepository;
  private final BoardCellRepository boardCellRepository;
  private final BanditThemeSelectionRepository banditThemeSelectionRepository;
  private final GameEventRepository gameEventRepository;

  public RoomStateLoader(
      GameRoomRepository roomRepository,
      PlayerRepository playerRepository,
      TileRepository tileRepository,
      RackEntryRepository rackEntryRepository,
      BoardCellRepository boardCellRepository,
      BanditThemeSelectionRepository banditThemeSelectionRepository,
      GameEventRepository gameEventRepository
  ) {
    this.roomRepository = roomRepository;
    this.playerRepository = playerRepository;
    this.tileRepository = tileRepository;
    this.rackEntryRepository = rackEntryRepository;
    this.boardCellRepository = boardCellRepository;
    this.banditThemeSelectionRepository = banditThemeSelectionRepository;
    this.gameEventRepository = gameEventRepository;
  }

  public RoomAggregate load(String roomIdOrCode) {
    GameRoomEntity room = roomRepository.findById(roomIdOrCode)
        .or(() -> roomRepository.findByCode(roomIdOrCode))
        .orElseThrow(() -> new MixmoException(ErrorCode.ROOM_NOT_FOUND, "Room not found.", HttpStatus.NOT_FOUND));
    return loadByRoomId(room.getId());
  }

  public RoomAggregate loadByRoomId(String roomId) {
    GameRoomEntity room = roomRepository.findById(roomId)
        .orElseThrow(() -> new MixmoException(ErrorCode.ROOM_NOT_FOUND, "Room not found.", HttpStatus.NOT_FOUND));
    List<PlayerEntity> players = playerRepository.findByRoomIdOrderBySeatOrderAsc(roomId);
    List<BanditThemeSelectionEntity> selections = banditThemeSelectionRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    long lastSequence = gameEventRepository.findMaxSequenceNumber(roomId).orElse(0L);
    return new RoomAggregate(
        room,
        players,
        tileRepository.findByRoomId(roomId),
        players.stream().flatMap(player -> rackEntryRepository.findByPlayerIdOrderByPositionAsc(player.getId()).stream()).toList(),
        players.stream().flatMap(player -> boardCellRepository.findByPlayerId(player.getId()).stream()).toList(),
        selections.stream().filter(selection -> "PENDING".equals(selection.getStatus())).findFirst(),
        lastSequence
    );
  }
}

