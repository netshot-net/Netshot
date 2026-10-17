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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.api.rest.v0.report.ReportMapper;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.report.AccessFailureDevice;
import net.netshot.netshot.report.DailyChangeCount;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.GroupSoftwareComplianceStat;
import net.netshot.netshot.report.HardwareSupportCount;
import net.netshot.netshot.report.HardwareSupportStats;
import net.netshot.netshot.report.PolicyRuleDevice;
import net.netshot.netshot.report.SoftwareLevelDevice;

/**
 * Checks that the report DTOs are written as the REST API always wrote the former response classes
 * (the expected files hold what those classes wrote): in JSON and XML, single and in lists,
 * including the values the seed data of the contract tests don't hold (null dates and flags).
 */
class ReportDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(ReportDtoTest.class);

	private static final Date D1 = new Date(1_600_000_000_000L);
	private static final Date D2 = new Date(1_700_000_000_000L);

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	private static DeviceSummary summary(long id, boolean sparse) {
		return sparse
			? new DeviceSummary(id, "r" + id, null, "10.0.0." + id, Device.Status.DISABLED, "CiscoIOS12", null, null, null, null, null)
			: new DeviceSummary(id, "r" + id, "Cisco", "10.0.0." + id, Device.Status.INPRODUCTION, "CiscoIOS12", true, false, true,
				ConformanceLevel.GOLD, Device.NetworkClass.ROUTER);
	}

	@Test
	@DisplayName("The daily change counts have the representation of the former class")
	void changeCounts() throws Exception {
		List<Object> dtos = new ArrayList<>(ReportMapper.toChangeCountDtos(List.of(
			new DailyChangeCount(3, D1), new DailyChangeCount(0, D2))));
		this.assertRepresentation(dtos);
		this.assertRepresentation(dtos.get(0));
	}

	@Test
	@DisplayName("The group compliance statistics have the representation of the former classes")
	void groupStats() throws Exception {
		List<Object> configDtos = new ArrayList<>(ReportMapper.toConfigComplianceDtos(List.of(
			new GroupConfigComplianceStat(1, "Core", "Production/Core", 2, 5),
			new GroupConfigComplianceStat(2, "Empty", null, 0, 0))));
		this.assertRepresentation(configDtos);
		this.assertRepresentation(configDtos.get(0));

		List<Object> softwareDtos = new ArrayList<>(ReportMapper.toSoftwareComplianceDtos(List.of(
			new GroupSoftwareComplianceStat(1, "Core", 1, 2, 3, 7),
			new GroupSoftwareComplianceStat(2, "Empty", 0, 0, 0, 0))));
		this.assertRepresentation(softwareDtos);
		this.assertRepresentation(softwareDtos.get(0));
	}

	@Test
	@DisplayName("The devices with a rule result, a software version or snapshot dates have the representation of the former subclasses of the light device")
	void deviceLists() throws Exception {
		for (boolean sparse : new boolean[] {false, true}) {
			DeviceSummary device = summary(5, sparse);
			for (ResultOption result : ResultOption.values()) {
				this.assertRepresentation(ReportMapper.toPolicyRuleDeviceDtos(List.of(
					new PolicyRuleDevice(device, 3, "Policy", 4, "Rule", sparse ? null : D1, result))).get(0));
			}
			this.assertRepresentation(new ArrayList<>(ReportMapper.toPolicyRuleDeviceDtos(List.of(
				new PolicyRuleDevice(device, 3, "P", 4, "R", D1, ResultOption.NONCONFORMING)))));

			this.assertRepresentation(
				ReportMapper.toSoftwareLevelDeviceDtos(List.of(new SoftwareLevelDevice(device, sparse ? null : "16.1"))).get(0));
			this.assertRepresentation(
				new ArrayList<>(ReportMapper.toSoftwareLevelDeviceDtos(List.of(new SoftwareLevelDevice(device, "16.1")))));

			this.assertRepresentation(ReportMapper.toAccessFailureDeviceDtos(List.of(
				new AccessFailureDevice(device, sparse ? null : D1, sparse ? null : D2))).get(0));
			this.assertRepresentation(
				new ArrayList<>(ReportMapper.toAccessFailureDeviceDtos(List.of(new AccessFailureDevice(device, null, D2)))));
		}
	}

	@Test
	@DisplayName("The hardware support counts keep the type ids of the former classes, in JSON and in XML")
	void hardwareSupportStats() throws Exception {
		// The former classes were polymorphic with MINIMAL_CLASS type ids: the ids were
		// ".RestService$RsHardwareSupportEoSStat" and "...EoLStat" in JSON, and the same with
		// ".." instead of "$" in XML (the ids of the contract goldens).
		var dtos = ReportMapper.toHardwareSupportDtos(new HardwareSupportStats(
			List.of(new HardwareSupportCount(D1, 2), new HardwareSupportCount(null, 1)),
			List.of(new HardwareSupportCount(D2, 3))));
		Assertions.assertEquals(3, dtos.size());
		String expectedJson = """
			[ {
			  "type" : ".RestService$RsHardwareSupportEoSStat",
			  "eoxDate" : 1600000000000,
			  "deviceCount" : 2
			}, {
			  "type" : ".RestService$RsHardwareSupportEoSStat",
			  "eoxDate" : null,
			  "deviceCount" : 1
			}, {
			  "type" : ".RestService$RsHardwareSupportEoLStat",
			  "eoxDate" : 1700000000000,
			  "deviceCount" : 3
			} ]""";
		Assertions.assertEquals(JSON.readTree(expectedJson), JSON.readTree(json(new ArrayList<>(dtos))));
		String xml = new String(xml(new ArrayList<>(dtos)));
		Assertions.assertTrue(xml.contains("<item type=\".RestService..RsHardwareSupportEoSStat\">"), xml);
		Assertions.assertTrue(xml.contains("<item type=\".RestService..RsHardwareSupportEoLStat\">"), xml);
		Assertions.assertFalse(xml.contains("<type>"), xml);
		Assertions.assertTrue(xml.contains("<eoxDate>1600000000000</eoxDate>"), xml);
	}
}
