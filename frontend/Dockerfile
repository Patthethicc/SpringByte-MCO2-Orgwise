# syntax=docker/dockerfile:1
# Orgwise SPA - built with Vite, served by nginx.
# Build context is ./frontend:  docker build -t orgwise/frontend ./frontend

# ---- Stage 1: build static assets ------------------------------------------------------
FROM node:20.19-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# The SPA only ever talks to the API gateway. Inside compose nginx proxies /api to the
# gateway, so a same-origin relative URL works; override for other setups.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Stage 2: nginx ---------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
