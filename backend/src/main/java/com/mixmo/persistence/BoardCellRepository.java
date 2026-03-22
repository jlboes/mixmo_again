package com.mixmo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardCellRepository extends JpaRepository<BoardCellEntity, String> {
  List<BoardCellEntity> findByPlayerId(String playerId);

  Optional<BoardCellEntity> findByPlayerIdAndXAndY(String playerId, int x, int y);
}

