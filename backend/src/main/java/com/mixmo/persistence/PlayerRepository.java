package com.mixmo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<PlayerEntity, String> {
  List<PlayerEntity> findByRoomIdOrderBySeatOrderAsc(String roomId);

  Optional<PlayerEntity> findByRoomIdAndSessionToken(String roomId, String sessionToken);
}

