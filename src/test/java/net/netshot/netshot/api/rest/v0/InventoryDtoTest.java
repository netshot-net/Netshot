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
package net.netshot.netshot.api.rest.v0;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.ApiContractGolden;
import net.netshot.netshot.DtoContract;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.api.rest.v0.config.ConfigDiffDto;
import net.netshot.netshot.api.rest.v0.config.ConfigDto;
import net.netshot.netshot.api.rest.v0.config.ConfigMapper;
import net.netshot.netshot.api.rest.v0.device.DeviceCatalogMapper;
import net.netshot.netshot.api.rest.v0.device.DeviceTypeDto;
import net.netshot.netshot.api.rest.v0.device.NetworkInterfaceDto;
import net.netshot.netshot.api.rest.v0.device.SearchRequest;
import net.netshot.netshot.api.rest.v0.domain.DomainMapper;
import net.netshot.netshot.api.rest.v0.domain.DomainRequest;
import net.netshot.netshot.api.rest.v0.domain.DomainSummaryDto;
import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.api.rest.v0.group.GroupRequest;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDelta;
import net.netshot.netshot.inventory.config.ConfigDiff;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkAddress.AddressUsage;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;

/**
 * Checks that the inventory DTOs (groups, configurations, interfaces, device types...) and their
 * mappers are a faithful replacement of the entities the REST API used to serialize (same JSON and
 * XML with the same view), including the kinds of objects the API contract seed doesn't hold, and
 * that the requests are read like the former request classes were.
 */
class InventoryDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(InventoryDtoTest.class);

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestConfig.init(new Properties());
		TestDrivers.reload();
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	@Test
	@DisplayName("Both kinds of groups have the same JSON and XML representation as the entities, alone and in a list")
	void groups() throws Exception {
		DynamicDeviceGroup dynamic = new DynamicDeviceGroup("Dynamic", "CiscoIOS12", "[Name] contains \"r\"");
		dynamic.setId(10);
		dynamic.setFolder("Production");
		dynamic.setHiddenFromReports(true);
		dynamic.setChangeDate(new Date(1_600_000_200_000L));
		StaticDeviceGroup fixed = new StaticDeviceGroup("Static");
		fixed.setId(11);
		fixed.setChangeDate(new Date(1_600_000_300_000L));
		this.assertRepresentation(GroupMapper.toDto(dynamic));
		this.assertRepresentation(GroupMapper.toDto(fixed));
		List<DeviceGroup> entities = new ArrayList<>(List.of(dynamic, fixed));
		List<DeviceGroupDto> dtos = new ArrayList<>(GroupMapper.toDtos(entities));
		this.assertRepresentation(dtos);
		Assertions.assertTrue(new String(xml(GroupMapper.toDto(fixed))).startsWith("<StaticDeviceGroup type=\"StaticDeviceGroup\">"));
		Assertions.assertTrue(new String(xml(GroupMapper.toDto(dynamic))).startsWith("<DynamicDeviceGroup type=\"DynamicDeviceGroup\">"));
	}

	private static Config fullConfig() {
		Device device = new Device("CiscoIOS12", "10.1.1.1", null, "seed");
		Config config = new Config(device);
		config.setId(7);
		config.setAuthor("tester");
		config.setChangeDate(new Date(1_600_000_000_000L));
		config.setCustomHash("hash");
		config.addAttribute(new ConfigTextAttribute(config, "text", "some text"));
		config.addAttribute(new ConfigNumericAttribute(config, "number", 12.5));
		config.addAttribute(new ConfigLongTextAttribute(config, "long", "some very long text"));
		config.addAttribute(new ConfigBinaryAttribute(config, "binary", true));
		ConfigBinaryFileAttribute file = new ConfigBinaryFileAttribute(config, "file", "backup.tar.gz");
		file.setFileSize(4096);
		file.setChecksum("0123456789abcdef");
		config.addAttribute(file);
		return config;
	}

	@Test
	@DisplayName("A configuration with all the kinds of attributes has the same representation as the entity")
	void config() throws Exception {
		Config config = fullConfig();
		ConfigDto dto = ConfigMapper.toDto(config);
		this.assertRepresentation(dto);
		String json = new String(json(dto));
		Assertions.assertTrue(json.contains("\"type\":\"BINARYFILE\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"LONGTEXT\""), json);
		Assertions.assertFalse(json.contains("some very long text"), "long texts are not part of the representation");
		this.assertRepresentation(new ArrayList<>(ConfigMapper.toDtos(new ArrayList<>(List.of(config)))));

		Config bare = new Config(config.getDevice());
		this.assertRepresentation(ConfigMapper.toDto(bare));
	}

	@Test
	@DisplayName("A list of configurations which is a sub list is published as one, like the query result was")
	void configSubList() throws Exception {
		List<Config> whole = new ArrayList<>(List.of(fullConfig(), fullConfig()));
		List<Config> sub = whole.subList(0, 1);
		this.assertRepresentation(ConfigMapper.toDtos(sub));
		Assertions.assertTrue(new String(xml(ConfigMapper.toDtos(sub))).contains("SubList"));
	}

	@Test
	@DisplayName("An interface has the same representation as the entity, with IPv4 and IPv6 addresses of every usage")
	void networkInterface() throws Exception {
		NetworkInterface networkInterface = new NetworkInterface(null, "Gi0/0", "vd", "VRF1", true, true, "Uplink");
		networkInterface.setId(3);
		Network4Address primary = new Network4Address("10.0.0.1", 24);
		Network4Address secondary = new Network4Address("10.0.1.1", 24);
		secondary.setAddressUsage(AddressUsage.SECONDARY);
		networkInterface.addIpAddress(primary);
		networkInterface.addIpAddress(secondary);
		Network6Address v6 = new Network6Address("2001:db8::1", 64);
		v6.setAddressUsage(AddressUsage.HSRP);
		networkInterface.addIpAddress(v6);
		NetworkInterface bare = new NetworkInterface(null, null, null, null, false, false, null);
		bare.setId(4);
		List<NetworkInterface> entities = new ArrayList<>(List.of(networkInterface, bare));
		List<NetworkInterfaceDto> dtos = DeviceCatalogMapper.toInterfaceDtos(entities);
		this.assertRepresentation(dtos);
		Assertions.assertEquals(2, dtos.get(0).ip4Addresses().size());
	}

	@Test
	@DisplayName("The device types have the same representation as the driver objects they replace")
	void deviceTypes() throws Exception {
		List<DeviceDriver> drivers = new ArrayList<>(TestDrivers.registry().getAllDrivers());
		Assertions.assertFalse(drivers.isEmpty());
		List<DeviceTypeDto> dtos = new ArrayList<>(DeviceCatalogMapper.toDeviceTypeDtos(drivers));
		this.contract.checkBoth(json(dtos), xml(dtos), ApiContractGolden.Transform.DEVICE_TYPES);
		Assertions.assertTrue(drivers.stream().anyMatch(d -> !d.getOptions().isEmpty()), "a driver with options is covered");
	}

	@Test
	@DisplayName("A domain is published with its IPv4 address only")
	void domain() throws Exception {
		Domain domain = new Domain("Paris DC", "Paris data center", new Network4Address("10.255.0.1"), new Network6Address("::"));
		domain.setId(1);
		DomainSummaryDto dto = DomainMapper.toSummaryDto(domain);
		Assertions.assertEquals(Map.of("description", "Paris data center", "id", 1, "ipAddress", "10.255.0.1", "name", "Paris DC"),
			JSON.readValue(json(dto), Map.class));
		Assertions.assertTrue(new String(xml(dto)).startsWith("<RsDomain>"));
		Domain withoutAddress = new Domain("No address", "", null, null);
		Assertions.assertNull(DomainMapper.toSummaryDto(withoutAddress).ipAddress());
	}

	// ------------------------------------------------------------------
	// diff
	// ------------------------------------------------------------------

	@Test
	@DisplayName("A comparison is published with its deltas (or without), and its configurations on demand")
	void configDiff() throws Exception {
		ConfigDelta delta = new ConfigDelta(ConfigDelta.Type.DELETE, 4, 3, List.of("a"), List.of(), List.of("x"), List.of("y"),
			List.of(new ConfigDelta.LineWithPosition("interface Gi0/0", 2)));
		ConfigDiff diff = new ConfigDiff(new Date(1), new Date(2), 5, 6, null, fullConfig(), Map.of("Running configuration", List.of(delta)));
		ConfigDiffDto dto = ConfigMapper.toDto(diff);
		String json = new String(json(dto));
		Assertions.assertTrue(json.contains("\"diffType\":\"DELETE\""), json);
		Assertions.assertTrue(json.contains("\"item\":null"), json);
		Assertions.assertTrue(json.contains("\"hierarchy\":[{\"line\":\"interface Gi0/0\",\"position\":2}]"), json);
		Assertions.assertTrue(json.contains("\"originalConfig\":null"), json);
		Assertions.assertTrue(json.contains("\"revisedConfig\":{"), json);
		Assertions.assertTrue(json.contains("\"originalConfigId\":5"), json);

		ConfigDiff noDeltas = new ConfigDiff(new Date(1), new Date(2), 5, 6, null, null, null);
		Assertions.assertFalse(new String(json(ConfigMapper.toDto(noDeltas))).contains("deltas"));
		ConfigDiff emptyDeltas = new ConfigDiff(new Date(1), new Date(2), 5, 6, null, null, Map.of());
		Assertions.assertTrue(new String(json(ConfigMapper.toDto(emptyDeltas))).contains("\"deltas\":{}"));
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	private static <T> T read(Class<T> type, String content) throws Exception {
		return JSON.readerFor(type).readValue(content);
	}

	@Test
	@DisplayName("A domain request has the default values of the former request, and an explicit null stays null")
	void domainRequest() throws Exception {
		DomainRequest empty = read(DomainRequest.class, "{}");
		Assertions.assertEquals("", empty.getName());
		Assertions.assertEquals("", empty.getDescription());
		Assertions.assertEquals(-1L, empty.getId());
		Assertions.assertNull(empty.getIpAddress());
		DomainRequest explicitNull = read(DomainRequest.class, "{\"name\": null, \"description\": null}");
		Assertions.assertNull(explicitNull.getName());
		Assertions.assertNull(explicitNull.getDescription());
		DomainRequest full = read(DomainRequest.class, "{\"id\": 4, \"name\": \"n\", \"description\": \"d\", \"ipAddress\": \"10.0.0.1\"}");
		Assertions.assertEquals("10.0.0.1", DomainMapper.toDefinition(full).server4Address());
		Assertions.assertEquals("n", DomainMapper.toDefinition(full).name());
		Assertions.assertThrows(JsonMappingException.class, () -> read(DomainRequest.class, "{\"unknown\": 1}"));
		// The address is parsed by the service, which reports a malformed one
		Assertions.assertEquals("not an address", read(DomainRequest.class, "{\"ipAddress\": \"not an address\"}").getIpAddress());
	}

	@Test
	@DisplayName("A group request has the default values of the former request, and an explicit null stays null")
	void groupRequest() throws Exception {
		GroupRequest empty = read(GroupRequest.class, "{}");
		Assertions.assertEquals(List.of(), empty.getStaticDevices());
		Assertions.assertEquals("", empty.getFolder());
		Assertions.assertFalse(empty.isHiddenFromReports());
		Assertions.assertNull(empty.getName());
		Assertions.assertNull(empty.getType());
		Assertions.assertNull(empty.getQuery());
		GroupRequest explicitNull = read(GroupRequest.class, "{\"staticDevices\": null, \"folder\": null}");
		Assertions.assertNull(explicitNull.getStaticDevices());
		Assertions.assertNull(explicitNull.getFolder());
		GroupRequest full = read(GroupRequest.class, """
			{"id": 3, "name": "n", "type": "StaticDeviceGroup", "staticDevices": [2, 1], "query": "q", "folder": "f", "hiddenFromReports": true}""");
		var definition = GroupMapper.toDefinition(full);
		Assertions.assertEquals("StaticDeviceGroup", definition.type());
		Assertions.assertEquals(List.of(2L, 1L), definition.staticDevices());
		Assertions.assertEquals("q", definition.query());
		Assertions.assertEquals("f", definition.folder());
		Assertions.assertTrue(definition.hiddenFromReports());
		Assertions.assertThrows(JsonMappingException.class, () -> read(GroupRequest.class, "{\"unknown\": 1}"));
	}

	@Test
	@DisplayName("A search request reads its query, and refuses unknown properties")
	void searchRequest() throws Exception {
		Assertions.assertEquals("[Name] is \"a\"", read(SearchRequest.class, "{\"query\": \"[Name] is \\\"a\\\"\"}").query());
		Assertions.assertNull(read(SearchRequest.class, "{}").query());
		Assertions.assertThrows(JsonMappingException.class, () -> read(SearchRequest.class, "{\"unknown\": 1}"));
	}
}
