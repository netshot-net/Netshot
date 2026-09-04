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
package net.netshot.netshot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.graalvm.polyglot.proxy.ProxyObject;
import org.hibernate.Session;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;
import com.sun.net.httpserver.HttpServer;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Device.NetworkClass;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.DeviceDriver.DriverProtocol;
import net.netshot.netshot.device.DeviceDriver.Location;
import net.netshot.netshot.device.DeviceDriver.LocationType;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.DriverValueType;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.DeviceAccess;
import net.netshot.netshot.device.access.Http;
import net.netshot.netshot.device.access.Http.AuthScheme;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.device.attribute.ConfigTextAttribute;
import net.netshot.netshot.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.device.attribute.OptionDefinition;
import net.netshot.netshot.device.script.helper.JsDeviceHelper;
import net.netshot.netshot.device.credentials.DeviceHttpAccount;
import net.netshot.netshot.device.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.device.script.SnapshotDeviceScript;
import net.netshot.netshot.work.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@Testcontainers
public class DeviceDriverTest {

	@BeforeAll
	static void initNetshot() throws Exception {
		Netshot.readConfig();
		Ssh.loadConfig();
		DeviceDriver.refreshDrivers();
	}

	/**
	 * The single device-simulator image ({@code dev/device-simulator}), shared
	 * by every simulated device test below in place of the old in-JVM
	 * {@code *FakeCli}/{@code FakeGenericSnmp} test doubles, so these tests
	 * exercise a real SSH/SNMP connection end to end. Every driver it can
	 * emulate also runs real snmpd with driver-matching sysObjectID/sysDescr
	 * values (so a real SNMP-based scan of one of these containers
	 * auto-discovers the right driver, same as it would for the real device),
	 * on top of the SSH CLI for CLI-based drivers - {@code Generic_SNMP} is
	 * the exception, an SNMP-only device with no CLI at all, matching the
	 * real driver. {@link ImageFromDockerfile} caches its own build result,
	 * so this is built at most once no matter how many nested test classes
	 * start a container from it.
	 */
	private static final ImageFromDockerfile DEVICE_SIMULATOR_IMAGE = new ImageFromDockerfile("netshot-test/device-simulator")
		.withDockerfile(Paths.get("dev", "device-simulator", "Dockerfile"));

