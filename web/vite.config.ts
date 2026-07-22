import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Painel web do Razor. Em dev, o proxy encaminha /api para o backend Spring (porta 8081),
// evitando CORS local; em producao o front consome a API via VITE_API_URL.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_API_PROXY ?? "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
});
