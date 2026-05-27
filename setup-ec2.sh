#!/usr/bin/env bash

# ==============================================================================
# BarberBot AI - Script de Configuração Automatizada para AWS EC2 (Ubuntu)
# ==============================================================================
# Este script automatiza a instalação de dependências e a configuração do ambiente.
# Execute no terminal da sua instância EC2 com o comando:
# chmod +x setup-ec2.sh && ./setup-ec2.sh
# ==============================================================================

set -e

# Cores para o output
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}    Iniciando Setup do BarberBot AI no EC2          ${NC}"
echo -e "${CYAN}====================================================${NC}"

# 1. Verificar se está rodando como Ubuntu/Debian
if [ ! -f /etc/debian_version ]; then
    echo -e "${RED}ERRO: Este script foi projetado apenas para distribuições baseadas em Ubuntu/Debian.${NC}"
    exit 1
fi

# 2. Atualizando pacotes
echo -e "\n${YELLOW}[1/5] Atualizando lista de pacotes do sistema...${NC}"
sudo apt update -y

# 3. Instalando ferramentas essenciais e JDK 17
echo -e "\n${YELLOW}[2/5] Instalando utilitários e Java 17 (JDK)...${NC}"
sudo apt install -y curl apt-transport-https ca-certificates gnupg lsb-release openjdk-17-jdk screen git

# 4. Instalando o Docker Engine
echo -e "\n${YELLOW}[3/5] Instalando Docker e Docker Compose...${NC}"
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sudo bash
    sudo usermod -aG docker $USER
    echo -e "${GREEN}✔ Docker instalado com sucesso!${NC}"
else
    echo -e "${GREEN}✔ Docker já está instalado.${NC}"
fi

# Instalar Docker Compose v2 se necessário
if ! docker compose version &> /dev/null; then
    sudo apt install -y docker-compose-plugin
fi
echo -e "${GREEN}✔ Docker Compose verificado.${NC}"

# 5. Configuração de Memória SWAP (Essencial para instâncias gratuitas t2.micro)
echo -e "\n${YELLOW}[4/5] Configuração de Memória Virtual (Swap)...${NC}"
echo -e "Você está utilizando a instância gratuita t2.micro (1GB de RAM)? [s/N]"
read -r swap_response

if [[ "$swap_response" =~ ^([sS][iI][mM]|[sS])$ ]]; then
    if [ -f /swapfile ]; then
        echo -e "${GREEN}✔ Arquivo Swap já existe.${NC}"
    else
        echo -e "${CYAN}Criando 2GB de Swap de memória virtual...${NC}"
        sudo fallocate -l 2G /swapfile
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
        sudo swapon /swapfile
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
        echo -e "${GREEN}✔ Swap de 2GB criado e ativado com sucesso!${NC}"
    fi
else
    echo -e "${CYAN}Pulando a criação do arquivo Swap (recomendado para instâncias com >= 2GB RAM).${NC}"
fi

# 6. Permissões e Finalização
echo -e "\n${YELLOW}[5/5] Finalizando configurações...${NC}"
# Permissão de execução para o Maven Wrapper do projeto
if [ -f ./mvnw ]; then
    chmod +x ./mvnw
    echo -e "${GREEN}✔ Permissão de execução aplicada ao Maven Wrapper (./mvnw).${NC}"
fi

echo -e "${CYAN}====================================================${NC}"
echo -e "${GREEN}✔ SETUP CONCLUÍDO COM SUCESSO!${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Próximos Passos:"
echo -e " 1. Edite o arquivo ${YELLOW}.env${NC} configurando o novo número admin (${YELLOW}ADMIN_PHONE${NC}) e sua chave OpenAI."
echo -e " 2. Suba o banco e a Evolution API com: ${GREEN}docker compose up -d${NC}"
echo -e " 3. Inicie o Spring Boot em background usando o comando:"
echo -e "    ${GREEN}screen -dmS bot bash -c 'export \$(grep -v \"^#\" .env | xargs) && ./mvnw spring-boot:run'${NC}"
echo -e " 4. Para ver os logs do bot rodando, use: ${GREEN}screen -r bot${NC}"
echo -e " 5. Para sair da tela de logs sem desligar o bot, aperte: ${GREEN}Ctrl + A e depois a tecla D${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Lembre-se de adicionar as portas ${YELLOW}8080${NC} e ${YELLOW}8081${NC} nas Regras de Entrada (Security Group) da AWS!"
echo -e "${CYAN}====================================================${NC}"
echo -e "Deseja que apliquemos as novas permissões do grupo docker nesta sessão? [s/N]"
read -r shell_response
if [[ "$shell_response" =~ ^([sS][iI][mM]|[sS])$ ]]; then
    exec sg docker "$0"
fi
