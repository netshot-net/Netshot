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
package net.netshot.netshot.tools;

import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;

@Slf4j
public class DemoDataCreator {

	private long domainCount = 3;
	private List<Domain> testDomains = new ArrayList<>();

	private long ciscoIosDeviceCount = 3000;
	private long junosDeviceCount = 1000;
	private long fortiosDeviceCount = 1000;
	private List<Device> testDevices = new ArrayList<>();

	/**
	 * A device to create, with its configurations (the oldest first) and its diagnostic result.
	 *
	 * @param device the device
	 * @param configs the configurations, the oldest first
	 * @param diagnosticResult the diagnostic result
	 */
	private record FakeDevice(Device device, List<Config> configs, DiagnosticResult diagnosticResult) {
	}

	/**
	 * Collects the values of a network interface.
	 */
	private static final class InterfaceBuilder {
		private final NetworkInterface.NetworkInterfaceBuilder builder;
		private final Set<Network4Address> ip4Addresses = new LinkedHashSet<>();
		private final Set<Network6Address> ip6Addresses = new LinkedHashSet<>();

		InterfaceBuilder(String name, String virtualDevice, String vrf, boolean enabled, boolean level3, String description) {
			this.builder = NetworkInterface.builder().interfaceName(name).virtualDevice(virtualDevice).vrfInstance(vrf)
				.enabled(enabled).level3(level3).description(description);
		}

		void addIpAddress(NetworkAddress address) {
			NetworkInterface.addIpAddress(this.ip4Addresses, this.ip6Addresses, address);
		}

		NetworkInterface build() {
			return this.builder.ip4Addresses(this.ip4Addresses).ip6Addresses(this.ip6Addresses).build();
		}
	}

	private static Module module(String slot, String partNumber, String serialNumber) {
		Instant now = Instant.now();
		return Module.builder().slot(slot).partNumber(partNumber).serialNumber(serialNumber)
			.firstSeenDate(now).lastSeenDate(now).build();
	}

