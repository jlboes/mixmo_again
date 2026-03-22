package com.mixmo.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameEventRepository extends JpaRepository<GameEventEntity, Long> {

  @Query("select max(e.sequenceNumber) from GameEventEntity e where e.roomId = :roomId")
  Optional<Long> findMaxSequenceNumber(String roomId);
}

