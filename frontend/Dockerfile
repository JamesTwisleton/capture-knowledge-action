FROM node:22-alpine

# node:22-alpine already ships a `node` user at UID 1000, so reuse it rather than
# creating one — `adduser -u 1000` would fail with "uid in use" and break the build.
#
# /app/node_modules and /app/.next are pre-created here on purpose. Compose mounts
# named volumes over both, and Docker only inherits ownership from the image if the
# path already exists at first init. Without this they would be created root-owned
# and `next dev`, running as `node`, could not write to them.
RUN mkdir -p /app/node_modules /app/.next && chown -R node:node /app

USER node
WORKDIR /app

# Copy package management files first for dependency caching
COPY --chown=node:node package.json package-lock.json ./

# ci, not install: it installs exactly what the lockfile pins and fails if the two
# have drifted, so the image matches what was tested.
RUN npm ci

# Copy application source
COPY --chown=node:node . .

# 3000 application, 9229 Node inspector — the frontend equivalent of the backend's
# 5005, so a debugger can attach to either half of the stack.
EXPOSE 3000 9229

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["npm", "run", "dev"]
