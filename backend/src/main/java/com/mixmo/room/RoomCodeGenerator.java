package com.mixmo.room;

import com.mixmo.persistence.GameRoomRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {
  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private final SecureRandom random = new SecureRandom();
  private final GameRoomRepository roomRepository;

  public RoomCodeGenerator(GameRoomRepository roomRepository) {
    this.roomRepository = roomRepository;
  }

  public String nextCode() {
    while (true) {
      StringBuilder builder = new StringBuilder(6);
      for (int i = 0; i < 6; i++) {
        builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      }
      String code = builder.toString();
      if (roomRepository.findByCode(code).isEmpty()) {
        return code;
      }
    }
  }
}
