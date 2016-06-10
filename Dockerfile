FROM java:7-jre-alpine

MAINTAINER komuW <komuw05@gmail.com>

RUN apk add --update bash && rm -rf /var/cache/apk/*

COPY SMPPSim /app

RUN chmod +x /app/startsmppsim.sh

WORKDIR /app

CMD ["/app/startsmppsim.sh"]
