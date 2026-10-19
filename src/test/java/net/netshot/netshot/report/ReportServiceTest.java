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
package net.netshot.netshot.report;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithDatabaseTest;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Tests of the report use cases (statistics, device lists, export), against a real database.
 */
@TestInstance(Lifecycle.PER_CLASS)
class ReportServiceTest extends WithDatabaseTest {

	private static final long DAY = 24L * 3600 * 1000;
	/** 2040-01-01T00:00:00Z. */
	private static final Date EOS = new Date(2_208_988_800_000L);
	/** 2041-01-01T00:00:00Z. */
	private static final Date EOL = new Date(2_240_524_800_000L);

	private TimeZone previousTimeZone;
	private Transactions transactions;
	private ReportService service;
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
		Properties config = getFreshDatabaseConfig("reportservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		TestConfig.init(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		this.transactions = new Transactions();
		this.service = new ReportService(this.transactions, new ReportRepository(), TestDrivers.registry());
		this.seed();
	}

	@AfterAll
	void restoreTimeZone() {
		TimeZone.setDefault(this.previousTimeZone);
	}

	private Device device(org.hibernate.Session session, String name, Domain domain, Device.Status status, ConformanceLevel level,
		String version, Date eos, Date eol) {
		Device device = new Device("CiscoIOS12", "10.40.0." + (name.length() + (int) (Math.random() * 200)), domain, "seed");
		device.setName(name);
		device.setStatus(status);
		device.setSoftwareLevel(level);
		device.setSoftwareVersion(version);
		device.setEosDate(eos);
		device.setEolDate(eol);
		session.persist(device);
		return device;
	}

	private void seed() {
		this.transactions.run(session -> {
			Domain a = new Domain("Report A", "A", null, null);
			session.persist(a);
			Domain b = new Domain("Report B", "B", null, null);
			session.persist(b);
			this.domainA = a.getId();
			this.domainB = b.getId();
			Device d1 = this.device(session, "rep-1", a, Device.Status.INPRODUCTION, ConformanceLevel.GOLD, "1.0", EOS, null);
			Device d2 = this.device(session, "rep-2", b, Device.Status.INPRODUCTION, ConformanceLevel.SILVER, "2.0", EOS, EOL);
			Device d3 = this.device(session, "rep-3", a, Device.Status.DISABLED, ConformanceLevel.GOLD, "3.0", EOS, EOL);
			Device d4 = this.device(session, "rep-4", a, Device.Status.INPRODUCTION, ConformanceLevel.BRONZE, "4.0", null, null);
			this.dev1 = d1.getId();
			this.dev2 = d2.getId();
			this.dev3 = d3.getId();
			this.dev4 = d4.getId();
			StaticDeviceGroup g1 = new StaticDeviceGroup("Report group");
			session.persist(g1);
			g1.addDevice(d1);
			g1.addDevice(d2);
			g1.addDevice(d3);
			session.merge(g1);
			StaticDeviceGroup hidden = new StaticDeviceGroup("Report hidden group");
			hidden.setHiddenFromReports(true);
			session.persist(hidden);
			hidden.addDevice(d4);
			session.merge(hidden);
			StaticDeviceGroup empty = new StaticDeviceGroup("Report empty group");
			session.persist(empty);
			this.group1 = g1.getId();
			this.hiddenGroup = hidden.getId();
			this.emptyGroup = empty.getId();
			session.persist(new DeviceNumericAttribute(d1, "mainMemorySize", 2048));
			session.persist(new DeviceLongTextAttribute(d1, "inventoryNotes", "no definition for this one"));
		});
		PolicyService policyService = new PolicyService(this.transactions, new PolicyRepository());
		RuleService ruleService = new RuleService(this.transactions, new RuleRepository(), new PolicyRepository(),
			RuleEvaluators.standard(new JsRuleRuntime(), new PyRuleRuntime(), TestDrivers.registry()));
		this.policy = policyService.create(new PolicyDefinition("Report policy", Set.of(this.group1))).getId();
		this.rule1 = ruleService.create(textRule("Report rule 1", this.policy)).getId();
		this.rule2 = ruleService.create(textRule("Report rule 2", this.policy)).getId();
		this.transactions.run(session -> {
			Rule r1 = session.get(Rule.class, this.rule1);
			Rule r2 = session.get(Rule.class, this.rule2);
			session.persist(new CheckResult(r1, session.get(Device.class, this.dev1), ResultOption.NONCONFORMING, "bad"));
			session.persist(new CheckResult(r2, session.get(Device.class, this.dev1), ResultOption.CONFORMING, "ok"));
			session.persist(new CheckResult(r1, session.get(Device.class, this.dev2), ResultOption.CONFORMING, "ok"));
			session.persist(new CheckResult(r2, session.get(Device.class, this.dev2), ResultOption.EXEMPTED, "exempted"));
			session.persist(new CheckResult(r1, session.get(Device.class, this.dev3), ResultOption.NONCONFORMING, "disabled device"));
			long now = System.currentTimeMillis();
			this.snapshot(session, this.dev1, Task.Status.SUCCESS, now - 10 * DAY);
			this.snapshot(session, this.dev1, Task.Status.FAILURE, now - DAY);
			this.snapshot(session, this.dev2, Task.Status.SUCCESS, now - DAY);
			// Configurations, for the daily counts: now, and 3 days ago
			Device d1 = session.get(Device.class, this.dev1);
			Config today = new Config(d1);
			today.setChangeDate(new Date(now));
			session.persist(today);
			Config old = new Config(d1);
			old.setChangeDate(new Date(now - 3 * DAY));
			session.persist(old);
		});
	}

