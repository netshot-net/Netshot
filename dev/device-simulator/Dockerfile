FROM node:24-slim

RUN apt-get update \
	&& apt-get install -y --no-install-recommends openssh-server snmpd inetutils-telnetd socat \
	&& rm -rf /var/lib/apt/lists/* \
	&& mkdir -p /run/sshd

COPY entrypoint.sh /entrypoint.sh
RUN chmod 755 /entrypoint.sh

COPY sshd_config.conf /etc/ssh/sshd_config.d/netshot-device-simulator.conf

COPY *.js /opt/netshot-device-simulator/
RUN chmod 755 /opt/netshot-device-simulator/*.js

EXPOSE 22
EXPOSE 23
EXPOSE 161/udp
ENTRYPOINT ["/entrypoint.sh"]
