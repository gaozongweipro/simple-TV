FROM node:20-alpine

WORKDIR /app
RUN apk add --no-cache ffmpeg
COPY package.json ./
COPY src ./src
COPY config ./config

ENV NODE_ENV=production
ENV PORT=8088
EXPOSE 8088

CMD ["node", "src/server.js"]
