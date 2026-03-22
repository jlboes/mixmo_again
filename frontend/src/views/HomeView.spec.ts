import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import HomeView from "./HomeView.vue";

const shared = vi.hoisted(() => ({
  push: vi.fn(),
  createRoom: vi.fn(),
  createDemoRoom: vi.fn(),
  joinRoom: vi.fn(),
  saveSession: vi.fn(),
  uuid: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: shared.push })
}));

vi.mock("@/services/api/rooms", () => ({
  createRoom: shared.createRoom,
  createDemoRoom: shared.createDemoRoom,
  joinRoom: shared.joinRoom
}));

vi.mock("@/utils/device", () => ({
  detectDeviceType: () => "DESKTOP"
}));

vi.mock("@/utils/storage", () => ({
  saveSession: shared.saveSession
}));

describe("HomeView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    shared.createRoom.mockResolvedValue({ data: { room: {}, self: {} } });
    shared.createDemoRoom.mockResolvedValue({ data: { room: {}, self: {} } });
    shared.uuid.mockReset();
    shared.uuid.mockReturnValueOnce("token-1").mockReturnValueOnce("token-2");
    vi.stubGlobal("crypto", { randomUUID: shared.uuid });
  });

  it("renders a visible toast when join fails", async () => {
    shared.joinRoom.mockRejectedValueOnce(new Error("Room not found."));

    const wrapper = mount(HomeView, {
      global: {
        stubs: {
          ToastBanner: {
            props: ["toast"],
            template: '<div class="toast-banner">{{ toast?.message }}</div>'
          }
        }
      }
    });

    await wrapper.get('input[placeholder="ABCD12"]').setValue("zzzzzz");
    await wrapper.get('input[placeholder="Alice"]').setValue("Alice");
    await wrapper.findAll("form")[1].trigger("submit.prevent");
    await flushPromises();

    expect(shared.joinRoom).toHaveBeenCalledWith("ZZZZZZ", "Alice", "DESKTOP", "token-1");
    expect(wrapper.get(".toast-banner").text()).toContain("Room not found.");
  });

  it("treats repeated join submits as one in-flight attempt and rotates the token for the next attempt", async () => {
    let rejectJoin!: (error: Error) => void;
    shared.joinRoom.mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectJoin = reject as (error: Error) => void;
        })
    );
    shared.joinRoom.mockRejectedValueOnce(new Error("Still wrong."));

    const wrapper = mount(HomeView, {
      global: {
        stubs: {
          ToastBanner: {
            props: ["toast"],
            template: '<div class="toast-banner">{{ toast?.message }}</div>'
          }
        }
      }
    });

    await wrapper.get('input[placeholder="ABCD12"]').setValue("abcd12");
    await wrapper.get('input[placeholder="Alice"]').setValue("Alice");
    const joinForm = wrapper.findAll("form")[1];

    await joinForm.trigger("submit.prevent");
    await joinForm.trigger("submit.prevent");

    expect(shared.joinRoom).toHaveBeenCalledTimes(1);
    expect(shared.joinRoom).toHaveBeenNthCalledWith(1, "ABCD12", "Alice", "DESKTOP", "token-1");

    rejectJoin(new Error("Room not found."));
    await flushPromises();

    await joinForm.trigger("submit.prevent");
    await flushPromises();

    expect(shared.joinRoom).toHaveBeenCalledTimes(2);
    expect(shared.joinRoom).toHaveBeenNthCalledWith(2, "ABCD12", "Alice", "DESKTOP", "token-2");
  });
});
