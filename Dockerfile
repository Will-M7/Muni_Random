FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY angular.json tsconfig*.json ./
COPY src ./src
COPY public ./public
RUN npm run build -- --configuration production

FROM nginx:1.28-alpine
COPY docker/nginx.prod.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/San_miguel/browser/ /usr/share/nginx/html/
EXPOSE 443
