package com.mixmo.persistence;

import com.mixmo.common.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRoomRepository extends JpaRepository<GameRoomEntity, String> {
  Optional<GameRoomEntity> findByCode(String code);

  @Query("""
      select room.id
      from GameRoomEntity room
      where room.status = :status
        and (
          (room.nextStaleWarningAt is not null and room.nextStaleWarningAt <= :now)
          or (room.automaticMixmoAt is not null and room.automaticMixmoAt <= :now)
        )
      """)
  List<String> findDueStaleRoomIds(RoomStatus status, Instant now);
}
