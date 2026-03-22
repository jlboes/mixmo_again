package com.mixmo.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RackEntryRepository extends JpaRepository<RackEntryEntity, String> {
  List<RackEntryEntity> findByPlayerIdOrderByPositionAsc(String playerId);

  void deleteByTileIdIn(List<String> tileIds);
}

