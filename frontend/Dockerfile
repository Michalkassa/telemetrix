# syntax=docker/dockerfile:1
# The frontend image (plan.md → DevOps): the React build served by nginx, which also proxies /api/
# to the backend, so the browser sees one origin. Type-checking and tests run on the host or in CI.

# ─── Build ──────────────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# empty: the bundle calls the API on its own origin
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npx vite build

# ─── Serve ──────────────────────────────────────────────────────────────────────────────────────
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
