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
package net.netshot.netshot.devicescript;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriver.Location;
import net.netshot.netshot.driver.DeviceDriver.LocationType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.BinaryFileStorage;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Tests the snapshot script with inline drivers: what the driver writes on the device ({@code device.set},
 * {@code device.add}, {@code device.reset}), the text files it downloads from the device, the configuration dump
 * and the detection of an unchanged configuration.
 */
@ScriptEngineTest
@DisplayName("Snapshot script")
class SnapshotDeviceScriptTest {

	@TempDir
	Path tempDir;

	private FakeTaskContext taskContext;

	private SshServer deviceServer;

	private Ssh ssh;

	@BeforeEach
	void setUp() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("netshot.snapshots.binary.path", Files.createDirectories(this.tempDir.resolve("binary")).toString());
		TestConfig.init(properties);
		this.taskContext = new FakeTaskContext();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (this.ssh != null) {
			this.ssh.disconnect();
		}
		if (this.deviceServer != null) {
			this.deviceServer.stop(true);
		}
		TestConfig.init(new Properties());
	}

	/**
	 * Registers a driver with the given snapshot function body: device attributes of each type, and a dumped
	 * configuration attribute.
	 *
	 * @param name the driver name
	 * @param snapshotBody the body of the snapshot function
	 * @throws Exception if the driver can't be loaded
	 */
	private static void register(String name, String snapshotBody) throws Exception {
		String driverJs = """
			var Info = { name: "%s", author: "test", description: "Test driver of the snapshot script", version: "1.0" };
			var Config = {
				"configuration": { type: "LongText", title: "Configuration",
					dump: { pre: "! Start", preLine: "> ", postLine: " <", post: "! End" } },
				"version": { type: "Text", title: "Version" },
			};
			var Device = {
				"memory": { type: "Numeric", title: "Memory" },
				"saved": { type: "Binary", title: "Saved" },
				"notes": { type: "LongText", title: "Notes" },
				"label": { type: "Text", title: "Label" },
			};
			var CLI = { ssh: {} };
			function snapshot(cli, device, config) {
			%s
			}
			""".formatted(name, snapshotBody);
		TestDrivers.registry().getDrivers().put(name,
			new DeviceDriver(new StringReader(driverJs), name + ".js", new Location(LocationType.EMBEDDED, name + ".js")));
	}

	private static DeviceDraft device(String driverName) {
		Domain domain = Domain.builder().name("Lab").description("Lab domain").build();
		return new DeviceDraft(Device.create(driverName, "127.0.0.1", domain, "test").toBuilder().name("router1")
			.family("Old family").vrfInstances(Set.of("OLD")).networkClass(NetworkClass.ROUTER)
			.modules(List.of(Module.builder().slot("0").partNumber("OLD-PN").serialNumber("OLD-SN").build())).build());
	}

	/**
	 * Runs the snapshot of the device, the SSH access going to the test SSH server when started.
	 *
	 * @param device the device
	 * @param dumpFolder the folder to dump the configuration into, or null
	 * @throws Exception if the snapshot fails
	 */
	private void snapshot(DeviceDraft device, String dumpFolder) throws Exception {
		AccessManager accessManager = new AccessManager(TestDrivers.registry(), TestConnectors.local(), null, device, null, this.taskContext, null);
		if (this.ssh != null) {
			accessManager.forceClientForTest(this.ssh, DeviceSshAccount.builder().username(Secret.local("admin"))
				.password(Secret.local("admin")).name("test-ssh-account").build());
		}
		SnapshotDeviceScript script = new SnapshotDeviceScript(TestDrivers.registry(), TestConnectors.local(), null, this.taskContext,
			dumpFolder, new BinaryFileStorage(TestConfig.store()));
		Method run = SnapshotDeviceScript.class.getDeclaredMethod("run", DeviceDraft.class, AccessManager.class);
		run.setAccessible(true);
		try {
			run.invoke(script, device, accessManager);
		}
		catch (InvocationTargetException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw e;
		}
	}

	/**
	 * Starts an SSH server (the device) serving a folder by SCP and SFTP, and connects to it.
	 *
	 * @param folder the folder served
	 * @throws Exception if the server can't be started or reached
	 */
	private void startDevice(Path folder) throws Exception {
		this.deviceServer = SshServer.setUpDefaultServer();
		this.deviceServer.setHost("127.0.0.1");
		this.deviceServer.setPort(0);
		this.deviceServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
		this.deviceServer.setPasswordAuthenticator((_, _, _) -> true);
		this.deviceServer.setCommandFactory(new ScpCommandFactory());
		this.deviceServer.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
		this.deviceServer.setFileSystemFactory(new VirtualFileSystemFactory(folder));
		this.deviceServer.start();
		this.ssh = new Ssh(TestConnectors.sshClient(), Ssh.Settings.from(TestConfig.store().current()), "127.0.0.1",
			this.deviceServer.getPort(), "admin", "admin", this.taskContext);
		this.ssh.setConnectionTimeout(5000);
		this.ssh.connect(false);
	}

	@Test
	@DisplayName("Sets the inventory and the device attributes of each type")
	void inventoryAndAttributes() throws Exception {
		register("SnapshotInventoryDriver", """
			device.set("name", "edge1");
			device.set("family", "Edge routers");
			device.set("location", "Lab");
			device.set("contact", "NOC");
			device.set("softwareVersion", "9.1");
			device.set("serialNumber", "SN-1");
			device.set("networkClass", "SWITCH");
			device.set("networkClass", "NOT_A_CLASS");
			device.set("memory", 512);
			device.set("saved", true);
			device.set("notes", "line 1\\nline 2");
			device.set("label", "L1");
			device.set("notAnAttribute", "ignored");
			device.set("memory", "not numeric");
			config.set("version", "9.1");
			""");
		DeviceDraft device = device("SnapshotInventoryDriver");
		this.snapshot(device, null);

		Device result = device.toDevice();
		Assertions.assertEquals("edge1", result.name());
		Assertions.assertEquals("Edge routers", result.family());
		Assertions.assertEquals("Lab", result.location());
		Assertions.assertEquals("NOC", result.contact());
		Assertions.assertEquals("9.1", result.softwareVersion());
		Assertions.assertEquals("SN-1", result.serialNumber());
		Assertions.assertEquals(NetworkClass.SWITCH, result.networkClass(), "The invalid class is ignored");
		Assertions.assertEquals(512.0, device.getAttribute("memory").getData());
		Assertions.assertEquals(true, device.getAttribute("saved").getData());
		Assertions.assertEquals("line 1\nline 2", device.getAttribute("notes").getData());
		Assertions.assertEquals("L1", device.getAttribute("label").getData());
		Assertions.assertNull(device.getAttribute("notAnAttribute"));
		Assertions.assertEquals(4, device.getAttributes().size());
		Assertions.assertTrue(this.taskContext.getLog().contains("Can't add device attribute networkClass"), this.taskContext.getLog());
		Assertions.assertEquals("9.1", device.getNewConfig().getAttribute("version").getAsText());
	}

	@Test
	@DisplayName("Adds the modules, interfaces with their addresses, VRFs and virtual devices")
	void collections() throws Exception {
		register("SnapshotCollectionsDriver", """
			device.add("module", { slot: "1", partNumber: "PN-1", serialNumber: "SN-1" });
			device.add("networkInterface", {
				name: "eth0", vrf: "MGMT", virtualDevice: "ctx1", description: "Uplink", mac: "0011.2233.4455",
				enabled: false, level3: true,
				ip: [
					{ ip: "10.1.1.1", mask: 24, usage: "PRIMARY" },
					{ ip: "10.1.2.1", mask: "255.255.255.0", usage: "SECONDARY" },
					{ ipv6: "2001:db8::1", mask: 64, usage: "PRIMARY" },
				],
			});
			device.add("networkInterface", { name: "eth1" });
			device.add("networkInterface", { name: "bad", ip: [{ ip: "999.1.1.1", mask: 24 }] });
			device.add("vrf", "MGMT");
			device.add("virtualDevice", "ctx1");
			device.add("tag", "snapshot");
			device.remove("vrf", "MGMT");
			device.remove("tag", "snapshot");
			""");
		DeviceDraft device = device("SnapshotCollectionsDriver");
		this.snapshot(device, null);

		Assertions.assertTrue(device.getModules().stream().anyMatch(m -> "PN-1".equals(m.partNumber()) && "SN-1".equals(m.serialNumber())));
		List<NetworkInterface> interfaces = device.getNetworkInterfaces();
		Assertions.assertEquals(List.of("eth0", "eth1"), interfaces.stream().map(NetworkInterface::interfaceName).toList());
		NetworkInterface eth0 = interfaces.get(0);
		Assertions.assertEquals("MGMT", eth0.vrfInstance());
		Assertions.assertEquals("ctx1", eth0.virtualDevice());
		Assertions.assertEquals("Uplink", eth0.description());
		Assertions.assertFalse(eth0.enabled());
		Assertions.assertEquals("0011.2233.4455", eth0.getMacAddress());
		Assertions.assertEquals(List.of("10.1.1.1/24 PRIMARY", "10.1.2.1/24 SECONDARY"),
			eth0.ip4Addresses().stream().map(a -> a.getIp() + "/" + a.getPrefixLength() + " " + a.getAddressUsage()).toList());
		Assertions.assertEquals(1, eth0.ip6Addresses().size());
		Assertions.assertEquals(64, eth0.ip6Addresses().iterator().next().getPrefixLength());
		NetworkInterface eth1 = interfaces.get(1);
		Assertions.assertTrue(eth1.enabled() && eth1.level3(), "Enabled level 3 by default");
		Assertions.assertTrue(device.getVrfInstances().contains("MGMT"), "VRFs can't be removed");
		Assertions.assertEquals(Set.of("ctx1"), device.getVirtualDevices());
		Assertions.assertEquals(Set.of(), device.getTags());
		String log = this.taskContext.getLog();
		Assertions.assertTrue(log.contains("Can't add device attribute networkInterface"), log);
		Assertions.assertTrue(log.contains("Removing key vrf is not supported"), log);
	}

	@Test
	@DisplayName("Each snapshot starts from a reset inventory")
	void reset() throws Exception {
		register("SnapshotResetDriver", """
			device.add("vrf", "NEW");
			""");
		DeviceDraft device = device("SnapshotResetDriver");
		this.snapshot(device, null);

		Device result = device.toDevice();
		Assertions.assertEquals("", result.family());
		Assertions.assertEquals(NetworkClass.UNKNOWN, result.networkClass());
		Assertions.assertEquals(Set.of("NEW"), result.vrfInstances());
		Assertions.assertEquals("router1", result.name(), "The name is kept");
	}

	@Test
	@DisplayName("Downloads text files from the device, by SFTP or SCP, in the given charset")
	void textDownload() throws Exception {
		Path folder = Files.createDirectories(this.tempDir.resolve("device"));
		Files.writeString(folder.resolve("config.txt"), "hostname caf\u00e9\n", StandardCharsets.ISO_8859_1);
		this.startDevice(folder);
		register("SnapshotDownloadDriver", """
			const sftp = device.textDownload("config.txt", { method: "sftp", charset: "ISO-8859-1" });
			const scp = device.textDownload("config.txt", { method: "scp", charset: "ISO-8859-1", newSession: true });
			const errors = [];
			for (const download of [
				() => device.textDownload("config.txt", { method: "sftp", charset: "NOT-A-CHARSET" }),
				() => device.textDownload("config.txt", { method: "ftp" }),
				() => device.textDownload("config.txt"),
			]) {
				try {
					download();
				}
				catch (e) {
					errors.push(String(e).length > 0);
				}
			}
			config.set("configuration", sftp + scp + errors.join(","));
			""");
		DeviceDraft device = device("SnapshotDownloadDriver");
		this.snapshot(device, null);

		Assertions.assertEquals("hostname caf\u00e9\nhostname caf\u00e9\ntrue,true,true",
			device.getNewConfig().getAttribute("configuration").getAsText());
		Assertions.assertTrue(this.taskContext.getLog().contains("Invalid charset name 'NOT-A-CHARSET'"), this.taskContext.getLog());
	}

	@Test
	@DisplayName("Dumps the configuration into the dump folder, with the decorations of the driver")
	void dump() throws Exception {
		register("SnapshotDumpDriver", """
			config.set("configuration", "line 1\\nline 2");
			config.set("version", "9.1");
			""");
		Path dumpFolder = Files.createDirectories(this.tempDir.resolve("dump"));
		DeviceDraft device = device("SnapshotDumpDriver");
		this.snapshot(device, dumpFolder.toString());

		Assertions.assertEquals("! Start\r\n> line 1 <\r\n> line 2 <\r\n! End\r\n",
			Files.readString(dumpFolder.resolve("router1")));
		Assertions.assertTrue(this.taskContext.getLog().contains("The configuration has been saved as a file in the dump folder."));
	}

	@Test
	@DisplayName("Dumps the attributes in the order the driver declares them")
	void dumpOrder() throws Exception {
		String driverJs = """
			var Info = { name: "SnapshotDumpOrderDriver", author: "test", description: "Test driver of the dump order", version: "1.0" };
			var Config = {
				"zulu": { type: "Text", title: "Zulu", dump: { pre: "[zulu]" } },
				"alpha": { type: "Text", title: "Alpha", dump: { pre: "[alpha]" } },
				"mike": { type: "Text", title: "Mike", dump: { pre: "[mike]" } },
				"bravo": { type: "Text", title: "Bravo", dump: { pre: "[bravo]" } },
				"yankee": { type: "Text", title: "Yankee", dump: { pre: "[yankee]" } },
				"charlie": { type: "Text", title: "Charlie", dump: { pre: "[charlie]" } },
			};
			var Device = {};
			var CLI = { ssh: {} };
			function snapshot(cli, device, config) {
				for (const name of ["alpha", "bravo", "charlie", "mike", "yankee", "zulu"]) {
					config.set(name, name + "\\n");
				}
			}
			""";
		TestDrivers.registry().getDrivers().put("SnapshotDumpOrderDriver", new DeviceDriver(new StringReader(driverJs),
			"SnapshotDumpOrderDriver.js", new Location(LocationType.EMBEDDED, "SnapshotDumpOrderDriver.js")));
		Path dumpFolder = Files.createDirectories(this.tempDir.resolve("dump-order"));
		this.snapshot(device("SnapshotDumpOrderDriver"), dumpFolder.toString());

		Assertions.assertEquals("[zulu]\r\nzulu\n[alpha]\r\nalpha\n[mike]\r\nmike\n[bravo]\r\nbravo\n[yankee]\r\nyankee\n[charlie]\r\ncharlie\n",
			Files.readString(dumpFolder.resolve("router1")));
	}

	@Test
	@DisplayName("Doesn't store a new configuration when nothing changed")
	void unchangedConfiguration() throws Exception {
		register("SnapshotUnchangedDriver", """
			config.set("configuration", "same");
			""");
		DeviceDraft first = device("SnapshotUnchangedDriver");
		this.snapshot(first, null);
		Config lastConfig = first.getNewConfig();
		Assertions.assertNotNull(lastConfig);

		DeviceDraft second = device("SnapshotUnchangedDriver");
		second.setLastConfig(lastConfig);
		this.snapshot(second, null);
		Assertions.assertNull(second.getNewConfig());
		Assertions.assertTrue(this.taskContext.getLog().contains("The configuration hasn't changed."), this.taskContext.getLog());
	}

	@Test
	@DisplayName("Reports a script error of the driver")
	void scriptError() throws Exception {
		register("SnapshotErrorDriver", """
			throw "Unexpected output";
			""");
		Assertions.assertThrows(Exception.class, () -> this.snapshot(device("SnapshotErrorDriver"), null));
		Assertions.assertTrue(this.taskContext.getLog().contains("Error while running snapshot using driver SnapshotErrorDriver"),
			this.taskContext.getLog());
	}
}
