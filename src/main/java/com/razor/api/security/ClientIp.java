package com.razor.api.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP do cliente para os freios (login e rate limit da API). Fonte unica de proposito: antes cada
 * um lia o <b>primeiro</b> valor do {@code X-Forwarded-For}, que e exatamente o pedaco que o
 * cliente escreve — bastava mandar um IP falso por tentativa para nunca ser bloqueado.
 *
 * <p>Agora o cabecalho nao e lido aqui. Quem resolve o IP real e o Tomcat
 * ({@code server.forward-headers-strategy=native} -&gt; {@code RemoteIpValve}): ele so aceita o
 * {@code X-Forwarded-For} quando a conexao vem de um proxy confiavel
 * ({@code server.tomcat.remoteip.internal-proxies}; por padrao redes privadas/loopback, onde
 * fica o nginx do painel) e o percorre da direita para a esquerda, parando no primeiro IP que
 * nao e proxy. Do lado do nginx, {@code X-Forwarded-For $remote_addr} descarta o que o cliente
 * tiver mandado. Resultado: {@code getRemoteAddr()} ja e o IP real, e quem fala direto com a
 * aplicacao nao consegue forja-lo.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null && !remote.isBlank() ? remote : "?";
    }
}
