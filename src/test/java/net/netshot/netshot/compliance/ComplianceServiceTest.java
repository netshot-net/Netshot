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
package net.netshot.netshot.compliance;

import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.testing.TestDevices;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.RuleService.Replacement;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDrivers;

/**
 * Tests of the compliance use cases (policies, rules, software and hardware rules), against a
 * real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Compliance service")
class ComplianceServiceTest {

	private final AtomicInteger counter = new AtomicInteger();
	private PolicyService policyService;
	private RuleService ruleService;
	private SoftwareRuleService softwareRuleService;
	private HardwareRuleService hardwareRuleService;
	private Transactions transactions;
	private long groupId;
	private long otherGroupId;
	private long deviceId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		PolicyRepository policyRepository = new PolicyRepository();
		this.policyService = new PolicyService(this.transactions, policyRepository);
		this.ruleService = new RuleService(this.transactions, new RuleRepository(), policyRepository,
			RuleEvaluators.standard(new JsRuleRuntime(Netshot.config()), new PyRuleRuntime(Netshot.config()), TestDrivers.registry(), TestWiring.get().transactions()));
		this.softwareRuleService = new SoftwareRuleService(this.transactions, new SoftwareRuleRepository(), TestDrivers.registry());
		this.hardwareRuleService = new HardwareRuleService(this.transactions, new HardwareRuleRepository(), TestDrivers.registry());
		this.groupId = TestDevices.addStaticGroup("Group").id();
		this.otherGroupId = TestDevices.addStaticGroup("Other group").id();
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		Device device = TestDevices.add(Device.create("CiscoIOS12", "10.30.0.1", domain, "seed").toBuilder().name("router-a").build());
		TestDevices.addMembers(StaticDeviceGroup.builder().id(this.groupId).build(), device);
		this.deviceId = device.id();
	}

	private String name(String prefix) {
		return prefix + " " + this.counter.incrementAndGet();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private Policy createPolicy(String name, Long... groups) {
		return this.policyService.create(new PolicyDefinition(name, Set.of(groups)));
	}

	private Policy updatePolicy(long id, PolicyDefinition definition) {
		return this.policyService.update(id, _ -> definition, null);
	}

	private static TextRule textRule(String name, long policyId) {
		return TextRule.builder(name, policyId).text("ntp server").field("runningConfig").build();
	}

	// ------------------------------------------------------------------
	// policies
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Policy create() stores the trimmed name and the target groups, readable once detached")
	void policyCreate() {
		Policy policy = this.createPolicy("  " + this.name("Policy") + " ", this.groupId, this.otherGroupId);
		Assertions.assertFalse(policy.name().startsWith(" "));
		Assertions.assertEquals(2, policy.targetGroupIds().size());
		Policy listed = this.policyService.list(Page.ALL).stream().map(PolicyView::policy).filter(p -> p.id() == policy.id()).findFirst().orElseThrow();
		Assertions.assertEquals(policy.name(), listed.name());
		Assertions.assertEquals(Set.of(this.groupId, this.otherGroupId),
			Set.of(listed.targetGroupIds().stream().toArray(Long[]::new)), "groups loaded by the list");
	}

	@Test
	@DisplayName("Policy create() refuses a blank name and a duplicate name")
	void policyCreateErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_POLICY_NAME, () -> this.createPolicy("   "));
		String name = this.name("Duplicate");
		this.createPolicy(name);
		assertError(ErrorCode.NETSHOT_DUPLICATE_POLICY, () -> this.createPolicy(name));
	}

	@Test
	@DisplayName("Policy list() applies the page")
	void policyList() {
		this.createPolicy(this.name("Paged"));
		this.createPolicy(this.name("Paged"));
		Assertions.assertEquals(1, this.policyService.list(new Page(0, 1)).size());
		Assertions.assertTrue(this.policyService.list(Page.ALL).size() >= 2);
	}

	@Test
	@DisplayName("Policy update() renames the policy and replaces its groups, and drops the results of the groups removed")
	void policyUpdate() {
		Policy policy = this.createPolicy(this.name("To update"), this.groupId);
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		this.transactions.run(dsl -> new RuleRepository().saveCheckResults(dsl,
			List.of(CheckResult.of(rule.id(), this.deviceId, ResultOption.NONCONFORMING, "bad"))));
		Assertions.assertEquals(1, this.countResults(rule.id()));
		Policy updated = this.updatePolicy(policy.id(), new PolicyDefinition("  Renamed " + policy.id() + " ", Set.of(this.otherGroupId)));
		Assertions.assertEquals("Renamed " + policy.id(), updated.name());
		Assertions.assertEquals(List.of(this.otherGroupId), updated.targetGroupIds().stream().toList());
		Assertions.assertEquals(0, this.countResults(rule.id()), "the device belongs to the group removed from the policy");
	}

	@Test
	@DisplayName("Policy update() keeps the results of the groups which stay")
	void policyUpdateKeepsResults() {
		Policy policy = this.createPolicy(this.name("To keep"), this.groupId);
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		this.transactions.run(dsl -> new RuleRepository().saveCheckResults(dsl,
			List.of(CheckResult.of(rule.id(), this.deviceId, ResultOption.CONFORMING, "ok"))));
		this.updatePolicy(policy.id(), new PolicyDefinition(policy.name(), Set.of(this.groupId, this.otherGroupId)));
		Assertions.assertEquals(1, this.countResults(rule.id()));
	}

	private long countResults(long ruleId) {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.CHECK_RESULT, Tables.CHECK_RESULT.RULE.eq(ruleId)));
	}

	@Test
	@DisplayName("Policy update() reports an unknown policy, a blank name (nothing changed) and a duplicate name")
	void policyUpdateErrors() {
		assertError(ErrorCode.NETSHOT_POLICY_NOT_FOUND,
			() -> this.updatePolicy(99999999L, new PolicyDefinition("x", Set.of())));
		Policy policy = this.createPolicy(this.name("Update errors"), this.groupId);
		assertError(ErrorCode.NETSHOT_INVALID_POLICY_NAME,
			() -> this.updatePolicy(policy.id(), new PolicyDefinition(" ", Set.of())));
		Policy other = this.createPolicy(this.name("Other"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_POLICY,
			() -> this.updatePolicy(other.id(), new PolicyDefinition(policy.name(), Set.of())));
		Policy reread = this.policyService.list(Page.ALL).stream().map(PolicyView::policy).filter(p -> p.id() == policy.id()).findFirst().orElseThrow();
		Assertions.assertEquals(1, reread.targetGroupIds().size(), "the failed update left the groups alone");
	}

	@Test
	@DisplayName("Policy delete() removes the policy with its rules, and reports an unknown one")
	void policyDelete() {
		Policy policy = this.createPolicy(this.name("To delete"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		this.policyService.delete(policy.id(), null);
		assertError(ErrorCode.NETSHOT_POLICY_NOT_FOUND, () -> this.policyService.delete(policy.id(), null));
		Assertions.assertEquals(0, this.countResults(rule.id()));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.listOfPolicy(policy.id(), Page.ALL));
	}

	@Test
	@DisplayName("Policy create() and update() refuse a group which doesn't exist, and change nothing")
	void policyUnknownGroup() {
		String name = this.name("Unknown group");
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.createPolicy(name, 99999999L));
		Assertions.assertTrue(this.policyService.list(Page.ALL).stream().map(PolicyView::policy).noneMatch(p -> p.name().equals(name)));
		Policy policy = this.createPolicy(name, this.groupId);
		assertError(ErrorCode.NETSHOT_INVALID_GROUP,
			() -> this.updatePolicy(policy.id(), new PolicyDefinition(name, Set.of(this.groupId, 99999999L))));
		Policy listed = this.policyService.list(Page.ALL).stream().map(PolicyView::policy).filter(p -> p.id() == policy.id()).findFirst().orElseThrow();
		Assertions.assertEquals(Set.of(this.groupId), listed.targetGroupIds().stream().collect(Collectors.toSet()));
	}

	// ------------------------------------------------------------------
	// rules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Rule create() stores the three kinds of rules as given")
	void ruleCreate() {
		Policy policy = this.createPolicy(this.name("Rules"));
		Rule text = this.ruleService.create(TextRule.builder(this.name("Text"), policy.id()).enabled(true).text("ntp").regExp(true)
			.context("ctx").deviceDriver("CiscoIOS12").field("runningConfig").anyBlock(true).matchAll(false).invert(true).normalize(true)
			.build());
		TextRule textRule = Assertions.assertInstanceOf(TextRule.class, text);
		Assertions.assertTrue(textRule.enabled());
		Assertions.assertEquals("ntp", textRule.text());
		Assertions.assertTrue(textRule.regExp());
		Assertions.assertEquals("ctx", textRule.context());
		Assertions.assertEquals("CiscoIOS12", textRule.deviceDriver());
		Assertions.assertEquals("runningConfig", textRule.field());
		Assertions.assertTrue(textRule.anyBlock());
		Assertions.assertFalse(textRule.matchAll());
		Assertions.assertTrue(textRule.invert());
		Assertions.assertTrue(textRule.normalize());
		Rule js = this.ruleService.create(new JavaScriptRule(0, 0, null, this.name("JS"), false, policy.id(),
			"function check(d) { return CONFORMING; }"));
		Assertions.assertEquals("function check(d) { return CONFORMING; }", Assertions.assertInstanceOf(JavaScriptRule.class, js).script());
		Rule py = this.ruleService.create(PythonRule.of(this.name("Py"), policy.id()));
		Assertions.assertTrue(Assertions.assertInstanceOf(PythonRule.class, py).script().length() > 0, "the template script is kept");
		Assertions.assertEquals(3, this.ruleService.listOfPolicy(policy.id(), Page.ALL).size());
		Assertions.assertEquals(1, this.ruleService.listOfPolicy(policy.id(), new Page(1, 1)).size());
		List<RuleSummary> summaries = this.ruleService.list(Page.ALL);
		Assertions.assertTrue(summaries.stream().anyMatch(s -> s.id() == js.id() && "JavaScriptRule".equals(s.type())
			&& s.policyId() == policy.id() && !s.enabled()));
	}

	@Test
	@DisplayName("Rule create() reports a blank name, an unknown policy (in this order) and a duplicate name in the policy")
	void ruleCreateErrors() {
		Policy policy = this.createPolicy(this.name("Rule errors"));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME, () -> this.ruleService.create(textRule(null, policy.id())));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME, () -> this.ruleService.create(textRule("  ", 99999999L)));
		NetshotException unknownPolicy = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
			() -> this.ruleService.create(textRule("x", 99999999L)));
		Assertions.assertEquals("policy", unknownPolicy.getTarget());
		String name = this.name("Dup");
		this.ruleService.create(textRule(name, policy.id()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_RULE, () -> this.ruleService.create(textRule(name, policy.id())));
		Policy other = this.createPolicy(this.name("Other policy"));
		this.ruleService.create(textRule(name, other.id()));
	}

	@Test
	@DisplayName("Rule getOfPolicy() and listOfPolicy() report the unknown policy and the unknown rule")
	void ruleReadErrors() {
		Policy policy = this.createPolicy(this.name("Read"));
		Policy other = this.createPolicy(this.name("Read other"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		Assertions.assertEquals(rule.id(), this.ruleService.getOfPolicy(policy.id(), rule.id()).id());
		assertError(ErrorCode.NETSHOT_POLICY_NOT_FOUND, () -> this.ruleService.getOfPolicy(99999999L, rule.id()));
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.ruleService.getOfPolicy(other.id(), rule.id()));
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.ruleService.getOfPolicy(policy.id(), 99999999L));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.listOfPolicy(99999999L, Page.ALL));
		Assertions.assertTrue(this.ruleService.listOfPolicy(other.id(), Page.ALL).isEmpty());
	}

	@Test
	@DisplayName("Rule update() replaces the definition, keeps the exemptions unless given, and replaces all of them when given")
	void ruleUpdate() {
		Policy policy = this.createPolicy(this.name("Replace"));
		TextRule rule = (TextRule) this.ruleService.create(TextRule.builder(this.name("To replace"), policy.id())
			.text("text").regExp(true).context("ctx").deviceDriver("CiscoIOS12").field("runningConfig").build());
		Instant expiration = Instant.ofEpochMilli(1_700_000_000_000L);
		TextRule replaced = (TextRule) this.ruleService.update(rule.id(),
			current -> new Replacement(((TextRule) current).toBuilder().enabled(true).text("new text").build(),
				Map.of(this.deviceId, expiration)), null);
		Assertions.assertEquals(rule.name(), replaced.name());
		Assertions.assertTrue(replaced.enabled());
		Assertions.assertEquals("new text", replaced.text());
		Assertions.assertTrue(replaced.regExp());
		Assertions.assertEquals(rule.version() + 1, replaced.version(), "one change of the rule");
		List<ExemptedDevice> exempted = this.ruleService.listExemptedDevices(rule.id(), Page.ALL);
		Assertions.assertEquals(1, exempted.size());
		Assertions.assertEquals(this.deviceId, exempted.get(0).device().id());
		Assertions.assertEquals("router-a", exempted.get(0).device().name());
		Assertions.assertEquals(Date.from(expiration), exempted.get(0).expirationDate());

		// Without exemptions, they are kept
		Rule renamed = this.ruleService.update(rule.id(),
			current -> Replacement.of(((TextRule) current).toBuilder().name(this.name("Renamed")).build()), null);
		Assertions.assertEquals(1, this.ruleService.listExemptedDevices(rule.id(), Page.ALL).size());

		// All the exemptions are replaced
		this.ruleService.update(rule.id(), current -> new Replacement(current, Map.of()), (long) renamed.version());
		Assertions.assertTrue(this.ruleService.listExemptedDevices(rule.id(), Page.ALL).isEmpty());
		Assertions.assertTrue(this.ruleService.listExemptedDevices(99999999L, Page.ALL).isEmpty());
	}

	@Test
	@DisplayName("Rule update() refuses a change of the kind or of the policy of the rule")
	void ruleUpdateKindAndPolicy() {
		Policy policy = this.createPolicy(this.name("Kind"));
		Policy other = this.createPolicy(this.name("Kind other"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		NetshotException kind = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.ruleService.update(rule.id(),
			_ -> Replacement.of(JavaScriptRule.of(rule.name(), policy.id())), null));
		Assertions.assertEquals("type", kind.getTarget());
		NetshotException policyChange = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.ruleService.update(
			rule.id(), _ -> Replacement.of(textRule(rule.name(), other.id())), null));
		Assertions.assertEquals("policy", policyChange.getTarget());
		// A policy of 0 is the current one
		Assertions.assertEquals(policy.id(), this.ruleService.update(rule.id(), _ -> Replacement.of(textRule(rule.name(), 0)), null)
			.policyId());
	}

	@Test
	@DisplayName("Rule update() reports an unknown rule, a blank name, an unknown exempted device and a duplicate name; nothing is changed")
	void ruleUpdateErrors() {
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND,
			() -> this.ruleService.update(99999999L, current -> Replacement.of(current), null));
		Policy policy = this.createPolicy(this.name("Replace errors"));
		String name = this.name("Rule");
		Rule rule = this.ruleService.create(textRule(name, policy.id()));
		Instant expiration = Instant.ofEpochMilli(1_700_000_000_000L);
		this.ruleService.replaceExemptions(rule.id(), Map.of(this.deviceId, expiration), null);
		Rule other = this.ruleService.create(textRule(this.name("Other rule"), policy.id()));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME,
			() -> this.ruleService.update(rule.id(), _ -> Replacement.of(textRule(" ", 0)), null));
		Map<Long, Instant> unknownDevice = new HashMap<>();
		unknownDevice.put(99999999L, null);
		NetshotException exempted = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
			() -> this.ruleService.update(rule.id(), _ -> new Replacement(textRule("renamed", 0), unknownDevice), null));
		Assertions.assertEquals("exemptions", exempted.getTarget());
		assertError(ErrorCode.NETSHOT_DUPLICATE_RULE,
			() -> this.ruleService.update(other.id(), _ -> new Replacement(textRule(name, 0), Map.of()), null));
		Rule reread = this.ruleService.getOfPolicy(policy.id(), rule.id());
		Assertions.assertEquals(name, reread.name());
		Assertions.assertEquals(rule.version() + 1, reread.version(), "only the exemptions were changed");
		Assertions.assertEquals(1, this.ruleService.listExemptedDevices(rule.id(), Page.ALL).size());
	}

	@Test
	@DisplayName("Rule delete() removes the rule, and reports an unknown one")
	void ruleDelete() {
		Policy policy = this.createPolicy(this.name("Delete"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		this.ruleService.delete(rule.id(), null);
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.ruleService.delete(rule.id(), null));
		Assertions.assertTrue(this.ruleService.listOfPolicy(policy.id(), Page.ALL).isEmpty());
	}

	@Test
	@DisplayName("Rule test() refuses an unknown device, and reports a device without configuration as not applicable")
	void ruleTestUnknownDevice() {
		Rule rule = TextRule.builder("Test", 0).text("ntp server").field("runningConfig").enabled(true).build();
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.ruleService.test(99999999L, rule));
		RuleTestResult result = this.ruleService.test(this.deviceId, rule);
		Assertions.assertEquals(ResultOption.NOTAPPLICABLE, result.result());
		Assertions.assertEquals("The device has no configuration", result.comment());
		Assertions.assertNull(result.log());
	}

	@Test
	@DisplayName("Device results lists nothing for a device which doesn't exist or is not a member of a policy group")
	void deviceResults() {
		Assertions.assertTrue(this.ruleService.listDeviceResults(99999999L, Page.ALL).isEmpty());
		Policy policy = this.createPolicy(this.name("Results"));
		this.ruleService.create(textRule(this.name("Rule"), policy.id()));
		Assertions.assertTrue(this.ruleService.listDeviceResults(this.deviceId, Page.ALL).stream()
			.noneMatch(r -> r.policyId() == policy.id()));
		Policy policyWithGroup = this.createPolicy(this.name("Results group"), this.groupId);
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policyWithGroup.id()));
		List<DeviceRuleResult> results = this.ruleService.listDeviceResults(this.deviceId, Page.ALL);
		DeviceRuleResult result = results.stream().filter(r -> r.ruleId() == rule.id()).findFirst().orElseThrow();
		Assertions.assertEquals(policyWithGroup.name(), result.policyName());
		Assertions.assertNull(result.result(), "never checked");
		Assertions.assertNull(result.expirationDate());
	}

	// ------------------------------------------------------------------
	// software rules
	// ------------------------------------------------------------------

	private static SoftwareRule software(Long groupId, String driver, String version) {
		return new SoftwareRule(0, 0, null, 0, groupId, driver, "", false, version, false, "", false, ConformanceLevel.SILVER);
	}

	@Test
	@DisplayName("Software rule create() takes the lowest priority, or the given position; an unknown driver or group is refused")
	void softwareCreate() {
		RankedSoftwareRule ranked = this.softwareRuleService.create(software(this.groupId, "CiscoIOS12", "1.0"), null);
		SoftwareRule rule = ranked.rule();
		Assertions.assertEquals(this.groupId, rule.targetGroupId());
		Assertions.assertEquals("CiscoIOS12", rule.driver());
		Assertions.assertEquals(ConformanceLevel.SILVER, rule.level());
		Assertions.assertEquals(Double.MAX_VALUE, rule.priority(), "the lowest priority, until the rules are sorted");
		NetshotException driver = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
			() -> this.softwareRuleService.create(software(null, "NoSuchDriver", "2.0"), null));
		Assertions.assertEquals("driver", driver.getTarget());
		NetshotException group = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
			() -> this.softwareRuleService.create(software(99999999L, null, "3.0"), null));
		Assertions.assertEquals("group", group.getTarget());
		RankedSoftwareRule first = this.softwareRuleService.create(software(null, null, "4.0"), 1);
		Assertions.assertEquals(1, first.rank());
		Assertions.assertEquals(first.rule().id(), this.softwareOrder().get(0));
		List<SoftwareRuleView> rules = this.softwareRuleService.list(Page.ALL);
		SoftwareRuleView listedView = rules.stream().filter(r -> r.rule().id() == rule.id()).findFirst().orElseThrow();
		Assertions.assertEquals("Group", listedView.targetGroup().name(), "the group is loaded by the list");
		Assertions.assertEquals(1, this.softwareRuleService.list(new Page(0, 1)).size());
	}

	@Test
	@DisplayName("Software rule update() replaces all the properties and keeps the priority, delete() reports an unknown rule")
	void softwareUpdateDelete() {
		SoftwareRule rule = this.softwareRuleService.create(software(this.groupId, "CiscoIOS12", "1.0"), null).rule();
		SoftwareRule updated = this.softwareRuleService.update(rule.id(), _ -> new SoftwareRuleService.Replacement(
			new SoftwareRule(0, 0, null, 0, null, null, "fam", true, "2.0", true, "pn", true, ConformanceLevel.BRONZE), null), null).rule();
		Assertions.assertNull(updated.targetGroupId());
		Assertions.assertNull(updated.driver());
		Assertions.assertEquals("fam", updated.family());
		Assertions.assertTrue(updated.familyRegExp());
		Assertions.assertEquals("2.0", updated.softwareVersion());
		Assertions.assertTrue(updated.softwareVersionRegExp());
		Assertions.assertEquals("pn", updated.partNumber());
		Assertions.assertTrue(updated.partNumberRegExp());
		Assertions.assertEquals(ConformanceLevel.BRONZE, updated.level());
		Assertions.assertEquals(rule.priority(), updated.priority());
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND,
			() -> this.softwareRuleService.update(99999999L, r -> new SoftwareRuleService.Replacement(r.rule(), null), null));
		this.softwareRuleService.delete(rule.id(), null);
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.softwareRuleService.delete(rule.id(), null));
	}

	private List<Long> softwareOrder() {
		return this.softwareRuleService.list(Page.ALL).stream().map(v -> v.rule().id()).toList();
	}

	// Moves a software rule to a position, without changing it
	private void moveSoftware(long id, int position) {
		this.softwareRuleService.update(id, r -> new SoftwareRuleService.Replacement(r.rule(), position), null);
	}

	@Test
	@DisplayName("Software rule update() moves a rule to the given position (clamped), and renumbers the priorities")
	void softwareMove() {
		long a = this.softwareRuleService.create(software(null, null, "a"), null).rule().id();
		long b = this.softwareRuleService.create(software(null, null, "b"), null).rule().id();
		long c = this.softwareRuleService.create(software(null, null, "c"), null).rule().id();
		// The new rules all have the same priority: move them to get a known order
		this.moveSoftware(a, Integer.MAX_VALUE);
		this.moveSoftware(b, Integer.MAX_VALUE);
		this.moveSoftware(c, Integer.MAX_VALUE);
		List<Long> initial = this.softwareOrder();
		Assertions.assertEquals(List.of(a, b, c), initial.subList(initial.size() - 3, initial.size()));

		this.moveSoftware(c, initial.size() - 2);
		List<Long> order = this.softwareOrder();
		Assertions.assertEquals(List.of(c, a, b), order.subList(order.size() - 3, order.size()));
		Assertions.assertEquals(10.0 * order.size(), this.softwareRuleService.list(Page.ALL).get(order.size() - 1).rule().priority());

		this.moveSoftware(c, Integer.MAX_VALUE);
		order = this.softwareOrder();
		Assertions.assertEquals(List.of(a, b, c), order.subList(order.size() - 3, order.size()));
	}

	// ------------------------------------------------------------------
	// hardware rules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Hardware rule create(), update(), list() and delete()")
	void hardwareRules() {
		Instant sale = Instant.ofEpochMilli(1_700_000_000_000L);
		Instant life = Instant.ofEpochMilli(1_800_000_000_000L);
		HardwareRule rule = this.hardwareRuleService.create(
			new HardwareRule(0, 0, null, "CiscoIOS12", this.groupId, "fam", false, "pn", true, sale, life));
		Assertions.assertEquals(this.groupId, rule.targetGroupId());
		Assertions.assertEquals("CiscoIOS12", rule.driver());
		Assertions.assertEquals(sale, rule.endOfSale());
		Assertions.assertEquals(life, rule.endOfLife());
		HardwareRuleView listedView = this.hardwareRuleService.list().stream().filter(r -> r.rule().id() == rule.id()).findFirst().orElseThrow();
		Assertions.assertEquals("Group", listedView.targetGroup().name(), "the group is loaded by the list");

		// A group or a driver which doesn't exist is refused, and nothing is changed
		NetshotException group = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.hardwareRuleService.create(
			new HardwareRule(0, 0, null, null, 99999999L, "", false, "x", false, null, null)));
		Assertions.assertEquals("group", group.getTarget());
		NetshotException driver = assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.hardwareRuleService.update(
			rule.id(), _ -> new HardwareRule(0, 0, null, "NoSuchDriver", null, "", false, "x", false, null, null), null));
		Assertions.assertEquals("driver", driver.getTarget());
		Assertions.assertEquals("pn", this.hardwareRuleService.get(rule.id()).partNumber());

		HardwareRule updated = this.hardwareRuleService.update(rule.id(),
			_ -> new HardwareRule(0, 0, null, null, null, "", true, "other", false, null, life), null);
		Assertions.assertNull(updated.targetGroupId());
		Assertions.assertNull(updated.driver());
		Assertions.assertNull(updated.endOfSale());
		Assertions.assertEquals(life, updated.endOfLife());
		Assertions.assertEquals("other", updated.partNumber());
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.hardwareRuleService.update(99999999L, r -> r, null));
		this.hardwareRuleService.delete(rule.id(), null);
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.hardwareRuleService.delete(rule.id(), null));
	}
}
