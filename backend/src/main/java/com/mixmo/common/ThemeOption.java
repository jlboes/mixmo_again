package com.mixmo.common;

import java.util.Arrays;
import java.util.Optional;

public enum ThemeOption {
  ANIMALS("Animals"),
  FOOD_AND_DRINKS("Food & Drinks"),
  COUNTRIES_AND_CITIES("Countries & Cities"),
  NATURE("Nature"),
  JOBS_AND_PROFESSIONS("Jobs / Professions"),
  SPORTS("Sports"),
  TECHNOLOGY("Technology"),
  MOVIES_AND_ENTERTAINMENT("Movies & Entertainment"),
  TRANSPORTATION("Transportation"),
  HOUSEHOLD_OBJECTS("Household Objects");

  private final String label;

  ThemeOption(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static Optional<ThemeOption> fromLabel(String label) {
    return Arrays.stream(values()).filter(value -> value.label.equals(label)).findFirst();
  }
}

