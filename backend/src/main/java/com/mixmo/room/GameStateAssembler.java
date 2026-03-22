package com.mixmo.room;

import com.mixmo.api.RestModels;
import com.mixmo.api.RestModels.ActionStateDto;
import com.mixmo.api.RestModels.BoardCellDto;
import com.mixmo.api.RestModels.GameSnapshotDto;
import com.mixmo.api.RestModels.PlayerSummaryDto;
import com.mixmo.api.RestModels.RackTileDto;
import com.mixmo.api.RestModels.RoomDto;
import com.mixmo.api.RestModels.SelfDto;
import com.mixmo.api.RestModels.ThemeStateDto;
import com.mixmo.common.RoomStatus;
import com.mixmo.persistence.PlayerEntity;
import com.mixmo.persistence.RoomChatMessageEntity;
import com.mixmo.persistence.TileEntity;
import com.mixmo.rack.RackService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GameStateAssembler {

  private final RackService rackService;

  public GameStateAssembler(RackService rackService) {
    this.rackService = rackService;
  }

  public RestModels.RoomBootstrapData bootstrap(RoomAggregate aggregate, PlayerEntity self) {
    return new RestModels.RoomBootstrapData(roomSummary(aggregate), new SelfDto(self.getId(), self.getName(), self.getSeatOrder(), self.getSessionToken()));
  }

  public RoomDto roomSummary(RoomAggregate aggregate) {
    return new RoomDto(
        aggregate.room().getId(),
        aggregate.room().getCode(),
        aggregate.room().getStatus(),
        aggregate.room().getHostPlayerId(),
        players(aggregate),
        aggregate.room().getCurrentBanditTheme(),
        aggregate.room().getPauseReason(),
        aggregate.room().getVersion()
    );
  }

  public RestModels.PlayerBoardViewDto playerBoardView(RoomAggregate aggregate, String targetPlayerId) {
    PlayerEntity target = aggregate.playersById().get(targetPlayerId);
    return new RestModels.PlayerBoardViewDto(
        playerSummary(aggregate, target),
        boardFor(aggregate, targetPlayerId),
        aggregate.room().getVersion(),
        Instant.now()
    );
  }

  public RestModels.ChatMessageDto chatMessage(RoomAggregate aggregate, RoomChatMessageEntity message) {
    PlayerEntity author = aggregate.playersById().get(message.getPlayerId());
    return new RestModels.ChatMessageDto(
        message.getSequenceNumber(),
        author.getId(),
        author.getName(),
        author.getSeatOrder(),
        message.getMessageText(),
        message.getCreatedAt()
    );
  }

  public RestModels.ChatHistoryDto chatHistory(RoomAggregate aggregate, List<RoomChatMessageEntity> messages) {
    List<RestModels.ChatMessageDto> orderedMessages = messages.stream()
        .sorted(Comparator.comparingLong(RoomChatMessageEntity::getSequenceNumber))
        .map(message -> chatMessage(aggregate, message))
        .toList();
    long latestSequence = orderedMessages.isEmpty() ? 0 : orderedMessages.getLast().sequenceNumber();
    return new RestModels.ChatHistoryDto(orderedMessages, latestSequence);
  }

  public GameSnapshotDto snapshot(RoomAggregate aggregate, String selfPlayerId) {
    PlayerEntity self = aggregate.playersById().get(selfPlayerId);
    String pauseTriggeringPlayerId = aggregate.pendingBanditSelection().map(com.mixmo.persistence.BanditThemeSelectionEntity::getTriggeringPlayerId).orElse(null);
    boolean staleWarningActive = aggregate.room().getAutomaticMixmoAt() != null;
    return new GameSnapshotDto(
        aggregate.room().getId(),
        aggregate.room().getCode(),
        aggregate.room().getStatus(),
        aggregate.room().getVersion(),
        aggregate.bagRemaining(),
        aggregate.room().getPauseReason(),
        aggregate.room().getCurrentBanditTheme(),
        aggregate.room().getWinnerPlayerId(),
        selfPlayerId,
        aggregate.room().getHostPlayerId(),
        players(aggregate),
        selfRack(aggregate, selfPlayerId),
        boardFor(aggregate, selfPlayerId),
        null,
        new ActionStateDto(
            aggregate.room().getStatus() == RoomStatus.ACTIVE && rackService.isRackEmpty(aggregate, selfPlayerId),
            aggregate.room().getStatus() == RoomStatus.ACTIVE,
            aggregate.room().getStatus() == RoomStatus.PAUSED && selfPlayerId.equals(pauseTriggeringPlayerId),
            rackService.isRackEmpty(aggregate, selfPlayerId) ? null : "Mixmo is available only when all rack letters are placed"
        ),
        new ThemeStateDto(RestModels.themeLabels(), aggregate.room().getCurrentBanditTheme(), aggregate.room().getStatus() == RoomStatus.PAUSED, pauseTriggeringPlayerId),
        new RestModels.StaleGameStateDto(
            staleWarningActive,
            staleWarningActive ? "Stale game detected" : null,
            aggregate.room().getAutomaticMixmoAt()
        ),
        aggregate.lastEventSequence(),
        Instant.now()
    );
  }

  private List<PlayerSummaryDto> players(RoomAggregate aggregate) {
    return aggregate.playersInSeatOrder().stream()
        .map(player -> playerSummary(aggregate, player))
        .toList();
  }

  private PlayerSummaryDto playerSummary(RoomAggregate aggregate, PlayerEntity player) {
    return new PlayerSummaryDto(
        player.getId(),
        player.getName(),
        player.getSeatOrder(),
        player.isConnected(),
        aggregate.rackEntriesFor(player.getId()).size(),
        aggregate.boardFor(player.getId()).size()
    );
  }

  private List<RackTileDto> selfRack(RoomAggregate aggregate, String playerId) {
    return aggregate.rackTilesFor(playerId).stream()
        .map(this::toRackTile)
        .toList();
  }

  private RackTileDto toRackTile(TileEntity tile) {
    String face = switch (tile.getKind()) {
      case NORMAL -> tile.getFaceValue();
      case JOKER -> "?";
      case BANDIT -> "!";
    };
    return new RackTileDto(tile.getId(), tile.getKind(), face, tile.getAssignedLetter());
  }

  private List<BoardCellDto> boardFor(RoomAggregate aggregate, String playerId) {
    return aggregate.boardFor(playerId).stream()
        .map(boardCell -> {
          TileEntity tile = aggregate.tilesById().get(boardCell.getTileId());
          return new BoardCellDto(boardCell.getX(), boardCell.getY(), boardCell.getResolvedLetter(), boardCell.getTileId(), tile.getKind());
        })
        .toList();
  }
}
