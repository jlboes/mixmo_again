import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

const allowedHost = process.env.MIXMO_DEV_ALLOWED_HOST;

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: "0.0.0.0",
    allowedHosts: allowedHost ? [allowedHost] : undefined
  },
  preview: {
    port: 4173,
    host: "0.0.0.0"
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts"
  }
});
