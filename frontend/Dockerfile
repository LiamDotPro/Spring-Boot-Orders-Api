# syntax=docker/dockerfile:1

# ---- build stage: compile the React app ----
FROM node:22-alpine AS build
WORKDIR /app

# Copy the manifests alone first so `npm ci` is cached and only re-runs when a
# dependency actually changes, not on every source edit.
COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json vite.config.ts index.html ./
COPY src src
RUN npm run build

# ---- runtime stage: nginx serving the built assets ----
FROM nginx:1.27-alpine

# Files in /etc/nginx/templates are run through envsubst at container start.
# NGINX_ENVSUBST_FILTER restricts that to API_UPSTREAM — without it, envsubst would
# also eat nginx's own $host, $request_uri and friends, which are not shell variables.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
