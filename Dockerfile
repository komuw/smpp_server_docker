FROM openjdk:8-jre-slim

MAINTAINER komuW <komuw05@gmail.com>

RUN apt -y autoremove && \
    apt -y clean && \
    rm -rf ~/.cache/* && \
    rm -rf /var/lib/{apt,dpkg,cache,log}/ && \
    rm -rf /var/lib/apt/lists/*

COPY SMPPSim /app

RUN chmod +x /app/startsmppsim.sh

EXPOSE 8884

EXPOSE 2775

WORKDIR /app

CMD ["/app/startsmppsim.sh"]
