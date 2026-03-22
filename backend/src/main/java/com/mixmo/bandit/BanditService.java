package com.mixmo.bandit;

import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import com.mixmo.common.RoomStatus;
import com.mixmo.common.ThemeOption;
import com.mixmo.persistence.BanditThemeSelectionEntity;
import com.mixmo.persistence.BanditThemeSelectionRepository;
import com.mixmo.persistence.GameRoomRepository;
import com.mixmo.placement.PlacementModels.PlacementRequest;
import com.mixmo.placement.PlacementService;
import com.mixmo.room.RoomAggregate;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BanditService {

  private final BanditThemeSelectionRepository selectionRepository;
  private final GameRoomRepository roomRepository;
  private final PlacementService placementService;
  private final Clock clock;

  public BanditService(
      BanditThemeSelectionRepository selectionRepository,
      GameRoomRepository roomRepository,
      PlacementService placementService,
      Clock clock
  ) {
    this.selectionRepository = selectionRepository;
    this.roomRepository = roomRepository;
    this.placementService = placementService;
    this.clock = clock;
  }

  public BanditThemeSelectionEntity selectTheme(RoomAggregate aggregate, String playerId, String theme) {
    ThemeOption resolvedTheme = ThemeOption.fromLabel(theme)
        .orElseThrow(() -> new MixmoException(ErrorCode.INVALID_THEME, "Invalid theme selection.", HttpStatus.BAD_REQUEST));
    BanditThemeSelectionEntity pending = aggregate.pendingBanditSelection()
        .orElseThrow(() -> new MixmoException(ErrorCode.ROOM_PAUSED, "No bandit theme selection is pending.", HttpStatus.CONFLICT));
    if (!pending.getTriggeringPlayerId().equals(playerId)) {
      throw new MixmoException(ErrorCode.NOT_BANDIT_TRIGGERING_PLAYER, "Only the triggering player can select the bandit theme.", HttpStatus.FORBIDDEN);
    }
    PlacementRequest request = placementService.readPendingPlacement(pending);
    var validation = placementService.validate(aggregate, playerId, request);
    if (!validation.valid()) {
      throw new MixmoException(ErrorCode.INVALID_TILE_USAGE, "Pending placement is no longer valid.", HttpStatus.CONFLICT);
    }
    placementService.commitPlacement(aggregate, playerId, validation.resolvedTiles());
    pending.setSelectedTheme(resolvedTheme.label());
    pending.setStatus("RESOLVED");
    pending.setResolvedAt(Instant.now(clock));
    selectionRepository.save(pending);
    aggregate.room().setCurrentBanditTheme(resolvedTheme.label());
    aggregate.room().setStatus(RoomStatus.ACTIVE);
    aggregate.room().setPauseReason(null);
    roomRepository.save(aggregate.room());
    return pending;
  }
}

