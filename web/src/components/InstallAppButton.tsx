// "Instalar app": abre o diálogo nativo de instalação (Chrome/Edge/Android) ou, no iPhone, o
// passo a passo do "Adicionar à Tela de Início". Some quando o painel já está instalado ou o
// navegador não oferece instalação.
import { useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { useInstallPrompt } from "../lib/pwa";

export function InstallAppButton({ className = "btn btn-ghost btn-sm", compact = false }: { className?: string; compact?: boolean }) {
  const install = useInstallPrompt();
  const toast = useToast();
  const [iosHelp, setIosHelp] = useState(false);

  if (install.installed || (!install.canPrompt && !install.showIosHelp)) return null;

  const onClick = async () => {
    if (install.canPrompt) {
      if (await install.prompt()) toast.success("Razor instalado ✓ — abra pelo ícone na tela inicial.");
      return;
    }
    setIosHelp(true);
  };

  return (
    <>
      <button type="button" className={className} onClick={onClick} title="Instalar o Razor como app">
        <span aria-hidden="true">⬇</span>
        {compact ? <span className="sr-only">Instalar app</span> : " Instalar app"}
      </button>
      {iosHelp && (
        <Modal title="Instalar o Razor no iPhone" onClose={() => setIosHelp(false)}>
          <ol className="install-steps">
            <li>
              Abra o painel no <strong>Safari</strong>.
            </li>
            <li>
              Toque em <strong>Compartilhar</strong> <span aria-hidden="true">(□↑)</span>, na barra de baixo.
            </li>
            <li>
              Escolha <strong>Adicionar à Tela de Início</strong> e confirme em <strong>Adicionar</strong>.
            </li>
          </ol>
          <p className="faint" style={{ margin: "10px 0 0", fontSize: 13 }}>
            O Razor aparece como um app, em tela cheia, sem a barra do navegador.
          </p>
        </Modal>
      )}
    </>
  );
}
