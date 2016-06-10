FROM ubuntu:14.04

MAINTAINER komuW <komuw05@gmail.com>

RUN apt-get update --fix-missing

RUN apt-get -y install wget default-jre

# fix for PID 1 problem
RUN wget https://github.com/Yelp/dumb-init/releases/download/v1.0.2/dumb-init_1.0.2_amd64.deb

RUN dpkg -i dumb-init_*.deb

RUN chmod +x /usr/bin/dumb-init && ln -sf /usr/bin/dumb-init /usr/local/bin/dinit

RUN apt-get -y purge wget

COPY SMPPSim /app

RUN chmod +x /app/startsmppsim.sh

WORKDIR /app

CMD ["dinit", "/app/startsmppsim.sh"]
