package com.mixmo.persistence;

import com.mixmo.common.DeviceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "game_player",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_game_player_room_session", columnNames = {"room_id", "session_token"})
    }
)
public class PlayerEntity {

  @Id
  private String id;

  @Column(nullable = false)
  private String roomId;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false)
  private int seatOrder;

  @Column(nullable = false)
  private boolean connected;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false, length = 128)
  private String sessionToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DeviceType deviceType;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getSeatOrder() {
    return seatOrder;
  }

  public void setSeatOrder(int seatOrder) {
    this.seatOrder = seatOrder;
  }

  public boolean isConnected() {
    return connected;
  }

  public void setConnected(boolean connected) {
    this.connected = connected;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public DeviceType getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(DeviceType deviceType) {
    this.deviceType = deviceType;
  }
}
