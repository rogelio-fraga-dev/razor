// Configuracao de ambiente da landing.
//
// Este arquivo e REESCRITO no boot do container a partir da variavel RAZOR_PANEL_URL
// (ver docker-entrypoint.d/10-panel-url.sh). Fora do Docker ele fica com `null` e o
// script.js cai no fallback por hostname — que serve para abrir o site direto do disco.
//
// Nao ponha segredo aqui: este arquivo e servido publicamente, como qualquer outro estatico.
window.RAZOR_CONFIG = {
  panelUrl: null,
};
