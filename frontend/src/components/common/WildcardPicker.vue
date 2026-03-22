<template>
  <div v-if="open" class="fixed inset-0 z-40 flex items-end bg-[rgba(16,17,20,0.32)] backdrop-blur-sm md:items-center md:justify-center">
    <div class="mixmo-modal w-full rounded-t-[32px] p-5 md:max-w-lg md:rounded-[32px]">
      <div class="flex items-center justify-between">
        <div>
          <p class="mixmo-kicker">Wildcard Letter</p>
          <h3 class="mt-2 text-2xl">Choose the resolved letter.</h3>
        </div>
        <button class="utility-button" @click="$emit('close')">Close</button>
      </div>
      <div class="mt-4 grid grid-cols-5 gap-2">
        <button
          v-for="letter in letters"
          :key="letter"
          class="mixmo-tile px-3 py-3 text-lg font-bold"
          @click="$emit('choose', letter)"
        >
          {{ letter }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  open: boolean;
  letters: string[];
}>();

defineEmits<{
  choose: [string];
  close: [];
}>();
</script>
