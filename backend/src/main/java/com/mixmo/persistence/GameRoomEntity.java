package com.mixmo.persistence;

import com.mixmo.common.PauseReason;
import com.mixmo.common.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_room")
public class GameRoomEntity {

  @Id
  private String id;

  @Column(nullable = false, unique = true, length = 16)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RoomStatus status;

  @Column(nullable = false, length = 64)
  private String hostPlayerId;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant startedAt;

  private Instant finishedAt;

  private Instant nextStaleWarningAt;

  private Instant automaticMixmoAt;

  @Column(length = 64)
  private String currentBanditTheme;

  @Enumerated(EnumType.STRING)
  @Column(length = 64)
  private PauseReason pauseReason;

  private String winnerPlayerId;

  @Column(nullable = false)
  private long version;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public RoomStatus getStatus() {
    return status;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
  }

  public String getHostPlayerId() {
    return hostPlayerId;
  }

  public void setHostPlayerId(String hostPlayerId) {
    this.hostPlayerId = hostPlayerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Instant getNextStaleWarningAt() {
    return nextStaleWarningAt;
  }

  public void setNextStaleWarningAt(Instant nextStaleWarningAt) {
    this.nextStaleWarningAt = nextStaleWarningAt;
  }

  public Instant getAutomaticMixmoAt() {
    return automaticMixmoAt;
  }

  public void setAutomaticMixmoAt(Instant automaticMixmoAt) {
    this.automaticMixmoAt = automaticMixmoAt;
  }

  public String getCurrentBanditTheme() {
    return currentBanditTheme;
  }

  public void setCurrentBanditTheme(String currentBanditTheme) {
    this.currentBanditTheme = currentBanditTheme;
  }

  public PauseReason getPauseReason() {
    return pauseReason;
  }

  public void setPauseReason(PauseReason pauseReason) {
    this.pauseReason = pauseReason;
  }

  public String getWinnerPlayerId() {
    return winnerPlayerId;
  }

  public void setWinnerPlayerId(String winnerPlayerId) {
    this.winnerPlayerId = winnerPlayerId;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
