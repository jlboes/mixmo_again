<template>
  <div
    ref="boardRef"
    class="mixmo-panel game-board relative cursor-grab select-none active:cursor-grabbing"
    @wheel.prevent="onWheel"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @click="onClickBoard"
  >
    <div class="absolute inset-0 board-guides" :style="guideStyle"></div>
    <div
      v-if="!hasRenderedCell(0, 0)"
      class="board-origin-marker absolute z-0"
      :style="cellFrameStyle(0, 0)"
    ></div>

    <div
      v-if="anchor && !hasRenderedCell(anchor.x, anchor.y)"
      class="board-cell-frame absolute z-10"
      :style="cellFrameStyle(anchor.x, anchor.y)"
    >
      <div class="board-anchor-face"></div>
    </div>

    <div
      v-for="cell in renderedCells"
      :key="`${cell.kind}-${cell.x}-${cell.y}`"
      class="board-cell-frame absolute z-10"
      :style="cellFrameStyle(cell.x, cell.y)"
    >
      <div
        :data-cell-key="`${cell.x}:${cell.y}`"
        class="board-cell-face flex items-center justify-center text-lg font-bold transition-all"
        :class="cellClass(cell)"
      >
        {{ cell.letter }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useElementSize } from "@vueuse/core";
import { computed, ref } from "vue";
import type { BoardCellDto, LetterValidationState, PlacementSuggestion, PreviewCell, ViewportState } from "@/models/types";

interface RenderCell {
  x: number;
  y: number;
  letter: string;
  kind: "placed" | "preview-valid" | "preview-conflict" | "preview-intersection";
  selected: boolean;
  returnSelected: boolean;
  validationStatus: LetterValidationState;
}

const props = withDefaults(defineProps<{
  boardCells: BoardCellDto[];
  previewCells: PreviewCell[];
  suggestions: PlacementSuggestion[];
  selectedSuggestionId: string | null;
  selectedBoardCells: Array<{ x: number; y: number }>;
  selectedReturnCells: Array<{ x: number; y: number }>;
  anchor: { x: number; y: number } | null;
  removeModeActive: boolean;
  suggestionsLocked: boolean;
  viewport: ViewportState;
  readOnly?: boolean;
}>(), {
  readOnly: false
});

const emit = defineEmits<{
  "select-cell": [{ x: number; y: number }];
  "select-suggestion": [PlacementSuggestion];
  "update:viewport": [ViewportState];
}>();

const BASE_CELL_SIZE = 36;
const MIN_ZOOM = 0.7;
const MAX_ZOOM = 2.4;
const ZOOM_STEP = 0.08;

const boardRef = ref<HTMLElement | null>(null);
const { width: boardWidth, height: boardHeight } = useElementSize(boardRef);
const dragState = ref<{ x: number; y: number; moved: boolean } | null>(null);
const selectedBoardKeys = computed(() => new Set(props.selectedBoardCells.map((cell) => `${cell.x}:${cell.y}`)));
const selectedReturnKeys = computed(() => new Set(props.selectedReturnCells.map((cell) => `${cell.x}:${cell.y}`)));
const renderMetrics = computed(() => {
  const cellSize = BASE_CELL_SIZE * props.viewport.zoom;
  return {
    cellSize,
    tileInset: Math.max(2.5, Math.min(5, cellSize * 0.09))
  };
});

const renderedCells = computed<RenderCell[]>(() => {
  const placed = props.boardCells.map((cell) => ({
    x: cell.x,
    y: cell.y,
    letter: cell.resolvedLetter,
    kind: "placed" as const,
    selected: selectedBoardKeys.value.has(`${cell.x}:${cell.y}`),
    returnSelected: selectedReturnKeys.value.has(`${cell.x}:${cell.y}`),
    validationStatus: cell.validationStatus ?? "NEUTRAL"
  }));
  const preview = props.previewCells.map((cell) => ({
    x: cell.x,
    y: cell.y,
    letter: cell.letter,
    kind: previewKind(cell.state),
    selected: false,
    returnSelected: false,
    validationStatus: "NEUTRAL" as const
  }));
  return [...placed, ...preview];
});

function previewKind(state: PreviewCell["state"]): RenderCell["kind"] {
  if (state === "CONFLICT") {
    return "preview-conflict";
  }
  if (state === "OVERLAP_MATCH") {
    return "preview-intersection";
  }
  return "preview-valid";
}

