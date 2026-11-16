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
package net.netshot.netshot.api.rest.v0.report;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.PolicyRuleDevice;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.TestConfig;

/**
 * Tests of the report use cases (statistics, device lists, export), against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("v0 reports")
class V0ReportsTest {

	private static final long DAY = 24L * 3600 * 1000;
	/** 2040-01-01T00:00:00Z. */
	private static final Date EOS = new Date(2_208_988_800_000L);
	/** 2041-01-01T00:00:00Z. */
	private static final Date EOL = new Date(2_240_524_800_000L);

	private TimeZone previousTimeZone;
	private Transactions transactions;
	private V0Reports reports;
	private long domainA;
	private long domainB;
	private long dev1;
	private long dev2;
	private long dev3;
	private long dev4;
	private long group1;
	private long hiddenGroup;
	private long emptyGroup;
	private long policy;
	private long rule1;
	private long rule2;

	@BeforeAll
	void initDb() throws Exception {
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		this.transactions = TestWiring.get().transactions();
		this.reports = new V0Reports(TestWiring.get().configService(), TestWiring.get().searchService(), TestWiring.get().ruleService(),
			TestWiring.get().policyService(), TestWiring.get().groupService());
		this.seed();
	}

	@AfterAll
	void restoreTimeZone() {
		TimeZone.setDefault(this.previousTimeZone);
	}

	private Device device(String name, Domain domain, Device.Status status, ConformanceLevel level,
		String version, Date eos, Date eol, DeviceAttribute... attributes) {
		return TestDevices.add(Device.create("CiscoIOS12", "10.40.0." + (name.length() + (int) (Math.random() * 200)), domain, "seed")
			.toBuilder()
			.name(name)
			.status(status)
			.softwareLevel(level)
			.softwareVersion(version)
			.eosDate(eos == null ? null : eos.toInstant())
			.eolDate(eol == null ? null : eol.toInstant())
			.attributes(List.of(attributes))
			.build());
	}

	private void seed() {
		Domain a = TestDevices.addDomain("Report A", "A");
		Domain b = TestDevices.addDomain("Report B", "B");
		this.domainA = a.id();
		this.domainB = b.id();
		Device d1 = this.device("rep-1", a, Device.Status.INPRODUCTION, ConformanceLevel.GOLD, "1.0", EOS, null,
			new DeviceNumericAttribute("mainMemorySize", 2048.0),
			new DeviceLongTextAttribute("inventoryNotes", "no definition for this one"));
		Device d2 = this.device("rep-2", b, Device.Status.INPRODUCTION, ConformanceLevel.SILVER, "2.0", EOS, EOL);
		Device d3 = this.device("rep-3", a, Device.Status.DISABLED, ConformanceLevel.GOLD, "3.0", EOS, EOL);
		Device d4 = this.device("rep-4", a, Device.Status.INPRODUCTION, ConformanceLevel.BRONZE, "4.0", null, null);
		this.dev1 = d1.id();
		this.dev2 = d2.id();
		this.dev3 = d3.id();
		this.dev4 = d4.id();
		this.group1 = TestDevices.addStaticGroup("Report group", d1, d2, d3).id();
		StaticDeviceGroup hidden = TestDevices.addStaticGroup("Report hidden group", d4);
		TestWiring.get().transactions().run(dsl -> new GroupRepository().save(dsl, hidden.toBuilder().hiddenFromReports(true).build()));
		this.hiddenGroup = hidden.id();
		this.emptyGroup = TestDevices.addStaticGroup("Report empty group").id();
		PolicyService policyService = new PolicyService(this.transactions, new PolicyRepository(), TestWiring.get().repositories().group());
		RuleService ruleService = new RuleService(this.transactions, new RuleRepository(), new PolicyRepository(),
			RuleEvaluators.standard(new JsRuleRuntime(TestConfig.store()), new PyRuleRuntime(TestConfig.store()), TestDrivers.registry(),
				TestWiring.get().repositories().deviceReads()), TestWiring.get().repositories().device());
		this.policy = policyService.create(new PolicyDefinition("Report policy", Set.of(this.group1))).id();
		this.rule1 = ruleService.create(textRule("Report rule 1", this.policy)).id();
		this.rule2 = ruleService.create(textRule("Report rule 2", this.policy)).id();
		this.transactions.run(dsl -> {
			new RuleRepository().saveCheckResults(dsl, List.of(
				CheckResult.of(this.rule1, this.dev1, ResultOption.NONCONFORMING, "bad"),
				CheckResult.of(this.rule2, this.dev1, ResultOption.CONFORMING, "ok"),
				CheckResult.of(this.rule1, this.dev2, ResultOption.CONFORMING, "ok"),
				CheckResult.of(this.rule2, this.dev2, ResultOption.EXEMPTED, "exempted"),
				CheckResult.of(this.rule1, this.dev3, ResultOption.NONCONFORMING, "disabled device")));
			long now = System.currentTimeMillis();
			this.snapshot(dsl, this.dev1, Task.Status.SUCCESS, now - 10 * DAY);
			this.snapshot(dsl, this.dev1, Task.Status.FAILURE, now - DAY);
			this.snapshot(dsl, this.dev2, Task.Status.SUCCESS, now - DAY);
		});
		// Configurations, for the daily counts: now, and 3 days ago
		long now = System.currentTimeMillis();
		TestDevices.addOldConfig(this.dev1, Config.builder().author("").changeDate(Instant.ofEpochMilli(now)).build());
		TestDevices.addOldConfig(this.dev1, Config.builder().author("").changeDate(Instant.ofEpochMilli(now - 3 * DAY)).build());
	}

	private void snapshot(DSLContext dsl, long deviceId, Task.Status status, long date) {
		Instant when = Instant.ofEpochMilli(date);
		new TaskRepository().add(dsl, TakeSnapshotTask.of(TestWiring.get().repositories().device().find(dsl, deviceId), "snapshot", "tester", true, false, false)
			.toBuilder()
			.status(status)
			.creationDate(when)
			.changeDate(when)
			.scheduleReference(when)
			.executionDate(when)
			.build());
		// As the snapshot tasks do
		dsl.update(DEVICE).set(status == Task.Status.SUCCESS ? DEVICE.LAST_SNAPSHOT_SUCCESS_DATE : DEVICE.LAST_SNAPSHOT_FAILURE_DATE, when)
			.where(DEVICE.ID.eq(deviceId)).execute();
	}

	private static TextRule textRule(String name, long policyId) {
		return TextRule.builder(name, policyId).text("ntp server").field("runningConfig").build();
	}

	private static Set<Long> ids(Long... ids) {
		return new HashSet<>(List.of(ids));
	}

	private static Set<Long> none() {
		return new HashSet<>();
	}

	private static Set<ResultOption> noResults() {
		return new HashSet<>();
	}

	// ------------------------------------------------------------------
	// statistics
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The daily change counts cover the last 7 days, oldest first, in the given time zone")
	void last7DaysChanges() {
		List<DailyChangeCount> counts = this.reports.last7DaysChanges("UTC");
		Assertions.assertEquals(7, counts.size());
		for (int i = 1; i < counts.size(); i++) {
			Assertions.assertEquals(DAY, counts.get(i).changeDay().getTime() - counts.get(i - 1).changeDay().getTime());
		}
		Assertions.assertEquals(0, counts.get(0).changeDay().getTime() % DAY, "days start at midnight");
		Assertions.assertTrue(counts.get(6).changeCount() >= 1, "today's change");
		Assertions.assertTrue(counts.get(3).changeCount() >= 1, "the change of 3 days ago");
		// An unreadable time zone falls back to GMT, a missing one to the server one: both work
		Assertions.assertEquals(7, this.reports.last7DaysChanges("No/Such_Zone").size());
		Assertions.assertEquals(7, this.reports.last7DaysChanges(null).size());
	}

	@Test
	@DisplayName("The configuration compliance of a group counts the devices in production without non conforming result")
	void groupConfigComplianceStats() {
		GroupConfigComplianceStat stat = this.configStat(this.reports.groupConfigComplianceStats(none(), none(), none()));
		Assertions.assertEquals(2, stat.deviceCount(), "the disabled device is not counted");
		Assertions.assertEquals(1, stat.compliantDeviceCount(), "only rep-2 has no non conforming result");
		Assertions.assertEquals("Report group", stat.groupName());
		// Filter on the domain of the devices
		stat = this.configStat(this.reports.groupConfigComplianceStats(ids(this.domainA), none(), none()));
		Assertions.assertEquals(1, stat.deviceCount());
		Assertions.assertEquals(0, stat.compliantDeviceCount());
		// Filter on a policy which doesn't hold the rules
		stat = this.configStat(this.reports.groupConfigComplianceStats(none(), none(), ids(this.policy + 1000)));
		Assertions.assertEquals(2, stat.compliantDeviceCount());
		// Filter on the groups
		List<GroupConfigComplianceStat> filtered = this.reports.groupConfigComplianceStats(none(), ids(this.emptyGroup), none());
		Assertions.assertEquals(1, filtered.size());
		Assertions.assertEquals(0, filtered.get(0).deviceCount());
		// The groups hidden from the reports are never listed
		Assertions.assertTrue(this.reports.groupConfigComplianceStats(none(), ids(this.hiddenGroup), none()).isEmpty());
		Assertions.assertTrue(this.reports.groupConfigComplianceStats(none(), none(), none()).stream()
			.noneMatch(s -> s.groupId() == this.hiddenGroup));
	}

	private GroupConfigComplianceStat configStat(List<GroupConfigComplianceStat> stats) {
		return stats.stream().filter(s -> s.groupId() == this.group1).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("The software compliance of a group counts the devices in production by level")
	void groupSoftwareComplianceStats() {
		GroupSoftwareComplianceStat stat = this.reports.groupSoftwareComplianceStats(none()).stream()
			.filter(s -> s.groupId() == this.group1).findFirst().orElseThrow();
		Assertions.assertEquals(1, stat.goldDeviceCount(), "rep-3 is gold but disabled");
		Assertions.assertEquals(1, stat.silverDeviceCount());
		Assertions.assertEquals(0, stat.bronzeDeviceCount());
		Assertions.assertEquals(2, stat.deviceCount());
		stat = this.reports.groupSoftwareComplianceStats(ids(this.domainB)).stream()
			.filter(s -> s.groupId() == this.group1).findFirst().orElseThrow();
		Assertions.assertEquals(0, stat.goldDeviceCount());
		Assertions.assertEquals(1, stat.silverDeviceCount());
		Assertions.assertEquals(1, stat.deviceCount());
	}

	private static Map<Date, Long> counts(List<HardwareSupportCount> counts) {
		Map<Date, Long> map = new HashMap<>();
		counts.forEach(c -> map.put(c.eoxDate(), c.deviceCount()));
		return map;
	}

	@Test
	@DisplayName("The hardware support statistics count the devices in production by end-of-sale and end-of-life date")
	void hardwareSupportStats() {
		HardwareSupportStats stats = this.reports.hardwareSupportStats(none(), none());
		Map<Date, Long> sale = counts(stats.endOfSale());
		Assertions.assertEquals(2, sale.size());
		Assertions.assertEquals(2, sale.get(EOS), "rep-1 and rep-2 (rep-3 is disabled)");
		Assertions.assertEquals(1, sale.get(null), "rep-4 has no date");
		Map<Date, Long> life = counts(stats.endOfLife());
		Assertions.assertEquals(1, life.get(EOL));
		Assertions.assertEquals(2, life.get(null));
		// Filters
		Assertions.assertEquals(Map.of(EOS, 1L), counts(this.reports.hardwareSupportStats(ids(this.domainB), none()).endOfSale()));
		Assertions.assertEquals(Map.of(EOS, 2L), counts(this.reports.hardwareSupportStats(none(), ids(this.group1)).endOfSale()));
	}

	// ------------------------------------------------------------------
	// device lists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The devices of a hardware support date are the ones in production at that date, or without date for 0")
	void hardwareSupportDevices() {
		Set<String> names = this.reports.hardwareSupportDevices("eos", EOS.getTime(), none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet());
		Assertions.assertEquals(Set.of("rep-1", "rep-2"), names);
		Assertions.assertEquals(Set.of("rep-2"), this.reports.hardwareSupportDevices("eol", EOL.getTime(), none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-4"), this.reports.hardwareSupportDevices("eos", 0, none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-1", "rep-4"), this.reports.hardwareSupportDevices("eol", 0, ids(this.domainA), none())
			.stream().map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-1"), this.reports.hardwareSupportDevices("eol", 0, none(), ids(this.group1)).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		NetshotException error = Assertions.assertThrows(NetshotException.class,
			() -> this.reports.hardwareSupportDevices("other", 0, none(), none()));
		Assertions.assertEquals(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, error.getErrorCode());
	}

	@Test
	@DisplayName("The compliance results list one row per rule, with the light device and the filters")
	void configComplianceDeviceStatuses() {
		List<PolicyRuleDevice> all = this.reports.complianceDeviceStatuses(none(), none(), none(), noResults());
		Set<String> keys = all.stream().map(r -> r.device().name() + "/" + r.ruleName() + "/" + r.result()).collect(Collectors.toSet());
		Assertions.assertEquals(Set.of("rep-1/Report rule 1/NONCONFORMING", "rep-1/Report rule 2/CONFORMING",
			"rep-2/Report rule 1/CONFORMING", "rep-2/Report rule 2/EXEMPTED"), keys, "the disabled device is ignored");
		PolicyRuleDevice first = all.stream().filter(r -> r.device().id() == this.dev1 && r.ruleId() == this.rule1).findFirst().orElseThrow();
		Assertions.assertEquals("Report policy", first.policyName());
		Assertions.assertEquals(this.policy, first.policyId());
		Assertions.assertEquals(Boolean.FALSE, first.device().configCompliant(), "rep-1 has a non conforming result");
		Assertions.assertNotNull(first.checkDate());
		Assertions.assertEquals(2, this.reports.complianceDeviceStatuses(none(), none(), none(), Set.of(ResultOption.CONFORMING)).size());
		Assertions.assertEquals(2, this.reports.complianceDeviceStatuses(ids(this.domainA), none(), none(), noResults()).size());
		Assertions.assertEquals(0, this.reports.complianceDeviceStatuses(none(), none(), ids(this.policy + 1000), noResults()).size());
	}

	@Test
	@DisplayName("The non compliant devices of a group are the non conforming results of its devices in production")
	void groupConfigNonCompliantDevices() {
		List<PolicyRuleDevice> devices = this.reports.groupNonCompliantDevices(this.group1, none(), none());
		Assertions.assertEquals(1, devices.size());
		Assertions.assertEquals(this.dev1, devices.get(0).device().id());
		Assertions.assertEquals(ResultOption.NONCONFORMING, devices.get(0).result());
		Assertions.assertTrue(this.reports.groupNonCompliantDevices(this.group1, ids(this.domainB), none()).isEmpty());
		Assertions.assertTrue(this.reports.groupNonCompliantDevices(this.group1, none(), ids(this.policy + 1000)).isEmpty());
		Assertions.assertTrue(this.reports.groupNonCompliantDevices(99999999L, none(), none()).isEmpty());
	}

	@Test
	@DisplayName("The devices by software level match the level whatever its case, an unknown level meaning the unknown level")
	void groupDevicesBySoftwareLevel() {
		List<SoftwareLevelDevice> gold = this.reports.groupDevicesBySoftwareLevel(this.group1, "GoLd", none());
		Assertions.assertEquals(1, gold.size(), "rep-3 is gold but disabled");
		Assertions.assertEquals("rep-1", gold.get(0).device().name());
		Assertions.assertEquals("1.0", gold.get(0).softwareVersion());
		Assertions.assertEquals(1, this.reports.groupDevicesBySoftwareLevel(this.group1, "silver", none()).size());
		Assertions.assertEquals(0, this.reports.groupDevicesBySoftwareLevel(this.group1, "silver", ids(this.domainA)).size());
		Assertions.assertEquals(0, this.reports.groupDevicesBySoftwareLevel(this.group1, "nonsense", none()).size());
	}

	@Test
	@DisplayName("The devices without successful snapshot are the ones whose last success is too old or missing, the most recent success first")
	void accessFailureDevices() {
		List<AccessFailureDevice> devices = this.reports.accessFailureDevices(3, none(), Page.ALL);
		List<String> names = devices.stream().filter(d -> d.device().name().startsWith("rep-")).map(d -> d.device().name()).toList();
		Assertions.assertEquals(List.of("rep-1", "rep-4"), names, "rep-2 had a success yesterday, rep-4 never; nulls last");
		AccessFailureDevice rep1 = devices.stream().filter(d -> d.device().id() == this.dev1).findFirst().orElseThrow();
		Assertions.assertNotNull(rep1.lastSuccess());
		Assertions.assertNotNull(rep1.lastFailure());
		Assertions.assertTrue(rep1.lastSuccess().before(rep1.lastFailure()));
		AccessFailureDevice rep4 = devices.stream().filter(d -> d.device().id() == this.dev4).findFirst().orElseThrow();
		Assertions.assertNull(rep4.lastSuccess());
		Assertions.assertNull(rep4.lastFailure());
		// An invalid number of days is replaced by 3
		Assertions.assertEquals(devices.size(), this.reports.accessFailureDevices(null, none(), Page.ALL).size());
		Assertions.assertEquals(devices.size(), this.reports.accessFailureDevices(0, none(), Page.ALL).size());
		// Longer period: only the devices which never had a success
		Assertions.assertEquals(Set.of(this.dev4), this.reports.accessFailureDevices(30, none(), Page.ALL).stream()
			.map(d -> d.device().id()).filter(id -> id >= this.dev1 && id <= this.dev4).collect(Collectors.toSet()));
		Assertions.assertEquals(1, this.reports.accessFailureDevices(3, none(), new Page(0, 1)).size());
		Assertions.assertTrue(this.reports.accessFailureDevices(3, ids(this.domainB), Page.ALL).stream()
			.noneMatch(d -> d.device().id() == this.dev1));
	}
}
