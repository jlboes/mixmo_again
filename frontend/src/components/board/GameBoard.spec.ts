import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import GameBoard from "./GameBoard.vue";

describe("GameBoard", () => {
  it("maps mixmo validation statuses to placed-cell border classes", () => {
    const wrapper = mount(GameBoard, {
      props: {
        boardCells: [
          { x: 0, y: 0, resolvedLetter: "A", tileId: "a", tileKind: "NORMAL", validationStatus: "VALID" },
          { x: 1, y: 0, resolvedLetter: "B", tileId: "b", tileKind: "NORMAL", validationStatus: "INVALID" },
          { x: 2, y: 0, resolvedLetter: "C", tileId: "c", tileKind: "NORMAL", validationStatus: "NEUTRAL" }
        ],
        previewCells: [],
        suggestions: [],
        selectedSuggestionId: null,
        selectedBoardCells: [],
        selectedReturnCells: [{ x: 2, y: 0 }],
        anchor: null,
        removeModeActive: true,
        suggestionsLocked: false,
        viewport: { centerX: 0, centerY: 0, zoom: 1 }
      }
    });

    expect(wrapper.get('[data-cell-key="0:0"]').classes()).toContain("border-[var(--color-state-success)]");
    expect(wrapper.get('[data-cell-key="1:0"]').classes()).toContain("border-[var(--color-preview-conflict)]");
    expect(wrapper.get('[data-cell-key="2:0"]').classes()).toContain("border-[var(--color-accent-theme)]");
    expect(wrapper.get('[data-cell-key="2:0"]').classes()).toContain("ring-4");
  });

  it("renders the softened board guides without the legacy grid layer", () => {
    const wrapper = mount(GameBoard, {
      props: {
        boardCells: [],
        previewCells: [],
        suggestions: [],
        selectedSuggestionId: null,
        selectedBoardCells: [],
        selectedReturnCells: [],
        anchor: null,
        removeModeActive: false,
        suggestionsLocked: false,
        viewport: { centerX: 0, centerY: 0, zoom: 1 }
      }
    });

    expect(wrapper.find(".board-guides").exists()).toBe(true);
    expect(wrapper.find(".board-grid").exists()).toBe(false);
    expect(wrapper.find(".board-origin-marker").exists()).toBe(true);
  });
});
