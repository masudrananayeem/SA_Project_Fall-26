# Builds one app of the frontend monorepo. APP = admin | client.
FROM node:22-alpine AS build
ARG APP=admin
WORKDIR /app
COPY package.json package-lock.json tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/
COPY apps/admin/package.json apps/admin/
COPY apps/client/package.json apps/client/
RUN npm ci --no-audit --no-fund
COPY packages ./packages
COPY apps ./apps
ARG VITE_API_URL=
ARG VITE_ADMIN_URL=http://localhost:5173
ARG VITE_CLIENT_URL=http://localhost:5174
ENV VITE_API_URL=$VITE_API_URL VITE_ADMIN_URL=$VITE_ADMIN_URL VITE_CLIENT_URL=$VITE_CLIENT_URL
RUN npm run build -w @meterflow/${APP} && cp -r apps/${APP}/dist /out

FROM nginx:alpine
COPY --from=build /out /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
