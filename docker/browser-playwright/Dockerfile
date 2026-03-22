FROM mcr.microsoft.com/playwright:v1.58.2-noble

ENV PLAYWRIGHT_VERSION=1.58.2 \
    PLAYWRIGHT_SERVER_PORT=3000

USER root
RUN npm install -g "playwright@${PLAYWRIGHT_VERSION}" \
    && playwright --version

WORKDIR /home/pwuser

USER pwuser

EXPOSE 3000

CMD ["sh", "-lc", "playwright run-server --host 0.0.0.0 --port ${PLAYWRIGHT_SERVER_PORT}"]
