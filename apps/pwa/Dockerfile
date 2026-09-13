# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Imagem da PWA Angular — build multi-stage
#
# Estágio 1 (build): Node.js 22.23.2 compila a aplicação Angular 22
# Estágio 2 (runtime): nginx:alpine serve os assets estáticos
#
# Build context: raiz do repositório (docker compose -f infra/docker-compose.yml)
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Estágio 1 — build Angular
# ---------------------------------------------------------------------------
FROM node:22.23.2-bookworm-slim AS build

WORKDIR /workspace/apps/pwa

# Instala dependências antes de copiar o código fonte para maximizar o cache
COPY apps/pwa/package.json apps/pwa/package-lock.json ./
RUN npm ci --prefer-offline

# Copia todo o código da PWA
COPY apps/pwa/ ./

# Build de produção: otimização, tree-shaking, hashes nos nomes dos arquivos,
# service worker ngsw e manifesto PWA
RUN ./node_modules/.bin/ng build --configuration production

# ---------------------------------------------------------------------------
# Estágio 2 — runtime nginx
# ---------------------------------------------------------------------------
FROM nginx:1.29-alpine AS runtime

LABEL org.opencontainers.image.title="satoshi-pet-pwa" \
      org.opencontainers.image.description="Satoshi Pet Web — PWA Angular 22" \
      org.opencontainers.image.source="https://github.com/satoshi-pet/satoshi-pet-web"

# Configuração nginx com suporte a Angular routing e proxy /api
COPY apps/pwa/nginx.conf /etc/nginx/conf.d/default.conf

# Assets compilados pelo estágio de build
COPY --from=build /workspace/apps/pwa/dist/pwa/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