const guideStyle = computed(() => {
  const { cellSize } = renderMetrics.value;
  const left = boardWidth.value / 2 - props.viewport.centerX * cellSize - cellSize / 2;
  const top = boardHeight.value / 2 - props.viewport.centerY * cellSize - cellSize / 2;
  return {
    backgroundSize: `${cellSize}px ${cellSize}px`,
    backgroundPosition: `${left}px ${top}px`
  };
});

function cellPosition(x: number, y: number) {
  const { cellSize } = renderMetrics.value;
  const left = boardWidth.value / 2 + (x - props.viewport.centerX) * cellSize - cellSize / 2;
  const top = boardHeight.value / 2 + (y - props.viewport.centerY) * cellSize - cellSize / 2;
  return { left, top };
}

function cellFrameStyle(x: number, y: number) {
  const { cellSize, tileInset } = renderMetrics.value;
  const { left, top } = cellPosition(x, y);
  return {
    width: `${cellSize}px`,
    height: `${cellSize}px`,
    left: `${left}px`,
    top: `${top}px`,
    "--board-tile-inset": `${tileInset}px`
  };
}

function cellClass(cell: RenderCell) {
  switch (cell.kind) {
    case "preview-valid":
      return "border-transparent bg-[var(--color-preview-valid)] text-[var(--color-text-primary)]";
    case "preview-conflict":
      return "border-transparent bg-[var(--color-preview-conflict)] text-white";
    case "preview-intersection":
      return "border-transparent bg-[var(--color-preview-intersection)] text-[var(--color-text-primary)] ring-2 ring-[var(--color-accent-primary)] ring-offset-1 ring-offset-white";
    default:
      return placedCellClass(cell.validationStatus, cell.selected, cell.returnSelected);
  }
}

function placedCellClass(validationStatus: LetterValidationState, selected: boolean, returnSelected: boolean) {
  const validationClass = (() => {
    switch (validationStatus) {
      case "VALID":
        return "border-[var(--color-state-success)] bg-[var(--color-tile-placed)] text-[var(--color-text-primary)]";
      case "INVALID":
        return "border-[var(--color-preview-conflict)] bg-[var(--color-tile-placed)] text-[var(--color-text-primary)]";
      default:
        return "border-white/60 bg-[var(--color-tile-placed)] text-[var(--color-text-primary)]";
    }
  })();

  if (returnSelected) {
    return "border-[var(--color-accent-theme)] bg-[rgba(72,221,216,0.32)] text-[var(--color-text-primary)] ring-4 ring-[rgba(39,211,207,0.42)] ring-offset-2 ring-offset-white shadow-[0_12px_24px_rgba(39,211,207,0.18)]";
  }
  return selected
    ? `${validationClass} ring-2 ring-[var(--color-accent-primary)] ring-offset-1 ring-offset-white`
    : props.removeModeActive
      ? `${validationClass} shadow-[inset_0_0_0_1px_rgba(39,211,207,0.16)]`
      : validationClass;
}

function hasRenderedCell(x: number, y: number) {
  return renderedCells.value.some((cell) => cell.x === x && cell.y === y);
}

function onWheel(event: WheelEvent) {
  const nextZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, props.viewport.zoom + (event.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP)));
  emit("update:viewport", { ...props.viewport, zoom: Number(nextZoom.toFixed(2)) });
}

function onPointerDown(event: PointerEvent) {
  dragState.value = { x: event.clientX, y: event.clientY, moved: false };
}

function onPointerMove(event: PointerEvent) {
  if (!dragState.value || !boardRef.value) {
    return;
  }
  const deltaX = event.clientX - dragState.value.x;
  const deltaY = event.clientY - dragState.value.y;
  if (Math.abs(deltaX) < 5 && Math.abs(deltaY) < 5 && !dragState.value.moved) {
    return;
  }
  dragState.value.moved = true;
  const { cellSize } = renderMetrics.value;
  emit("update:viewport", {
    ...props.viewport,
    centerX: props.viewport.centerX - deltaX / cellSize,
    centerY: props.viewport.centerY - deltaY / cellSize
  });
  dragState.value = { x: event.clientX, y: event.clientY, moved: true };
}

function onPointerUp() {
  dragState.value = null;
}

function onClickBoard(event: MouseEvent) {
  if (!boardRef.value || dragState.value?.moved || props.readOnly) {
    return;
  }
  const rect = boardRef.value.getBoundingClientRect();
  const { cellSize } = renderMetrics.value;
  const x = Math.round((event.clientX - rect.left - rect.width / 2) / cellSize + props.viewport.centerX);
  const y = Math.round((event.clientY - rect.top - rect.height / 2) / cellSize + props.viewport.centerY);
  emit("select-cell", { x, y });
}
</script>
