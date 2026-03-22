package com.mixmo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomChatMessageRepository extends JpaRepository<RoomChatMessageEntity, Long> {

  @Query("select max(message.sequenceNumber) from RoomChatMessageEntity message where message.roomId = :roomId")
  Optional<Long> findMaxSequenceNumber(String roomId);

  List<RoomChatMessageEntity> findTop50ByRoomIdOrderBySequenceNumberDesc(String roomId);

  List<RoomChatMessageEntity> findByRoomIdOrderBySequenceNumberDesc(String roomId);
}
