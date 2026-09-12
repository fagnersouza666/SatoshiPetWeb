# syntax=docker/dockerfile:1

FROM node:22.23.2-bookworm-slim AS build

WORKDIR /workspace/apps/pwa

COPY apps/pwa/package.json apps/pwa/package-lock.json ./
RUN npm ci

COPY apps/pwa/ ./
RUN ./node_modules/.bin/ng build --configuration production

FROM nginx:1.29-alpine AS runtime

COPY apps/pwa/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/pwa/dist/pwa/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
