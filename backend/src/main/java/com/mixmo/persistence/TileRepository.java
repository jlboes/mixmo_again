package com.mixmo.persistence;

import com.mixmo.common.TileLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TileRepository extends JpaRepository<TileEntity, String> {
  List<TileEntity> findByRoomId(String roomId);

  List<TileEntity> findByRoomIdAndLocationOrderByBagPositionAsc(String roomId, TileLocation location);
}

