FROM coturn/coturn:latest

COPY turnserver.conf /etc/coturn/turnserver.conf

EXPOSE 3478/tcp 3478/udp 5349/tcp 5349/udp 49152-65535/udp

ENTRYPOINT ["turnserver", "-c", "/etc/coturn/turnserver.conf"]
