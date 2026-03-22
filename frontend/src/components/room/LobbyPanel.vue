<template>
  <section class="mixmo-panel mx-auto flex max-w-2xl flex-col gap-6 p-6">
    <div class="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
      <div>
        <p class="mixmo-kicker">Room Code</p>
        <h1 class="mt-3 text-4xl tracking-[0.08em] md:text-5xl">{{ roomCode }}</h1>
        <p class="mt-3 text-sm leading-6 text-[var(--color-text-muted)]">Share this code so others can join the room.</p>
      </div>
      <div class="flex flex-col gap-2 md:items-end">
        <button v-if="canStart" class="primary-button" @click="$emit('start')">Start Match</button>
        <div
          v-else
          class="mixmo-chip text-xs uppercase tracking-[0.18em]"
        >
          Waiting for host
        </div>
        <p class="text-sm leading-6 text-[var(--color-text-muted)]">{{ startHint }}</p>
      </div>
    </div>

    <div class="grid gap-3">
      <div
        v-for="player in players"
        :key="player.playerId"
        class="flex items-center justify-between rounded-[24px] border border-[rgba(16,17,20,0.08)] bg-[rgba(255,255,255,0.78)] px-4 py-4 shadow-[0_10px_20px_rgba(16,17,20,0.08)]"
      >
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <p class="font-semibold text-[var(--color-text-primary)]">{{ player.playerName }}</p>
            <span
              v-if="player.playerId === selfPlayerId"
              class="mixmo-badge mixmo-badge-primary"
            >
              You
            </span>
            <span
              v-if="player.playerId === hostPlayerId"
              class="mixmo-badge mixmo-badge-success"
            >
              Host
            </span>
          </div>
          <p class="text-sm text-[var(--color-text-muted)]">Seat {{ player.seatOrder }}</p>
        </div>
        <span class="mixmo-badge" :class="player.connected ? 'mixmo-badge-success' : 'mixmo-badge-muted'">
          {{ player.connected ? "Connected" : "Offline" }}
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { PlayerSummary } from "@/models/types";

const props = defineProps<{
  roomCode: string;
  players: PlayerSummary[];
  hostPlayerId: string | null;
  canStart: boolean;
  selfPlayerId: string | null;
}>();

defineEmits<{
  start: [];
}>();

const startHint = computed(() => {
  if (props.canStart) {
    return props.players.length > 1
      ? "Everyone is in. Start when ready."
      : "You can start now, or wait for another player to join.";
  }
  return "Only the room host can start the match.";
});
</script>
