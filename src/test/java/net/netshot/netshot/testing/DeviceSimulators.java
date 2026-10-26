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
package net.netshot.netshot.testing;

import java.nio.file.Paths;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Builders of the simulated device containers ({@code dev/device-simulator}) of the {@link SimulatorTest}
 * classes.
 */
public final class DeviceSimulators {

	private DeviceSimulators() {
		// Static helpers only
	}

	/**
	 * The single device-simulator image ({@code dev/device-simulator}), shared
	 * by every simulated device test in place of the old in-JVM
	 * {@code *FakeCli}/{@code FakeGenericSnmp} test doubles, so these tests
	 * exercise a real SSH/SNMP connection end to end. Every driver it can
	 * emulate also runs real snmpd with driver-matching sysObjectID/sysDescr
	 * values (so a real SNMP-based scan of one of these containers
	 * auto-discovers the right driver, same as it would for the real device),
	 * on top of the SSH CLI for CLI-based drivers - {@code Generic_SNMP} is
	 * the exception, an SNMP-only device with no CLI at all, matching the
	 * real driver. {@link ImageFromDockerfile} caches its own build result,
	 * so this is built at most once no matter how many test classes
	 * start a container from it.
	 */
	public static final ImageFromDockerfile DEVICE_SIMULATOR_IMAGE = new ImageFromDockerfile("netshot-test/device-simulator")
		.withDockerfile(Paths.get("dev", "device-simulator", "Dockerfile"));

	/**
	 * Builds (but does not start) a real-OpenSSH-backed simulated CLI device
	 * container emulating the given driver. Assign the result to a
	 * static {@code @Container} field of a {@link SimulatorTest} class, which
	 * starts and stops it around the class.
	 * @param driverName the driver to emulate: case sensitive, matching the
	 *        corresponding {@code .js} filename (without extension) under
	 *        {@code src/main/resources/drivers/}, e.g. {@code "Cisco_IOS"}
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	public static GenericContainer<?> ssh(String driverName) {
		return new GenericContainer<>(DEVICE_SIMULATOR_IMAGE)
			.withEnv("NETSHOT_SIMULATOR_DRIVER", driverName)
			.withExposedPorts(22)
			.waitingFor(Wait.forListeningPort());
	}

	/**
	 * Same as {@link #ssh(String)}, for one of the platform variants the
	 * simulator script of the driver knows.
	 * @param driverName the driver to emulate, see {@link #ssh(String)}
	 * @param variant the platform variant (NETSHOT_SIMULATOR_VARIANT), see the simulator script
	 * @return the not-yet-started container
	 */
	public static GenericContainer<?> ssh(String driverName, String variant) {
		return ssh(driverName).withEnv("NETSHOT_SIMULATOR_VARIANT", variant);
	}

	/**
	 * Builds (but does not start) the device-simulator container in SNMP-only
	 * mode ({@code Generic_SNMP}, UDP port 161), with the given container
	 * hostname (net-snmp reports the container's own hostname as sysName, so
	 * this is how tests control the expected value). Assign the result to a
	 * static {@code @Container} field of a {@link SimulatorTest} class, which
	 * starts and stops it around the class.
	 * @param hostname the hostname to give the container (used as SNMP sysName)
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	public static GenericContainer<?> snmp(String hostname) {
		return new GenericContainer<>(DEVICE_SIMULATOR_IMAGE)
			.withEnv("NETSHOT_SIMULATOR_DRIVER", "Generic_SNMP")
			.withCreateContainerCmdModifier(cmd -> {
				cmd.withHostName(hostname);
				ExposedPort udpPort = new ExposedPort(161, InternetProtocol.UDP);
				Ports portBindings = new Ports();
				portBindings.bind(udpPort, Ports.Binding.empty());
				cmd.withExposedPorts(udpPort);
				cmd.getHostConfig().withPortBindings(portBindings);
			})
			.waitingFor(Wait.forLogMessage(".*NET-SNMP version.*\\n", 1));
	}

	/**
	 * Looks up the host port a container's UDP port was published to.
	 * {@link GenericContainer#getMappedPort(int)} only ever resolves TCP
	 * bindings, so a UDP-only port (like the simulated SNMP device's 161/udp)
	 * has to be looked up directly from the container's network settings.
	 * @param container the (started) container
	 * @param containerPort the UDP port as exposed by the container
	 * @return the host port it was published to
	 */
	public static int mappedUdpPort(GenericContainer<?> container, int containerPort) {
		Ports.Binding[] bindings = container.getContainerInfo().getNetworkSettings()
			.getPorts().getBindings().get(new ExposedPort(containerPort, InternetProtocol.UDP));
		return Integer.parseInt(bindings[0].getHostPortSpec());
	}
}
