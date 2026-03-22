package com.mixmo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bandit_theme_selection")
public class BanditThemeSelectionEntity {

  @Id
  private String id;

  @Column(nullable = false)
  private String roomId;

  @Column(nullable = false)
  private String triggeringPlayerId;

  @Column(length = 64)
  private String selectedTheme;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant resolvedAt;

  @Column(columnDefinition = "text")
  private String pendingPlacementJson;

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

  public String getTriggeringPlayerId() {
    return triggeringPlayerId;
  }

  public void setTriggeringPlayerId(String triggeringPlayerId) {
    this.triggeringPlayerId = triggeringPlayerId;
  }

  public String getSelectedTheme() {
    return selectedTheme;
  }

  public void setSelectedTheme(String selectedTheme) {
    this.selectedTheme = selectedTheme;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public String getPendingPlacementJson() {
    return pendingPlacementJson;
  }

  public void setPendingPlacementJson(String pendingPlacementJson) {
    this.pendingPlacementJson = pendingPlacementJson;
  }
}

