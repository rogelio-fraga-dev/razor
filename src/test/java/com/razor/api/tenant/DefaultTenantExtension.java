package com.razor.api.tenant;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Define a barbearia padrao no {@link TenantContext} antes de cada teste e limpa depois.
 *
 * <p><b>Por que existe.</b> O {@code TenantContext} e fail-closed: em producao, ler o tenant sem
 * que um ponto de entrada o tenha definido e um bug, e estoura. Testes unitarios chamam os
 * services diretamente, sem passar por filtro de JWT, webhook ou scheduler — entao precisam
 * dizer de qual barbearia estao falando.
 *
 * <p>Antes esse "de qual barbearia" era um fallback dentro do proprio {@code TenantContext}, o
 * que fazia o codigo de producao carregar uma conveniencia que so o teste precisava — e que,
 * calada, transformava caminho sem tenant em escrita na barbearia padrao. O fallback nao sumiu:
 * mudou de lugar, para onde e verdade. Teste que se importa com isolamento continua chamando
 * {@code TenantContext.set(...)} e sobrescreve o valor daqui.
 *
 * <p>Registrada globalmente por ServiceLoader
 * ({@code META-INF/services/org.junit.jupiter.api.extension.Extension}) com
 * {@code junit.jupiter.extensions.autodetection.enabled=true} — assim vale para todo teste sem
 * que cada classe precise anotar nada, inclusive as que vierem depois.
 */
public class DefaultTenantExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        TenantContext.set(TenantContext.DEFAULT_BARBERSHOP_ID);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Sempre limpa: o JUnit reusa threads entre testes, e um tenant vazado de um teste para
        // o seguinte esconderia justamente o tipo de erro que o fail-closed existe para pegar.
        TenantContext.clear();
    }
}
