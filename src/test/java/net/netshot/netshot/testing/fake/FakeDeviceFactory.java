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
package net.netshot.netshot.testing.fake;

import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.NetworkAddress;

/**
 * Builds fake devices (records, not stored: see {@code TestDevices} to store them).
 */
public final class FakeDeviceFactory {

	/**
	 * Builds a fake Cisco IOS device, with its last configuration and a diagnostic result, as a
	 * device being worked on (not stored: for the tests without database).
	 *
	 * @return the device
	 */
	public static DeviceDraft getFakeCiscoIosDraft() {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		Diagnostic diagnostic = getReloadReasonIosSimpleDiagnostic();
		Device device = getFakeCiscoIosDevice(domain, null, 100);
		DeviceDraft draft = new DeviceDraft(device);
		draft.setLastConfig(getFakeCiscoIosConfig(device).toBuilder().id(1).build());
		draft.setStoredDiagnosticResults(List.of(DiagnosticTextResult.of(diagnostic.id(), diagnostic.name(), "Reload Command")));
		return draft;
	}

	/**
	 * Builds a fake Cisco IOS device (not stored).
	 *
	 * @return the device
	 */
	public static Device getFakeCiscoIosDevice() {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		return FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, 100);
	}

	/**
	 * Builds the diagnostic result a fake Cisco IOS device would have.
	 *
	 * @param diagnostic the diagnostic
	 * @return the result
	 */
	public static DiagnosticResult getFakeCiscoIosDiagnosticResult(Diagnostic diagnostic) {
		return DiagnosticTextResult.of(diagnostic.id(), diagnostic.name(), "Reload Command");
	}

	/**
	 * Builds the configuration of a fake Cisco IOS device (not stored).
	 *
	 * @param device the device
	 * @return the configuration
	 */
	public static Config getFakeCiscoIosConfig(Device device) {
		List<ConfigAttribute> attributes = new ArrayList<>();
		ConfigAttribute runningConfig = new ConfigLongTextAttribute("runningConfig", ""
			+ "version 16.1\n"
			+ "no service pad\n"
			+ "service timestamps debug datetime msec\n"
			+ "no service password-encryption\n"
			+ "!\n"
			+ "hostname " + device.name() + "\n"
			+ "!\n"
			+ "boot-start-marker\n"
			+ "boot-end-marker\n"
			+ "!\n"
			+ "enable secret $6$aaa\n"
			+ "!\n"
			+ "ip cef\n"
			+ "ipv6 unicast-routing\f\n" // \f to test normalization
			+ "!\n"
			+ "interface GigabitEthernet0/0\n"
			+ " description Description of Gi0/0\n"
			+ " ip address 10.0.0.1 255.255.255.0\n"
			+ " no shutdown\n"
			+ "!\n"
			+ "interface GigabitEthernet0/1\n"
			+ " description Description of Gi0/1\n"
			+ " ip address 10.0.1.1 255.255.255.0\n"
			+ " no shutdown\n"
			+ "!\n"
			+ "interface GigabitEthernet0/2\n"
			+ " description Description of Gi0/2\n"
			+ " ip address 10.0.2.1 255.255.255.0\n"
			+ "!\n"
			+ "line con 0\n"
			+ " password something\n"
			+ "line vty 0 4\n"
			+ " password something\n"
			+ " transport input all\n"
			+ " transport output all\n"
			+ "!\n");
		attributes.add(runningConfig);
		attributes.add(new ConfigTextAttribute("iosVersion", "16.1.6"));
		attributes.add(new ConfigTextAttribute("iosImageFile", "cisco-ios-16.1.6.bin"));
		return Config.builder().deviceId(device.id()).author("").attributes(attributes).build();
	}

	private static NetworkInterface fakeInterface(String name, String vrf, boolean enabled, String description,
			String ip4, int ip4Length, String ip6, int shift) {
		Set<Network4Address> ip4Addresses = new LinkedHashSet<>();
		Set<Network6Address> ip6Addresses = new LinkedHashSet<>();
		try {
			Network4Address na1 = new Network4Address(ip4, ip4Length);
			na1.setIntAddress(na1.getIntAddress() + (shift << 8));
			NetworkInterface.addIpAddress(ip4Addresses, ip6Addresses, na1);
			NetworkAddress na2 = new Network6Address(ip6, 64);
			NetworkInterface.addIpAddress(ip4Addresses, ip6Addresses, na2);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		return NetworkInterface.builder().interfaceName(name).virtualDevice("").vrfInstance(vrf).enabled(enabled).level3(true)
			.description(description).ip4Addresses(ip4Addresses).ip6Addresses(ip6Addresses).build();
	}

	/**
	 * Builds a fake Cisco IOS device (not stored, without configuration).
	 *
	 * @param domain the domain of the device
	 * @param diagnostic not used (the diagnostic results are stored apart, see {@link #getFakeCiscoIosDiagnosticResult})
	 * @param shift a number making the device unique (name, addresses)
	 * @return the device
	 */
	public static Device getFakeCiscoIosDevice(Domain domain, Diagnostic diagnostic, int shift) {
		Network4Address mgmtIp = null;
		try {
			mgmtIp = new Network4Address("172.16.0.0");
			mgmtIp.setIntAddress(mgmtIp.getIntAddress() + shift);
		}
		catch (UnknownHostException e) {
			// Ignore
		}
		DeviceCredentialSet cs = DeviceSshAccount.builder().username("user").password("pass").superPassword("pass")
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		Instant now = Instant.now();
		return Device.create("CiscoIOS12", mgmtIp.getIp(), domain, "test").toBuilder()
			.name(String.format("router%05d", shift))
			.family("Unknown IOS device")
			.networkClass(NetworkClass.ROUTER)
			.location("Test Location")
			.contact("Test Contact")
			.softwareVersion("16.1.6")
			.softwareLevel(ConformanceLevel.GOLD)
			.creator("tester")
			.accesses(List.of(DeviceAccess.of("ssh").toBuilder().specificCredentialSet(cs).build()))
			.serialNumber("16161616TEST16")
			.comments("Comments for testing")
			.vrfInstances(Set.of("VRF1", "VRF2"))
			.modules(List.of(
				Module.builder().slot("chassis").partNumber("TESTCHASSIS").serialNumber("16161616TEST16").firstSeenDate(now).lastSeenDate(now).build(),
				Module.builder().slot("slot").partNumber("TESTSLOT").serialNumber("29038POSD203").firstSeenDate(now).lastSeenDate(now).build()))
			.networkInterfaces(List.of(
				fakeInterface("GigabitEthernet0/0", "VRF1", true, "Desc for interface 0/0", "10.0.0.1", 25,
					String.format("2016:1:%x:1::1", shift), shift),
				fakeInterface("GigabitEthernet0/1", "VRF2", false, "Desc for interface 0/1", "10.0.0.129", 25,
					String.format("2016:1:%x:2::1", shift), shift)))
			.attributes(List.of(
				new DeviceNumericAttribute("mainMemorySize", 2048.0),
				new DeviceTextAttribute("configRegister", "0x1202"),
				new DeviceBinaryAttribute("configurationSaved", true)))
			.build();
	}

	/**
	 * Builds a dynamic group of all the devices (not stored).
	 *
	 * @return the group
	 */
	public static DynamicDeviceGroup getAllDevicesGroup() {
		return DynamicDeviceGroup.builder().name("All").folder("").query("").build();
	}

	/**
	 * Builds a simple diagnostic, of the reload reason of a Cisco IOS device (not stored).
	 *
	 * @return the diagnostic
	 */
	public static Diagnostic getReloadReasonIosSimpleDiagnostic() {
		Diagnostic diagnostic = new SimpleDiagnostic(0, "Reload reason", true, null, AttributeType.TEXT, "CiscoIOS12",
			"enable",
			"show version | include reason",
			"(?s).*Last reload reason: (.+?)[\r\n]+",
			"$1");
		return diagnostic;
	}

	private FakeDeviceFactory() {
	}
}
