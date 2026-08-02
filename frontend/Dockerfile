# syntax=docker/dockerfile:1

# No build step on purpose. The console is plain HTML/CSS/JS with no dependencies,
# so there is nothing to compile and nothing to keep up to date. nginx serves the
# files and proxies /api to the Spring app.
FROM nginx:1.27-alpine

# Files in /etc/nginx/templates are run through envsubst at container start and the
# result is written to /etc/nginx/conf.d. NGINX_ENVSUBST_FILTER restricts that to
# API_UPSTREAM — without it, envsubst would also eat nginx's own $host,
# $request_uri and friends, which are not shell variables.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

COPY html/ /usr/share/nginx/html/

EXPOSE 80
