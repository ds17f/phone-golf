# Phone Golf API + web leaderboard in one small image.
# Uses Node's built-in SQLite (node:sqlite), so there are no native modules to compile.

FROM node:24.22.1-bookworm-slim AS build
WORKDIR /app/api
COPY api/package.json api/package-lock.json ./
RUN npm ci --ignore-scripts
COPY api/tsconfig.json ./
COPY api/src ./src
RUN npx tsc

FROM node:24.22.1-bookworm-slim
ENV NODE_ENV=production PORT=3000 DB_PATH=/app/data/phone-golf.db WEB_DIR=/app/web
WORKDIR /app/api
COPY api/package.json api/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force
COPY --from=build /app/api/dist ./dist
COPY web /app/web
RUN mkdir -p /app/data && chown -R node:node /app/data
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s CMD node -e "fetch('http://localhost:3000/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "dist/server.js"]
