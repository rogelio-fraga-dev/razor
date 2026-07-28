# Backend do Razor (Spring Boot 3 / Java 17).
#
# Build em dois estagios: o primeiro compila com Maven, o segundo carrega so o JRE e o jar.
# A imagem final nao tem Maven nem codigo-fonte — sobe mais rapido e expoe menos coisa.
#
# Os testes NAO rodam aqui: os de integracao usam Testcontainers, que precisaria de um Docker
# dentro do Docker. Rode `./mvnw test` na maquina, como sempre.

# ---------- estagio 1: compila ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# O pom entra sozinho primeiro para que o cache de dependencias so invalide quando ele mudar.
COPY pom.xml ./
COPY src ./src

# O cache mount guarda o ~/.m2 entre builds — a segunda vez nao rebaixa a internet inteira.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package

# ---------- estagio 2: roda ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl serve ao healthcheck do compose; a imagem base do temurin nao traz nenhum cliente HTTP.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario sem privilegio: se alguem escapar da aplicacao, nao cai como root.
RUN useradd --system --create-home --uid 1001 razor

COPY --from=build /build/target/*.jar app.jar
USER razor

EXPOSE 8081

# MaxRAMPercentage faz a JVM respeitar o limite de memoria do container em vez do da maquina.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
