package com.mixmo.room;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleMixmoWatchdog {

  private final RoomService roomService;

  public StaleMixmoWatchdog(RoomService roomService) {
    this.roomService = roomService;
  }

  @Scheduled(fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle).detectAndParse('${mixmo.stale.poll-interval:1s}').toMillis()}")
  public void pollDueRooms() {
    for (String roomId : roomService.dueStaleRoomIds()) {
      roomService.processDueStaleRoom(roomId);
    }
  }
}
