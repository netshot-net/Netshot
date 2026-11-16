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
package net.netshot.netshot.report;

import java.time.Instant;
import java.util.ArrayList;
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

import net.netshot.netshot.Netshot;
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
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.TestRepositories;

/**
 * Tests of the report use cases (statistics, device lists, export), against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Report service")
class ReportServiceTest {

	private static final long DAY = 24L * 3600 * 1000;
	/** 2040-01-01T00:00:00Z. */
	private static final Date EOS = new Date(2_208_988_800_000L);
	/** 2041-01-01T00:00:00Z. */
	private static final Date EOL = new Date(2_240_524_800_000L);

	private TimeZone previousTimeZone;
	private Transactions transactions;
	private ReportService service;
	private long domainA;
	private long dev1;
	private long dev2;
	private long dev3;
	private long group1;
	private long policy;
	private long rule1;
	private long rule2;

	@BeforeAll
	void initDb() throws Exception {
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		this.transactions = TestWiring.get().transactions();
		this.service = new ReportService(this.transactions, new ReportRepository(), TestDrivers.registry());
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
		Device d1 = this.device("rep-1", a, Device.Status.INPRODUCTION, ConformanceLevel.GOLD, "1.0", EOS, null,
			new DeviceNumericAttribute("mainMemorySize", 2048.0),
			new DeviceLongTextAttribute("inventoryNotes", "no definition for this one"));
		Device d2 = this.device("rep-2", b, Device.Status.INPRODUCTION, ConformanceLevel.SILVER, "2.0", EOS, EOL);
		Device d3 = this.device("rep-3", a, Device.Status.DISABLED, ConformanceLevel.GOLD, "3.0", EOS, EOL);
		Device d4 = this.device("rep-4", a, Device.Status.INPRODUCTION, ConformanceLevel.BRONZE, "4.0", null, null);
		this.dev1 = d1.id();
		this.dev2 = d2.id();
		this.dev3 = d3.id();
		this.group1 = TestDevices.addStaticGroup("Report group", d1, d2, d3).id();
		StaticDeviceGroup hidden = TestDevices.addStaticGroup("Report hidden group", d4);
		TestWiring.get().transactions().run(dsl -> new GroupRepository().save(dsl, hidden.toBuilder().hiddenFromReports(true).build()));
		TestDevices.addStaticGroup("Report empty group");
		PolicyService policyService = new PolicyService(this.transactions, new PolicyRepository(), TestRepositories.GROUP);
		RuleService ruleService = new RuleService(this.transactions, new RuleRepository(), new PolicyRepository(),
			RuleEvaluators.standard(new JsRuleRuntime(Netshot.config()), new PyRuleRuntime(Netshot.config()), TestDrivers.registry(),
				TestRepositories.deviceReads(TestWiring.get().transactions())), TestRepositories.DEVICE);
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
		new TaskRepository().add(dsl, TakeSnapshotTask.of(TestRepositories.DEVICE.find(dsl, deviceId), "snapshot", "tester", true, false, false)
			.toBuilder()
			.status(status)
			.creationDate(when)
			.changeDate(when)
			.scheduleReference(when)
			.executionDate(when)
			.build());
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

	// ------------------------------------------------------------------
	// export
	// ------------------------------------------------------------------

	/** Records the calls it receives. */
	private static final class RecordingSink implements ReportExportSink {

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

	/**
	 * Collapses the repeated rows of the events, keeping the order of the sections.
	 * @param events the recorded events
	 * @return the sections, in order
	 */
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
