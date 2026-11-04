FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_APP_NAME="Banking Fraud Detection"
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_APP_NAME=$VITE_APP_NAME
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV NGINX_ENVSUBST_FILTER=^APP_SERVICE_TOKEN$
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
