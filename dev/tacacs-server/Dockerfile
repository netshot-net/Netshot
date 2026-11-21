# TACACS+ server (tac_plus-ng, from Marc Huber's event-driven-servers) for the Netshot TACACS+ tests
FROM alpine:3.22

# Pinned commit of https://github.com/MarcJHuber/event-driven-servers (no release tags)
ARG EDS_COMMIT=9bf4afe8b6bcb747f3353b26805ba0716e1364f3

RUN apk add --no-cache build-base linux-headers perl wget c-ares-dev openssl-dev pcre2-dev \
	&& wget -q -O /tmp/eds.tar.gz "https://github.com/MarcJHuber/event-driven-servers/archive/${EDS_COMMIT}.tar.gz" \
	&& mkdir /tmp/eds && tar -xzf /tmp/eds.tar.gz -C /tmp/eds --strip-components=1 \
	&& cd /tmp/eds \
	&& ./configure --minimum --prefix=/usr/local tac_plus-ng \
	&& make \
	&& make install \
	&& cd / && rm -rf /tmp/eds /tmp/eds.tar.gz

COPY tac_plus-ng.cfg /usr/local/etc/tac_plus-ng.cfg
EXPOSE 49
CMD ["/usr/local/sbin/tac_plus-ng", "/usr/local/etc/tac_plus-ng.cfg"]
