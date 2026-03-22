<template>
  <div class="flex gap-3 overflow-x-auto pb-1">
    <button
      v-for="suggestion in suggestions"
      :key="suggestion.suggestionId"
      class="mixmo-chip whitespace-nowrap"
      :class="suggestion.suggestionId === selectedSuggestionId ? 'mixmo-chip-active' : ''"
      :disabled="disabled"
      @click="$emit('select', suggestion)"
    >
      {{ suggestion.label }} · {{ suggestion.victoryCount }}
    </button>
  </div>
</template>

<script setup lang="ts">
import type { PlacementSuggestion } from "@/models/types";

defineProps<{
  suggestions: PlacementSuggestion[];
  selectedSuggestionId: string | null;
  disabled: boolean;
}>();

defineEmits<{
  select: [PlacementSuggestion];
}>();
</script>
