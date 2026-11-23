package com.razor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro sozinho pela landing (V49): a barbearia e o dono, numa tela so.
 *
 * <p>Os {@code @Size} cortam payload absurdo; as regras de verdade (telefone brasileiro, senha,
 * plano) ficam no {@code SignupService}, com mensagem em portugues para a tela.
 *
 * @param shopName  nome da barbearia (vira o nome do bot e a base do endereco do site)
 * @param ownerName nome do dono (vira tambem o primeiro barbeiro da agenda)
 * @param email     login do painel — unico na plataforma
 * @param password  senha do painel (minimo 8, a mesma regra da troca de senha)
 * @param whatsapp  celular do DONO (vira o numero de admin, por onde ele comanda o bot)
 * @param city      cidade da barbearia
 * @param plan      {@code ?plano=} do botao da landing: solo | equipe | pro (opcional)
 * @param website   honeypot: campo escondido da pagina. Gente nao ve e nao preenche; robo sim.
 */
public record SignupRequest(
        @NotBlank(message = "Informe o nome da barbearia.") @Size(max = 120) String shopName,
        @NotBlank(message = "Informe o seu nome.") @Size(max = 120) String ownerName,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 255) String email,
        @NotBlank(message = "Crie uma senha.") @Size(max = 100) String password,
        @NotBlank(message = "Informe o seu WhatsApp.") @Size(max = 30) String whatsapp,
        @NotBlank(message = "Informe a cidade.") @Size(max = 120) String city,
        @Size(max = 20) String plan,
        @Size(max = 200) String website) {
}
