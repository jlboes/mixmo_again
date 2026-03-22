<template>
  <section class="flex h-full min-h-0 flex-col">
    <header class="flex items-center justify-between gap-3 border-b border-[rgba(16,17,20,0.08)] px-4 py-3">
      <div class="min-w-0">
        <p class="mixmo-kicker">Players</p>
        <h2 class="truncate text-lg font-semibold text-[var(--color-text-primary)]">
          {{ selectedPlayer?.playerName ?? "Spectate" }}
        </h2>
      </div>
      <button class="utility-button mixmo-compact-button" @click="$emit('close')">
        {{ closeLabel }}
      </button>
    </header>

    <div class="border-b border-[rgba(16,17,20,0.08)] px-4 py-3">
      <div class="flex items-center gap-2">
        <button class="utility-button mixmo-compact-button px-3" :disabled="!previousPlayerId" @click="selectPlayer(previousPlayerId)">
          Prev
        </button>
        <div class="flex-1 overflow-x-auto">
          <div class="flex min-w-max gap-2">
            <button
              v-for="player in players"
              :key="player.playerId"
              class="mixmo-chip whitespace-nowrap"
              :class="player.playerId === selectedPlayerId ? 'mixmo-chip-active' : ''"
              @click="selectPlayer(player.playerId)"
            >
              {{ player.playerName }}
            </button>
          </div>
        </div>
        <button class="utility-button mixmo-compact-button px-3" :disabled="!nextPlayerId" @click="selectPlayer(nextPlayerId)">
          Next
        </button>
      </div>
      <p v-if="selectedPlayer" class="mt-3 text-sm text-[var(--color-text-secondary)]">
        Seat {{ selectedPlayer.seatOrder }} · Rack {{ selectedPlayer.rackCount ?? 0 }} · Cells {{ selectedPlayer.boardCellCount ?? 0 }}
      </p>
    </div>

    <div v-if="error" class="mx-4 mt-4 rounded-[18px] border border-[rgba(229,72,77,0.18)] bg-[rgba(229,72,77,0.1)] px-4 py-3 text-sm text-[var(--color-preview-conflict)]">
      {{ error }}
    </div>

    <div class="flex-1 px-4 py-4">
      <div v-if="selectedPlayerId && loadingByPlayerId[selectedPlayerId] && !selectedBoard" class="flex h-full min-h-[18rem] items-center justify-center rounded-[26px] border border-[rgba(16,17,20,0.08)] bg-white/70 text-sm text-[var(--color-text-secondary)]">
        Loading board…
      </div>
      <div v-else-if="selectedPlayer && !selectedBoard" class="flex h-full min-h-[18rem] items-center justify-center rounded-[26px] border border-[rgba(16,17,20,0.08)] bg-white/70 px-6 text-center text-sm text-[var(--color-text-secondary)]">
        This board is not available yet.
      </div>
      <GameBoard
        v-else
        class="h-full min-h-[20rem]"
        :board-cells="selectedBoard?.boardCells ?? []"
        :preview-cells="[]"
        :suggestions="[]"
        :selected-suggestion-id="null"
        :selected-board-cells="[]"
        :selected-return-cells="[]"
        :anchor="null"
        :remove-mode-active="false"
        :suggestions-locked="true"
        :viewport="viewport"
        read-only
        @update:viewport="$emit('updateViewport', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import GameBoard from "@/components/board/GameBoard.vue";
import type { PlayerBoardView, PlayerSummary, ViewportState } from "@/models/types";

const props = withDefaults(defineProps<{
  players: PlayerSummary[];
  selectedPlayerId: string | null;
  boards: Record<string, PlayerBoardView>;
  loadingByPlayerId: Record<string, boolean>;
  viewport: ViewportState;
  error: string | null;
  closeLabel?: string;
}>(), {
  closeLabel: "Close"
});

const emit = defineEmits<{
  close: [];
  selectPlayer: [playerId: string];
  updateViewport: [viewport: ViewportState];
}>();

const selectedIndex = computed(() => props.players.findIndex((player) => player.playerId === props.selectedPlayerId));
const selectedPlayer = computed(() => props.players.find((player) => player.playerId === props.selectedPlayerId) ?? null);
const selectedBoard = computed(() => props.selectedPlayerId ? props.boards[props.selectedPlayerId] ?? null : null);
const previousPlayerId = computed(() => {
  if (selectedIndex.value <= 0) {
    return null;
  }
  return props.players[selectedIndex.value - 1]?.playerId ?? null;
});
const nextPlayerId = computed(() => {
  if (selectedIndex.value < 0 || selectedIndex.value >= props.players.length - 1) {
    return null;
  }
  return props.players[selectedIndex.value + 1]?.playerId ?? null;
});

function selectPlayer(playerId: string | null): void {
  if (!playerId || playerId === props.selectedPlayerId) {
    return;
  }
  emit("selectPlayer", playerId);
}
</script>
