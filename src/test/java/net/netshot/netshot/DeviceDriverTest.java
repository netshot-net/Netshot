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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.proxy.ProxyObject;
import org.hibernate.Session;

import com.sun.net.httpserver.HttpServer;

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
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.Cli;
import net.netshot.netshot.device.access.Http;
import net.netshot.netshot.device.access.Http.AuthScheme;
import net.netshot.netshot.device.access.Snmp;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.device.attribute.ConfigTextAttribute;
import net.netshot.netshot.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.device.attribute.OptionDefinition;
import net.netshot.netshot.device.script.helper.JsDeviceHelper;
import net.netshot.netshot.device.credentials.DeviceCliAccount;
import net.netshot.netshot.device.credentials.DeviceHttpAccount;
import net.netshot.netshot.device.credentials.DeviceSnmpCommunity;
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

public class DeviceDriverTest {

	@BeforeAll
	static void initNetshot() throws Exception {
		Netshot.readConfig();
		Ssh.loadConfig();
		DeviceDriver.refreshDrivers();
	}

	/**
	 * CLI class to mock a connection to a device.
	 */
	private abstract static class FakeCli extends Cli {

		protected PrintStream srvOutStream;
		protected DeviceCliAccount cliAccount;

		public FakeCli(String host, DeviceCliAccount cliAccount, TaskContext taskContext) throws IOException {
			super(host, taskContext);
			PipedOutputStream pipedStream = new PipedOutputStream();
			this.srvOutStream = new PrintStream(pipedStream);
			this.inStream = new PipedInputStream(pipedStream, 16384);
			this.cliAccount = cliAccount;
		}

		@Override
		public void connect() throws IOException {
			// Nothing
		}

		@Override
		public void disconnect() {
			// Nothing
		}
	}

	/**
	 * Fake CiscoIOS12 CLI.
	 */
	public static class CiscoIOS12FakeCli extends FakeCli {
		public enum CliMode {
			INIT,
			DISABLE,
			ENABLE,
			ENABLE_PASSWORD,
		}

		private CliMode cliMode = CliMode.INIT;
		private final String hostname = "router1";
		private final Pattern commandPattern = Pattern.compile("(.+)\r");

		public CiscoIOS12FakeCli(String host, DeviceCliAccount cliAccount, TaskContext taskContext) throws IOException {
			super(host, cliAccount, taskContext);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case DISABLE:
					this.srvOutStream.printf("%s>", this.hostname);
					break;
				case ENABLE:
					this.srvOutStream.printf("%s#", this.hostname);
					break;
				default:
					break;
			}
		}

		protected void cmdShowVersion() {
			this.srvOutStream.print(
				"Cisco IOS XE Software, Version 03.16.07b.S - Extended Support Release\r\n"
					+ "Cisco IOS Software, CSR1000V Software (X86_64_LINUX_IOSD-UNIVERSALK9-M), Version 15.5(3)S7b, RELEASE SOFTWARE (fc1)\r\n"
					+ "Technical Support: http://www.cisco.com/techsupport\r\n"
					+ "Copyright (c) 1986-2018 by Cisco Systems, Inc.\r\n"
					+ "Compiled Fri 02-Mar-18 08:11 by mcpre\r\n"
					+ "\r\n"
					+ "\r\n"
					+ "ROM: IOS-XE ROMMON\r\n"
					+ "\r\n"
					+ this.hostname + " uptime is 18 weeks, 1 day, 43 minutes\r\n"
					+ "Uptime for this control processor is 18 weeks, 1 day, 45 minutes\r\n"
					+ "System returned to ROM by reload at 18:12:05 UTC Sun Feb 21 2021\r\n"
					+ "System image file is \"bootflash:packages.conf\"\r\n"
					+ "Last reload reason: <NULL>\r\n"
					+ "\r\n"
					+ "License Level: ax\r\n"
					+ "License Type: Default. No valid license found.\r\n"
					+ "Next reload license Level: ax\r\n"
					+ "\r\n"
					+ "cisco CSR1000V (VXE) processor (revision VXE) with 1090048K/6147K bytes of memory.\r\n"
					+ "Processor board ID 90PIQM03HLS\r\n"
					+ "4 Gigabit Ethernet interfaces\r\n"
					+ "32768K bytes of non-volatile configuration memory.\r\n"
					+ "3022136K bytes of physical memory.\r\n"
					+ "7774207K bytes of virtual hard disk at bootflash:.\r\n"
					+ "\r\n"
					+ "Configuration register is 0x2102\r\n"
			);
		}

		protected void cmdShowStartupHead() {
			this.srvOutStream.print(
				"!\r\n"
					+ "! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin\r\n"
					+ "!\r\n"
			);
		}

		protected void cmdShowRunning() {
			this.srvOutStream.print(
				"!\r\n"
					+ "! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin\r\n"
					+ "!\r\n"
					+ "version 15.5\r\n"
					+ "service timestamps debug datetime msec\r\n"
					+ "service timestamps log datetime msec\r\n"
					+ "!\r\n"
					+ "hostname router1\r\n"
					+ "!\r\n"
					+ "boot-start-marker\r\n"
					+ "boot-end-marker\r\n"
					+ "!\r\n"
					+ "no aaa new-model\r\n"
					+ "!\r\n"
					+ "no ip domain lookup\r\n"
					+ "!\r\n"
					+ "interface Loopback0\r\n"
					+ " ip address 10.255.0.1 255.255.255.255\r\n"
					+ "!\r\n"
					+ "interface GigabitEthernet1\r\n"
					+ " description Management\r\n"
					+ " ip address 192.168.200.101 255.255.255.0\r\n"
					+ " negotiation auto\r\n"
					+ "!\r\n"
					+ "interface GigabitEthernet2\r\n"
					+ " ip address 10.0.0.1 255.255.255.254\r\n"
					+ " ip ospf network point-to-point\r\n"
					+ " negotiation auto\r\n"
					+ "!\r\n"
					+ "interface GigabitEthernet3\r\n"
					+ " no ip address\r\n"
					+ " shutdown\r\n"
					+ " negotiation auto\r\n"
					+ "!\r\n"
					+ "interface GigabitEthernet4\r\n"
					+ " no ip address\r\n"
					+ " shutdown\r\n"
					+ " negotiation auto\r\n"
					+ "!\r\n"
					+ "router ospf 1\r\n"
					+ " network 10.0.0.0 0.0.0.1 area 0\r\n"
					+ " network 10.255.0.1 0.0.0.0 area 0\r\n"
					+ "!\r\n"
					+ "ip forward-protocol nd\r\n"
					+ "!\r\n"
					+ "no ip http server\r\n"
					+ "no ip http secure-server\r\n"
					+ "ip ssh version 2\r\n"
					+ "!\r\n"
					+ "access-list 98 permit 192.168.200.0 0.0.0.255\r\n"
					+ "!\r\n"
					+ "snmp-server community cisco RO 98\r\n"
					+ "snmp-server location SNMPLOCATION\r\n"
					+ "snmp-server contact SNMPCONTACT\r\n"
					+ "snmp-server enable traps config\r\n"
					+ "!\r\n"
					+ "!\r\n"
					+ "control-plane\r\n"
					+ "!\r\n"
					+ "!\r\n"
					+ "line con 0\r\n"
					+ " stopbits 1\r\n"
					+ "line vty 0 4\r\n"
					+ " login local\r\n"
					+ " transport input telnet ssh\r\n"
					+ "line vty 5 15\r\n"
					+ " login local\r\n"
					+ " transport input telnet ssh\r\n"
					+ "!\r\n"
					+ "ntp server pool.ntp.org\r\n"
					+ "!\r\n"
					+ "end\r\n"
			);
		}

