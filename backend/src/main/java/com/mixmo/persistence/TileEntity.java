package com.mixmo.persistence;

import com.mixmo.common.TileKind;
import com.mixmo.common.TileLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tile")
public class TileEntity {

  @Id
  private String id;

  @Column(nullable = false)
  private String roomId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TileKind kind;

  @Column(length = 8)
  private String faceValue;

  @Column(length = 8)
  private String assignedLetter;

  private String ownerPlayerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TileLocation location;

  private Integer bagPosition;

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

  public TileKind getKind() {
    return kind;
  }

  public void setKind(TileKind kind) {
    this.kind = kind;
  }

  public String getFaceValue() {
    return faceValue;
  }

  public void setFaceValue(String faceValue) {
    this.faceValue = faceValue;
  }

  public String getAssignedLetter() {
    return assignedLetter;
  }

  public void setAssignedLetter(String assignedLetter) {
    this.assignedLetter = assignedLetter;
  }

  public String getOwnerPlayerId() {
    return ownerPlayerId;
  }

  public void setOwnerPlayerId(String ownerPlayerId) {
    this.ownerPlayerId = ownerPlayerId;
  }

  public TileLocation getLocation() {
    return location;
  }

  public void setLocation(TileLocation location) {
    this.location = location;
  }

  public Integer getBagPosition() {
    return bagPosition;
  }

  public void setBagPosition(Integer bagPosition) {
    this.bagPosition = bagPosition;
  }
}

