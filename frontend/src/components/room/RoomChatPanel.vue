<template>
  <section class="flex h-full min-h-0 flex-col">
    <header class="flex items-center justify-between gap-3 border-b border-[rgba(16,17,20,0.08)] px-4 py-3">
      <div class="min-w-0">
        <p class="mixmo-kicker">Chat</p>
        <h2 class="text-lg font-semibold text-[var(--color-text-primary)]">Room Chat</h2>
      </div>
      <button v-if="showBackButton" class="utility-button mixmo-compact-button" @click="$emit('back')">
        Back to game
      </button>
    </header>

    <div v-if="error" class="mx-4 mt-4 rounded-[18px] border border-[rgba(229,72,77,0.18)] bg-[rgba(229,72,77,0.1)] px-4 py-3 text-sm text-[var(--color-preview-conflict)]">
      <div class="flex items-start justify-between gap-3">
        <p>{{ error }}</p>
        <button class="text-xs font-semibold uppercase tracking-[0.18em]" @click="$emit('dismissError')">Hide</button>
      </div>
    </div>

    <div ref="scrollRef" class="flex-1 overflow-y-auto px-4 py-4">
      <div v-if="loading && messages.length === 0" class="rounded-[20px] border border-[rgba(16,17,20,0.08)] bg-white/70 px-4 py-6 text-center text-sm text-[var(--color-text-secondary)]">
        Loading chat…
      </div>
      <div v-else-if="messages.length === 0" class="rounded-[20px] border border-[rgba(16,17,20,0.08)] bg-white/70 px-4 py-6 text-center text-sm text-[var(--color-text-secondary)]">
        No messages yet. Start the room conversation.
      </div>
      <div v-else class="grid gap-3">
        <article
          v-for="message in messages"
          :key="message.sequenceNumber"
          class="max-w-[85%] rounded-[22px] border px-4 py-3 shadow-[0_10px_22px_rgba(16,17,20,0.08)]"
          :class="message.playerId === selfPlayerId ? 'ml-auto border-[rgba(239,61,154,0.12)] bg-[rgba(255,238,247,0.92)] text-right' : 'border-[rgba(16,17,20,0.08)] bg-white/90'"
        >
          <div class="flex items-center gap-2 text-xs uppercase tracking-[0.16em] text-[var(--color-text-muted)]" :class="message.playerId === selfPlayerId ? 'justify-end' : ''">
            <span>{{ message.playerName }}</span>
            <span>·</span>
            <span>{{ formatTime(message.createdAt) }}</span>
          </div>
          <p class="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-[var(--color-text-primary)]">
            {{ message.text }}
          </p>
        </article>
      </div>
    </div>

    <form class="border-t border-[rgba(16,17,20,0.08)] px-4 py-3" @submit.prevent="submitMessage">
      <div class="flex items-end gap-3">
        <label class="sr-only" for="room-chat-input">Room chat message</label>
        <textarea
          id="room-chat-input"
          v-model="draft"
          class="min-h-[3rem] flex-1 resize-none rounded-[20px] border border-[rgba(16,17,20,0.12)] bg-white/92 px-4 py-3 text-sm text-[var(--color-text-primary)] shadow-[inset_0_1px_0_rgba(255,255,255,0.8)] outline-none transition focus:border-[rgba(18,184,180,0.32)]"
          maxlength="280"
          placeholder="Message the room"
          rows="1"
        />
        <button class="primary-button" :disabled="sendDisabled" type="submit">
          Send
        </button>
      </div>
      <div class="mt-2 flex justify-end text-xs uppercase tracking-[0.16em] text-[var(--color-text-muted)]">
        {{ remainingCharacters }} left
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import type { ChatMessage } from "@/models/types";

const props = withDefaults(defineProps<{
  messages: ChatMessage[];
  selfPlayerId: string | null;
  loading: boolean;
  error: string | null;
  showBackButton?: boolean;
}>(), {
  showBackButton: false
});

const emit = defineEmits<{
  back: [];
  send: [text: string];
  dismissError: [];
}>();

const draft = ref("");
const scrollRef = ref<HTMLElement | null>(null);

const remainingCharacters = computed(() => 280 - draft.value.trim().length);
const sendDisabled = computed(() => {
  const text = draft.value.trim();
  return text.length === 0 || text.length > 280;
});

watch(
  () => props.messages.length,
  async () => {
    await nextTick();
    if (!scrollRef.value) {
      return;
    }
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight;
  },
  { immediate: true }
);

function submitMessage(): void {
  const text = draft.value.trim();
  if (text.length === 0 || text.length > 280) {
    return;
  }
  emit("send", text);
  draft.value = "";
}

function formatTime(timestamp: string): string {
  return new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(timestamp));
}
</script>
