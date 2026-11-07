/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.testing;

import org.apache.sshd.client.SshClient;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.access.SshClientService;
import net.netshot.netshot.vault.SecretResolver;

/**
 * The connectors of the device accesses for the tests: the local secrets only, and an SSH client
 * started on first use, shared by the tests (and stopped with the JVM).
 */
public final class TestConnectors {

	private TestConnectors() {
	}

	/** Holder of the SSH client service, started on first use. */
	private static final class Client {
		static final SshClientService SERVICE = start();

		private static SshClientService start() {
			SshClientService service = new SshClientService(Netshot.config());
			service.start();
			return service;
		}
	}

	/**
	 * Gets the shared SSH client of the tests.
	 *
	 * @return the started SSH client
	 */
	public static SshClient sshClient() {
		return Client.SERVICE.client();
	}

	/**
	 * Gets the connectors: the local secrets, the shared SSH client of the tests, and no database.
	 *
	 * @return the connectors
	 */
	public static Connectors local() {
		return new Connectors(SecretResolver.LOCAL, sshClient(), null);
	}
}
