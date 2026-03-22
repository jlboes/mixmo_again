<template>
  <div
    v-if="toast"
    class="fixed left-1/2 top-4 z-50 flex max-w-[calc(100vw-2rem)] -translate-x-1/2 items-center rounded-[18px] border px-4 py-3 text-sm font-bold shadow-[0_14px_28px_rgba(16,17,20,0.18)] backdrop-blur"
    :class="toastClass"
  >
    <span>{{ toast.message }}</span>
    <button class="ml-3 text-xs uppercase tracking-[0.2em]" @click="$emit('dismiss')">Dismiss</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { ToastState } from "@/models/types";

const props = defineProps<{
  toast: ToastState | null;
}>();

defineEmits<{
  dismiss: [];
}>();

const toastClass = computed(() => {
  switch (props.toast?.type) {
    case "error":
      return "border-[rgba(229,72,77,0.2)] bg-[rgba(229,72,77,0.94)] text-white";
    case "success":
      return "border-[rgba(32,194,107,0.2)] bg-[rgba(255,255,255,0.94)] text-[var(--color-text-primary)]";
    default:
      return "border-[rgba(239,61,154,0.18)] bg-[rgba(239,61,154,0.94)] text-white";
  }
});
</script>
