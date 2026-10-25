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
package net.netshot.netshot.task.handler;

import java.util.List;

import org.testcontainers.containers.GenericContainer;

import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.testing.TestDevices;

/**
 * A Cisco IOS device of the inventory reaching a device simulator container (see
 * {@link net.netshot.netshot.testing.DeviceSimulators#ssh(String)} with {@code Cisco_IOS}) over SSH, with
 * the credentials of its domain (auto-try pool), as in production.
 */
final class SimulatedRouter {

	private SimulatedRouter() {
		// Static helpers only
	}

	/**
	 * Saves a domain, its SSH account and a device pointing at the simulator.
	 *
	 * @param container the started Cisco IOS simulator
	 * @param port the SSH port to use (the mapped port of the container, or another one to fail)
	 * @return the saved device
	 */
	static Device persist(GenericContainer<?> container, int port) {
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		TestDevices.addCredentialSet(DeviceSshAccount.builder().username("admin").password("admin").superPassword("admin")
			.name("Lab SSH").mgmtDomain(domain).build());
		return TestDevices.add(Device.create("CiscoIOS12", container.getHost(), domain, "tester").toBuilder()
			.accesses(List.of(DeviceAccess.of("ssh").toBuilder().address(container.getHost()).port(port).build()))
			.build());
	}

	/**
	 * Saves a domain, its SSH account and a device pointing at the simulator.
	 *
	 * @param container the started Cisco IOS simulator
	 * @return the saved device
	 */
	static Device persist(GenericContainer<?> container) {
		return persist(container, container.getMappedPort(22));
	}
}
