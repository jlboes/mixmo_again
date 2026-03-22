<template>
  <main class="mixmo-page-shell min-h-screen px-4 py-8 text-[var(--color-text-primary)] sm:px-6 lg:py-10">
    <div class="mx-auto grid max-w-6xl gap-6 xl:grid-cols-[1.15fr_0.85fr]">
      <section class="mixmo-hero p-7 md:p-9">
        <div class="flex flex-wrap gap-3">
          <span class="mixmo-chip mixmo-chip-active text-xs uppercase tracking-[0.18em]">Fast Word Race</span>
          <span class="mixmo-chip text-xs uppercase tracking-[0.18em]">Realtime Sync</span>
          <span class="mixmo-chip text-xs uppercase tracking-[0.18em]">Bandit Pause</span>
        </div>
        <p class="mixmo-kicker mt-8">Mixmo MVP</p>
        <h1 class="mt-4 max-w-2xl text-5xl leading-[0.96] tracking-[-0.04em] md:text-6xl">Compose first. Place whole words. Keep the board in view.</h1>
        <p class="mt-5 max-w-xl text-base leading-7 text-[var(--color-text-secondary)] md:text-lg">
          Create a room, join from another device, and play a server-authoritative Mixmo match with realtime MIXMO, Bandit pause flow, placement previews, and reconnect support.
        </p>
        <div class="mt-8 grid gap-3 sm:grid-cols-3">
          <div class="mixmo-panel p-4">
            <p class="mixmo-kicker">Compose</p>
            <p class="mt-2 text-sm leading-6 text-[var(--color-text-secondary)]">Build from the rack first, then commit whole words to the board.</p>
          </div>
          <div class="mixmo-panel p-4">
            <p class="mixmo-kicker">Social</p>
            <p class="mt-2 text-sm leading-6 text-[var(--color-text-secondary)]">Join from a second device and keep every move synced in realtime.</p>
          </div>
          <div class="mixmo-panel p-4">
            <p class="mixmo-kicker">Arcade</p>
            <p class="mt-2 text-sm leading-6 text-[var(--color-text-secondary)]">Trigger MIXMO fast, then react to Bandit pauses without losing state.</p>
          </div>
        </div>
      </section>

      <section class="grid gap-4 self-start">
        <form class="mixmo-panel p-5 md:p-6" @submit.prevent="handleCreate">
          <p class="mixmo-kicker">Create Room</p>
          <label class="mt-4 block text-sm font-bold text-[var(--color-text-primary)]">Name</label>
          <input v-model="createName" class="mixmo-input mt-2" placeholder="Jean-Luc" />
          <button class="primary-button mt-5 w-full" :disabled="busy">Create Room</button>
        </form>

        <form class="mixmo-panel p-5 md:p-6" @submit.prevent="handleJoin">
          <p class="mixmo-kicker">Join Room</p>
          <label class="mt-4 block text-sm font-bold text-[var(--color-text-primary)]">Room Code</label>
          <input v-model="joinRoomId" class="mixmo-input mt-2 uppercase" placeholder="ABCD12" />
          <label class="mt-4 block text-sm font-bold text-[var(--color-text-primary)]">Name</label>
          <input v-model="joinName" class="mixmo-input mt-2" placeholder="Alice" />
          <button class="primary-button mt-5 w-full" :disabled="busy">Join Room</button>
        </form>
        <div v-if="demoRoomEnabled" class="mixmo-panel border-dashed bg-[rgba(255,255,255,0.72)] p-5 md:p-6">
          <p class="mixmo-kicker">Debug</p>
          <p class="mt-3 text-sm leading-6 text-[var(--color-text-secondary)]">Open a pre-seeded demo room with an active crossword-style board.</p>
          <button class="special-button mt-5 w-full" :disabled="busy" @click="handleOpenDemoRoom">Open Demo Room</button>
        </div>
      </section>
    </div>
    <ToastBanner :toast="toast" @dismiss="toast = null" />
  </main>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import ToastBanner from "@/components/common/ToastBanner.vue";
import { createDemoRoom, createRoom, joinRoom } from "@/services/api/rooms";
import { detectDeviceType } from "@/utils/device";
import { saveSession } from "@/utils/storage";
import type { ToastState } from "@/models/types";

const router = useRouter();
const demoRoomEnabled = import.meta.env.DEV || import.meta.env.VITE_DEBUG_DEMO_ROOM === "true";
const createName = ref("");
const joinRoomId = ref("");
const joinName = ref("");
const busy = ref(false);
const toast = ref<ToastState | null>(null);
const pendingJoinSessionToken = ref<string | null>(null);

function nextJoinSessionToken(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `join-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

async function handleCreate() {
  if (busy.value) {
    return;
  }
  busy.value = true;
  try {
    const response = await createRoom(createName.value, detectDeviceType());
    const { room, self } = response.data;
    saveSession({
      roomId: room.roomId,
      roomCode: room.roomCode,
      playerId: self.playerId,
      playerName: self.playerName,
      seatOrder: self.seatOrder,
      sessionToken: self.sessionToken
    });
    await router.push({ name: "room", params: { roomId: room.roomId } });
  } catch (error) {
    toast.value = { type: "error", message: (error as Error).message };
  } finally {
    busy.value = false;
  }
}

async function handleOpenDemoRoom() {
  if (busy.value) {
    return;
  }
  busy.value = true;
  try {
    const response = await createDemoRoom(detectDeviceType());
    const { room, self } = response.data;
    saveSession({
      roomId: room.roomId,
      roomCode: room.roomCode,
      playerId: self.playerId,
      playerName: self.playerName,
      seatOrder: self.seatOrder,
      sessionToken: self.sessionToken
    });
    await router.push({ name: "room", params: { roomId: room.roomId } });
  } catch (error) {
    toast.value = { type: "error", message: (error as Error).message };
  } finally {
    busy.value = false;
  }
}

async function handleJoin() {
  if (busy.value) {
    return;
  }
  busy.value = true;
  if (!pendingJoinSessionToken.value) {
    pendingJoinSessionToken.value = nextJoinSessionToken();
  }
  try {
    const response = await joinRoom(joinRoomId.value.trim().toUpperCase(), joinName.value, detectDeviceType(), pendingJoinSessionToken.value);
    const { room, self } = response.data;
    saveSession({
      roomId: room.roomId,
      roomCode: room.roomCode,
      playerId: self.playerId,
      playerName: self.playerName,
      seatOrder: self.seatOrder,
      sessionToken: self.sessionToken
    });
    await router.push({ name: "room", params: { roomId: room.roomId } });
  } catch (error) {
    toast.value = { type: "error", message: (error as Error).message };
  } finally {
    pendingJoinSessionToken.value = null;
    busy.value = false;
  }
}
</script>
