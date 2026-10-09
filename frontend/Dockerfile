# Frontend do SGT EHS/Visitas Online (25d) — modo dev (ng serve), como o frontend do SGT.
FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
EXPOSE 4300
CMD ["npm", "start"]
