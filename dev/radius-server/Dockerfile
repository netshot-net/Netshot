# RADIUS server (FreeRADIUS) for the Netshot RADIUS tests
FROM freeradius/freeradius-server:3.2.10-alpine

COPY clients.conf /etc/raddb/clients.conf
COPY authorize /etc/raddb/mods-config/files/authorize
# No delay before the rejects: the tests check several
RUN sed -i 's/^\treject_delay = 1$/\treject_delay = 0/' /etc/raddb/radiusd.conf \
	&& grep -q '^\treject_delay = 0$' /etc/raddb/radiusd.conf

EXPOSE 1812/udp 1813/udp
CMD ["radiusd", "-l", "stdout"]
