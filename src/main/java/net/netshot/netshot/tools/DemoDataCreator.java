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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.boot.EntityModel;
import net.netshot.netshot.platform.database.Database;

@Slf4j
public class DemoDataCreator {

	private long domainCount = 3;
	private List<Domain> testDomains = new ArrayList<>();

	private long ciscoIosDeviceCount = 3000;
	private long junosDeviceCount = 1000;
	private long fortiosDeviceCount = 1000;
	private List<Device> testDevices = new ArrayList<>();

	private String getFakeCiscoIosInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("interface %s\n".formatted(ni.getInterfaceName()));
		if (ni.getDescription() != null) {
			sb.append(" description %s\n".formatted(ni.getDescription()));
		}
		if (ni.getVrfInstance() != null && !ni.getVrfInstance().isBlank()) {
			sb.append(" vrf forwarding %s".formatted(ni.getVrfInstance()));
		}
		for (Network4Address na : ni.getIp4Addresses()) {
			sb.append(" ip address %s %s\n".formatted(na.getIp(), Network4Address.prefixLengthToDottedMask(na.getPrefixLength())));
		}
		for (Network6Address na : ni.getIp6Addresses()) {
			sb.append(" ipv6 address %s/%d\n".formatted(na.getIp(), na.getPrefixLength()));
		}
		if (!ni.isEnabled()) {
			sb.append(" shutdown");
		}
		sb.append("!\n");
		return sb.toString();
	}

	private Config getFakeCiscoIosConfig(Device device, int shift) {
		Config config = new Config(device);

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.getNetworkInterfaces()) {
			allInterfaceConfig.append(this.getFakeCiscoIosInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		ConfigAttribute runningConfig = new ConfigLongTextAttribute(config, "runningConfig", ""
			+ "version 16.1\n"
			+ "no service pad\n"
			+ "service timestamps debug datetime msec\n"
			+ "no service password-encryption\n"
			+ "!\n"
			+ "hostname %s\n".formatted(device.getName())
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
		config.addAttribute(runningConfig);
		ConfigAttribute iosVersion = new ConfigTextAttribute(config, "iosVersion", "16.1.6");
		config.addAttribute(iosVersion);
		ConfigAttribute iosImageFile = new ConfigTextAttribute(config, "iosImageFile", "cisco-ios-16.1.6.bin");
		config.addAttribute(iosImageFile);
		return config;
	}

	private Device getFakeCiscoIosDevice(Domain domain, Diagnostic diagnostic, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.16.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device device = new Device("CiscoIOS12", mgmtIp.getIp(), domain, "test");
		device.setName(String.format("cisco%05d", shift));
		device.setFamily("Fake IOS device");
		device.setNetworkClass(NetworkClass.ROUTER);
		device.setLocation("Test Location");
		device.setContact("Test Contact");
		device.setSoftwareVersion("16.1.6");
		device.setSoftwareLevel(ConformanceLevel.GOLD);
		device.setCreator("tester");
		DeviceCredentialSet cs = new DeviceSshAccount("user", "pass", "pass",
			DeviceCredentialSet.generateSpecificName());
		cs.setDeviceSpecific(true);
		DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
		sshAccess.setSpecificCredentialSet(cs);
		device.getAccesses().add(sshAccess);
		device.setSerialNumber("1616TEST%05d".formatted(shift));
		device.setComments("Comments for testing");
		device.setVrfInstances(new HashSet<String>(Set.of("VRF1", "VRF2")));
		device.getModules().add(new Module("chassis", "TESTCHASSIS", device.getSerialNumber(), device));
		device.getModules().add(new Module("slot", "TESTSLOT", "29038POSD203", device));
		{
			NetworkInterface ni = new NetworkInterface(device, "GigabitEthernet0/0", "", "VRF1", true, true, "Desc for interface 0/0");
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
			device.getNetworkInterfaces().add(ni);
		}
		{
			NetworkInterface ni = new NetworkInterface(device, "GigabitEthernet0/1", "", "VRF2", false, true, "Desc for interface 0/1");
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
			device.getNetworkInterfaces().add(ni);
		}
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeCiscoIosConfig(device, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			config.setChangeDate(cal.getTime());
			device.getConfigs().add(config);
			device.setLastConfig(config);
		}

		DeviceAttribute mainMemorySize = new DeviceNumericAttribute(device, "mainMemorySize", 2048);
		device.addAttribute(mainMemorySize);
		DeviceAttribute configRegister = new DeviceTextAttribute(device, "configRegister", "0x1202");
		device.addAttribute(configRegister);
		DeviceAttribute configurationSaved = new DeviceBinaryAttribute(device, "configurationSaved", true);
		device.addAttribute(configurationSaved);

		if (diagnostic != null) {
			DiagnosticResult reloadReasonDiagResult = new DiagnosticTextResult(device, diagnostic, "Reload Command");
			device.addDiagnosticResult(reloadReasonDiagResult);
		}

		return device;
	}

	private String getFakeJunosInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("    %s {\n".formatted(ni.getInterfaceName()));
		if (ni.getDescription() != null) {
			sb.append("        description \"%s\";\n".formatted(ni.getDescription()));
		}
		sb.append("        unit 0 {\n");
		if (ni.getVrfInstance() != null && !ni.getVrfInstance().isBlank()) {
			sb.append("            vrf-instance %s;\n".formatted(ni.getVrfInstance()));
		}
		sb.append("            family inet {\n");
		for (Network4Address na : ni.getIp4Addresses()) {
			sb.append("                address %s/%d;\n".formatted(na.getIp(), na.getPrefixLength()));
		}
		sb.append("            }\n");
		if (!ni.getIp6Addresses().isEmpty()) {
			sb.append("            family inet6 {\n");
			for (Network6Address na : ni.getIp6Addresses()) {
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
		Config config = new Config(device);

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.getNetworkInterfaces()) {
			allInterfaceConfig.append(this.getFakeJunosInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		String curlyConfiguration = ""
			+ "version 23.4R1.10;\n"
			+ "system {\n"
			+ "    host-name %s;\n".formatted(device.getName())
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

		ConfigAttribute configuration = new ConfigLongTextAttribute(config, "configuration", curlyConfiguration);
		config.addAttribute(configuration);

		ConfigAttribute configurationAsSet = new ConfigLongTextAttribute(config, "configurationAsSet",
			convertJunosCurlyToSet(curlyConfiguration));
		config.addAttribute(configurationAsSet);

		ConfigAttribute junosVersion = new ConfigTextAttribute(config, "junosVersion", "23.4R1.10");
		config.addAttribute(junosVersion);
		return config;
	}

	private Device getFakeJunosDevice(Domain domain, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.17.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device device = new Device("JuniperJunos", mgmtIp.getIp(), domain, "test");
		device.setName(String.format("juniper%05d", shift));
		device.setFamily("Fake Junos device");
		device.setNetworkClass(NetworkClass.FIREWALL);
		device.setLocation("Test Location");
		device.setContact("Test Contact");
		device.setSoftwareVersion("23.4R1.10");
		device.setSoftwareLevel(ConformanceLevel.GOLD);
		device.setCreator("tester");
		DeviceCredentialSet cs = new DeviceSshAccount("user", "pass", "pass",
			DeviceCredentialSet.generateSpecificName());
		cs.setDeviceSpecific(true);
		DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
		sshAccess.setSpecificCredentialSet(cs);
		device.getAccesses().add(sshAccess);
		device.setSerialNumber("JN23040TEST");
		device.setComments("Comments for testing");
		device.setVrfInstances(new HashSet<String>(Set.of("VRF1", "VRF2")));
		device.getModules().add(new Module("Chassis", "SRX300", "JN23040TEST", device));
		{
			NetworkInterface ni = new NetworkInterface(device, "ge-0/0/0", "", "VRF1", true, true, "WAN interface");
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
			device.getNetworkInterfaces().add(ni);
		}
		{
			NetworkInterface ni = new NetworkInterface(device, "ge-0/0/1", "", "VRF2", true, true, "LAN interface");
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
			device.getNetworkInterfaces().add(ni);
		}
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeJunosConfig(device, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			config.setChangeDate(cal.getTime());
			device.getConfigs().add(config);
			device.setLastConfig(config);
		}

		return device;
	}

	private String getFakeFortiOSInterfaceConfig(NetworkInterface ni) {
		StringBuilder sb = new StringBuilder();
		sb.append("config system interface\n");
		sb.append("    edit \"%s\"\n".formatted(ni.getInterfaceName()));
		if (ni.getDescription() != null) {
			sb.append("        set alias \"%s\"\n".formatted(ni.getDescription()));
		}
		if (ni.getVrfInstance() != null && !ni.getVrfInstance().isBlank()) {
			sb.append("        set vrf %s\n".formatted(ni.getVrfInstance()));
		}
		for (Network4Address na : ni.getIp4Addresses()) {
			sb.append("        set ip %s %s\n".formatted(na.getIp(), Network4Address.prefixLengthToDottedMask(na.getPrefixLength())));
		}
		if (!ni.getIp6Addresses().isEmpty()) {
			sb.append("        set ipv6 {\n");
			for (Network6Address na : ni.getIp6Addresses()) {
				sb.append("            %s/%d\n".formatted(na.getIp(), na.getPrefixLength()));
			}
			sb.append("        }\n");
		}
		if (!ni.isEnabled()) {
			sb.append("        set status down\n");
		}
		sb.append("    next\n");
		sb.append("end\n");
		return sb.toString();
	}

	private Config getFakeFortiOSConfig(Device device, int shift) {
		Config config = new Config(device);

		StringBuffer allInterfaceConfig = new StringBuffer();
		for (NetworkInterface ni : device.getNetworkInterfaces()) {
			allInterfaceConfig.append(this.getFakeFortiOSInterfaceConfig(ni));
		}
		String location = "Location%05d".formatted(shift);

		ConfigAttribute configuration = new ConfigLongTextAttribute(config, "configuration", ""
			+ "#config-version=FGT60E-7.4.4-FW-build2662-240527:opmode=0:vdom=0\n"
			+ "config system global\n"
			+ "    set hostname \"%s\"\n".formatted(device.getName())
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
		config.addAttribute(configuration);
		ConfigAttribute osVersion = new ConfigTextAttribute(config, "osVersion", "7.4.4");
		config.addAttribute(osVersion);
		return config;
	}

	private Device getFakeFortiOSDevice(Domain domain, int shift, int configCount) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.18.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		Device device = new Device("FortinetFortiOS", mgmtIp.getIp(), domain, "test");
		device.setName(String.format("fortigate%05d", shift));
		device.setFamily("Fake FortiGate");
		device.setNetworkClass(NetworkClass.FIREWALL);
		device.setLocation("Test Location");
		device.setContact("Test Contact");
		device.setSoftwareVersion("7.4.4");
		device.setSoftwareLevel(ConformanceLevel.GOLD);
		device.setCreator("tester");
		DeviceCredentialSet cs = new DeviceSshAccount("admin", "pass", "pass",
			DeviceCredentialSet.generateSpecificName());
		cs.setDeviceSpecific(true);
		DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
		sshAccess.setSpecificCredentialSet(cs);
		device.getAccesses().add(sshAccess);
		device.setSerialNumber("FGT60E7440TEST");
		device.setComments("Comments for testing");
		device.setVrfInstances(new HashSet<String>(Set.of("VRF1", "VRF2")));
		device.getModules().add(new Module("System", "FortiGate-60E", "FGT60E7440TEST", device));
		{
			NetworkInterface ni = new NetworkInterface(device, "port1", "", "VRF1", true, true, "WAN port");
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
			device.getNetworkInterfaces().add(ni);
		}
		{
			NetworkInterface ni = new NetworkInterface(device, "port2", "", "VRF2", true, true, "LAN port");
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
			device.getNetworkInterfaces().add(ni);
		}
		for (int j = 0; j < configCount; j++) {
			Config config = getFakeFortiOSConfig(device, j);
			// Set config date: spread over the past year, oldest configs first
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR, -(365 * 24 * (configCount - j) / configCount));
			config.setChangeDate(cal.getTime());
			device.getConfigs().add(config);
			device.setLastConfig(config);
		}

		DeviceAttribute haMode = new DeviceTextAttribute(device, "haMode", "standalone");
		device.addAttribute(haMode);

		return device;
	}


	private DeviceGroup getAllDevicesGroup() {
		DeviceGroup group = new DynamicDeviceGroup("All", null, "");
		return group;
	}

	private Diagnostic getReloadReasonIosSimpleDiagnostic(DeviceGroup group) {
		Diagnostic diagnostic = new SimpleDiagnostic("Reload reason", true,
			group, AttributeType.TEXT, "CiscoIOS12",
			"enable",
			"show version | include reason",
			"(?s).*Last reload reason: (.+?)[\r\n]+",
			"$1");
		return diagnostic;
	}

	private Domain getDomain(int shift) throws UnknownHostException {
		Domain domain = new Domain(
			"Domain %d".formatted(shift), "Test Domain for devices",
			new Network4Address("10.%d.1.1".formatted(shift)),
			null
		);
		return domain;
	}




	public void createData() {
		log.info("Creating data...");
		try (Session session = Database.getSession()) {
			if (session.createQuery("select d from Domain d", Domain.class).getResultCount() > 0) {
				log.error("Data already exist in the DB (domain found)");
				return;
			}

			session.beginTransaction();

			// Create domains
			for (int i = 1; i <= domainCount; i++) {
				Domain domain = this.getDomain(i);
				testDomains.add(domain);
				session.persist(domain);
			}
			log.info("Created {} domains", domainCount);

			DeviceGroup allGroup = this.getAllDevicesGroup();
			session.persist(allGroup);

			Diagnostic diag = this.getReloadReasonIosSimpleDiagnostic(allGroup);
			session.persist(diag);

			// Create Cisco IOS devices
			for (int i = 0; i < ciscoIosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				Device device = getFakeCiscoIosDevice(testDomains.get(domainIndex), diag, i, configCount);

				// Add diversity
				if (i % 100 == 0) {
					device.setStatus(Device.Status.DISABLED);
				}
				if (i % 100 == 10) {
					device.setSoftwareVersion("15.2.3");
					device.setSoftwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 20) {
					device.setSoftwareVersion("16.3.1");
					device.setSoftwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 30) {
					device.setFamily("Catalyst 3850");
					device.setNetworkClass(NetworkClass.SWITCH);
				}
				if (i % 100 == 40) {
					((DeviceNumericAttribute) device.getAttribute("mainMemorySize")).setNumber(4096.0);
				}
				if (i % 100 == 50) {
					((DeviceBinaryAttribute) device.getAttribute("configurationSaved")).setAssumption(false);
				}

				this.testDevices.add(device);
				session.persist(device);
			}
			log.info("Created {} Cisco IOS devices", ciscoIosDeviceCount);

			// Create Junos devices
			for (int i = 0; i < junosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				Device device = getFakeJunosDevice(testDomains.get(domainIndex), i, configCount);

				// Add diversity
				if (i % 100 == 5) {
					device.setStatus(Device.Status.DISABLED);
				}
				if (i % 100 == 15) {
					device.setSoftwareVersion("22.4R1.10");
					device.setSoftwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 25) {
					device.setSoftwareVersion("23.2R1.5");
					device.setSoftwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 35) {
					device.setFamily("MX Series");
					device.setNetworkClass(NetworkClass.ROUTER);
				}

				this.testDevices.add(device);
				session.persist(device);
			}
			log.info("Created {} Junos devices", junosDeviceCount);

			// Create FortiOS devices
			for (int i = 0; i < fortiosDeviceCount; i++) {
				int configCount = i < 10 ? 100 : 3;
				int domainIndex = i % testDomains.size();
				Device device = getFakeFortiOSDevice(testDomains.get(domainIndex), i, configCount);

				// Add diversity
				if (i % 100 == 7) {
					device.setStatus(Device.Status.DISABLED);
				}
				if (i % 100 == 17) {
					device.setSoftwareVersion("7.2.8");
					device.setSoftwareLevel(ConformanceLevel.BRONZE);
				}
				if (i % 100 == 27) {
					device.setSoftwareVersion("7.4.1");
					device.setSoftwareLevel(ConformanceLevel.SILVER);
				}
				if (i % 100 == 37) {
					device.setFamily("FortiGate-100F");
				}
				if (i % 100 == 47) {
					((DeviceTextAttribute) device.getAttribute("haMode")).setText("a-p");
				}

				this.testDevices.add(device);
				session.persist(device);
			}
			log.info("Created {} FortiOS devices", fortiosDeviceCount);

			log.info("Refreshing dynamic group");
			allGroup.refreshCache(session);

			session.getTransaction().commit();

			log.info("Success");
		}
		catch (Exception e) {
			log.error("Error while creating demo data", e);
		}
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
			Database.init(EntityModel.build());
		}
		catch (Exception e) {
			log.error("Error while updating/initializing DB", e);
			System.exit(-1);
		}

		DemoDataCreator creator = new DemoDataCreator();
		creator.createData();
	}

	
}
