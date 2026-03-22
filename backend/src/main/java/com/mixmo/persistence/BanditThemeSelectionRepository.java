package com.mixmo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BanditThemeSelectionRepository extends JpaRepository<BanditThemeSelectionEntity, String> {
  Optional<BanditThemeSelectionEntity> findFirstByRoomIdAndStatusOrderByCreatedAtDesc(String roomId, String status);

  List<BanditThemeSelectionEntity> findByRoomIdOrderByCreatedAtDesc(String roomId);
}

