<template>
  <TransitionGroup tag="div" name="mixmo-rack" class="flex flex-wrap gap-2">
    <button
      v-for="tile in rack"
      :key="tile.tileId"
      class="mixmo-tile tile-button relative min-h-12 min-w-12 px-4 py-3 text-xl font-bold"
      :class="tile.selected ? 'mixmo-tile-selected ring-2 ring-[var(--color-accent-primary)] ring-offset-2 ring-offset-white' : ''"
      :disabled="disabled"
      :aria-pressed="tile.selected"
      @click="$emit('toggle-tile', tile.tileId)"
    >
      <span>{{ tile.face }}</span>
      <span v-if="tile.kind !== 'NORMAL'" class="ml-2 text-xs uppercase tracking-[0.22em] text-[var(--color-text-muted)]">{{ tile.kind }}</span>
    </button>
  </TransitionGroup>
</template>

<script setup lang="ts">
import type { RackTile } from "@/models/types";

defineProps<{
  rack: RackTile[];
  disabled: boolean;
}>();

defineEmits<{
  "toggle-tile": [string];
}>();
</script>