		protected void cmdShowInventory() {
			this.srvOutStream.print(
				"NAME: \"Chassis\", DESCR: \"Cisco CSR1000V Chassis\"\r\n"
					+ "PID: CSR1000V          , VID: V00, SN: 96NETS96HOT\r\n"
					+ "\r\n"
					+ "NAME: \"module R0\", DESCR: \"Cisco CSR1000V Route Processor\"\r\n"
					+ "PID: CSR1000V          , VID: V00, SN: JAB1616161C\r\n"
					+ "\r\n"
					+ "NAME: \"module F0\", DESCR: \"Cisco CSR1000V Embedded Services Processor\"\r\n"
					+ "PID: CSR1000V          , VID:    , SN:\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet1() {
			this.srvOutStream.print(
				"GigabitEthernet1 is up, line protocol is up\r\n"
					+ "Hardware is CSR vNIC, address is 5000.0001.0000 (bia 5000.0001.0000)\r\n"
					+ "Internet address is 192.168.200.101/24\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet2() {
			this.srvOutStream.print(
				"GigabitEthernet2 is up, line protocol is up\r\n"
					+ "Hardware is CSR vNIC, address is 5000.0001.0001 (bia 5000.0001.0001)\r\n"
					+ "Internet address is 10.0.0.1/31\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet3() {
			this.srvOutStream.print(
				"GigabitEthernet3 is administratively down, line protocol is down\r\n"
					+ "Hardware is CSR vNIC, address is 5000.0001.0002 (bia 5000.0001.0002)\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet4() {
			this.srvOutStream.print(
				"GigabitEthernet4 is administratively down, line protocol is down\r\n"
					+ "Hardware is CSR vNIC, address is 5000.0001.0003 (bia 5000.0001.0003)\r\n"
			);
		}

		protected void cmdShowInterfaceLoopback0() {
			this.srvOutStream.print(
				"Loopback0 is up, line protocol is up\r\n"
					+ "Internet address is 10.255.0.1/32\r\n"
			);
		}


		@Override
		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.DISABLE;
				this.printPrompt();
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				// Echo command back
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.ENABLE)
					&& ("show running-config".equals(command) || "show startup-config".equals(command))) {
					this.cmdShowRunning();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) && "show startup-config | i ^! .*".equals(command)) {
					this.cmdShowStartupHead();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) && "show inventory".equals(command)) {
					this.cmdShowInventory();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "show version".equals(command)) {
					this.cmdShowVersion();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "terminal length 0".equals(command)) {
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& command.startsWith("show interface")) {
					if (command.startsWith("show interface GigabitEthernet1 ")) {
						this.cmdShowInterfaceGigabitEthernet1();
					}
					else if (command.startsWith("show interface GigabitEthernet2 ")) {
						this.cmdShowInterfaceGigabitEthernet2();
					}
					else if (command.startsWith("show interface GigabitEthernet3 ")) {
						this.cmdShowInterfaceGigabitEthernet3();
					}
					else if (command.startsWith("show interface GigabitEthernet4 ")) {
						this.cmdShowInterfaceGigabitEthernet4();
					}
					else if (command.startsWith("show interface Loopback0 ")) {
						this.cmdShowInterfaceLoopback0();
					}
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.DISABLE) && "enable".equals(command)) {
					this.cliMode = CliMode.ENABLE_PASSWORD;
					this.srvOutStream.printf("Password: ");
				}
				else if (this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE)) {
					this.srvOutStream.print(
						"% Unknown command or computer name, or unable to find computer address\n");
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE_PASSWORD)) {
					if (this.cliAccount.getSuperPassword().equals(command)) {
						this.cliMode = CliMode.ENABLE;
					}
					else {
						this.srvOutStream.print("% Bad secrets\n");
						this.cliMode = CliMode.DISABLE;
					}
					this.printPrompt();
				}
			}
		}
	}

	@Nested
	@DisplayName("CiscoIOS12 driver test")
	class CiscoIOS12Test {

		TaskContext taskContext = new FakeTaskContext();

		@Test
		@DisplayName("CiscoIOS12 Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new CiscoIOS12FakeCli(null, credentials, taskContext);
			Session nullSession = null;
			Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, null);
			accessManager.forceClientForTest(fakeCli, credentials);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
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
			Assertions.assertTrue(device.getModules().get(0).isRemoved(), "The first module is not set as removed");
			Assertions.assertEquals("96NETS96HOT",
				device.getModules().get(2).getSerialNumber(), "The first module serial number is incorrect");
			Assertions.assertEquals(Network4Address.getNetworkAddress("192.168.200.101", 24),
				device.getNetworkInterface("GigabitEthernet1").getIp4Addresses().iterator().next(),
				"The first interface IP address is incorrect");
		}
	}

	/**
	 * Fake ZPE Node Grid CLI.
	 */
	public static class ZPENodeGridFakeCli extends FakeCli {
		public enum CliMode {
			INIT,
			BASIC,
			SCREEN,
			PAGING,
		}

		private CliMode cliMode = CliMode.INIT;
		private final Pattern commandPattern = Pattern.compile("(.*)\r");
		private final String hostname = "NODEGRID-1";
		private final String currentPath = "/";
		private List<String> pagedLines;

		public ZPENodeGridFakeCli(String host, DeviceCliAccount cliAccount, TaskContext taskContext) throws IOException {
			super(host, cliAccount, taskContext);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case BASIC:
					this.srvOutStream.printf("[%s@%s %s]# ", this.cliAccount.getUsername(), this.hostname, this.currentPath);
					break;
				default:
					break;
			}
		}

		protected void printPaged(String text) {
			if (text != null) {
				this.pagedLines = new ArrayList<>(Arrays.asList(text.split("\n")));
			}
			int i = 0;
			StringBuffer output = new StringBuffer();
			Iterator<String> lineIt = this.pagedLines.iterator();
			while (lineIt.hasNext() && (i++ < 13)) {
				output.append(lineIt.next());
				output.append("\n");
				lineIt.remove();
			}
			this.srvOutStream.print(output);
			if (!this.pagedLines.isEmpty()) {
				this.cliMode = CliMode.PAGING;
				this.srvOutStream.print("-- more --:");
			}
			else {
				this.cliMode = CliMode.BASIC;
				this.printPrompt();
			}
		}

		protected void cmdShowSystemAbout() {
			this.printPaged(
				"system: NodeGrid Serial Console\r\n"
					+ "licenses: 16\r\n"
					+ "software: v3.1.16 (Jul 16 2016 - 16:16:16)\r\n"
					+ "cpu: Intel(R) Atom(TM) CPU E3827  @ 1.74GHz\r\n"
					+ "cpu_cores: 2\r\n"
					+ "bogomips_per_core: 3416.16\r\n"
					+ "serial_number: 1416161616\r\n"
					+ "uptime: 16 days, 16 hours, 16 minutes\r\n"
					+ "model: NSC-T16S\r\n"
					+ "part_number:  NSC-T16S-STND-DAC-F-SFP\r\n"
					+ "bios_version: 80168T00\r\n"
					+ "psu: 2\r\n"
					+ "\u0007" // Beep
			);
		}

		protected void cmdShowSystemUsageMemory() {
			this.printPaged(
				"  memory type  total (kb)  used (kb)  free (kb)\r\n"
					+ "  ===========  ==========  =========  =========\r\n"
					+ "  Mem          3934644     2224184    1710460  \r\n"
					+ "  Swap         976892      520612     456280   \r\n"
					+ "\u0007"
			);
		}

		protected void cmdShowSettingsDevices() {
			this.printPaged(
				"  * name                     connected through  type          access \r\n"
					+ "  * =======================  =================  ============  =======\r\n"
					+ "  monitoring   \r\n"
					+ "  =============\r\n"
					+ "  * AAA-AAA-AAA-AAA-AAAA    ttyS1              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * BBB-BBB-BBB-BBB-BBBB    ttyS2              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * CCC-CCC-CCC-CCC-CCCC    ttyS3              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * DDD-DDD-DDD-DDD-DDDD    ttyS4              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * EEE-EEE-EEE-EEE-EEEE    ttyS5              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * FFF-FFF-FFF-FFF-FFFF    ttyS6              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * GGG-GGG-GGG-GGG-GGGG    ttyS7              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * HHH-HHH-HHH-HHH-HHHH    ttyS8              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * III-III-III-III-IIII    ttyS9              local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * JJJ-JJJ-JJJ-JJJ-JJJJ    ttyS10             local_serial  enabled\r\n"
					+ "  not supported\r\n"
					+ "  * usbS1                   usbS1              usb_serialB   enabled\r\n"
					+ "  not supported\r\n"
			);
		}

		protected void cmdShowSettings() {
			this.srvOutStream.print(
				"/settings/system_preferences help_url=http://www.zpesystems.com/ng/v3_0/NodeGrid-UserGuide-v3_0.pdf\r\n"
					+ "/settings/system_preferences idle_timeout=1500\r\n"
					+ "/settings/system_preferences enable_banner=no\r\n"
					+ "/settings/network_connections/ETH0 ethernet_interface=eth0\r\n"
					+ "/settings/network_connections/ETH0 connect_automatically=no\r\n"
					+ "/settings/network_connections/ETH0 set_as_primary_connection=yes\r\n"
					+ "/settings/network_connections/ETH0 enable_lldp=no\r\n"
					+ "/settings/network_connections/ETH0 ipv4_mode=dhcp\r\n"
					+ "/settings/network_connections/ETH0 ipv6_mode=address_auto_configuration\r\n"
					+ "/settings/network_connections/ETH1 ethernet_interface=eth1\r\n"
					+ "/settings/network_connections/ETH1 connect_automatically=no\r\n"
					+ "/settings/network_connections/ETH1 set_as_primary_connection=no\r\n"
					+ "/settings/network_connections/ETH1 enable_lldp=no\r\n"
					+ "/settings/network_connections/ETH1 ipv4_mode=dhcp\r\n"
					+ "/settings/network_connections/ETH1 ipv6_mode=address_auto_configuration\r\n"
					+ "/settings/network_connections/bond connect_automatically=yes\r\n"
					+ "/settings/network_connections/bond set_as_primary_connection=no\r\n"
					+ "/settings/network_connections/bond enable_lldp=no\r\n"
					+ "/settings/network_connections/bond primary_interface=eth0\r\n"
					+ "/settings/network_connections/bond secondary_interface=eth1\r\n"
					+ "/settings/network_connections/bond bonding_mode=active_backup\r\n"
					+ "/settings/network_connections/bond link_monitoring=mii\r\n"
					+ "/settings/network_connections/bond monitoring_frequency=100\r\n"
					+ "/settings/network_connections/bond link_up_delay=0\r\n"
					+ "/settings/network_connections/bond link_down_delay=0\r\n"
					+ "/settings/network_connections/bond arp_validate=none\r\n"
					+ "/settings/network_connections/bond bond_mac_policy=primary_interf\r\n"
					+ "/settings/network_connections/bond ipv4_mode=static\r\n"
					+ "/settings/network_connections/bond ipv4_address=10.10.16.16\r\n"
					+ "/settings/network_connections/bond ipv4_bitmask=24\r\n"
					+ "/settings/network_connections/bond ipv4_gateway=10.10.16.254\r\n"
					+ "/settings/network_connections/bond ipv6_mode=no_ipv6_address\r\n"
					+ "/settings/snmp/system syscontact=support@zpesystems.com\r\n"
					+ "/settings/snmp/system syslocation=\"Nodegrid \"\r\n"
					+ "/settings/local_accounts/admin username=admin\r\n"
					+ "\r\n"
			);
		}

		protected void cmdEventSystemAudit() {
			this.srvOutStream.print(
				"<2022-01-03T04:01:16Z> Event ID 201: A user logged out of the system. User: alib/r/naba@10.16.2.3. Session type: HTTPS.\r\n"
					+ "<2022-01-03T11:21:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n"
					+ "<2022-01-03T11:21:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r\n"
					+ "<2022-01-03T12:23:16Z> Event ID 200: A user logged into the system. User: homer@/r/n10.16.2.3. Session type: SSH. Authentication Method: TACACS+.\r\n"
					+ "<2022-01-03T12:23:16Z> Event ID 201: A user logged out of the system. User: home/r/nr@10.16.2.3. Session type: SSH.\r\n"
					+ "<2022-01-04T01:52:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n"
					+ "<2022-01-04T01:53:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r\n"
					+ "<2022-01-04T02:52:16Z> Event ID 108: The configuration has changed. Change made by user: homer.\r\n"
					+ "<2022-01-04T03:04:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n"
			);
		}

		protected void cmdHostname() {
			this.printPaged(
				this.hostname + "\r\n"
			);
		}

		@Override
		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.BASIC;
				this.printPrompt();
			}
			else if (this.cliMode == CliMode.SCREEN && "q".equals(value)) {
				this.cliMode = CliMode.BASIC;
				this.srvOutStream.print("\r\n");
				this.printPrompt();
				return;
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.PAGING) && "".equals(command)) {
					this.printPaged(null);
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *hostname")) {
					this.cmdHostname();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?system/about/?")) {
					this.cmdShowSystemAbout();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?system/system_usage/memory_usage/?")) {
					this.cmdShowSystemUsageMemory();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?settings/devices/?")) {
					this.cmdShowSettingsDevices();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show_settings")) {
					this.cmdShowSettings();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *event_system_audit")) {
					this.cmdEventSystemAudit();
					this.srvOutStream.print("(h->Help, q->Quit)");
					this.cliMode = CliMode.SCREEN;
				}
				else {
					this.srvOutStream.printf("Error: Invalid command: %s\r\n", command);
					this.printPrompt();
				}
			}
		}

	}

	@Nested
	@DisplayName("ZPENodeGrid driver test")
	class ZPENodeGridTest {

		TaskContext taskContext = new FakeTaskContext();

		@Test
		@DisplayName("ZPENodeGrid Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new ZPENodeGridFakeCli(null, credentials, taskContext);
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("ZPENodeGrid", null, domain, "test");
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, null);
			accessManager.forceClientForTest(fakeCli, credentials);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
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
	}


	/**
	 * Fake AristaMOS CLI.
	 */
	public static class AristaMOSFakeCli extends FakeCli {
		public enum CliMode {
			INIT,
			DISABLE,
			ENABLE,
			ENABLE_PASSWORD,
		}

		private CliMode cliMode = CliMode.INIT;
		private final String hostname = "switch1";
		private final Pattern commandPattern = Pattern.compile("(.+)\r");

		public AristaMOSFakeCli(String host, DeviceCliAccount cliAccount, TaskContext taskContext) throws IOException {
			super(host, cliAccount, taskContext);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case DISABLE:
					this.srvOutStream.printf("%s>", this.hostname);
					break;
				case ENABLE:
					this.srvOutStream.printf("%s#", this.hostname);
					break;
				default:
					break;
			}
		}

		protected void cmdShowVersion() {
			this.srvOutStream.print(
				"Device: Metamako MetaConnect 48\n"
					+ "SKU: DCS-7130-48\n"
					+ "Serial number: C48-A6-21567-0\n"
					+ " \n"
					+ "Software image version: 0.31.0\n"
					+ "Internal build ID: mos-0.31+12\n"
					+ "Applications: netconf-0.9\n"
					+ " \n"
					+ "System management controller version: 1.3.2 release-platmicro-1.3+2\n"
					+ " \n"
					+ "Uptime: 177 days, 9:00:05.700000\n"
					+ " \n"
			);
		}

		protected void cmdShowRunning() {
			this.srvOutStream.print(
				"! command: show running-config\n"
					+ "! time: Tue 11 Oct 2022 09:02:39\n"
					+ "! device: switch1 (C48-A6, MOS-0.31.0)\n"
					+ " \n"
					+ "hostname switch1\n"
					+ "username admin secret sha512 $6$Iwn/TscxWEdXQVcu$yeqcWHWUt1qVmldsfPVM/O9z2hiYs/iL35WNP6zOcM.PwkGRVgTO8r3kWp3k4DpRGHYnohK/xx3gw//rxqlPo1\n"
					+ "tacacs-server host 10.18.18.1 key 7 095C4F1A0A1218000F\n"
					+ "tacacs-server host 10.18.19.1 key 7 12090404011C03162E\n"
					+ " \n"
					+ "clock timezone GB\n"
					+ "ntp server 10.18.18.12 prefer\n"
					+ "ntp server 10.18.19.12\n"
					+ " \n"
					+ "logging host 10.1.18.10\n"
					+ " \n"
					+ "interface et1\n"
					+ "    source et3\n"
					+ " \n"
					+ "interface et2\n"
					+ "    source et1\n"
					+ " \n"
					+ "interface et3\n"
					+ "    source et1\n"
					+ " \n"
					+ "interface et4\n"
					+ "    source et3\n"
					+ " \n"
					+ "interface et5\n"
					+ "    source et7\n"
					+ " \n"
					+ "interface et6\n"
					+ "    source et5\n"
					+ " \n"
					+ "interface et7\n"
					+ "    source et5\n"
					+ " \n"
					+ "interface et8\n"
					+ "    source et7\n"
					+ " \n"
					+ "interface et9\n"
					+ "    source et11\n"
					+ " \n"
					+ "interface et10\n"
					+ "    source et9\n"
					+ " \n"
					+ "interface et11\n"
					+ "    source et9\n"
					+ " \n"
					+ "interface et12\n"
					+ "    source et11\n"
					+ " \n"
					+ "interface et13\n"
					+ "    description to switch2\n"
					+ "    source et15\n"
					+ " \n"
					+ "interface et14\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et15\n"
					+ "    description to switch2\n"
					+ "    source et13\n"
					+ " \n"
					+ "interface et16\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et17\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et18\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et19\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et20\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et21\n"
					+ "    description to switch3\n"
					+ "    source et23\n"
					+ " \n"
					+ "interface et22\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et23\n"
					+ "    description to switch4\n"
					+ "    negotiation\n"
					+ "    source et21\n"
					+ " \n"
					+ "interface et24\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et25\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et26\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et27\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et28\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et29\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et30\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et31\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et32\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et33\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et34\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et35\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et36\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et37\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et38\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et39\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et40\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et41\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et42\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et43\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et44\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et45\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et46\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et47\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface et48\n"
					+ "    shutdown\n"
					+ " \n"
					+ "interface ma1\n"
					+ "    ip address 10.18.25.40 255.255.255.0\n"
					+ "    ip default-gateway 10.18.25.254\n"
					+ " \n"
					+ "management snmp\n"
					+ "    snmp-server community comm1 ro\n"
					+ "    snmp-server community comm2 ro\n"
					+ "    snmp-server community comm3 ro\n"
					+ "    snmp-server host 10.1.18.135 version 2c comm1\n"
					+ "    snmp-server host 10.1.20.135 version 2c comm1\n"
					+ "    snmp-server host 10.2.18.135 version 2c comm2\n"
					+ "    snmp-server host 10.2.18.135 version 2c comm2\n"
					+ " \n"
					+ "end\n"
			);
		}

		protected void cmdShowInventory() {
			this.srvOutStream.print(
				"System Information:\n"
					+ "    Model: C48-A6\n"
					+ "    Serial number: C48-A6-12627-0\n"
					+ "    Software image version: 0.31.0\n"
					+ "    System management controller version: 1.3.2 release-platmicro-1.3+2\n"
					+ "    Description: 1RU 48-port layer-1 crosspoint switch\n"
					+ " \n"
					+ "PLD:\n"
					+ "    Specification: 2.4\n"
					+ "    Version: P505.001C\n"
					+ " \n"
					+ "Mezzanine Module Information:\n"
					+ " \n"
					+ "FPGA Information:\n"
					+ " \n"
					+ "Clock Module Information:\n"
					+ " \n"
					+ "Power Supply Information: System has 2 power supply slots\n"
					+ " \n"
					+ "Slot Model            Serial           Airflow              Capacity\n"
					+ "---- ---------------- ---------------- -------------------- --------\n"
					+ "1    DS460S-3-002     J756TY005WZBZ    FRONT-TO-BACK (STD)      460W\n"
					+ "2    DS460S-3-002     J756TY005WZBY    FRONT-TO-BACK (STD)      460W\n"
					+ " \n"
					+ "Fan Information: System has 4 fan modules\n"
					+ " \n"
					+ "Fan  Airflow\n"
					+ "---- ------------------------\n"
					+ "1    FRONT-TO-BACK (STD)\n"
					+ "2    FRONT-TO-BACK (STD)\n"
					+ "3    FRONT-TO-BACK (STD)\n"
					+ "4    FRONT-TO-BACK (STD)\n"
					+ " \n"
					+ "Port Information: System has 49 ports\n"
					+ "    Switched: 48\n"
					+ "    Management: 1\n"
					+ " \n"
					+ "Transceiver Information:\n"
					+ " \n"
					+ "Port Name                   Type        Vendor          Vendor PN        Vendor SN\n"
					+ "---- ---------------------- ----------- --------------- ---------------- ---------------\n"
					+ "et1                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7756\n"
					+ "et2                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7856\n"
					+ "et3                         1000BASE-LX CISCO           RTXM191-404-C88  ACW21170215\n"
					+ "et4                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7956\n"
					+ "et5\n"
					+ "et6\n"
					+ "et7\n"
					+ "et8\n"
					+ "et9\n"
					+ "et10                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7156\n"
					+ "et11\n"
					+ "et12                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7256\n"
					+ "et13 to switch2             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7356\n"
					+ "et14\n"
					+ "et15 to switch2             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7655\n"
					+ "et16\n"
					+ "et17\n"
					+ "et18\n"
					+ "et19\n"
					+ "et20\n"
					+ "et21 to switch3             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7658\n"
					+ "et22\n"
					+ "et23 to switch4             10GBASE-LR  OEM             SFP-10G-LR-CURV  XN2353C7651\n"
					+ "et24\n"
					+ "et25\n"
					+ "et26\n"
					+ "et27\n"
					+ "et28\n"
					+ "et29\n"
					+ "et30\n"
					+ "et31\n"
					+ "et32\n"
					+ "et33\n"
					+ "et34\n"
					+ "et35\n"
					+ "et36\n"
					+ "et37\n"
					+ "et38\n"
					+ "et39\n"
					+ "et40\n"
					+ "et41                        1000BASE-SX OEM             GLC-SX-MM-CURV   XN2353C7642\n"
					+ "et42\n"
					+ "et43                        1000BASE-LX OEM             GLC-LH-SM-CURV   XN2353C7643\n"
					+ "et44                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7646\n"
					+ "et45                        10GBASE-LR  OEM             SFP-10G-LR-CURV  N153517EF105\n"
					+ "et46                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7658\n"
					+ "et47                        10GBASE-LR  CISCO-FINISAR   FTLX1474D3BCL-C1 FNS170566J9\n"
					+ "et48                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7659\n"
					+ "ma1                         100/1000\n"
					+ "Drives:\n"
					+ "    Count: 1\n"
					+ "    /dev/sda (internal):\n"
					+ "        User Capacity: 64,023,257,088 bytes [64.0 GB]\n"
					+ "        ATA Version is: ACS-2 (minor revision not indicated)\n"
					+ "        Local Time is: Tue Oct 15 11:18:09 2022 BST\n"
					+ "        SATA Version is: SATA 3.1, 6.0 Gb/s (current: 3.0 Gb/s)\n"
					+ "        Power mode is: ACTIVE or IDLE\n"
					+ "        Serial Number: D271220319\n"
					+ "        Device Model: TS64GMTS400\n"
					+ "        Sector Size: 512 bytes logical/physical\n"
					+ "        Firmware Version: O1225G\n"
					+ "        Model Family: Silicon Motion based SSDs\n"
					+ "        SMART support is: Enabled\n"
					+ "        Rotation Rate: Solid State Device\n"
					+ " \n"
			);
		}

		protected void cmdShowLogging() {
			this.srvOutStream.print(
				"Oct 10 09:21:11 switch1 user.info cli: Configured from cli by other on pts/0 (10.218.2.3)\n"
					+ "Oct 11 09:01:15 switch1 user.info cli: Configured from cli by admin on pts/0 (10.218.2.3)\n"
			);
		}

		protected void cmdShowSnmpLocation() {
			this.srvOutStream.print(
				"Location: SNMPLOCATION\n"
			);
		}

		protected void cmdShowSnmpContact() {
			this.srvOutStream.print(
				"Contact: SNMPCONTACT\n"
			);
		}

		@Override
		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.DISABLE;
				this.printPrompt();
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				// Echo command back
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.ENABLE) && "show running-config".equals(command)) {
					this.cmdShowRunning();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) && "show inventory".equals(command)) {
					this.cmdShowInventory();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) && "show logging system | include Configured".equals(command)) {
					this.cmdShowLogging();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "show version".equals(command)) {
					this.cmdShowVersion();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "show snmp v2-mib location".equals(command)) {
					this.cmdShowSnmpLocation();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "show snmp v2-mib contact".equals(command)) {
					this.cmdShowSnmpContact();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))
					&& "terminal length 0".equals(command)) {
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.DISABLE) && "enable".equals(command)) {
					this.cliMode = CliMode.ENABLE;
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE)) {
					this.srvOutStream.print(
						"           ^\n"
							+ "% Invalid input detected at '^' marker.\n");
					this.printPrompt();
				}
			}
		}
	}

	@Nested
	@DisplayName("Arista MOS driver test")
	class AristaMOSTest {

		TaskContext taskContext = new FakeTaskContext();

		@Test
		@DisplayName("Arista MOS Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new AristaMOSFakeCli(null, credentials, taskContext);
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("AristaMOS", null, domain, "test");
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, null);
			accessManager.forceClientForTest(fakeCli, credentials);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
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
	}


	/**
	 * Fake CiscoAsyncOS CLI.
	 */
	public static class CiscoAsyncOSFakeCli extends FakeCli {
		public enum CliMode {
			INIT,
			OPER,
			PAGING,
			SHOWCONFIG_OPTION,
		}

		private static final String PAGER = "-Press Any Key For More-";
		private static final String POST_PAGER = "\r                         \r";
		private static final int PAGE_SIZE = 20;

		private CliMode cliMode = CliMode.INIT;
		private final String hostname = "esa.netshot.lab";
		private final Pattern commandPattern = Pattern.compile("(.+)\r");
		private Deque<String> nextPages;

		public CiscoAsyncOSFakeCli(String host, DeviceCliAccount cliAccount, TaskContext taskContext) throws IOException {
			super(host, cliAccount, taskContext);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case OPER:
					this.srvOutStream.printf("%s> ", this.hostname);
					break;
				default:
					break;
			}
		}

		protected void printPaged(String text) {
			if (text != null) {
				this.nextPages = new ArrayDeque<>();
				ArrayDeque<String> lines = new ArrayDeque<>(Arrays.asList(text.split("\n")));
				int lineCount = 0;
				StringBuilder page = new StringBuilder();

				while (lines.size() > 0) {
					if (lineCount == PAGE_SIZE) {
						this.nextPages.add(page.toString());
						page.setLength(0);
						lineCount = 0;
					}
					page.append(lines.removeFirst());
					page.append("\n");
					lineCount++;
				}
				if (page.length() > 0) {
					this.nextPages.add(page.toString());
				}
			}
			if (this.cliMode == CliMode.PAGING) {
				this.srvOutStream.print(POST_PAGER);
			}
			String page = this.nextPages.pop();
			this.srvOutStream.print(page);
			if (!this.nextPages.isEmpty()) {
				this.cliMode = CliMode.PAGING;
				this.srvOutStream.print(PAGER);
			}
			else {
				this.cliMode = CliMode.OPER;
				this.printPrompt();
			}
		}

		protected void cmdShowConfigOptions() {
			this.srvOutStream.print(
				"Choose the passphrase option:\n" +
				"1. Mask passphrases (Files with masked passphrases cannot be loaded using loadconfig command)\n" +
				"2. Encrypt passphrases\n" +
				"[1]> "
			);
			this.cliMode = CliMode.SHOWCONFIG_OPTION;
		}

		protected void cmdShowConfig(boolean showSecrets) {
			String config = 
				"<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n" +
				"<!DOCTYPE config SYSTEM \"config.dtd\">\r\n" +
				"\r\n" +
				"<!--\r\n" +
				"  Product: Cisco M600V Secure Email and Web Manager\r\n" +
				"  Model Number: M600V\r\n" +
				"  Version: 15.0.1-416\r\n" +
				"  Serial Number: BE59F09812A56BFCA12A-1478FC8D8A90\r\n" +
				"  Number of CPUs: 8\r\n" +
				"  Memory (GB): 15\r\n" +
				"  Current Time: Wed Sep 17 15:04:21 2025\r\n" +
				"  Feature \"Cisco Centralized Email Reporting\": Quantity = 1, Time Remaining = \"Perpetual\"\r\n" +
				"  Feature \"Cisco IronPort Centralized Email Message Tracking\": Quantity = 1, Time Remaining = \"Perpetual\"\r\n" +
				"  Feature \"Incoming Mail Handling\": Quantity = 1, Time Remaining = \"Perpetual\"\r\n" +
				"-->\r\n" +
				"<config>\r\n" +
				"<!--\r\n" +
				"******************************************************************************\r\n" +
				"*                           Network Configuration                            *\r\n" +
				"******************************************************************************\r\n" +
				"-->\r\n" +
				"\r\n" +
				"  <hostname>" + this.hostname + "</hostname>\r\n" +
				"\r\n" +
				"  <ports>\r\n" +
				"    <port_interface>\r\n" +
				"      <port_name>Management</port_name>\r\n" +
				"      <direct>\r\n" +
				"        <jack>Management</jack>\r\n" +
				"        <jack_mtu>1500</jack_mtu>\r\n" +
				"      </direct>\r\n" +
				"    </port_interface>\r\n" +
				"    <port_interface>\r\n" +
				"      <port_name>Data 1</port_name>\r\n" +
				"      <direct>\r\n" +
				"        <jack>Data 1</jack>\r\n" +
				"        <jack_mtu>1500</jack_mtu>\r\n" +
				"      </direct>\r\n" +
				"    </port_interface>\r\n" +
				"  </ports>\r\n" +
				"  <interfaces>\r\n" +
				"    <interface>\r\n" +
				"      <interface_name>Production</interface_name>\r\n" +
				"      <ip>172.16.1.10</ip>\r\n" +
				"      <phys_interface>Data 1</phys_interface>\r\n" +
				"      <netmask>255.255.255.224</netmask>\r\n" +
				"      <interface_hostname>ciscom600v.lab.netshot.net</interface_hostname>\r\n" +
				"      <sshd_port>22</sshd_port>\r\n" +
				"      <httpsd_port>443</httpsd_port>\r\n" +
				"      <api_httpd_port>6080</api_httpd_port>\r\n" +
				"      <api_httpsd_port>6443</api_httpsd_port>\r\n" +
				"    </interface>\r\n" +
				"    <interface>\r\n" +
				"      <interface_name>Management</interface_name>\r\n" +
				"      <ip>172.16.254.10</ip>\r\n" +
				"      <phys_interface>Management</phys_interface>\r\n" +
				"      <netmask>0xffffffe0</netmask>\r\n" +
				"      <interface_hostname>ciscom600v.lab.netshot.net</interface_hostname>\r\n" +
				"      <sshd_port>22</sshd_port>\r\n" +
				"      <trailblazer_httpsd_port>4431</trailblazer_httpsd_port>\r\n" +
				"      <httpsd_port>443</httpsd_port>\r\n" +
				"      <api_httpd_port>6080</api_httpd_port>\r\n" +
				"      <api_httpsd_port>6443</api_httpsd_port>\r\n" +
				"    </interface>\r\n" +
				"  </interfaces>\r\n" +
				"\r\n" +
				"  <ip_groups>\r\n" +
				"  </ip_groups>\r\n" +
				"\r\n" +
				"  <allow_arp_multicast>0</allow_arp_multicast>\r\n" +
				"  <dehydration_enabled>0</dehydration_enabled>\r\n" +
				"\r\n" +
				"  <ethernet_settings>\r\n" +
				"    <ethernet>\r\n" +
				"      <ethernet_interface>Management</ethernet_interface>\r\n" +
				"      <media>manual</media>\r\n" +
				"      <media_opt></media_opt>\r\n" +
				"      <macaddr>06:2b:74:dc:d4:d7</macaddr>\r\n" +
				"    </ethernet>\r\n" +
				"    <ethernet>\r\n" +
				"      <ethernet_interface>Data 1</ethernet_interface>\r\n" +
				"      <media>manual</media>\r\n" +
				"      <media_opt></media_opt>\r\n" +
				"      <macaddr>06:cd:d8:74:9b:ef</macaddr>\r\n" +
				"    </ethernet>\r\n" +
				"  </ethernet_settings>\r\n" +
				"\r\n" +
				"  <dns>\r\n" +
				"    <local_dns>\r\n" +
				"      <dns_ip priority=\"0\">172.16.254.98</dns_ip>\r\n" +
				"      <dns_ip priority=\"0\">172.16.254.99</dns_ip>\r\n" +
				"    </local_dns>\r\n" +
				"    <dns_ptr_timeout>20</dns_ptr_timeout>\r\n" +
				"\r\n" +
				"  </dns>\r\n" +
				"\r\n" +
				"  <dns_cache_ttl_min>1800</dns_cache_ttl_min>\r\n" +
				"\r\n" +
				"  <dns_interface></dns_interface>\r\n" +
				"\r\n" +
				"  <default_gateway>172.16.1.30</default_gateway>\r\n" +
				"\r\n" +
				"  <routes>\r\n" +
				"    <route>\r\n" +
				"      <route_name>MGMT</route_name>\r\n" +
				"      <destination>172.16.254.0/24</destination>\r\n" +
				"      <gateway>172.16.254.30</gateway>\r\n" +
				"    </route>\r\n" +
				"  </routes>\r\n" +
				"\r\n" +
				"<!--\r\n" +
				"******************************************************************************\r\n" +
				"*                            System Configuration                            *\r\n" +
				"******************************************************************************\r\n" +
				"-->\r\n" +
				"\r\n" +
				"  <fips_mode>0</fips_mode>\r\n" +
				"  <config_encryption>\r\n" +
				"  </config_encryption>\r\n" +
				"  <ntp>\r\n" +
				"    <ntp_server>172.18.114.78</ntp_server>\r\n" +
				"    <ntp_server>172.18.115.56</ntp_server>\r\n" +
				"    <ntp_server_info>\r\n" +
				"      <ntp_server_addr>172.18.114.78</ntp_server_addr>\r\n" +
				"    </ntp_server_info>\r\n" +
				"    <ntp_server_info>\r\n" +
				"      <ntp_server_addr>172.18.115.56</ntp_server_addr>\r\n" +
				"    </ntp_server_info>\r\n" +
				"    <ntp_source_ip_interface></ntp_source_ip_interface>\r\n" +
				"    <ntp_use_auth>0</ntp_use_auth>\r\n" +
				"  </ntp>\r\n" +
				"\r\n";
				if (showSecrets) {
					config +=
						"  <https_certificate>\r\n" +
						"    <certificate>\r\n" +
						"-----BEGIN CERTIFICATE-----\r\n" +
						"MIID8TCCAtmgAwIBAgIUU0V+Vgqhs6fsS4MX6dbNYFn2Nk4wDQYJKoZIhvcNAQEL\r\n" +
						"BQAwgYcxCzAJBgNVBAYTAkZSMQwwCgYDVQQIDANJZEYxDjAMBgNVBAcMBVBhcmlz\r\n" +
						"MRAwDgYDVQQKDAdOZXRzaG90MQ0wCwYDVQQLDARUZXN0MRUwEwYDVQQDDAxBc3lu\r\n" +
						"Y09TIHRlc3QxIjAgBgkqhkiG9w0BCQEWE2NvbnRhY3RAbmV0c2hvdC5uZXQwHhcN\r\n" +
						"MjUwOTIwMTI1MjA5WhcNMjYwOTIwMTI1MjA5WjCBhzELMAkGA1UEBhMCRlIxDDAK\r\n" +
						"BgNVBAgMA0lkRjEOMAwGA1UEBwwFUGFyaXMxEDAOBgNVBAoMB05ldHNob3QxDTAL\r\n" +
						"BgNVBAsMBFRlc3QxFTATBgNVBAMMDEFzeW5jT1MgdGVzdDEiMCAGCSqGSIb3DQEJ\r\n" +
						"ARYTY29udGFjdEBuZXRzaG90Lm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\r\n" +
						"AQoCggEBANNRfOo37uan0pRKrfSVpefgwbZwZ/J5MRtaeZcBqHvompZA8FTz6Yp1\r\n" +
						"amNuKehdKXae/gYCc83cNTwS7IBivz30iZt4/1VewIwKiZEVbK4DbsGQioseb3vO\r\n" +
						"gJoot7FzGrkoKnHn9n9cZmOA2zWiKE7SqVztg1MXcKnZhz5QE1mIG4Abz8dAYnM7\r\n" +
						"yRQ7DuDl9L7ESFQA8NcsML+zZ1q8kpQz82Oq10lolbnMolHCJx8jjAYnnMG/tK4I\r\n" +
						"Q6gUFPxS0gNsgoKnOe6OYMX7Z06hU5sibVc6jF+wCnVZLjEMuOhpCvLcZmbaYTmg\r\n" +
						"Cy7KpGF6ILyKKG83NlOYttHJBSA3eP8CAwEAAaNTMFEwHQYDVR0OBBYEFMABqRHf\r\n" +
						"1AxhRgx/dCyPTbbIkJYjMB8GA1UdIwQYMBaAFMABqRHf1AxhRgx/dCyPTbbIkJYj\r\n" +
						"MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADQ5fEVeNuw8RaFf\r\n" +
						"hv97+xQfej39CSDCI9foSSFr6LL624S4wyiSwWKrtg9lH+mxozg5q2NXUW+qWKPR\r\n" +
						"NDuOBY9SNVwkEoLqsdXbOIncuvwXBJ+5+/Md/IVjH5O88Gb6fEczhb0snBqp9+8v\r\n" +
						"jD75NoACvUucKL4J5A8M54mw+wZvRn64RhwUhigqQETRilUUsVAJITRdXvXtyYK0\r\n" +
						"Jtb1Bt8xVvOHzMi/n6I8yKdjuIrDiec4+XGaXK9DqNeZc4znTVXkx3Sr1HLs3BT4\r\n" +
						"SlvJ+y6SluxPcAoSFBGy4uC8DhJiWXpPaCDWPY1IE7aBiwMFCzdQrczWnp6vOKIa\r\n" +
						"Ahhsx70=\r\n" +
						"-----END CERTIFICATE-----\r\n" +
						"\r\n" +
						"    </certificate>\r\n" +
						"      <key>MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDTUXzqN+7mp9KUSq30laXn4MG2cGfyeTEbWnmXAah76JqWQPBU8+mKdWpjbinoXSl2nv4GAnPN3DU8EuyAYr899ImbeP9VXsCMComRFWyuA27BkIqLHm97zoCaKLexcxq5KCpx5/Z/XGZjgNs1oihO0qlc7YNTF3Cp2Yc+UBNZiBuAG8/HQGJzO8kUOw7g5fS+xEhUAPDXLDC/s2davJKUM/NjqtdJaJW5zKJRwicfI4wGJ5zBv7SuCEOoFBT8UtIDbIKCpznujmDF+2dOoVObIm1XOoxfsAp1WS4xDLjoaQry3GZm2mE5oAsuyqRheiC8iihvNzZTmLbRyQUgN3j/AgMBAAECggEAFwB7Zgyj6Yws+iKi4B5bSmvcSh00Lgrjvdl8ULlH0K0CCVw/jM+8BRaWIlqUdIw804JSSO8tZyQS0LDdTiBG4YSOZ1OlX3KKkkkiA56JukCkvAHIGC/o7K4OsOPBEwS3d54X+KwfgxTYuaNbY8bR4mVAit1OT9YbrQarl6x5tA36ZUCGKAWwa+SsII7x/LZ5s6q2208TBIQYWFTLTNQgcOol03KSYANxQi20+sf013Wm6tqKFs/xiaJ0Q/6OdzpC1YtJmOemsQZY0aV50h/uY7tzAy1cpLr6TW86X4rYrhNVYfz2zGDQbQ8CwqyNcwoSj48+ppDRIi2GvZtBro5m6QKBgQDqq/ViLQqo8gNmtNRFeKJItnnvNHz52HERsFytpGTEwdNdmKn35fd2zbpWpLhqvSPd4hNs+oGtqRdAkrQymQRAXY0G6n2DF55UwWoz+P6WJyS9EVPGt9XILLXiO+1x9PwdfDINv4gE7wnGzocIm6jZ4IHMqP4w6129PrTW/k8bLQKBgQDmhiwej7mExF8zTj7NS5Yu8KaCS7GfzmySTI47DDD6OTS3tT37LeHgmfojd0VYKWaHSDAYnnyEQNX1G5C4illSE5vI0ITtbBE5LMLWmFVEa82Aql6K6y06AM+D76vrRsbRZqXSAiO9S9bbUxg3uYmjb2Nk6H09VHgjy7syMb4QWwKBgQCPd2VjCepUcvCFX9POTPvZvEU7ajllfV9S0yE1vyUj7ONNe0GeHmd1qDxdMALWrp84zTfXDicthgyDG60yqb2hpA/NxJnLBSt04XIOKBFstq2NMQSonkcCQ+NGViDJ5F4loIHxScDcU08EzcpcBt3ppYl1F3OfcKU0RxvDvGGcWQKBgAkOtocQ4Jot2Qu8BE2urZz0iaZO8RC1XKC271O0m+wI+WBKd7/5wok/o4tnMUtQfR3NoU4mVAAiSAXUanBFx1KpQJK4VrC5cUfM0W77F0aT8cQfbY2JxgIkbALkFN4urrsljFhfCyZx52RAtI/j81t/kekyFVGmkv3dEGLdf3lHAoGBANOYiFXnco4vW+FMFVZxGjeYS4X5SfZUgaye1XpRZUUmfKS0rGxrw9C8mneiKQEqlksYHnU3yYsFdG7R3WyPMR1TvJhIIKUgfxjXAAAnq96BnBe3Y3Y4Lh6X8KVXzyHw8yZ8zvWBVLJznpJ96EycwUeSYTCwBiWo6FkUhwfCygGm</key>\r\n" +
						"  </https_certificate>\r\n";
				}
			config +=
				"  <crl_sources>\r\n" +
				"    <crl_listener_enabled>0</crl_listener_enabled>\r\n" +
				"    <crl_delivery_enabled>0</crl_delivery_enabled>\r\n" +
				"    <crl_gui_enabled>0</crl_gui_enabled>\r\n" +
				"  </crl_sources>\r\n" +
				"<!--\r\n" +
				"******************************************************************************\r\n" +
				"*                             Filebeat Configuration.                        *\r\n" +
				"******************************************************************************\r\n" +
				"-->\r\n" +
				"\r\n" +
				"<filebeat_config>\r\n" +
				"  <customer_details>\r\n" +
				"    <cd_allocation></cd_allocation>\r\n" +
				"    <cd_data_center></cd_data_center>\r\n" +
				"    <cd_name></cd_name>\r\n" +
				"  </customer_details>\r\n" +
				"  <kafka>\r\n" +
				"    <cert></cert>\r\n" +
				"    <host></host>\r\n" +
				"    <topic></topic>\r\n" +
				"  </kafka>\r\n" +
				"</filebeat_config>\r\n" +
				"</config>\r\n" +
				"\r\n";
			this.printPaged(config);
		}

		protected void cmdVersion() {
			this.srvOutStream.print(
				"\r\n" +
				"Current Version\r\n" +
				"===============\r\n" +
				"Product: Cisco M600V Secure Email and Web Manager\r\n" +
				"Model: M600V\r\n" +
				"Version: 15.0.0-416\r\n" +
				"Build Date: 2023-11-10\r\n" +
				"Install Date: 2024-06-11 12:17:23\r\n" +
				"Serial #: BE59F09812A56BFCA12A-1478FC8D8A90\r\n" +
				"BIOS: 4.11.fp\r\n" +
				"CPUs: 8 expected, 8 allocated\r\n" +
				"Memory: 16384 MB expected, 15360 MB allocated\r\n" +
				"RAID: NA\r\n" +
				"RAID Status: Unknown\r\n" +
				"RAID Type: NA\r\n" +
				"BMC: NA\r\n"
			);
			this.printPrompt();
		}

		@Override
		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.OPER;
				this.printPrompt();
			}
			if (this.cliMode.equals(CliMode.PAGING) && " ".equals(value)) {
				this.printPaged(null);
				return;
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				// Echo command back
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.OPER) && "showconfig".equals(command)) {
					this.cmdShowConfigOptions();
				}
				else if (this.cliMode.equals(CliMode.SHOWCONFIG_OPTION) && "1".equals(command)) {
					this.cmdShowConfig(false);
				}
				else if (this.cliMode.equals(CliMode.SHOWCONFIG_OPTION) && "2".equals(command)) {
					this.cmdShowConfig(true);
				}
				else if (this.cliMode.equals(CliMode.SHOWCONFIG_OPTION)) {
					this.cmdShowConfigOptions();
				}
				else if (this.cliMode.equals(CliMode.OPER) && "version".equals(command)) {
					this.cmdVersion();
				}
				else if (this.cliMode.equals(CliMode.OPER)) {
					this.srvOutStream.print("\nUnknown command or missing feature key: %s".formatted(command));
					this.printPrompt();
				}
			}
		}
	}

	@Nested
	@DisplayName("Cisco AsyncOS driver test")
	class CiscoAsyncOSTest {

		TaskContext taskContext = new FakeTaskContext();

		@Test
		@DisplayName("Cisco AsyncOS Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new CiscoAsyncOSFakeCli(null, credentials, taskContext);
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("CiscoAsyncOS", null, domain, "test");
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);
			AccessManager accessManager = new AccessManager(nullSession, device, null, this.taskContext, null);
			accessManager.forceClientForTest(fakeCli, credentials);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, nullSession, device, accessManager);
			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals("esa.netshot.lab", device.getName(), "The device name is incorrect");
		}
	}

	/**
	 * Fake SNMP poller returning canned data instead of doing real network I/O.
	 * This is what caught the "results.forEach is not a function on a Java Map"
	 * bug in poller.walk() (Object.keys(results) must be used instead, since
	 * only @Export-annotated members are callable from JS, and a Java Map's
	 * own forEach isn't one - plus JsSnmpHelper.walkAsString() needs to wrap
	 * its result in ProxyObject.fromMap(...) for JS to see its keys at all).
	 */
	public static class FakeGenericSnmp extends Snmp {
		FakeGenericSnmp(NetworkAddress address, DeviceSnmpCommunity community) throws IOException {
			super(address, community);
		}

		@Override
		public String getAsString(String oid) throws IOException {
			return switch (oid) {
				case "1.3.6.1.2.1.1.5.0" -> "myhost"; // sysName
				case "1.3.6.1.2.1.1.4.0" -> "someone@example.com"; // sysContact
				case "1.3.6.1.2.1.1.6.0" -> "Rack 1"; // sysLocation
				case "1.3.6.1.2.1.1.2.0" -> "1.3.6.1.4.1.9.1.1"; // sysObjectID
				case "1.3.6.1.2.1.1.1.0" -> "Fake SNMP Device"; // sysDescr
				default -> throw new IOException("noSuchObject");
			};
		}

		@Override
		public Map<String, String> walkAsString(String oid) throws IOException {
			Map<String, String> result = new TreeMap<>();
			if ("1.3.6.1.2.1.2.2.1.2".equals(oid)) {
				result.put(oid + ".1", "eth0");
			}
			else if ("1.3.6.1.2.1.2.2.1.7".equals(oid)) {
				result.put(oid + ".1", "1");
			}
			return result;
		}
	}

	@Nested
	@DisplayName("GenericSNMP driver test")
	class GenericSNMPTest {

		TaskContext taskContext = new FakeTaskContext();

		@Test
		@DisplayName("GenericSNMP Snapshot (via poller.walk, real JS engine)")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceSnmpCommunity community = new DeviceSnmpv2cCommunity("public", "community1");
			NetworkAddress address = NetworkAddress.getNetworkAddress(InetAddress.getByName("127.0.0.1"));
			FakeGenericSnmp fakeSnmp = new FakeGenericSnmp(address, community);
			Session nullSession = null;
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("GenericSNMP", null, domain, "test");
			SnapshotDeviceScript script = new SnapshotDeviceScript(this.taskContext);

			AccessManager accessManager = new AccessManager(nullSession, device, address, this.taskContext, null);
			accessManager.forceClientForTest(fakeSnmp, community);
			Method runMethod = SnapshotDeviceScript.class.getDeclaredMethod("run", Session.class,
				Device.class, AccessManager.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, nullSession, device, accessManager);

			Assertions.assertEquals("myhost", device.getName(), "The device name is incorrect");
			Assertions.assertEquals("Rack 1", device.getLocation(), "The location is incorrect");
			Assertions.assertEquals("someone@example.com", device.getContact(), "The contact is incorrect");
			Assertions.assertEquals(NetworkClass.UNKNOWN, device.getNetworkClass(), "The network class is incorrect");
			Assertions.assertEquals("1.3.6.1.4.1.9.1.1",
				((DeviceTextAttribute) device.getAttribute("sysObjectId")).getText(), "The sysObjectId is incorrect");
			Assertions.assertNotNull(device.getNetworkInterface("eth0"), "The eth0 interface should have been created");
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
