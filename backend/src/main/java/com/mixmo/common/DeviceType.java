package com.mixmo.common;

public enum DeviceType {
  MOBILE,
  TABLET,
  DESKTOP;

  public int suggestionLimit() {
    return this == DESKTOP ? 5 : 3;
  }
}

