import { defineConfig } from "vite";
import { readFileSync } from "node:fs";

const pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf-8")) as { version: string };
import react from "@vitejs/plugin-react";

// Painel web do Razor. Em dev, o proxy encaminha /api para o backend Spring (porta 8081):
// painel e API ficam na mesma origem (localhost:5173), condicao para o cookie de sessao
// (HttpOnly, SameSite=Strict, Path=/api) e o XSRF-TOKEN funcionarem. O proxy repassa Cookie e
// Set-Cookie sem reescrever: nenhum dos dois define Domain, entao valem para o host do painel.
export default defineConfig({
  plugins: [react()],
  // Versão do Razor (package.json) no rodapé do menu e na página "Sobre".
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
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
