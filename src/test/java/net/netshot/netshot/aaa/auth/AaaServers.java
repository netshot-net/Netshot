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
package net.netshot.netshot.aaa.auth;

import java.nio.file.Paths;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Builders of the containers of the remote authentication servers ({@code dev/radius-server}: FreeRADIUS,
 * {@code dev/tacacs-server}: tac_plus-ng), with their test users. The images are kept after the run: the
 * build cache then spares the next runs the build of tac_plus-ng.
 */
final class AaaServers {

	/** Shared secret of the RADIUS server. */
	static final String RADIUS_SECRET = "netshot-radius-secret";

	/** Key of the TACACS+ server. */
	static final String TACACS_KEY = "netshot-tacacs-key";

	private AaaServers() {
		// Static helpers only
	}

	/**
	 * Builds (but does not start) the FreeRADIUS container, its UDP ports 1812 and 1813 published on random
	 * host ports (see {@link net.netshot.netshot.testing.DeviceSimulators#mappedUdpPort(GenericContainer, int)}).
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	static GenericContainer<?> radius() {
		return new GenericContainer<>(new ImageFromDockerfile("netshot-test/radius-server", false)
			.withDockerfile(Paths.get("dev", "radius-server", "Dockerfile")))
			.withCreateContainerCmdModifier(cmd -> {
				ExposedPort auth = new ExposedPort(1812, InternetProtocol.UDP);
				ExposedPort acct = new ExposedPort(1813, InternetProtocol.UDP);
				Ports bindings = new Ports();
				bindings.bind(auth, Ports.Binding.empty());
				bindings.bind(acct, Ports.Binding.empty());
				cmd.withExposedPorts(auth, acct);
				cmd.getHostConfig().withPortBindings(bindings);
			})
			.waitingFor(Wait.forLogMessage(".*Ready to process requests.*\\n", 1));
	}

	/**
	 * Builds (but does not start) the tac_plus-ng container, TCP port 49.
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	static GenericContainer<?> tacacs() {
		return new GenericContainer<>(new ImageFromDockerfile("netshot-test/tacacs-server", false)
			.withDockerfile(Paths.get("dev", "tacacs-server", "Dockerfile")))
			.withExposedPorts(49)
			.waitingFor(Wait.forListeningPort());
	}
}
