package com.mixmo.room;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RoomLockManager {
  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  public <T> T withRoomLock(String roomId, Supplier<T> supplier) {
    ReentrantLock lock = locks.computeIfAbsent(roomId, ignored -> new ReentrantLock());
    lock.lock();
    try {
      return supplier.get();
    } finally {
      lock.unlock();
    }
  }

  public void withRoomLock(String roomId, Runnable runnable) {
    withRoomLock(roomId, () -> {
      runnable.run();
      return null;
    });
  }
}

