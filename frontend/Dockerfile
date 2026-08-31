#Stage 1: Build
FROM node:20-alpine AS build

WORKDIR /app

#Copy dependencies
COPY package*.json ./

#Install dependencies
RUN npm ci

COPY . .

ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

#Stage 2: serve
#use the nginx alpine to serve the static output
FROM nginx:alpine AS serve

#Remove the default configuration
RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/app.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]