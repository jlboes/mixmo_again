<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-end bg-[rgba(16,17,20,0.36)] backdrop-blur-sm md:items-center md:justify-center">
    <div class="mixmo-modal w-full rounded-t-[32px] p-5 md:max-w-xl md:rounded-[32px]">
      <p class="mixmo-kicker">Bandit Theme</p>
      <h2 class="mt-2 text-2xl md:text-3xl">Theme selection is blocking the match.</h2>
      <p class="mt-3 text-sm leading-6 text-[var(--color-text-muted)]">
        <template v-if="isOwner">Choose one theme to resolve the Bandit placement.</template>
        <template v-else>Another player is choosing the Bandit theme. Normal actions are blocked until they confirm.</template>
      </p>

      <div class="mt-5 grid grid-cols-2 gap-2 md:grid-cols-3">
        <button
          v-for="option in options"
          :key="option"
          class="mixmo-chip w-full justify-start rounded-[20px] px-3 py-3 text-left text-sm font-bold"
          :class="selectedTheme === option ? 'mixmo-chip-active' : ''"
          :disabled="!isOwner"
          @click="$emit('select', option)"
        >
          {{ option }}
        </button>
      </div>

      <div class="mt-5 flex justify-end gap-3">
        <button class="utility-button" @click="$emit('close')">Hide</button>
        <button class="primary-button" :disabled="!isOwner || !selectedTheme" @click="$emit('confirm')">Confirm Theme</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ThemeOption } from "@/models/types";

defineProps<{
  open: boolean;
  isOwner: boolean;
  options: ThemeOption[];
  selectedTheme: ThemeOption | null;
}>();

defineEmits<{
  select: [ThemeOption];
  close: [];
  confirm: [];
}>();
</script>