	/**
	 * Builds (but does not start) a real-OpenSSH-backed simulated CLI device
	 * container emulating the given driver. Assign the result to a
	 * {@code @Container}-annotated field so the {@code @Testcontainers}
	 * extension starts/stops it around the enclosing test class.
	 * @param driverName the driver to emulate: case sensitive, matching the
	 *        corresponding {@code .js} filename (without extension) under
	 *        {@code src/main/resources/drivers/}, e.g. {@code "Cisco_IOS"}
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	private static GenericContainer<?> buildSshDeviceSimulator(String driverName) {
		return new GenericContainer<>(DEVICE_SIMULATOR_IMAGE)
			.withEnv("NETSHOT_SIMULATOR_DRIVER", driverName)
			.withExposedPorts(22)
			.waitingFor(Wait.forListeningPort());
	}

	/**
	 * Builds (but does not start) the device-simulator container in SNMP-only
	 * mode ({@code Generic_SNMP}, UDP port 161), with the given container
	 * hostname (net-snmp reports the container's own hostname as sysName, so
	 * this is how tests control the expected value). Assign the result to a
	 * {@code @Container}-annotated field so the {@code @Testcontainers}
	 * extension starts/stops it around the enclosing test class.
	 * @param hostname the hostname to give the container (used as SNMP sysName)
	 * @return the not-yet-started container
	 */
	@SuppressWarnings("resource")
	private static GenericContainer<?> buildSnmpDeviceSimulator(String hostname) {
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
	private static int getMappedUdpPort(GenericContainer<?> container, int containerPort) {
		Ports.Binding[] bindings = container.getContainerInfo().getNetworkSettings()
			.getPorts().getBindings().get(new ExposedPort(containerPort, InternetProtocol.UDP));
		return Integer.parseInt(bindings[0].getHostPortSpec());
	}

	@Nested
	@DisplayName("CiscoIOS12 driver test")
	class CiscoIOS12Test {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSshDeviceSimulator("Cisco_IOS");

		@Test
		@DisplayName("CiscoIOS12 Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("CiscoIOS12", null, domain, "test");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setAddress(container.getHost());
			sshAccess.setPort(container.getMappedPort(22));
			device.getAccesses().add(sshAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(credentials));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			try {
				runMethod.invoke(script, nullSession, device, accessManager);
				Assertions.assertEquals("router1", device.getName(), "The device name is incorrect");
				Assertions.assertEquals("15.5(3)S7b", device.getSoftwareVersion(), "The software version is incorrect");
				Assertions.assertEquals("Cisco CSR1000V", device.getFamily(), "The device family is incorrect");
				Assertions.assertEquals("SNMPLOCATION", device.getLocation(), "The location is incorrect");
				Assertions.assertEquals("SNMPCONTACT", device.getContact(), "The contact is incorrect");
				Assertions.assertEquals(NetworkClass.ROUTER, device.getNetworkClass(), "The network class is incorrect");
				Assertions.assertEquals(1071.0,
					((DeviceNumericAttribute) device.getAttribute("mainMemorySize")).getNumber().doubleValue(),
					"The memory size is incorrect");
				Assertions.assertEquals("0x2102",
					((DeviceTextAttribute) device.getAttribute("configRegister")).getText(),
					"The config register is incorrect");
				Assertions.assertEquals(
					Boolean.TRUE,
					((DeviceBinaryAttribute) device.getAttribute("configurationSaved")).getAssumption(),
					"The configuration is not seen as saved");
				Config config = device.getLastConfig();
				Assertions.assertNotNull(config, "The config doesn't exist");
				Assertions.assertEquals("admin", config.getAuthor(), "The config author is incorrect");
				Assertions.assertEquals("bootflash:packages.conf",
					((ConfigTextAttribute) config.getAttribute("iosImageFile")).getText(),
					"The IOS image file is incorrect");
				Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("runningConfig"))
					.getLongText().getText().contains("ip ssh version 2"), "The running config is not correct");
				Assertions.assertEquals("96NETS96HOT",
					device.getModules().get(0).getSerialNumber(), "The first module serial number is incorrect");
				Assertions.assertEquals(Network4Address.getNetworkAddress("192.168.200.101", 24),
					device.getNetworkInterface("GigabitEthernet1").getIp4Addresses().iterator().next(),
					"The first interface IP address is incorrect");
			}
			finally {
				accessManager.disconnectAll();
			}
		}
	}

	@Nested
	@DisplayName("ZPENodeGrid driver test")
	class ZPENodeGridTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSshDeviceSimulator("ZPE_NodeGrid");

		@Test
		@DisplayName("ZPENodeGrid Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("ZPENodeGrid", null, domain, "test");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setAddress(container.getHost());
			sshAccess.setPort(container.getMappedPort(22));
			device.getAccesses().add(sshAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(credentials));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			try {
				runMethod.invoke(script, nullSession, device, accessManager);
				Assertions.assertEquals("NODEGRID-1", device.getName(), "The device name is incorrect");
				Assertions.assertEquals("3.1.16", device.getSoftwareVersion(), "The software version is incorrect");
				Assertions.assertEquals("NSC-T16S", device.getFamily(), "The device family is incorrect");
				Assertions.assertEquals("Nodegrid", device.getLocation(), "The location is incorrect");
				Assertions.assertEquals("support@zpesystems.com", device.getContact(), "The contact is incorrect");
				Assertions.assertEquals(NetworkClass.SWITCH, device.getNetworkClass(), "The network class is incorrect");
				Assertions.assertEquals(
					3842.0,
					((DeviceNumericAttribute) device.getAttribute("mainMemorySize")).getNumber().doubleValue(),
					"The memory size is incorrect");
				Assertions.assertEquals(
					16, ((DeviceNumericAttribute) device.getAttribute("licenseCount")).getNumber().doubleValue(),
					"The license count is incorrect");
				Assertions.assertEquals(
					"Intel(R) Atom(TM) CPU E3827  @ 1.74GHz (2 core(s))",
					((DeviceTextAttribute) device.getAttribute("cpuInfo")).getText(),
					"The CPU info is incorrect");
				Config config = device.getLastConfig();
				Assertions.assertNotNull(config, "The config doesn't exist");
				Assertions.assertEquals("homer", config.getAuthor(), "The config author is incorrect");
				Assertions.assertEquals("3.1.16",
					((ConfigTextAttribute) config.getAttribute("softwareVersion")).getText(),
					"The software version (in config) is incorrect");
				Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("settings"))
					.getLongText().getText().contains("enable_banner=no"), "The settings are not correct");
				Assertions.assertEquals("1416161616",
					device.getModules().get(0).getSerialNumber(), "The first module serial number is incorrect");
				Assertions.assertEquals(Network4Address.getNetworkAddress("10.10.16.16", 24),
					device.getNetworkInterface("bond").getIp4Addresses().iterator().next(),
					"The bond interface IP address is incorrect");
				Assertions.assertNotNull(device.getNetworkInterface("usbS1"), "The usbS1 interface does not exist");
				Assertions.assertEquals("JJJ-JJJ-JJJ-JJJ-JJJJ",
					device.getNetworkInterface("ttyS10").getDescription(),
					"The description of ttyS10 is incorrect");
			}
			finally {
				accessManager.disconnectAll();
			}
		}
	}

	@Nested
	@DisplayName("Arista MOS driver test")
	class AristaMOSTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSshDeviceSimulator("Arista_MOS");

		@Test
		@DisplayName("Arista MOS Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("AristaMOS", null, domain, "test");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setAddress(container.getHost());
			sshAccess.setPort(container.getMappedPort(22));
			device.getAccesses().add(sshAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(credentials));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			try {
				runMethod.invoke(script, nullSession, device, accessManager);
				Assertions.assertEquals("switch1", device.getName(), "The device name is incorrect");
				Assertions.assertEquals("0.31.0", device.getSoftwareVersion(), "The software version is incorrect");
				Assertions.assertEquals("MetaConnect 48", device.getFamily(), "The device family is incorrect");
				Assertions.assertEquals("SNMPLOCATION", device.getLocation(), "The location is incorrect");
				Assertions.assertEquals("SNMPCONTACT", device.getContact(), "The contact is incorrect");
				Assertions.assertEquals(NetworkClass.SWITCH, device.getNetworkClass(), "The network class is incorrect");
				Assertions.assertEquals(
					61057.0, ((DeviceNumericAttribute) device.getAttribute("totalDiskSize")).getNumber().doubleValue(),
					"The disk size is incorrect");
				Config config = device.getLastConfig();
				Assertions.assertNotNull(config, "The config doesn't exist");
				Assertions.assertEquals("admin", config.getAuthor(), "The config author is incorrect");
				Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("runningConfig"))
					.getLongText().getText().contains("username admin"), "The running config is not correct");
				Assertions.assertEquals(3, device.getModules().size(), "The number of modules is incorrect");
				Assertions.assertEquals("C48-A6-12627-0", device.getModules().get(0).getSerialNumber(),
					"The chassis module serial number is incorrect");
				Assertions.assertEquals(
					Network4Address.getNetworkAddress("10.18.25.40", 24),
					device.getNetworkInterface("ma1").getIp4Addresses().iterator().next(),
					"The ma1 interface IP address is incorrect");
			}
			finally {
				accessManager.disconnectAll();
			}
		}
	}

	@Nested
	@DisplayName("Cisco AsyncOS driver test")
	class CiscoAsyncOSTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSshDeviceSimulator("Cisco_AsyncOS");

		@Test
		@DisplayName("Cisco AsyncOS Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("CiscoAsyncOS", null, domain, "test");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setAddress(container.getHost());
			sshAccess.setPort(container.getMappedPort(22));
			device.getAccesses().add(sshAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(credentials));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			try {
				runMethod.invoke(script, nullSession, device, accessManager);
				Config config = device.getLastConfig();
				Assertions.assertNotNull(config, "The config doesn't exist");
				Assertions.assertEquals("esa.netshot.lab", device.getName(), "The device name is incorrect");
			}
			finally {
				accessManager.disconnectAll();
			}
		}
	}

	@Nested
	@DisplayName("GenericSNMP driver test")
	class GenericSNMPTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSnmpDeviceSimulator("myhost");

		@Test
		@DisplayName("GenericSNMP Snapshot (real snmpd container)")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSnmpv2cCommunity community = new DeviceSnmpv2cCommunity("public", "community1");
			int port = getMappedUdpPort(container, 161);
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("GenericSNMP", null, domain, "test");
			DeviceAccess snmpAccess = new DeviceAccess(device, "snmpv2c");
			snmpAccess.setAddress(container.getHost());
			snmpAccess.setPort(port);
			device.getAccesses().add(snmpAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);

			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(community));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, nullSession, device, accessManager);

			Assertions.assertEquals("myhost", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("Rack 1", device.getLocation(), "The location is incorrect");
			Assertions.assertEquals("someone@example.com", device.getContact(), "The contact is incorrect");
			Assertions.assertEquals(NetworkClass.UNKNOWN, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals("1.3.6.1.4.1.8072.3.2.10",
				((DeviceTextAttribute) device.getAttribute("sysObjectId")).getText(), "The sysObjectId is incorrect");
			Assertions.assertNotNull(device.getNetworkInterface("eth0"), "The eth0 interface should have been created");
		}
	}

	@Nested
	@DisplayName("JuniperJunos driver test")
	class JuniperJunosTest {

		TaskContext taskContext = new FakeTaskContext();

		@Container
		private static final GenericContainer<?> container = buildSshDeviceSimulator("Juniper_Junos");

		@Test
		@DisplayName("JuniperJunos Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSshAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("JuniperJunos", null, domain, "test");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setAddress(container.getHost());
			sshAccess.setPort(container.getMappedPort(22));
			device.getAccesses().add(sshAccess);
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, Set.of(credentials));
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			try {
				runMethod.invoke(script, nullSession, device, accessManager);
				Assertions.assertEquals("mx1", device.getName(), "The device name is incorrect");
				Assertions.assertEquals("21.4R3.15", device.getSoftwareVersion(), "The software version is incorrect");
				Assertions.assertEquals("Juniper MX240", device.getFamily(), "The device family is incorrect");
				Assertions.assertEquals("Room 42", device.getLocation(), "The location is incorrect");
				Assertions.assertEquals("netshot@example.com", device.getContact(), "The contact is incorrect");
				Assertions.assertEquals(NetworkClass.ROUTER, device.getNetworkClass(), "The network class is incorrect");
				Config config = device.getLastConfig();
				Assertions.assertNotNull(config, "The config doesn't exist");
				Assertions.assertEquals("admin", config.getAuthor(), "The config author is incorrect");
				Assertions.assertTrue(((ConfigLongTextAttribute) config.getAttribute("configuration"))
					.getLongText().getText().contains("host-name mx1"), "The configuration is not correct");
				Assertions.assertEquals(2, device.getModules().size(), "The number of modules is incorrect");
				Assertions.assertEquals("JN123F456789", device.getModules().get(0).getSerialNumber(),
					"The chassis module serial number is incorrect");
				Assertions.assertEquals(Network4Address.getNetworkAddress("10.0.0.1", 30),
					device.getNetworkInterface("ge-0/0/0.0").getIp4Addresses().iterator().next(),
					"The ge-0/0/0.0 interface IP address is incorrect");
			}
			finally {
				accessManager.disconnectAll();
			}
		}
	}

	/**
	 * Tests for the access "group"/"priority" defaulting and the HTTP/HTTPS
	 * protocol split (Phase 4 of the generic multi-protocol client work).
	 */
	@Nested
	@DisplayName("Device driver access test")
	class AccessTest {

		private static final String TEST_DRIVER_JS = """
			var Info = {
				name: "TestAccessGroupsDriver",
				author: "test",
				description: "Test driver for access groups/priority",
				version: "1.0"
			};

			var Config = {};
			var Device = {};

			var CLI = {
				telnet: {},
				ssh: {},
			};

			var SNMP = {
				snmpv1: {},
				snmpv2c: {},
				snmpv3: {},
			};

			var HTTP = {
				http: {},
				https: {},
				custom: { protocol: "https", group: "custom", priority: 5 },
			};

			function snapshot(client, device, config) {
			}
			""";

		private static final String COOKIE_AUTH_DRIVER_JS = """
			var Info = {
				name: "CookieAuthDriver",
				author: "test",
				description: "Test driver for cookie-based HTTP auth",
				version: "1.0"
			};

			var Config = {};
			var Device = {};
			var CLI = { ssh: {} };

			var HTTP = {
				https: {
					auth: {
						type: "cookie",
						method: "post",
						path: "/login",
						data: {
							domain: "local",
							userName: "$$NetshotUsername$$",
							userPasswd: "$$NetshotPassword$$",
						},
						contentType: "json",
					}
				}
			};

			function snapshot(client, device, config) {
			}
			""";

		private static final String BAD_CASE_AUTH_TYPE_DRIVER_JS = """
			var Info = {
				name: "BadCaseAuthTypeDriver",
				author: "test",
				description: "test",
				version: "1.0"
			};

			var Config = {};
			var Device = {};
			var CLI = {};

			var HTTP = {
				https: {
					auth: {
						type: "APIKEY",
						in: "header",
						name: "X-API-Key",
					}
				}
			};

			function snapshot(client, device, config) {
			}
			""";

		private static final String BAD_SNMP_KEY_DRIVER_JS = """
			var Info = {
				name: "BadSnmpKeyDriver",
				author: "test",
				description: "test",
				version: "1.0"
			};

			var Config = {};
			var Device = {};

			var CLI = { ssh: {} };
			var SNMP = { snmp1: {} };

			function snapshot(client, device, config) {
			}
			""";

		private DeviceDriver buildTestDriver() throws Exception {
			return new DeviceDriver(new StringReader(TEST_DRIVER_JS), "TestAccessGroupsDriver.js",
				new Location(LocationType.EMBEDDED, "TestAccessGroupsDriver.js"));
		}

		@Test
		void strictSnmpKeyMatchingRejectsAmbiguousNames() {
			// Previously "snmp1" would have matched SNMPv1 via a loose `.contains("1")`
			// check; the key must now be one of the exact "snmpv1"/"snmpv2c"/"snmpv3" names.
			Assertions.assertThrows(IllegalArgumentException.class, () -> new DeviceDriver(
				new StringReader(BAD_SNMP_KEY_DRIVER_JS), "BadSnmpKeyDriver.js",
				new Location(LocationType.EMBEDDED, "BadSnmpKeyDriver.js")));
		}

		@Test
		void defaultGroupsAndPriorities() throws Exception {
			DeviceDriver driver = this.buildTestDriver();

			AccessDefinition ssh = driver.getAccessDefinition("ssh");
			Assertions.assertEquals("cli", ssh.getGroup());
			Assertions.assertEquals(100, ssh.getPriority());
			Assertions.assertEquals(22, ssh.getDefaultPort());

			AccessDefinition telnet = driver.getAccessDefinition("telnet");
			Assertions.assertEquals("cli", telnet.getGroup());
			Assertions.assertEquals(10, telnet.getPriority());
			Assertions.assertEquals(23, telnet.getDefaultPort());

			AccessDefinition snmpv1 = driver.getAccessDefinition("snmpv1");
			Assertions.assertEquals("snmp", snmpv1.getGroup());
			Assertions.assertEquals(20, snmpv1.getPriority());
			Assertions.assertEquals(161, snmpv1.getDefaultPort());

			AccessDefinition snmpv2c = driver.getAccessDefinition("snmpv2c");
			Assertions.assertEquals("snmp", snmpv2c.getGroup());
			Assertions.assertEquals(22, snmpv2c.getPriority());

			AccessDefinition snmpv3 = driver.getAccessDefinition("snmpv3");
			Assertions.assertEquals("snmp", snmpv3.getGroup());
			Assertions.assertEquals(80, snmpv3.getPriority());

			AccessDefinition http = driver.getAccessDefinition("http");
			Assertions.assertEquals(DriverProtocol.HTTP, http.getProtocol());
			Assertions.assertEquals("http", http.getGroup());
			Assertions.assertEquals(30, http.getPriority());
			Assertions.assertEquals(80, http.getDefaultPort());
			Assertions.assertEquals(DeviceHttpAccount.class, http.getCredentialClass());

			AccessDefinition https = driver.getAccessDefinition("https");
			Assertions.assertEquals(DriverProtocol.HTTPS, https.getProtocol());
			Assertions.assertEquals("http", https.getGroup());
			Assertions.assertEquals(90, https.getPriority());
			Assertions.assertEquals(443, https.getDefaultPort());
			Assertions.assertEquals(DeviceHttpAccount.class, https.getCredentialClass());
		}

		@Test
		void explicitGroupAndPriorityOverride() throws Exception {
			DeviceDriver driver = this.buildTestDriver();

			AccessDefinition custom = driver.getAccessDefinition("custom");
			Assertions.assertEquals(DriverProtocol.HTTPS, custom.getProtocol());
			Assertions.assertEquals("custom", custom.getGroup());
			Assertions.assertEquals(5, custom.getPriority());
		}

		@Test
		void defaultCliAccessesSortedByPriority() throws Exception {
			DeviceDriver driver = this.buildTestDriver();
			List<AccessDefinition> cliAccesses = driver.getDefaultCliAccessDefinitions();
			Assertions.assertEquals(2, cliAccesses.size());
			Assertions.assertEquals("ssh", cliAccesses.get(0).getName());
			Assertions.assertEquals("telnet", cliAccesses.get(1).getName());
		}

		@Test
		void defaultSnmpAccessesSortedByPriority() throws Exception {
			DeviceDriver driver = this.buildTestDriver();
			List<AccessDefinition> snmpAccesses = driver.getDefaultSnmpAccessDefinitions();
			Assertions.assertEquals(3, snmpAccesses.size());
			Assertions.assertEquals("snmpv3", snmpAccesses.get(0).getName());
			Assertions.assertEquals("snmpv2c", snmpAccesses.get(1).getName());
			Assertions.assertEquals("snmpv1", snmpAccesses.get(2).getName());
		}

		@Test
		void accessDefinitionsByGroup() throws Exception {
			DeviceDriver driver = this.buildTestDriver();

			List<AccessDefinition> httpGroup = driver.getAccessDefinitionsByGroup("http");
			Assertions.assertEquals(2, httpGroup.size());
			Assertions.assertEquals("https", httpGroup.get(0).getName());
			Assertions.assertEquals("http", httpGroup.get(1).getName());

			List<AccessDefinition> customGroup = driver.getAccessDefinitionsByGroup("custom");
			Assertions.assertEquals(1, customGroup.size());
			Assertions.assertEquals("custom", customGroup.get(0).getName());
		}

		@Test
		void cookieAuthSchemeIsParsed() throws Exception {
			DeviceDriver driver = new DeviceDriver(new StringReader(COOKIE_AUTH_DRIVER_JS), "CookieAuthDriver.js",
				new Location(LocationType.EMBEDDED, "CookieAuthDriver.js"));

			AuthScheme auth = driver.getAccessDefinition("https").getHttpConfig().getAuth();
			Assertions.assertEquals("cookie", auth.getType());
			Assertions.assertEquals("POST", auth.getMethod());
			Assertions.assertEquals("/login", auth.getPath());
			Assertions.assertEquals("json", auth.getContentType());
			Assertions.assertEquals(Map.of(
				"domain", "local",
				"userName", "$$NetshotUsername$$",
				"userPasswd", "$$NetshotPassword$$"
			), auth.getData());
		}

		@Test
		void authSchemeTypeIsCaseSensitive() {
			// "APIKEY" must be rejected: driver-declared auth constants like "apiKey"
			// are matched with exact case, not loosely/case-insensitively.
			Assertions.assertThrows(IllegalArgumentException.class, () -> new DeviceDriver(
				new StringReader(BAD_CASE_AUTH_TYPE_DRIVER_JS), "BadCaseAuthTypeDriver.js",
				new Location(LocationType.EMBEDDED, "BadCaseAuthTypeDriver.js")));
		}

	}

	/**
	 * Tests for {@code Http.download()}/{@code http.download(...)}: the
	 * binary-safe counterpart to {@code Http.request()}/{@code http.request(...)},
	 * added because the latter always decodes the response body as a
	 * (charset-decoded) {@link String} - lossy for arbitrary binary content
	 * such as a backup archive (see {@link net.netshot.netshot.device.access.Http}
	 * and {@link net.netshot.netshot.device.script.helper.JsHttpHelper}).
	 */
	@Nested
	@DisplayName("HTTP client binary download test")
	class HttpDownloadTest {

		private HttpServer fakeServer;
		private int port;

		/** All 256 possible byte values, including sequences that are not valid UTF-8 - proves byte-exact transfer. */
		private byte[] binaryPayload() {
			byte[] data = new byte[256];
			for (int i = 0; i < 256; i++) {
				data[i] = (byte) i;
			}
			return data;
		}

		private void serveBinary(String path, byte[] payload) {
			this.fakeServer.createContext(path, exchange -> {
				exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
				exchange.sendResponseHeaders(200, payload.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(payload);
				}
			});
		}

		@BeforeEach
		void setUp() throws IOException {
			// Binary file attributes (ConfigBinaryFileAttribute) need a storage
			// folder configured - point it at a fresh temporary directory for
			// each test rather than relying on the default (/var/local/netshot),
			// which won't exist on a test machine.
			Path storagePath = Files.createTempDirectory("netshot-test-binary-");
			Properties props = new Properties();
			props.putAll(Netshot.getConfig());
			props.setProperty("netshot.snapshots.binary.path", storagePath.toString());
			Netshot.initConfig(props);
			ConfigBinaryFileAttribute.loadConfig();

			this.fakeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			this.port = this.fakeServer.getAddress().getPort();
			this.fakeServer.start();
		}

		@AfterEach
		void tearDown() {
			this.fakeServer.stop(0);
		}

		@Test
		@DisplayName("Http.download() streams the exact response bytes to disk")
		void downloadIsByteExact() throws Exception {
			byte[] payload = this.binaryPayload();
			this.serveBinary("/binary.bin", payload);

			TaskContext taskContext = new FakeTaskContext();
			Http http = new Http("127.0.0.1", this.port, false, taskContext);
			Path target = Files.createTempFile("netshot-test-download-", ".bin");
			try {
				Http.HttpDownloadResult result =
					http.download("GET", "/binary.bin", null, null, null, null, null, null, target);

				Assertions.assertEquals(200, result.getStatus());
				Assertions.assertEquals(payload.length, result.getSize());
				Assertions.assertArrayEquals(payload, Files.readAllBytes(target),
					"Downloaded bytes must exactly match the server response, including non-UTF-8 byte sequences");
			}
			finally {
				Files.deleteIfExists(target);
			}
		}

		@Test
		@DisplayName("Unlike download(), request()'s String-decoded body does not preserve arbitrary bytes")
		void requestBodyIsNotBinarySafe() throws Exception {
			// Documents *why* download() exists: bytes 0x80-0xFF are not valid
			// UTF-8 on their own, so decoding the response as a String (what
			// request() does) and re-encoding it loses information.
			byte[] payload = this.binaryPayload();
			this.serveBinary("/binary2.bin", payload);

			TaskContext taskContext = new FakeTaskContext();
			Http http = new Http("127.0.0.1", this.port, false, taskContext);
			Http.HttpResult result = http.request("GET", "/binary2.bin", null, null, null, null, null, null);

			Assertions.assertEquals(200, result.getStatus());
			byte[] roundTripped = result.getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			Assertions.assertFalse(Arrays.equals(payload, roundTripped),
				"request()'s String round-trip is expected to corrupt arbitrary binary content");
		}

		@Test
		@DisplayName("Http.download() can carry an explicit Content-Type header on a bodyless GET")
		void downloadCanSendContentTypeHeaderWithoutABody() throws Exception {
			// Some servers (observed against a real Infoblox NIOS Grid, whose
			// database.bak download endpoint 415s a GET with no Content-Type
			// header) unusually insist on seeing one even without a request
			// body - this reproduces that behavior in miniature.
			byte[] payload = this.binaryPayload();
			final String[] receivedContentType = new String[1];
			this.fakeServer.createContext("/needs-content-type.bin", exchange -> {
				receivedContentType[0] = exchange.getRequestHeaders().getFirst("Content-Type");
				if (receivedContentType[0] == null) {
					exchange.sendResponseHeaders(415, -1);
					exchange.close();
					return;
				}
				exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
				exchange.sendResponseHeaders(200, payload.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(payload);
				}
			});

			TaskContext taskContext = new FakeTaskContext();
			Http http = new Http("127.0.0.1", this.port, false, taskContext);
			Path target = Files.createTempFile("netshot-test-download-", ".bin");
			try {
				Map<String, String> headers = new HashMap<>();
				headers.put("Content-Type", "application/octet-stream");
				Http.HttpDownloadResult result =
					http.download("GET", "/needs-content-type.bin", headers, null, null, null, null, null, target);

				Assertions.assertEquals("application/octet-stream", receivedContentType[0],
					"The Content-Type header set on a bodyless GET must reach the server, not be silently dropped");
				Assertions.assertEquals(200, result.getStatus());
				Assertions.assertArrayEquals(payload, Files.readAllBytes(target));
			}
			finally {
				Files.deleteIfExists(target);
			}
		}

		@Test
		@DisplayName("End-to-end: driver downloads over HTTP and commits the result as a BinaryFile config attribute")
		void driverDownloadsAndCommitsBinaryFile() throws Exception {
			byte[] payload = this.binaryPayload();
			this.serveBinary("/backup.tar.gz", payload);

			String driverJs = """
				var Info = {
					name: "HttpDownloadTestDriver",
					author: "test",
					description: "Test driver for HTTP binary download",
					version: "1.0"
				};
				var Config = {
					"backupArchive": { type: "BinaryFile", title: "Backup" },
				};
				var Device = {};
				var HTTP = { http: {} };

				function snapshot(client, device, config) {
					var http = client.create("http");
					http.download("backupArchive", "/backup.tar.gz", { storeFileName: "backup.tar.gz" });
				}
				""";
			DeviceDriver driver = new DeviceDriver(new StringReader(driverJs), "HttpDownloadTestDriver.js",
				new Location(LocationType.EMBEDDED, "HttpDownloadTestDriver.js"));
			DeviceDriver.getDrivers().put("HttpDownloadTestDriver", driver);

			TaskContext taskContext = new FakeTaskContext();
			Http fakeHttp = new Http("127.0.0.1", this.port, false, taskContext);
			DeviceHttpAccount credentials = new DeviceHttpAccount("user", "pass", "test-http-account");
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("HttpDownloadTestDriver", null, domain, "test");
			SnapshotDeviceScript script = new SnapshotDeviceScript(taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, taskContext, null);
			accessManager.forceClientForTest(fakeHttp, credentials);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, nullSession, device, accessManager);

			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			ConfigBinaryFileAttribute fileAttribute = (ConfigBinaryFileAttribute) config.getAttribute("backupArchive");
			Assertions.assertNotNull(fileAttribute, "The backupArchive attribute wasn't set");
			Assertions.assertEquals(payload.length, fileAttribute.getFileSize(), "The stored file size is incorrect");
			Assertions.assertArrayEquals(payload, Files.readAllBytes(fileAttribute.getFilePath()),
				"The stored file content doesn't match the server response");
			Assertions.assertEquals(
				".tmp.%d_cfg0_backupArchive_%s.data".formatted(device.getId(), fileAttribute.getUid()),
				fileAttribute.getFilePath().getFileName().toString(),
				"The pending file name should embed the device id, cfg0 placeholder, attribute name and uid");
		}
	}

	/**
	 * Tests for {@link ConfigBinaryFileAttribute}'s pending-to-final storage
	 * lifecycle: filenames before/after the owning config is "persisted"
	 * (simulated here via setId(), without a real database).
	 */
	@Nested
	@DisplayName("ConfigBinaryFileAttribute storage lifecycle test")
	class ConfigBinaryFileAttributeStorageTest {

		@BeforeEach
		void setUp() throws IOException {
			Path storagePath = Files.createTempDirectory("netshot-test-binary-storage-");
			Properties props = new Properties();
			props.putAll(Netshot.getConfig());
			props.setProperty("netshot.snapshots.binary.path", storagePath.toString());
			Netshot.initConfig(props);
			ConfigBinaryFileAttribute.loadConfig();
		}

		private ConfigBinaryFileAttribute newPersistedAttribute(long deviceId, long configId) {
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("HttpDownloadTestDriver", null, domain, "test");
			device.setId(deviceId);
			Config config = new Config(device);
			config.setId(configId);
			ConfigBinaryFileAttribute attribute = new ConfigBinaryFileAttribute(config, "backupArchive", "backup.tar.gz");
			attribute.setId(1); // Simulate a persisted attribute row
			return attribute;
		}

		@Test
		@DisplayName("finalizeStorage() renames the pending file to its final name once the config has a real id")
		void finalizeStorageRenamesPendingToFinal() throws IOException {
			ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
			Path pendingPath = attribute.getPendingFilePath();
			Files.write(pendingPath, "hello".getBytes());

			attribute.finalizeStorage();

			Path expectedFinalPath = pendingPath.getParent()
				.resolve("12_cfg483_backupArchive_%s.data".formatted(attribute.getUid()));
			Assertions.assertFalse(Files.exists(pendingPath), "The pending file should have been renamed away");
			Assertions.assertTrue(Files.exists(expectedFinalPath), "The final file should now exist");
			Assertions.assertEquals(expectedFinalPath, attribute.getFilePath());
		}

		@Test
		@DisplayName("finalizeStorage() is a no-op when called again after already finalizing")
		void finalizeStorageIsIdempotent() throws IOException {
			ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
			Path pendingPath = attribute.getPendingFilePath();
			Files.write(pendingPath, "hello".getBytes());

			attribute.finalizeStorage();
			Path finalPath = attribute.getFilePath();

			Assertions.assertDoesNotThrow(attribute::finalizeStorage);
			Assertions.assertEquals(finalPath, attribute.getFilePath());
			Assertions.assertTrue(Files.exists(finalPath));
		}

		@Test
		@DisplayName("getFilePath() falls back to the pending path when the file hasn't been finalized yet")
		void getFilePathFallsBackToPendingWhenNotYetFinalized() throws IOException {
			// Simulates a persisted attribute whose finalizeStorage() never ran
			// (e.g. process crash between commit() and finalizeStorage()), or
			// whose rename attempt failed - either way, nothing exists yet at
			// the final path, only at the pending one.
			ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
			Path pendingPath = attribute.getPendingFilePath();
			Files.write(pendingPath, "hello".getBytes());

			Assertions.assertEquals(pendingPath, attribute.getFilePath(),
				"getFilePath() should resolve to the pending path when the final one doesn't exist yet");
		}

		@Test
		@DisplayName("getFilePath() falls back to the legacy <uid>.data name for a pre-feature row")
		void getFilePathFallsBackToLegacyName() throws IOException {
			ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
			Path legacyPath = attribute.getPendingFilePath().getParent()
				.resolve("%s.data".formatted(attribute.getUid()));
			Files.write(legacyPath, "hello".getBytes());

			Assertions.assertEquals(legacyPath, attribute.getFilePath(),
				"With neither the final nor pending file present, getFilePath() should fall back to the legacy name");
		}
	}

	/**
	 * Tests for the {@code Options} descriptor block: per-device,
	 * user-configurable settings a driver declares (as opposed to the
	 * driver-collected {@code Config}/{@code Device} attributes).
	 */
	@Nested
	@DisplayName("Options block test")
	class OptionsBlockTest {

		private static final String OPTIONS_DRIVER_JS = """
			var Info = {
				name: "OptionsTestDriver",
				author: "test",
				description: "test",
				version: "1.0"
			};

			var Config = {};
			var Device = {};
			var CLI = { ssh: {} };

			var Options = {
				fullBackup: {
					type: "Boolean",
					title: "Force full backup",
					default: true,
				},
				backupMode: {
					type: "Text",
					title: "Backup mode",
					choices: ["running-config", "startup-config", "both"],
					default: "running-config",
				},
				comment: {
					type: "Text",
					title: "Comment",
				},
			};

			function snapshot(client, device, config) {
			}
			""";

		private static final String NO_OPTIONS_DRIVER_JS = """
			var Info = {
				name: "NoOptionsTestDriver",
				author: "test",
				description: "test",
				version: "1.0"
			};

			var Config = {};
			var Device = {};
			var CLI = { ssh: {} };

			function snapshot(client, device, config) {
			}
			""";

		private static String badOptionsDriverJs(String optionsBlock) {
			return """
				var Info = {
					name: "BadOptionsTestDriver",
					author: "test",
					description: "test",
					version: "1.0"
				};

				var Config = {};
				var Device = {};
				var CLI = { ssh: {} };

				var Options = %s;

				function snapshot(client, device, config) {
				}
				""".formatted(optionsBlock);
		}

		private DeviceDriver buildDriver(String js, String name) throws Exception {
			return new DeviceDriver(new StringReader(js), name,
				new Location(LocationType.EMBEDDED, name));
		}

		@Test
		@DisplayName("Text, choices-restricted and Boolean options are parsed with their title/choices/default")
		void parsesAllOptionTypes() throws Exception {
			DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");

			OptionDefinition fullBackup = driver.getOptions().get("fullBackup");
			Assertions.assertNotNull(fullBackup, "The 'fullBackup' option should exist");
			Assertions.assertEquals(DriverValueType.BOOLEAN, fullBackup.getType());
			Assertions.assertEquals("Force full backup", fullBackup.getTitle());
			Assertions.assertEquals(Boolean.TRUE, fullBackup.getDefaultValue(),
				"Boolean option default should be a real boolean, not a string");

			OptionDefinition backupMode = driver.getOptions().get("backupMode");
			Assertions.assertNotNull(backupMode, "The 'backupMode' option should exist");
			Assertions.assertEquals(DriverValueType.TEXT, backupMode.getType());
			Assertions.assertEquals(
				List.of("running-config", "startup-config", "both"), backupMode.getChoices());
			Assertions.assertEquals("running-config", backupMode.getDefaultValue());

			OptionDefinition comment = driver.getOptions().get("comment");
			Assertions.assertNotNull(comment, "The 'comment' option should exist");
			Assertions.assertEquals(DriverValueType.TEXT, comment.getType());
			Assertions.assertNull(comment.getDefaultValue(), "No default was declared for 'comment'");
		}

		@Test
		@DisplayName("A driver with no Options block simply has no declared options")
		void missingOptionsBlockYieldsNoOptions() throws Exception {
			DeviceDriver driver = this.buildDriver(NO_OPTIONS_DRIVER_JS, "NoOptionsTestDriver.js");
			Assertions.assertTrue(driver.getOptions().isEmpty());
		}

		@Test
		@DisplayName("A Boolean option with 'choices' is rejected")
		void booleanOptionWithChoicesIsRejected() {
			String js = badOptionsDriverJs(
				"{ mode: { type: \"Boolean\", title: \"Mode\", choices: [\"a\", \"b\"] } }");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
		}

		@Test
		@DisplayName("A Text option whose default is not one of its choices is rejected")
		void textOptionWithInvalidDefaultIsRejected() {
			String js = badOptionsDriverJs(
				"{ mode: { type: \"Text\", title: \"Mode\", choices: [\"a\", \"b\"], default: \"c\" } }");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
		}

		@Test
		@DisplayName("An option with an unknown type is rejected")
		void unknownOptionTypeIsRejected() {
			String js = badOptionsDriverJs(
				"{ mode: { type: \"Enum\", title: \"Mode\" } }");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
		}

		@Test
		@DisplayName("An option with an invalid title is rejected")
		void invalidOptionTitleIsRejected() {
			String js = badOptionsDriverJs(
				"{ mode: { type: \"Text\", title: \"!\" } }");
			Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.buildDriver(js, "BadOptionsTestDriver.js"));
		}

		/**
		 * Registers a driver directly into {@link DeviceDriver}'s private static
		 * registry (there's no public API for this - drivers are normally only
		 * discovered from disk by {@link DeviceDriver#refreshDrivers()}), so that
		 * {@link Device#getDeviceDriver()} can resolve it by name like any other
		 * driver. Callers must remove it again with {@link #unregisterDriver}.
		 */
		@SuppressWarnings("unchecked")
		private void registerDriver(DeviceDriver driver) throws Exception {
			java.lang.reflect.Field field = DeviceDriver.class.getDeclaredField("drivers");
			field.setAccessible(true);
			((Map<String, DeviceDriver>) field.get(null)).put(driver.getName(), driver);
		}

		@SuppressWarnings("unchecked")
		private void unregisterDriver(String name) throws Exception {
			java.lang.reflect.Field field = DeviceDriver.class.getDeclaredField("drivers");
			field.setAccessible(true);
			((Map<String, DeviceDriver>) field.get(null)).remove(name);
		}

		@Test
		@DisplayName("A Boolean option is exposed at runtime as a real JS boolean, not the string \"true\"/\"false\"")
		void booleanOptionIsExposedAsRealBoolean() throws Exception {
			// A non-empty string is truthy in JS, so if this leaked a stringified
			// "false", a driver's natural `if (device.options.x)` check would
			// always be true - this locks in that Device#getOptions() (and the
			// JSON column behind it) stores real typed values, not strings.
			DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");
			this.registerDriver(driver);
			try {
				Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
				Device device = new Device("OptionsTestDriver", null, domain, "test");

				JsDeviceHelper helper = new JsDeviceHelper(device, null, null, new FakeTaskContext(), false);
				ProxyObject options = (ProxyObject) helper.getOptions();
				Assertions.assertEquals(Boolean.TRUE, options.getMember("fullBackup"),
					"Default Boolean option value should be a real boolean, not a string");
				Assertions.assertEquals("running-config", options.getMember("backupMode"),
					"Text option value should remain a plain string");

				device.setOptions(Map.of("fullBackup", Boolean.FALSE));
				ProxyObject updatedOptions = (ProxyObject) helper.getOptions();
				Assertions.assertEquals(Boolean.FALSE, updatedOptions.getMember("fullBackup"),
					"An explicitly stored false value should be exposed as boolean false");
			}
			finally {
				this.unregisterDriver("OptionsTestDriver");
			}
		}

		@Test
		@DisplayName("A snapshot in full-debug mode dumps the resolved option values to the debug log")
		void snapshotDumpsOptionValuesInDebugLog() throws Exception {
			DeviceDriver driver = this.buildDriver(OPTIONS_DRIVER_JS, "OptionsTestDriver.js");
			this.registerDriver(driver);
			try {
				Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
				Device device = new Device("OptionsTestDriver", null, domain, "test");
				FakeTaskContext taskContext = new FakeTaskContext();
				SnapshotDeviceScript script = new SnapshotDeviceScript(taskContext);
				Session nullSession = null;
				AccessManager accessManager = new AccessManager(nullSession, device, null, taskContext, null);
				Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
					Device.class, AccessManager.class);
				runMethod.setAccessible(true);
				runMethod.invoke(script, nullSession, device, accessManager);

				String log = taskContext.getLog();
				Assertions.assertTrue(log.contains("options") && log.contains("fullBackup=true"),
					"Debug log should contain the resolved option values (with defaults applied): " + log);
			}
			finally {
				this.unregisterDriver("OptionsTestDriver");
			}
		}

	}

}
