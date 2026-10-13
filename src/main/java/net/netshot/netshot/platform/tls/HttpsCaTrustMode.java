/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.platform.tls;

/**
 * TLS server certificate CA trust mode, used for every outgoing HTTPS
 * connection Netshot makes (device HTTPS access, Web hooks, ...).
 */
public enum HttpsCaTrustMode {
	/**
	 * No verification at all - any server certificate is accepted, including
	 * one whose CN/SAN doesn't match the connection hostname (historical
	 * behavior). Hostname verification is deliberately not offered as a
	 * separate option here: checking it alone, without a trusted certificate
	 * chain, offers no real protection against an active attacker, who can
	 * simply present a certificate with a matching CN/SAN.
	 */
	TRUST_ANY,
	/** The JVM's default (system) trust store is used; the hostname is verified. */
	SYSTEM_TRUSTSTORE,
	/** Only the configured custom CA certificate (and certificates it issued) is trusted; the hostname is verified. */
	CUSTOM_CA
}
