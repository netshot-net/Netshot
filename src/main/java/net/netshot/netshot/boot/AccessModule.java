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
package net.netshot.netshot.boot;

import java.util.Optional;

import net.netshot.netshot.access.AccessSettings;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.access.SshClientService;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.platform.config.ConfigStore;

/**
 * The access to the devices: the SSH client, the connectors given to the device scripts, and the SSH server receiving the files of the devices (absent when disabled).
 *
 * @param sshClientService the SSH client (a service)
 * @param connectors what the device accesses need from the server
 * @param sshServer the SSH server (a service), absent when disabled
 */
public record AccessModule(
	SshClientService sshClientService,
	Connectors connectors,
	Optional<SshServer> sshServer) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @return the module
	 */
	public static AccessModule create(CoreModule core) {
		ConfigStore config = core.config();
		SshClientService sshClientService = new SshClientService(config);
		Connectors connectors = new Connectors(core.vaultManager(), sshClientService.client(), core.repositories().deviceReads(),
			new AccessSettings(config));
		Optional<SshServer> sshServer = config.current().get(SshServer.Settings.ENABLED) ? Optional.of(new SshServer(config)) : Optional.empty();
		return new AccessModule(sshClientService, connectors, sshServer);
	}
}
