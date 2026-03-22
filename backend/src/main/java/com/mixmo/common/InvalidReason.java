package com.mixmo.common;

public enum InvalidReason {
  COLLISION_WITH_DIFFERENT_LETTER("collision_with_different_letter"),
  DISCONNECTED_PLACEMENT("disconnected_placement"),
  FIRST_WORD_MUST_CROSS_ORIGIN("first_word_must_cross_origin"),
  INVALID_BANDIT_LETTER("invalid_bandit_letter"),
  INVALID_TILE_USAGE("invalid_tile_usage"),
  MISSING_ANCHOR("missing_anchor"),
  EMPTY_CANDIDATE_WORD("empty_candidate_word"),
  INVALID_ORIENTATION("invalid_orientation");

  private final String code;

  InvalidReason(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}