	private String getFakeCiscoIosInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("interface %s\n".formatted(ni.interfaceName()));
		if (ni.description() != null) {
			sb.append(" description %s\n".formatted(ni.description()));
		}
		if (ni.vrfInstance() != null && !ni.vrfInstance().isBlank()) {
			sb.append(" vrf forwarding %s".formatted(ni.vrfInstance()));
		}
		for (Network4Address na : ni.ip4Addresses()) {
			sb.append(" ip address %s %s\n".formatted(na.getIp(), Network4Address.prefixLengthToDottedMask(na.getPrefixLength())));
		}
		for (Network6Address na : ni.ip6Addresses()) {
			sb.append(" ipv6 address %s/%d\n".formatted(na.getIp(), na.getPrefixLength()));
		}
		if (!ni.enabled()) {
			sb.append(" shutdown");
		}
		sb.append("!\n");
		return sb.toString();
	}

	private Config getFakeCiscoIosConfig(Device device, int shift) {
		List<ConfigAttribute> attributes = new ArrayList<>();

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.networkInterfaces()) {
			allInterfaceConfig.append(this.getFakeCiscoIosInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		ConfigAttribute runningConfig = new ConfigLongTextAttribute("runningConfig", ""
			+ "version 16.1\n"
			+ "no service pad\n"
			+ "service timestamps debug datetime msec\n"
			+ "no service password-encryption\n"
			+ "!\n"
			+ "hostname %s\n".formatted(device.name())
			+ "!\n"
			+ "boot-start-marker\n"
			+ "boot-end-marker\n"
			+ "!\n"
			+ "enable secret $6$aaa\n"
			+ "!\n"
			+ "ip cef\n"
			+ "ipv6 unicast-routing\f\n" // \f to test normalization
			+ "!\n"
			+ allInterfaceConfig.toString()
			+ "snmp-server location %s\n".formatted(location)
			+ "!\n"
			+ "line con 0\n"
			+ " password something\n"
			+ "line vty 0 4\n"
			+ " password something\n"
			+ " transport input all\n"
			+ " transport output all\n"
			+ "!\n");
		attributes.add(runningConfig);
		ConfigAttribute iosVersion = new ConfigTextAttribute("iosVersion", "16.1.6");
		attributes.add(iosVersion);
		ConfigAttribute iosImageFile = new ConfigTextAttribute("iosImageFile", "cisco-ios-16.1.6.bin");
		attributes.add(iosImageFile);
		return Config.builder().deviceId(device.id()).author("").attributes(attributes).build();
	}

	private FakeDevice getFakeCiscoIosDevice(Domain domain, Diagnostic diagnostic, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.16.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device.DeviceBuilder device = Device.create("CiscoIOS12", mgmtIp.getIp(), domain, "test").toBuilder();
		List<Module> modules = new ArrayList<>();
		List<NetworkInterface> interfaces = new ArrayList<>();
		List<DeviceAttribute> attributes = new ArrayList<>();
		device.name(String.format("cisco%05d", shift));
		device.family("Fake IOS device");
		device.networkClass(NetworkClass.ROUTER);
		device.location("Test Location");
		device.contact("Test Contact");
		device.softwareVersion("16.1.6");
		device.softwareLevel(ConformanceLevel.GOLD);
		device.creator("tester");
		DeviceCredentialSet cs = DeviceSshAccount.builder().username("user").password("pass").superPassword("pass")
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		device.accesses(List.of(DeviceAccess.of("ssh").toBuilder().specificCredentialSet(cs).build()));
		String serial = "1616TEST%05d".formatted(shift);
		device.serialNumber(serial);
		device.comments("Comments for testing");
		device.vrfInstances(Set.of("VRF1", "VRF2"));
		modules.add(module("chassis", "TESTCHASSIS", serial));
		modules.add(module("slot", "TESTSLOT", "29038POSD203"));
		{
			InterfaceBuilder ni = new InterfaceBuilder("GigabitEthernet0/0", "", "VRF1", true, true, "Desc for interface 0/0");
			try {
				Network4Address na1 = new Network4Address("10.0.0.1", 25);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2016:1:%x:1::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		{
			InterfaceBuilder ni = new InterfaceBuilder("GigabitEthernet0/1", "", "VRF2", false, true, "Desc for interface 0/1");
			try {
				Network4Address na1 = new Network4Address("10.0.0.129", 25);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2016:1:%x:2::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		Device built = device.modules(modules).networkInterfaces(interfaces).attributes(attributes).build();
		List<Config> configs = new ArrayList<>();
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeCiscoIosConfig(built, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			configs.add(config.toBuilder().changeDate(cal.getTime().toInstant()).build());
		}

		DeviceAttribute mainMemorySize = new DeviceNumericAttribute("mainMemorySize", 2048.0);
		attributes.add(mainMemorySize);
		DeviceAttribute configRegister = new DeviceTextAttribute("configRegister", "0x1202");
		attributes.add(configRegister);
		DeviceAttribute configurationSaved = new DeviceBinaryAttribute("configurationSaved", true);
		attributes.add(configurationSaved);

		DiagnosticResult reloadReasonDiagResult = diagnostic == null ? null : DiagnosticTextResult.of(diagnostic, "Reload Command");

		return new FakeDevice(built.toBuilder().attributes(attributes).build(), configs, reloadReasonDiagResult);
	}

	private String getFakeJunosInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("    %s {\n".formatted(ni.interfaceName()));
		if (ni.description() != null) {
			sb.append("        description \"%s\";\n".formatted(ni.description()));
		}
		sb.append("        unit 0 {\n");
		if (ni.vrfInstance() != null && !ni.vrfInstance().isBlank()) {
			sb.append("            vrf-instance %s;\n".formatted(ni.vrfInstance()));
		}
		sb.append("            family inet {\n");
		for (Network4Address na : ni.ip4Addresses()) {
			sb.append("                address %s/%d;\n".formatted(na.getIp(), na.getPrefixLength()));
		}
		sb.append("            }\n");
		if (!ni.ip6Addresses().isEmpty()) {
			sb.append("            family inet6 {\n");
			for (Network6Address na : ni.ip6Addresses()) {
				sb.append("                address %s/%d;\n".formatted(na.getIp(), na.getPrefixLength()));
			}
			sb.append("            }\n");
		}
		sb.append("        }\n");
		sb.append("    }\n");
		return sb.toString();
	}

	private String convertJunosCurlyToSet(String curlyConfig) {
		StringBuilder result = new StringBuilder();
		String[] lines = curlyConfig.split("\n");
		StringBuilder currentPath = new StringBuilder();

		for (String line : lines) {
			String trimmed = line.trim();

			// Skip empty lines
			if (trimmed.isEmpty()) {
				continue;
			}

			// Handle closing braces
			if (trimmed.equals("}")) {
				// Remove last path component
				int lastSpace = currentPath.lastIndexOf(" ");
				if (lastSpace > 0) {
					currentPath.setLength(lastSpace);
				}
				else {
					currentPath.setLength(0);
				}
				continue;
			}

			// Handle opening braces
			if (trimmed.endsWith("{")) {
				String pathComponent = trimmed.substring(0, trimmed.length() - 1).trim();
				if (currentPath.length() > 0) {
					currentPath.append(" ");
				}
				currentPath.append(pathComponent);
				continue;
			}

			// Handle leaf statements (lines ending with semicolon)
			if (trimmed.endsWith(";")) {
				String statement = trimmed.substring(0, trimmed.length() - 1).trim();
				result.append("set ");
				if (currentPath.length() > 0) {
					result.append(currentPath).append(" ");
				}
				result.append(statement).append("\n");
			}
		}

		return result.toString();
	}

	private Config getFakeJunosConfig(Device device, int shift) {
		List<ConfigAttribute> attributes = new ArrayList<>();

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.networkInterfaces()) {
			allInterfaceConfig.append(this.getFakeJunosInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		String curlyConfiguration = ""
			+ "version 23.4R1.10;\n"
			+ "system {\n"
			+ "    host-name %s;\n".formatted(device.name())
			+ "    root-authentication {\n"
			+ "        encrypted-password \"$6$xxx\";\n"
			+ "    }\n"
			+ "    location {\n"
			+ "        building \"%s\";\n".formatted(location)
			+ "    }\n"
			+ "    services {\n"
			+ "        ssh;\n"
			+ "        netconf {\n"
			+ "            ssh;\n"
			+ "        }\n"
			+ "    }\n"
			+ "}\n"
			+ "interfaces {\n"
			+ allInterfaceConfig.toString()
			+ "}\n"
			+ "snmp {\n"
			+ "    location \"%s\";\n".formatted(location)
			+ "    community public;\n"
			+ "}\n"
			+ "routing-options {\n"
			+ "    static {\n"
			+ "        route 0.0.0.0/0 next-hop 10.0.0.1;\n"
			+ "    }\n"
			+ "}\n";

		ConfigAttribute configuration = new ConfigLongTextAttribute("configuration", curlyConfiguration);
		attributes.add(configuration);

		ConfigAttribute configurationAsSet = new ConfigLongTextAttribute("configurationAsSet",
			convertJunosCurlyToSet(curlyConfiguration));
		attributes.add(configurationAsSet);

		ConfigAttribute junosVersion = new ConfigTextAttribute("junosVersion", "23.4R1.10");
		attributes.add(junosVersion);
		return Config.builder().deviceId(device.id()).author("").attributes(attributes).build();
	}

	private FakeDevice getFakeJunosDevice(Domain domain, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.17.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device.DeviceBuilder device = Device.create("JuniperJunos", mgmtIp.getIp(), domain, "test").toBuilder();
		List<Module> modules = new ArrayList<>();
		List<NetworkInterface> interfaces = new ArrayList<>();
		List<DeviceAttribute> attributes = new ArrayList<>();
		device.name(String.format("juniper%05d", shift));
		device.family("Fake Junos device");
		device.networkClass(NetworkClass.FIREWALL);
		device.location("Test Location");
		device.contact("Test Contact");
		device.softwareVersion("23.4R1.10");
		device.softwareLevel(ConformanceLevel.GOLD);
		device.creator("tester");
		DeviceCredentialSet cs = DeviceSshAccount.builder().username("user").password("pass").superPassword("pass")
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		device.accesses(List.of(DeviceAccess.of("ssh").toBuilder().specificCredentialSet(cs).build()));
		String serial = "JN23040TEST";
		device.serialNumber(serial);
		device.comments("Comments for testing");
		device.vrfInstances(Set.of("VRF1", "VRF2"));
		modules.add(module("Chassis", "SRX300", "JN23040TEST"));
		{
			InterfaceBuilder ni = new InterfaceBuilder("ge-0/0/0", "", "VRF1", true, true, "WAN interface");
			try {
				Network4Address na1 = new Network4Address("10.1.0.1", 24);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2017:1:%x:1::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		{
			InterfaceBuilder ni = new InterfaceBuilder("ge-0/0/1", "", "VRF2", true, true, "LAN interface");
			try {
				Network4Address na1 = new Network4Address("10.1.1.1", 24);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2017:1:%x:2::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		Device built = device.modules(modules).networkInterfaces(interfaces).attributes(attributes).build();
		List<Config> configs = new ArrayList<>();
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeJunosConfig(built, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			configs.add(config.toBuilder().changeDate(cal.getTime().toInstant()).build());
		}

		return new FakeDevice(built.toBuilder().attributes(attributes).build(), configs, null);
	}

	private String getFakeFortiOSInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("config system interface\n");
		sb.append("    edit \"%s\"\n".formatted(ni.interfaceName()));
		if (ni.description() != null) {
			sb.append("        set alias \"%s\"\n".formatted(ni.description()));
		}
		if (ni.vrfInstance() != null && !ni.vrfInstance().isBlank()) {
			sb.append("        set vrf %s\n".formatted(ni.vrfInstance()));
		}
		for (Network4Address na : ni.ip4Addresses()) {
			sb.append("        set ip %s %s\n".formatted(na.getIp(), Network4Address.prefixLengthToDottedMask(na.getPrefixLength())));
		}
		if (!ni.ip6Addresses().isEmpty()) {
			sb.append("        set ipv6 {\n");
			for (Network6Address na : ni.ip6Addresses()) {
				sb.append("            %s/%d\n".formatted(na.getIp(), na.getPrefixLength()));
			}
			sb.append("        }\n");
		}
		if (!ni.enabled()) {
			sb.append("        set status down\n");
		}
		sb.append("    next\n");
		sb.append("end\n");
		return sb.toString();
	}

	private Config getFakeFortiOSConfig(Device device, int shift) {
		List<ConfigAttribute> attributes = new ArrayList<>();

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.networkInterfaces()) {
			allInterfaceConfig.append(this.getFakeFortiOSInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		ConfigAttribute configuration = new ConfigLongTextAttribute("configuration", ""
			+ "#config-version=FGT60E-7.4.4-FW-build2662-240527:opmode=0:vdom=0\n"
			+ "config system global\n"
			+ "    set hostname \"%s\"\n".formatted(device.name())
			+ "    set timezone 04\n"
			+ "end\n"
			+ "config system admin\n"
			+ "    edit \"admin\"\n"
			+ "        set password ENC xxxxx\n"
			+ "    next\n"
			+ "end\n"
			+ allInterfaceConfig.toString()
			+ "config system snmp sysinfo\n"
			+ "    set location \"%s\"\n".formatted(location)
			+ "    set status enable\n"
			+ "end\n"
			+ "config router static\n"
			+ "    edit 1\n"
			+ "        set gateway 10.0.0.1\n"
			+ "        set device \"port1\"\n"
			+ "    next\n"
			+ "end\n"
			+ "config firewall policy\n"
			+ "    edit 1\n"
			+ "        set name \"Allow-All\"\n"
			+ "        set srcintf \"port1\"\n"
			+ "        set dstintf \"port2\"\n"
			+ "        set action accept\n"
			+ "        set srcaddr \"all\"\n"
			+ "        set dstaddr \"all\"\n"
			+ "        set schedule \"always\"\n"
			+ "        set service \"ALL\"\n"
			+ "    next\n"
			+ "end\n");
		attributes.add(configuration);
		ConfigAttribute osVersion = new ConfigTextAttribute("osVersion", "7.4.4");
		attributes.add(osVersion);
		return Config.builder().deviceId(device.id()).author("").attributes(attributes).build();
	}

	private FakeDevice getFakeFortiOSDevice(Domain domain, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.18.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device.DeviceBuilder device = Device.create("FortinetFortiOS", mgmtIp.getIp(), domain, "test").toBuilder();
		List<Module> modules = new ArrayList<>();
		List<NetworkInterface> interfaces = new ArrayList<>();
		List<DeviceAttribute> attributes = new ArrayList<>();
		device.name(String.format("fortigate%05d", shift));
		device.family("Fake FortiGate");
		device.networkClass(NetworkClass.FIREWALL);
		device.location("Test Location");
		device.contact("Test Contact");
		device.softwareVersion("7.4.4");
		device.softwareLevel(ConformanceLevel.GOLD);
		device.creator("tester");
		DeviceCredentialSet cs = DeviceSshAccount.builder().username("admin").password("pass").superPassword("pass")
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		device.accesses(List.of(DeviceAccess.of("ssh").toBuilder().specificCredentialSet(cs).build()));
		String serial = "FGT60E7440TEST";
		device.serialNumber(serial);
		device.comments("Comments for testing");
		device.vrfInstances(Set.of("VRF1", "VRF2"));
		modules.add(module("System", "FortiGate-60E", "FGT60E7440TEST"));
		{
			InterfaceBuilder ni = new InterfaceBuilder("port1", "", "VRF1", true, true, "WAN port");
			try {
				Network4Address na1 = new Network4Address("10.2.0.1", 24);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2018:1:%x:1::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		{
			InterfaceBuilder ni = new InterfaceBuilder("port2", "", "VRF2", true, true, "LAN port");
			try {
				Network4Address na1 = new Network4Address("10.2.1.1", 24);
				na1.setIntAddress(na1.getIntAddress() + (shift << 8));
				ni.addIpAddress(na1);
				Network6Address na2 = new Network6Address(
					String.format("2018:1:%x:2::1", shift), 64);
				ni.addIpAddress(na2);
			}
			catch (UnknownHostException e) {
			}
			interfaces.add(ni.build());
		}
		Device built = device.modules(modules).networkInterfaces(interfaces).attributes(attributes).build();
		List<Config> configs = new ArrayList<>();
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeFortiOSConfig(built, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			configs.add(config.toBuilder().changeDate(cal.getTime().toInstant()).build());
		}

		DeviceAttribute haMode = new DeviceTextAttribute("haMode", "standalone");
		attributes.add(haMode);

		return new FakeDevice(built.toBuilder().attributes(attributes).build(), configs, null);
	}


	private DynamicDeviceGroup getAllDevicesGroup() {
		return DynamicDeviceGroup.builder().name("All").folder("").query("").build();
	}

	private Diagnostic getReloadReasonIosSimpleDiagnostic(DeviceGroup group) {
		Diagnostic diagnostic = new SimpleDiagnostic(0, "Reload reason", true,
			group.id(), AttributeType.TEXT, "CiscoIOS12",
			"enable",
			"show version | include reason",
			"(?s).*Last reload reason: (.+?)[\r\n]+",
			"$1");
		return diagnostic;
	}

	private Domain getDomain(int shift) {
		try {
			return Domain.builder()
				.name("Domain %d".formatted(shift))
				.description("Test Domain for devices")
				.server4Address(new Network4Address("10.%d.1.1".formatted(shift)))
				.build();
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException("Invalid domain address", e);
		}
	}




	public void createData() {
		log.info("Creating data...");
		try {
			new Transactions().runDsl(this::createData);
			log.info("Success");
		}
		catch (Exception e) {
			log.error("Error while creating demo data", e);
		}
	}

	private void createData(DSLContext dsl) {
		{
			if (!new DomainRepository().findAll(dsl, Page.ALL).isEmpty()) {
				log.error("Data already exist in the DB (domain found)");
				return;
			}

			// Create domains
			for (int i = 1; i <= domainCount; i++) {
				Domain domain = this.getDomain(i);
				testDomains.add(new DomainRepository().add(dsl, domain));
			}
			log.info("Created {} domains", domainCount);

			DynamicDeviceGroup allGroup = (DynamicDeviceGroup) new GroupRepository().add(dsl, this.getAllDevicesGroup());

			Diagnostic draft = this.getReloadReasonIosSimpleDiagnostic(allGroup);
			Diagnostic diag = draft.withId(new DiagnosticRepository().add(dsl, draft));

			// Create Cisco IOS devices
			for (int i = 0; i < ciscoIosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				FakeDevice fake = getFakeCiscoIosDevice(testDomains.get(domainIndex), diag, i, configCount);
				Device.DeviceBuilder device = fake.device().toBuilder();

				// Add diversity
				if (i % 100 == 0) {
					device.status(Device.Status.DISABLED);
				}
				if (i % 100 == 10) {
					device.softwareVersion("15.2.3");
					device.softwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 20) {
					device.softwareVersion("16.3.1");
					device.softwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 30) {
					device.family("Catalyst 3850");
					device.networkClass(NetworkClass.SWITCH);
				}
				if (i % 100 == 40) {
					device.attributes(withAttribute(fake.device().attributes(), new DeviceNumericAttribute("mainMemorySize", 4096.0)));
				}
				if (i % 100 == 50) {
					device.attributes(withAttribute(fake.device().attributes(), new DeviceBinaryAttribute("configurationSaved", false)));
				}

				this.testDevices.add(this.store(dsl, new FakeDevice(device.build(), fake.configs(), fake.diagnosticResult())));
			}
			log.info("Created {} Cisco IOS devices", ciscoIosDeviceCount);

			// Create Junos devices
			for (int i = 0; i < junosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				FakeDevice fake = getFakeJunosDevice(testDomains.get(domainIndex), i, configCount);
				Device.DeviceBuilder device = fake.device().toBuilder();

				// Add diversity
				if (i % 100 == 5) {
					device.status(Device.Status.DISABLED);
				}
				if (i % 100 == 15) {
					device.softwareVersion("22.4R1.10");
					device.softwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 25) {
					device.softwareVersion("23.2R1.5");
					device.softwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 35) {
					device.family("MX Series");
					device.networkClass(NetworkClass.ROUTER);
				}

				this.testDevices.add(this.store(dsl, new FakeDevice(device.build(), fake.configs(), fake.diagnosticResult())));
			}
			log.info("Created {} Junos devices", junosDeviceCount);

			// Create FortiOS devices
			for (int i = 0; i < fortiosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				FakeDevice fake = getFakeFortiOSDevice(testDomains.get(domainIndex), i, configCount);
				Device.DeviceBuilder device = fake.device().toBuilder();

				// Add diversity
				if (i % 100 == 7) {
					device.status(Device.Status.DISABLED);
				}
				if (i % 100 == 17) {
					device.softwareVersion("7.2.8");
					device.softwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 27) {
					device.softwareVersion("7.4.1");
					device.softwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 37) {
					device.family("FortiGate-100F");
				}
				if (i % 100 == 47) {
					device.attributes(withAttribute(fake.device().attributes(), new DeviceTextAttribute("haMode", "a-p")));
				}

				this.testDevices.add(this.store(dsl, new FakeDevice(device.build(), fake.configs(), fake.diagnosticResult())));
			}
			log.info("Created {} FortiOS devices", fortiosDeviceCount);

			log.info("Refreshing dynamic group");
			try {
				DynamicGroups.refresh(dsl, allGroup);
			}
			catch (FinderParseException e) {
				throw new IllegalStateException("Invalid query of the group", e);
			}
		}
	}

	private static List<DeviceAttribute> withAttribute(List<DeviceAttribute> attributes, DeviceAttribute attribute) {
		List<DeviceAttribute> result = new ArrayList<>(attributes);
		result.replaceAll(a -> a.name().equals(attribute.name()) ? attribute : a);
		return result;
	}

	private Device store(DSLContext dsl, FakeDevice fake) {
		CredentialSetRepository credentialSetRepository = new CredentialSetRepository();
		List<DeviceAccess> accesses = new ArrayList<>();
		for (DeviceAccess access : fake.device().accesses()) {
			if (access.specificCredentialSet() != null) {
				access = access.toBuilder().specificCredentialSet(credentialSetRepository.add(dsl, access.specificCredentialSet())).build();
			}
			accesses.add(access);
		}
		DeviceRepository deviceRepository = new DeviceRepository();
		Device added = deviceRepository.add(dsl, fake.device().toBuilder().accesses(accesses).build());
		ConfigRepository configRepository = new ConfigRepository();
		Long lastConfigId = null;
		for (Config config : fake.configs()) {
			lastConfigId = configRepository.add(dsl, config.toBuilder().deviceId(added.id()).build());
		}
		if (lastConfigId != null) {
			deviceRepository.save(dsl, added, added.toBuilder().lastConfigId(lastConfigId).build());
		}
		if (fake.diagnosticResult() != null) {
			new DiagnosticResultRepository().save(dsl, added.id(), List.of(fake.diagnosticResult()));
		}
		return added;
	}

	public static void main(String[] args) {
		// Configure logging level to INFO
		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
			org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);

		if (!Netshot.readConfig()) {
			System.exit(1);
		}

		try {
			log.info("Updating the database schema, if necessary.");
			Database.update();
			log.info("Initializing access to the database.");
			Wiring.init();
		}
		catch (Exception e) {
			log.error("Error while updating/initializing DB", e);
			System.exit(-1);
		}

		DemoDataCreator creator = new DemoDataCreator();
		creator.createData();
	}

	
}
