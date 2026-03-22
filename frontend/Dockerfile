FROM node:23-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_WS_BASE_URL=ws://localhost:8080/ws
ARG VITE_DEBUG_DEMO_ROOM=true
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_WS_BASE_URL=${VITE_WS_BASE_URL}
ENV VITE_DEBUG_DEMO_ROOM=${VITE_DEBUG_DEMO_ROOM}
COPY package.json .
COPY package-lock.json* .
RUN npm install
COPY . .
RUN npm run build

FROM node:23-alpine
WORKDIR /app
COPY --from=build /app/package.json package.json
COPY --from=build /app/node_modules node_modules
COPY --from=build /app/dist dist
EXPOSE 4173
CMD ["npm", "run", "preview"]

