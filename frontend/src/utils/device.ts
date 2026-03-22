import type { DeviceType } from "@/models/types";

export function detectDeviceType(width = window.innerWidth): DeviceType {
  if (width >= 1024) {
    return "DESKTOP";
  }
  if (width >= 768) {
    return "TABLET";
  }
  return "MOBILE";
}

