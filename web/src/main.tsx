import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { AuthProvider } from "./auth/AuthContext";
import { ToastProvider } from "./components/Toast";
import App from "./App";
import { applyTheme, currentTheme } from "./lib/theme";
import { registerServiceWorker } from "./lib/pwa";
import "./index.css";
import "./shell.css";
import "./theme-light.css"; // por último: a paleta clara vence as demais folhas

applyTheme(currentTheme());
registerServiceWorker();

/** Provedores dentro do roteador; as rotas de verdade moram no <Routes> do App, como antes. */
function Root() {
  return (
    <AuthProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AuthProvider>
  );
}

// "Data router" (react-router 7) com uma rota coringa que entrega tudo ao <Routes> do App. É o
// caminho de migração oficial a partir do <BrowserRouter>: as rotas continuam declaradas em JSX,
// e ganhamos o que só existe no data router — o `useBlocker` (aviso de alterações não salvas no
// editor do site ao navegar dentro do painel).
const router = createBrowserRouter([{ path: "*", element: <Root /> }]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);
