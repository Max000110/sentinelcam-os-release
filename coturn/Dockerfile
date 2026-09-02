FROM coturn/coturn:4.6.3-alpine

COPY turnserver.conf /etc/coturn/turnserver.conf

EXPOSE 3478/tcp 3478/udp 5349/tcp 5349/udp 49152-65535/udp

USER nobody:nogroup

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD nc -z 127.0.0.1 3478 || exit 1

ENTRYPOINT ["turnserver", "-c", "/etc/coturn/turnserver.conf"]
