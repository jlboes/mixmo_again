package com.mixmo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "board_cell")
public class BoardCellEntity {

  @Id
  private String id;

  @Column(nullable = false)
  private String playerId;

  @Column(nullable = false)
  private int x;

  @Column(nullable = false)
  private int y;

  @Column(nullable = false, unique = true)
  private String tileId;

  @Column(nullable = false, length = 8)
  private String resolvedLetter;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPlayerId() {
    return playerId;
  }

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public int getX() {
    return x;
  }

  public void setX(int x) {
    this.x = x;
  }

  public int getY() {
    return y;
  }

  public void setY(int y) {
    this.y = y;
  }

  public String getTileId() {
    return tileId;
  }

  public void setTileId(String tileId) {
    this.tileId = tileId;
  }

  public String getResolvedLetter() {
    return resolvedLetter;
  }

  public void setResolvedLetter(String resolvedLetter) {
    this.resolvedLetter = resolvedLetter;
  }
}