	private void snapshot(org.hibernate.Session session, long deviceId, Task.Status status, long date) {
		TakeSnapshotTask task = new TakeSnapshotTask(session.get(Device.class, deviceId), "snapshot", "tester", true, false, false);
		task.setStatus(status);
		task.setCreationDate(new Date(date));
		task.setChangeDate(new Date(date));
		task.setScheduleReference(new Date(date));
		task.setExecutionDate(new Date(date));
		session.persist(task);
	}

	private static RuleDefinition textRule(String name, long policyId) {
		return new RuleDefinition("TextRule", name, policyId, false, Map.of(), null, "ntp server", null, null, null,
			"runningConfig", null, null, null, null);
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

	private static Set<Long> deviceIds(List<? extends Object> devices, java.util.function.Function<Object, Long> id) {
		return devices.stream().map(id).collect(Collectors.toSet());
	}

	// ------------------------------------------------------------------
	// statistics
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The daily change counts cover the last 7 days, oldest first, in the given time zone")
	void last7DaysChanges() {
		List<DailyChangeCount> counts = this.service.getLast7DaysChanges("UTC");
		Assertions.assertEquals(7, counts.size());
		for (int i = 1; i < counts.size(); i++) {
			Assertions.assertEquals(DAY, counts.get(i).changeDay().getTime() - counts.get(i - 1).changeDay().getTime());
		}
		Assertions.assertEquals(0, counts.get(0).changeDay().getTime() % DAY, "days start at midnight");
		Assertions.assertTrue(counts.get(6).changeCount() >= 1, "today's change");
		Assertions.assertTrue(counts.get(3).changeCount() >= 1, "the change of 3 days ago");
		// An unreadable time zone falls back to GMT, a missing one to the server one: both work
		Assertions.assertEquals(7, this.service.getLast7DaysChanges("No/Such_Zone").size());
		Assertions.assertEquals(7, this.service.getLast7DaysChanges(null).size());
	}

	@Test
	@DisplayName("The configuration compliance of a group counts the devices in production without non conforming result")
	void groupConfigComplianceStats() {
		GroupConfigComplianceStat stat = this.configStat(this.service.getGroupConfigComplianceStats(none(), none(), none()));
		Assertions.assertEquals(2, stat.deviceCount(), "the disabled device is not counted");
		Assertions.assertEquals(1, stat.compliantDeviceCount(), "only rep-2 has no non conforming result");
		Assertions.assertEquals("Report group", stat.groupName());
		// Filter on the domain of the devices
		stat = this.configStat(this.service.getGroupConfigComplianceStats(ids(this.domainA), none(), none()));
		Assertions.assertEquals(1, stat.deviceCount());
		Assertions.assertEquals(0, stat.compliantDeviceCount());
		// Filter on a policy which doesn't hold the rules
		stat = this.configStat(this.service.getGroupConfigComplianceStats(none(), none(), ids(this.policy + 1000)));
		Assertions.assertEquals(2, stat.compliantDeviceCount());
		// Filter on the groups
		List<GroupConfigComplianceStat> filtered = this.service.getGroupConfigComplianceStats(none(), ids(this.emptyGroup), none());
		Assertions.assertEquals(1, filtered.size());
		Assertions.assertEquals(0, filtered.get(0).deviceCount());
		// The groups hidden from the reports are never listed
		Assertions.assertTrue(this.service.getGroupConfigComplianceStats(none(), ids(this.hiddenGroup), none()).isEmpty());
		Assertions.assertTrue(this.service.getGroupConfigComplianceStats(none(), none(), none()).stream()
			.noneMatch(s -> s.groupId() == this.hiddenGroup));
	}

	private GroupConfigComplianceStat configStat(List<GroupConfigComplianceStat> stats) {
		return stats.stream().filter(s -> s.groupId() == this.group1).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("The software compliance of a group counts the devices in production by level")
	void groupSoftwareComplianceStats() {
		GroupSoftwareComplianceStat stat = this.service.getGroupSoftwareComplianceStats(none()).stream()
			.filter(s -> s.groupId() == this.group1).findFirst().orElseThrow();
		Assertions.assertEquals(1, stat.goldDeviceCount(), "rep-3 is gold but disabled");
		Assertions.assertEquals(1, stat.silverDeviceCount());
		Assertions.assertEquals(0, stat.bronzeDeviceCount());
		Assertions.assertEquals(2, stat.deviceCount());
		stat = this.service.getGroupSoftwareComplianceStats(ids(this.domainB)).stream()
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
		HardwareSupportStats stats = this.service.getHardwareSupportStats(none(), none());
		Map<Date, Long> sale = counts(stats.endOfSale());
		Assertions.assertEquals(2, sale.size());
		Assertions.assertEquals(2, sale.get(EOS), "rep-1 and rep-2 (rep-3 is disabled)");
		Assertions.assertEquals(1, sale.get(null), "rep-4 has no date");
		Map<Date, Long> life = counts(stats.endOfLife());
		Assertions.assertEquals(1, life.get(EOL));
		Assertions.assertEquals(2, life.get(null));
		// Filters
		Assertions.assertEquals(Map.of(EOS, 1L), counts(this.service.getHardwareSupportStats(ids(this.domainB), none()).endOfSale()));
		Assertions.assertEquals(Map.of(EOS, 2L), counts(this.service.getHardwareSupportStats(none(), ids(this.group1)).endOfSale()));
	}

	// ------------------------------------------------------------------
	// device lists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The devices of a hardware support date are the ones in production at that date, or without date for 0")
	void hardwareSupportDevices() {
		Set<String> names = this.service.getHardwareSupportDevices("eos", EOS.getTime(), none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet());
		Assertions.assertEquals(Set.of("rep-1", "rep-2"), names);
		Assertions.assertEquals(Set.of("rep-2"), this.service.getHardwareSupportDevices("eol", EOL.getTime(), none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-4"), this.service.getHardwareSupportDevices("eos", 0, none(), none()).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-1", "rep-4"), this.service.getHardwareSupportDevices("eol", 0, ids(this.domainA), none())
			.stream().map(d -> d.name()).collect(Collectors.toSet()));
		Assertions.assertEquals(Set.of("rep-1"), this.service.getHardwareSupportDevices("eol", 0, none(), ids(this.group1)).stream()
			.map(d -> d.name()).collect(Collectors.toSet()));
		NetshotException error = Assertions.assertThrows(NetshotException.class,
			() -> this.service.getHardwareSupportDevices("other", 0, none(), none()));
		Assertions.assertEquals(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, error.getErrorCode());
	}

	@Test
	@DisplayName("The compliance results list one row per rule, with the light device and the filters")
	void configComplianceDeviceStatuses() {
		List<PolicyRuleDevice> all = this.service.getConfigComplianceDeviceStatuses(none(), none(), none(), noResults());
		Set<String> keys = all.stream().map(r -> r.device().name() + "/" + r.ruleName() + "/" + r.result()).collect(Collectors.toSet());
		Assertions.assertEquals(Set.of("rep-1/Report rule 1/NONCONFORMING", "rep-1/Report rule 2/CONFORMING",
			"rep-2/Report rule 1/CONFORMING", "rep-2/Report rule 2/EXEMPTED"), keys, "the disabled device is ignored");
		PolicyRuleDevice first = all.stream().filter(r -> r.device().id() == this.dev1 && r.ruleId() == this.rule1).findFirst().orElseThrow();
		Assertions.assertEquals("Report policy", first.policyName());
		Assertions.assertEquals(this.policy, first.policyId());
		Assertions.assertEquals(Boolean.FALSE, first.device().configCompliant(), "rep-1 has a non conforming result");
		Assertions.assertNotNull(first.checkDate());
		Assertions.assertEquals(2, this.service.getConfigComplianceDeviceStatuses(none(), none(), none(), Set.of(ResultOption.CONFORMING)).size());
		Assertions.assertEquals(2, this.service.getConfigComplianceDeviceStatuses(ids(this.domainA), none(), none(), noResults()).size());
		Assertions.assertEquals(0, this.service.getConfigComplianceDeviceStatuses(none(), none(), ids(this.policy + 1000), noResults()).size());
	}

	@Test
	@DisplayName("The non compliant devices of a group are the non conforming results of its devices in production")
	void groupConfigNonCompliantDevices() {
		List<PolicyRuleDevice> devices = this.service.getGroupConfigNonCompliantDevices(this.group1, none(), none());
		Assertions.assertEquals(1, devices.size());
		Assertions.assertEquals(this.dev1, devices.get(0).device().id());
		Assertions.assertEquals(ResultOption.NONCONFORMING, devices.get(0).result());
		Assertions.assertTrue(this.service.getGroupConfigNonCompliantDevices(this.group1, ids(this.domainB), none()).isEmpty());
		Assertions.assertTrue(this.service.getGroupConfigNonCompliantDevices(this.group1, none(), ids(this.policy + 1000)).isEmpty());
		Assertions.assertTrue(this.service.getGroupConfigNonCompliantDevices(99999999L, none(), none()).isEmpty());
	}

	@Test
	@DisplayName("The devices by software level match the level whatever its case, an unknown level meaning the unknown level")
	void groupDevicesBySoftwareLevel() {
		List<SoftwareLevelDevice> gold = this.service.getGroupDevicesBySoftwareLevel(this.group1, "GoLd", none());
		Assertions.assertEquals(1, gold.size(), "rep-3 is gold but disabled");
		Assertions.assertEquals("rep-1", gold.get(0).device().name());
		Assertions.assertEquals("1.0", gold.get(0).softwareVersion());
		Assertions.assertEquals(1, this.service.getGroupDevicesBySoftwareLevel(this.group1, "silver", none()).size());
		Assertions.assertEquals(0, this.service.getGroupDevicesBySoftwareLevel(this.group1, "silver", ids(this.domainA)).size());
		Assertions.assertEquals(0, this.service.getGroupDevicesBySoftwareLevel(this.group1, "nonsense", none()).size());
	}

	@Test
	@DisplayName("The devices without successful snapshot are the ones whose last success is too old or missing, the most recent success first")
	void accessFailureDevices() {
		List<AccessFailureDevice> devices = this.service.getAccessFailureDevices(3, none(), Page.ALL);
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
		Assertions.assertEquals(devices.size(), this.service.getAccessFailureDevices(null, none(), Page.ALL).size());
		Assertions.assertEquals(devices.size(), this.service.getAccessFailureDevices(0, none(), Page.ALL).size());
		// Longer period: only the devices which never had a success
		Assertions.assertEquals(Set.of(this.dev4), this.service.getAccessFailureDevices(30, none(), Page.ALL).stream()
			.map(d -> d.device().id()).filter(id -> id >= this.dev1 && id <= this.dev4).collect(Collectors.toSet()));
		Assertions.assertEquals(1, this.service.getAccessFailureDevices(3, none(), new Page(0, 1)).size());
		Assertions.assertTrue(this.service.getAccessFailureDevices(3, ids(this.domainB), Page.ALL).stream()
			.noneMatch(d -> d.device().id() == this.dev1));
	}

	// ------------------------------------------------------------------
	// export
	// ------------------------------------------------------------------

	/** Records the calls it receives. */
	private static class RecordingSink implements ReportExportSink {

		final List<String> events = new ArrayList<>();
		final List<ExportedDevice> devices = new ArrayList<>();
		final List<ExportedAttribute> attributes = new ArrayList<>();
		final List<ExportedModule> modules = new ArrayList<>();
		final List<ExportedMembership> memberships = new ArrayList<>();
		List<String> domains = List.of("unset");
		List<String> groups = List.of("unset");
		RuntimeException failure;

		@Override
		public void summary(List<String> summaryDomains, List<String> summaryGroups) {
			this.events.add("summary");
			this.domains = summaryDomains;
			this.groups = summaryGroups;
		}

		@Override
		public void devicesStart(boolean compliance, boolean locations) {
			this.events.add("devices:" + compliance + ":" + locations);
		}

		@Override
		public void device(ExportedDevice device) {
			if (this.failure != null) {
				throw this.failure;
			}
			this.devices.add(device);
		}

		@Override
		public void interfacesStart() {
			this.events.add("interfaces");
		}

		@Override
		public void networkInterface(ExportedInterface networkInterface) {
			this.events.add("interface");
		}

		@Override
		public void inventoryStart(boolean history) {
			this.events.add("inventory:" + history);
		}

		@Override
		public void module(ExportedModule module) {
			this.modules.add(module);
		}

		@Override
		public void complianceStart() {
			this.events.add("compliance");
		}

		@Override
		public void complianceResult(PolicyRuleDevice result) {
			this.events.add("complianceResult");
		}

		@Override
		public void groupsStart(boolean compliance) {
			this.events.add("groups:" + compliance);
		}

		@Override
		public void group(GroupConfigComplianceStat group) {
			this.events.add("group");
		}

		@Override
		public void membershipsStart() {
			this.events.add("memberships");
		}

		@Override
		public void membership(ExportedMembership membership) {
			this.memberships.add(membership);
		}

		@Override
		public void attributesStart() {
			this.events.add("attributes");
		}

		@Override
		public void attribute(ExportedAttribute attribute) {
			this.attributes.add(attribute);
		}
	}

	private static ReportExportSelection selection(Set<Long> domains, Set<Long> groups, boolean all) {
		return new ReportExportSelection(domains, groups, all, all, all, all, all, all, all);
	}

	@Test
	@DisplayName("The export without option only holds the summary and the devices, by name")
	void exportDefault() {
		RecordingSink sink = new RecordingSink();
		this.service.export(selection(none(), none(), false), sink);
		Assertions.assertEquals(List.of("summary", "devices:false:false"), sink.events);
		Assertions.assertNull(sink.domains, "not filtered on domains");
		Assertions.assertNull(sink.groups, "not filtered on groups");
		List<String> names = sink.devices.stream().map(d -> d.name()).filter(n -> n.startsWith("rep-")).toList();
		Assertions.assertTrue(names.containsAll(List.of("rep-1", "rep-2", "rep-3", "rep-4")), "all the devices, whatever their status");
		Assertions.assertEquals(names, names.stream().sorted().toList(), "ordered by name");
		ReportExportSink.ExportedDevice rep1 = sink.devices.stream().filter(d -> d.id() == this.dev1).findFirst().orElseThrow();
		Assertions.assertEquals("Report A", rep1.domainName());
		Assertions.assertEquals("1.0", rep1.softwareVersion());
		Assertions.assertEquals(ConformanceLevel.GOLD, rep1.softwareLevel());
		Assertions.assertEquals(EOS, rep1.eosDate());
	}

	@Test
	@DisplayName("The export holds the selected sections in the order of the sheets, and names the selected domains and groups")
	void exportEverything() {
		RecordingSink sink = new RecordingSink();
		this.service.export(selection(ids(this.domainA, 99999999L), ids(this.group1), true), sink);
		Assertions.assertEquals(List.of("Report A (" + this.domainA + ")"), sink.domains, "unknown IDs are ignored");
		Assertions.assertEquals(List.of("Report group (" + this.group1 + ")"), sink.groups);
		Assertions.assertEquals(List.of("summary", "devices:true:true", "interfaces", "inventory:true", "compliance",
			"complianceResult", "groups:true", "group", "memberships", "attributes"), this.sections(sink.events));
		// Group memberships: the groups hidden from the reports are left out
		Assertions.assertTrue(sink.memberships.stream().allMatch(m -> m.groupId() == this.group1));
		Assertions.assertEquals(Set.of(this.dev1, this.dev3), sink.memberships.stream().map(m -> m.deviceId()).collect(Collectors.toSet()),
			"filtered on the domain A and the group 1");
		// Attributes: the title comes from the driver; an attribute the driver doesn't define has none
		Map<String, String> titles = new HashMap<>();
		sink.attributes.forEach(a -> titles.put(a.value(), a.title()));
		Assertions.assertEquals("Main memory size (MB)", titles.get("2048.0"));
		Assertions.assertTrue(titles.containsKey("no definition for this one"));
		Assertions.assertNull(titles.get("no definition for this one"));
	}

	/** Collapses the repeated rows of the events, keeping the order of the sections. */
	private List<String> sections(List<String> events) {
		List<String> sections = new ArrayList<>();
		for (String event : events) {
			if (sections.isEmpty() || !sections.get(sections.size() - 1).equals(event)) {
				sections.add(event);
			}
		}
		return sections;
	}

	@Test
	@DisplayName("An exception of the receiver goes through the export unchanged")
	void exportFailure() {
		RecordingSink sink = new RecordingSink();
		sink.failure = new IllegalStateException("no space left");
		IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
			() -> this.service.export(selection(none(), none(), false), sink));
		Assertions.assertEquals("no space left", error.getMessage());
	}

	@Test
	@DisplayName("The inventory export only holds the removed modules when asked for")
	void exportInventory() {
		RecordingSink sink = new RecordingSink();
		this.service.export(new ReportExportSelection(none(), none(), false, true, false, false, false, false, false), sink);
		Assertions.assertEquals(List.of("summary", "devices:false:false", "inventory:false"), sink.events);
		Assertions.assertTrue(sink.modules.isEmpty());
	}
}
