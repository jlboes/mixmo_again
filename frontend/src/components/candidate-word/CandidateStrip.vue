<template>
  <section :class="embedded ? 'mixmo-builder-section' : 'mixmo-panel p-4'">
    <div class="flex flex-col gap-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="mixmo-kicker">Build</p>
        <div class="mixmo-builder-actions">
          <button class="utility-button mixmo-builder-action" :disabled="disabled || candidate.length === 0" @click="$emit('remove-last')">Undo</button>
          <button class="utility-button mixmo-builder-action" :disabled="disabled || candidate.length === 0" @click="$emit('clear')">Clear</button>
        </div>
      </div>
      <div class="mixmo-builder-draft">
        <div class="flex min-h-14 w-full flex-wrap items-center gap-1.5">
          <span
            v-for="tile in candidate"
            :key="tile.tileId"
            class="mixmo-builder-tile"
            :class="tile.source === 'board' ? 'bg-[var(--color-preview-intersection)]' : 'bg-[var(--color-tile-selected)]'"
          >
            <span class="text-xl font-bold">{{ tile.letter }}</span>
            <span class="text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-text-secondary)]">
              {{ tile.source === "board" ? "Board" : "Rack" }}
            </span>
          </span>
          <span v-if="candidate.length === 0" class="text-sm leading-6 text-[var(--color-text-muted)]">Tap letters to start a word.</span>
        </div>
      </div>
      <div>
        <slot name="after-draft"></slot>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CandidateTile } from "@/models/types";

defineProps<{
  candidate: CandidateTile[];
  disabled: boolean;
  embedded?: boolean;
}>();

defineEmits<{
  "remove-last": [];
  clear: [];
}>();
</script>
