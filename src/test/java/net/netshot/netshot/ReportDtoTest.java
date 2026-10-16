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

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import net.netshot.netshot.api.rest.v0.RestService.RsLightDevice;
import net.netshot.netshot.api.rest.v0.report.ReportMapper;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;
import net.netshot.netshot.report.AccessFailureDevice;
import net.netshot.netshot.report.DailyChangeCount;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.GroupSoftwareComplianceStat;
import net.netshot.netshot.report.HardwareSupportCount;
import net.netshot.netshot.report.HardwareSupportStats;
import net.netshot.netshot.report.PolicyRuleDevice;
import net.netshot.netshot.report.SoftwareLevelDevice;

/**
 * Checks that the report DTOs and their mapper are a faithful replacement of the classes the REST
 * API used to write: copies of the former response classes (below) and the DTOs made from the same
 * values have the same representation, in JSON and XML, single and in lists, including the
 * values the seed data of the contract tests don't hold (null dates and flags).
 */
class ReportDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final XmlMapper XML = XmlMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

	private final ApiContractGolden golden = new ApiContractGolden(0);

	private static final Date D1 = new Date(1_600_000_000_000L);
	private static final Date D2 = new Date(1_700_000_000_000L);

	// ---------------------------------------------------------------------
	// The former response classes of RestService, as they were
	// ---------------------------------------------------------------------

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsConfigChangeNumberByDateStat {

		private final long changeCount;
		private final Date changeDay;

		public RsConfigChangeNumberByDateStat(long changeCount, Date changeDay) {
			this.changeCount = changeCount;
			this.changeDay = changeDay;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getChangeCount() {
			return this.changeCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public Date getChangeDay() {
			return this.changeDay;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsGroupConfigComplianceStat {

		private final long groupId;
		private final String groupName;
		private final String groupFolder;
		private final long compliantDeviceCount;
		private final long deviceCount;

		public RsGroupConfigComplianceStat(long groupId, String groupName, String groupFolder, long compliantDeviceCount,
			long deviceCount) {
			this.groupId = groupId;
			this.groupName = groupName;
			this.groupFolder = groupFolder;
			this.compliantDeviceCount = compliantDeviceCount;
			this.deviceCount = deviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getGroupId() {
			return this.groupId;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getGroupName() {
			return this.groupName;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getGroupFolder() {
			return this.groupFolder;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getCompliantDeviceCount() {
			return this.compliantDeviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getDeviceCount() {
			return this.deviceCount;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsGroupSoftwareComplianceStat {

		private final long groupId;
		private final String groupName;
		private final long goldDeviceCount;
		private final long silverDeviceCount;
		private final long bronzeDeviceCount;
		private final long deviceCount;

		public RsGroupSoftwareComplianceStat(long groupId, String groupName, long goldDeviceCount, long silverDeviceCount,
			long bronzeDeviceCount, long deviceCount) {
			this.groupId = groupId;
			this.groupName = groupName;
			this.goldDeviceCount = goldDeviceCount;
			this.silverDeviceCount = silverDeviceCount;
			this.bronzeDeviceCount = bronzeDeviceCount;
			this.deviceCount = deviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getGroupId() {
			return this.groupId;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getGroupName() {
			return this.groupName;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getGoldDeviceCount() {
			return this.goldDeviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getSilverDeviceCount() {
			return this.silverDeviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getBronzeDeviceCount() {
			return this.bronzeDeviceCount;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getDeviceCount() {
			return this.deviceCount;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLightPolicyRuleDevice extends RsLightDevice {

		private final long policyId;
		private final String policyName;
		private final long ruleId;
		private final String ruleName;
		private final Date checkDate;
		private final ResultOption result;

		public RsLightPolicyRuleDevice(DeviceSummary d, long policyId, String policyName, long ruleId, String ruleName,
			Date checkDate, ResultOption result) {
			super(d.id(), d.name(), d.family(), d.mgmtAddress(), d.status(), d.driver(), d.eol(), d.eos(),
				d.configCompliant(), d.softwareLevel(), d.networkClass());
			this.policyId = policyId;
			this.policyName = policyName;
			this.ruleId = ruleId;
			this.ruleName = ruleName;
			this.checkDate = checkDate;
			this.result = result;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getPolicyId() {
			return this.policyId;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getPolicyName() {
			return this.policyName;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public long getRuleId() {
			return this.ruleId;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getRuleName() {
			return this.ruleName;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public Date getCheckDate() {
			return this.checkDate;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public ResultOption getResult() {
			return this.result;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLightSoftwareLevelDevice extends RsLightDevice {

		private final String softwareVersion;

		public RsLightSoftwareLevelDevice(DeviceSummary d, String softwareVersion) {
			super(d.id(), d.name(), d.family(), d.mgmtAddress(), d.status(), d.driver(), d.eol(), d.eos(),
				d.configCompliant(), d.softwareLevel(), d.networkClass());
			this.softwareVersion = softwareVersion;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public String getSoftwareVersion() {
			return this.softwareVersion;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLightAccessFailureDevice extends RsLightDevice {

		private final Date lastSuccess;
		private final Date lastFailure;

		public RsLightAccessFailureDevice(DeviceSummary d, Date lastSuccess, Date lastFailure) {
			super(d.id(), d.name(), d.family(), d.mgmtAddress(), d.status(), d.driver(), d.eol(), d.eos(),
				d.configCompliant(), d.softwareLevel(), d.networkClass());
			this.lastSuccess = lastSuccess;
			this.lastFailure = lastFailure;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public Date getLastSuccess() {
			return this.lastSuccess;
		}

		@XmlElement
		@JsonView(DefaultView.class)
		public Date getLastFailure() {
			return this.lastFailure;
		}
	}

	// ---------------------------------------------------------------------

	private static byte[] json(Object value) throws Exception {
		return JSON.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private void assertSameRepresentation(Object legacy, Object dto) throws Exception {
		String expectedJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(legacy), ApiContractGolden.Transform.NONE, false);
		String actualJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedJson, actualJson, "JSON");
		String expectedXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(legacy), ApiContractGolden.Transform.NONE, false);
		String actualXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedXml, actualXml, "XML");
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
		List<Object> legacy = new ArrayList<>(List.of(new RsConfigChangeNumberByDateStat(3, D1),
			new RsConfigChangeNumberByDateStat(0, D2)));
		List<Object> dtos = new ArrayList<>(ReportMapper.toChangeCountDtos(List.of(
			new DailyChangeCount(3, D1), new DailyChangeCount(0, D2))));
		this.assertSameRepresentation(legacy, dtos);
		this.assertSameRepresentation(legacy.get(0), dtos.get(0));
	}

	@Test
	@DisplayName("The group compliance statistics have the representation of the former classes")
	void groupStats() throws Exception {
		List<Object> legacyConfig = new ArrayList<>(List.of(
			new RsGroupConfigComplianceStat(1, "Core", "Production/Core", 2, 5),
			new RsGroupConfigComplianceStat(2, "Empty", null, 0, 0)));
		List<Object> configDtos = new ArrayList<>(ReportMapper.toConfigComplianceDtos(List.of(
			new GroupConfigComplianceStat(1, "Core", "Production/Core", 2, 5),
			new GroupConfigComplianceStat(2, "Empty", null, 0, 0))));
		this.assertSameRepresentation(legacyConfig, configDtos);
		this.assertSameRepresentation(legacyConfig.get(0), configDtos.get(0));

		List<Object> legacySoftware = new ArrayList<>(List.of(
			new RsGroupSoftwareComplianceStat(1, "Core", 1, 2, 3, 7),
			new RsGroupSoftwareComplianceStat(2, "Empty", 0, 0, 0, 0)));
		List<Object> softwareDtos = new ArrayList<>(ReportMapper.toSoftwareComplianceDtos(List.of(
			new GroupSoftwareComplianceStat(1, "Core", 1, 2, 3, 7),
			new GroupSoftwareComplianceStat(2, "Empty", 0, 0, 0, 0))));
		this.assertSameRepresentation(legacySoftware, softwareDtos);
		this.assertSameRepresentation(legacySoftware.get(0), softwareDtos.get(0));
	}

	@Test
	@DisplayName("The devices with a rule result, a software version or snapshot dates have the representation of the former subclasses of the light device")
	void deviceLists() throws Exception {
		for (boolean sparse : new boolean[] {false, true}) {
			DeviceSummary device = summary(5, sparse);
			for (ResultOption result : ResultOption.values()) {
				this.assertSameRepresentation(
					new RsLightPolicyRuleDevice(device, 3, "Policy", 4, "Rule", sparse ? null : D1, result),
					ReportMapper.toPolicyRuleDeviceDtos(List.of(
						new PolicyRuleDevice(device, 3, "Policy", 4, "Rule", sparse ? null : D1, result))).get(0));
			}
			this.assertSameRepresentation(
				new ArrayList<>(List.of(new RsLightPolicyRuleDevice(device, 3, "P", 4, "R", D1, ResultOption.NONCONFORMING))),
				new ArrayList<>(ReportMapper.toPolicyRuleDeviceDtos(List.of(
					new PolicyRuleDevice(device, 3, "P", 4, "R", D1, ResultOption.NONCONFORMING)))));

			this.assertSameRepresentation(new RsLightSoftwareLevelDevice(device, sparse ? null : "16.1"),
				ReportMapper.toSoftwareLevelDeviceDtos(List.of(new SoftwareLevelDevice(device, sparse ? null : "16.1"))).get(0));
			this.assertSameRepresentation(
				new ArrayList<>(List.of(new RsLightSoftwareLevelDevice(device, "16.1"))),
				new ArrayList<>(ReportMapper.toSoftwareLevelDeviceDtos(List.of(new SoftwareLevelDevice(device, "16.1")))));

			this.assertSameRepresentation(new RsLightAccessFailureDevice(device, sparse ? null : D1, sparse ? null : D2),
				ReportMapper.toAccessFailureDeviceDtos(List.of(
					new AccessFailureDevice(device, sparse ? null : D1, sparse ? null : D2))).get(0));
			this.assertSameRepresentation(
				new ArrayList<>(List.of(new RsLightAccessFailureDevice(device, null, D2))),
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
