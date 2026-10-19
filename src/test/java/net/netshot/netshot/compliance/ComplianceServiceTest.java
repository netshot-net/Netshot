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
package net.netshot.netshot.compliance;

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

import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;

/**
 * Tests of the compliance use cases (policies, rules, software and hardware rules), against a
 * real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
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
		this.transactions = new Transactions();
		PolicyRepository policyRepository = new PolicyRepository();
		this.policyService = new PolicyService(this.transactions, policyRepository);
		this.ruleService = new RuleService(this.transactions, new RuleRepository(), policyRepository,
			RuleEvaluators.standard(new JsRuleRuntime(), new PyRuleRuntime(), TestDrivers.registry()));
		this.softwareRuleService = new SoftwareRuleService(this.transactions, new SoftwareRuleRepository(), TestDrivers.registry());
		this.hardwareRuleService = new HardwareRuleService(this.transactions, new HardwareRuleRepository(), TestDrivers.registry());
		this.groupId = this.transactions.write(session -> {
			StaticDeviceGroup group = new StaticDeviceGroup("Group");
			session.persist(group);
			return group.getId();
		});
		this.otherGroupId = this.transactions.write(session -> {
			StaticDeviceGroup group = new StaticDeviceGroup("Other group");
			session.persist(group);
			return group.getId();
		});
		this.deviceId = this.transactions.write(session -> {
			Domain domain = new Domain("Lab", "Test domain", null, null);
			session.persist(domain);
			Device device = new Device("CiscoIOS12", "10.30.0.1", domain, "seed");
			device.setName("router-a");
			session.persist(device);
			StaticDeviceGroup group = session.get(StaticDeviceGroup.class, this.groupId);
			group.addDevice(device);
			session.merge(group);
			return device.getId();
		});
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

	private static RuleDefinition textRule(String name, long policyId) {
		return new RuleDefinition("TextRule", name, policyId, false, Map.of(), null, "ntp server", null, null, null,
			"runningConfig", null, null, null, null);
	}

	private static RuleDefinition update(String name, boolean enabled, Map<Long, Date> exemptions) {
		return new RuleDefinition("TextRule", name, 0, enabled, exemptions, null, null, null, null, null, null, null, null,
			null, null);
	}

	// ------------------------------------------------------------------
	// policies
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Policy create() stores the trimmed name and the target groups, readable once detached")
	void policyCreate() {
		Policy policy = this.createPolicy("  " + this.name("Policy") + " ", this.groupId, this.otherGroupId);
		Assertions.assertFalse(policy.getName().startsWith(" "));
		Assertions.assertEquals(2, policy.getTargetGroups().size());
		Policy listed = this.policyService.list(Page.ALL).stream().filter(p -> p.getId() == policy.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(policy.getName(), listed.getName());
		Assertions.assertEquals(Set.of(this.groupId, this.otherGroupId),
			Set.of(listed.getTargetGroups().stream().map(g -> g.getId()).toArray(Long[]::new)), "groups loaded by the list");
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
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		this.transactions.run(session -> {
			Device device = session.get(Device.class, this.deviceId);
			session.persist(new CheckResult(session.get(Rule.class, rule.getId()), device, ResultOption.NONCONFORMING, "bad"));
		});
		Assertions.assertEquals(1, this.countResults(rule.getId()));
		Policy updated = this.policyService.update(policy.getId(), new PolicyDefinition("  Renamed " + policy.getId() + " ", Set.of(this.otherGroupId)));
		Assertions.assertEquals("Renamed " + policy.getId(), updated.getName());
		Assertions.assertEquals(List.of(this.otherGroupId), updated.getTargetGroups().stream().map(g -> g.getId()).toList());
		Assertions.assertEquals(0, this.countResults(rule.getId()), "the device belongs to the group removed from the policy");
	}

	@Test
	@DisplayName("Policy update() keeps the results of the groups which stay")
	void policyUpdateKeepsResults() {
		Policy policy = this.createPolicy(this.name("To keep"), this.groupId);
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		this.transactions.run(session -> session.persist(new CheckResult(session.get(Rule.class, rule.getId()),
			session.get(Device.class, this.deviceId), ResultOption.CONFORMING, "ok")));
		this.policyService.update(policy.getId(), new PolicyDefinition(policy.getName(), Set.of(this.groupId, this.otherGroupId)));
		Assertions.assertEquals(1, this.countResults(rule.getId()));
	}

	private long countResults(long ruleId) {
		return this.transactions.read(session -> session
			.createQuery("select count(cr) from CheckResult cr where cr.key.rule.id = :id", Long.class)
			.setParameter("id", ruleId).uniqueResult());
	}

	@Test
	@DisplayName("Policy update() reports an unknown policy, a blank name (nothing changed) and a duplicate name")
	void policyUpdateErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_POLICY,
			() -> this.policyService.update(99999999L, new PolicyDefinition("x", Set.of())));
		Policy policy = this.createPolicy(this.name("Update errors"), this.groupId);
		assertError(ErrorCode.NETSHOT_INVALID_POLICY_NAME,
			() -> this.policyService.update(policy.getId(), new PolicyDefinition(" ", Set.of())));
		Policy other = this.createPolicy(this.name("Other"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_POLICY,
			() -> this.policyService.update(other.getId(), new PolicyDefinition(policy.getName(), Set.of())));
		Policy reread = this.policyService.list(Page.ALL).stream().filter(p -> p.getId() == policy.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(1, reread.getTargetGroups().size(), "the failed update left the groups alone");
	}

	@Test
	@DisplayName("Policy delete() tells whether the policy existed, and removes its rules")
	void policyDelete() {
		Policy policy = this.createPolicy(this.name("To delete"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		Assertions.assertTrue(this.policyService.delete(policy.getId()));
		Assertions.assertFalse(this.policyService.delete(policy.getId()));
		Assertions.assertEquals(0, this.countResults(rule.getId()));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.listOfPolicy(policy.getId(), Page.ALL));
	}

	@Test
	@DisplayName("Policy create() and update() refuse a group which doesn't exist, and change nothing")
	void policyUnknownGroup() {
		String name = this.name("Unknown group");
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.createPolicy(name, 99999999L));
		Assertions.assertTrue(this.policyService.list(Page.ALL).stream().noneMatch(p -> p.getName().equals(name)));
		Policy policy = this.createPolicy(name, this.groupId);
		assertError(ErrorCode.NETSHOT_INVALID_GROUP,
			() -> this.policyService.update(policy.getId(), new PolicyDefinition(name, Set.of(this.groupId, 99999999L))));
		Policy listed = this.policyService.list(Page.ALL).stream().filter(p -> p.getId() == policy.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(Set.of(this.groupId), listed.getTargetGroups().stream().map(g -> g.getId()).collect(Collectors.toSet()));
	}

	// ------------------------------------------------------------------
	// rules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Rule create() stores the three kinds of rules, disabled, with the trimmed name and script")
	void ruleCreate() {
		Policy policy = this.createPolicy(this.name("Rules"));
		Rule text = this.ruleService.create(new RuleDefinition("TextRule", " " + this.name("Text") + " ", policy.getId(), true,
			Map.of(), "ignored", "ntp", true, "ctx", "CiscoIOS12", "runningConfig", true, false, true, true));
		TextRule textRule = Assertions.assertInstanceOf(TextRule.class, text);
		Assertions.assertFalse(textRule.getName().startsWith(" "));
		Assertions.assertFalse(textRule.isEnabled(), "a new rule is disabled");
		Assertions.assertEquals("ntp", textRule.getText());
		Assertions.assertTrue(textRule.isRegExp());
		Assertions.assertEquals("ctx", textRule.getContext());
		Assertions.assertEquals("CiscoIOS12", textRule.getDeviceDriver());
		Assertions.assertEquals("runningConfig", textRule.getField());
		Assertions.assertTrue(textRule.isAnyBlock());
		Assertions.assertFalse(textRule.isMatchAll());
		Assertions.assertTrue(textRule.isInvert());
		Assertions.assertTrue(textRule.isNormalize());
		Rule js = this.ruleService.create(new RuleDefinition("JavaScriptRule", this.name("JS"), policy.getId(), false, Map.of(),
			"  function check(d) { return CONFORMING; }  ", null, null, null, null, null, null, null, null, null));
		Assertions.assertEquals("function check(d) { return CONFORMING; }", Assertions.assertInstanceOf(JavaScriptRule.class, js).getScript());
		Rule py = this.ruleService.create(new RuleDefinition("PythonRule", this.name("Py"), policy.getId(), false, Map.of(),
			null, null, null, null, null, null, null, null, null, null));
		Assertions.assertInstanceOf(PythonRule.class, py);
		Assertions.assertTrue(Assertions.assertInstanceOf(PythonRule.class, py).getScript().length() > 0, "the template script is kept");
		Assertions.assertEquals(3, this.ruleService.listOfPolicy(policy.getId(), Page.ALL).size());
		Assertions.assertEquals(1, this.ruleService.listOfPolicy(policy.getId(), new Page(1, 1)).size());
		List<RuleSummary> summaries = this.ruleService.list(Page.ALL);
		Assertions.assertTrue(summaries.stream().anyMatch(s -> s.id() == js.getId() && "JavaScriptRule".equals(s.type())
			&& s.policyId() == policy.getId() && !s.enabled()));
	}

	@Test
	@DisplayName("Rule create() reports a blank name, an unknown policy, an invalid type (in this order) and a duplicate")
	void ruleCreateErrors() {
		Policy policy = this.createPolicy(this.name("Rule errors"));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME, () -> this.ruleService.create(textRule(null, policy.getId())));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME, () -> this.ruleService.create(textRule("  ", 99999999L)));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.create(textRule("x", 99999999L)));
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.ruleService.create(
			new RuleDefinition("NoSuchRule", "x", policy.getId(), false, Map.of(), null, null, null, null, null, null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.create(
			new RuleDefinition("NoSuchRule", "x", 99999999L, false, Map.of(), null, null, null, null, null, null, null, null, null, null)));
		String name = this.name("Dup");
		this.ruleService.create(textRule(name, policy.getId()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_RULE, () -> this.ruleService.create(textRule(name, policy.getId())));
		Policy other = this.createPolicy(this.name("Other policy"));
		this.ruleService.create(textRule(name, other.getId()));
	}

	@Test
	@DisplayName("Rule getOfPolicy() and listOfPolicy() report the unknown policy and the unknown rule")
	void ruleReadErrors() {
		Policy policy = this.createPolicy(this.name("Read"));
		Policy other = this.createPolicy(this.name("Read other"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		Assertions.assertEquals(rule.getId(), this.ruleService.getOfPolicy(policy.getId(), rule.getId()).getId());
		assertError(ErrorCode.NETSHOT_POLICY_NOT_FOUND, () -> this.ruleService.getOfPolicy(99999999L, rule.getId()));
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.ruleService.getOfPolicy(other.getId(), rule.getId()));
		assertError(ErrorCode.NETSHOT_RULE_NOT_FOUND, () -> this.ruleService.getOfPolicy(policy.getId(), 99999999L));
		assertError(ErrorCode.NETSHOT_INVALID_POLICY, () -> this.ruleService.listOfPolicy(99999999L, Page.ALL));
		Assertions.assertTrue(this.ruleService.listOfPolicy(other.getId(), Page.ALL).isEmpty());
	}

	@Test
	@DisplayName("Rule update() changes only the provided properties of the kind of the rule, and replaces the exemptions")
	void ruleUpdate() {
		Policy policy = this.createPolicy(this.name("Update"));
		TextRule rule = (TextRule) this.ruleService.create(new RuleDefinition("TextRule", this.name("To update"), policy.getId(),
			false, Map.of(), null, "text", true, "ctx", "CiscoIOS12", "runningConfig", true, true, true, true));
		Date expiration = new Date(1_700_000_000_000L);
		TextRule updated = (TextRule) this.ruleService.update(rule.getId(), update(null, true, Map.of(this.deviceId, expiration)));
		Assertions.assertEquals(rule.getName(), updated.getName(), "a null name keeps the name");
		Assertions.assertTrue(updated.isEnabled());
		Assertions.assertEquals("text", updated.getText(), "a null property is left alone");
		Assertions.assertTrue(updated.isRegExp());
		Assertions.assertEquals("CiscoIOS12", updated.getDeviceDriver());
		List<ExemptedDevice> exempted = this.ruleService.listExemptedDevices(rule.getId(), Page.ALL);
		Assertions.assertEquals(1, exempted.size());
		Assertions.assertEquals(this.deviceId, exempted.get(0).device().id());
		Assertions.assertEquals("router-a", exempted.get(0).device().name());
		Assertions.assertEquals(expiration, exempted.get(0).expirationDate());

		// The exemption is kept and its date changed
		Date later = new Date(1_800_000_000_000L);
		this.ruleService.update(rule.getId(), update("  " + this.name("Renamed") + " ", false, Map.of(this.deviceId, later)));
		Assertions.assertEquals(later, this.ruleService.listExemptedDevices(rule.getId(), Page.ALL).get(0).expirationDate());
		Assertions.assertFalse(this.ruleService.getOfPolicy(policy.getId(), rule.getId()).isEnabled());

		// All the exemptions are replaced
		this.ruleService.update(rule.getId(), update(null, false, Map.of()));
		Assertions.assertTrue(this.ruleService.listExemptedDevices(rule.getId(), Page.ALL).isEmpty());
		Assertions.assertTrue(this.ruleService.listExemptedDevices(99999999L, Page.ALL).isEmpty());

		RuleDefinition script = new RuleDefinition("TextRule", null, 0, false, Map.of(), null, "new text", false, null, null, null,
			false, null, null, null);
		TextRule textOnly = (TextRule) this.ruleService.update(rule.getId(), script);
		Assertions.assertEquals("new text", textOnly.getText());
		Assertions.assertFalse(textOnly.isRegExp());
		Assertions.assertFalse(textOnly.isAnyBlock());
		Assertions.assertEquals("ctx", textOnly.getContext());
	}

	@Test
	@DisplayName("Rule update() sets the script of the script rules, trimmed")
	void ruleUpdateScript() {
		Policy policy = this.createPolicy(this.name("Update script"));
		Rule js = this.ruleService.create(new RuleDefinition("JavaScriptRule", this.name("JS"), policy.getId(), false, Map.of(),
			null, null, null, null, null, null, null, null, null, null));
		JavaScriptRule updated = (JavaScriptRule) this.ruleService.update(js.getId(),
			new RuleDefinition("JavaScriptRule", null, 0, true, Map.of(), "  // new\n", "ignored", null, null, null, null, null, null, null, null));
		Assertions.assertEquals("// new", updated.getScript());
		Assertions.assertTrue(updated.isEnabled());
		Rule py = this.ruleService.create(new RuleDefinition("PythonRule", this.name("Py"), policy.getId(), false, Map.of(),
			null, null, null, null, null, null, null, null, null, null));
		String before = ((PythonRule) py).getScript();
		PythonRule kept = (PythonRule) this.ruleService.update(py.getId(),
			new RuleDefinition("PythonRule", null, 0, false, Map.of(), null, null, null, null, null, null, null, null, null, null));
		Assertions.assertEquals(before, kept.getScript(), "no script provided");
	}

	@Test
	@DisplayName("Rule update() reports an unknown rule, a blank name, an unknown device and a duplicate name; nothing is changed")
	void ruleUpdateErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.ruleService.update(99999999L, update("x", false, Map.of())));
		Policy policy = this.createPolicy(this.name("Update errors"));
		String name = this.name("Rule");
		Rule rule = this.ruleService.create(textRule(name, policy.getId()));
		Rule other = this.ruleService.create(textRule(this.name("Other rule"), policy.getId()));
		assertError(ErrorCode.NETSHOT_INVALID_RULE_NAME, () -> this.ruleService.update(rule.getId(), update(" ", true, Map.of())));
		Map<Long, Date> unknownDevice = new HashMap<>();
		unknownDevice.put(99999999L, null);
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.ruleService.update(rule.getId(), update("renamed", true, unknownDevice)));
		assertError(ErrorCode.NETSHOT_DUPLICATE_RULE, () -> this.ruleService.update(other.getId(), update(name, true, Map.of())));
		Rule reread = this.ruleService.getOfPolicy(policy.getId(), rule.getId());
		Assertions.assertEquals(name, reread.getName());
		Assertions.assertFalse(reread.isEnabled());
	}

	@Test
	@DisplayName("Rule delete() tells whether the rule existed")
	void ruleDelete() {
		Policy policy = this.createPolicy(this.name("Delete"));
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		Assertions.assertTrue(this.ruleService.delete(rule.getId()));
		Assertions.assertFalse(this.ruleService.delete(rule.getId()));
		Assertions.assertTrue(this.ruleService.listOfPolicy(policy.getId(), Page.ALL).isEmpty());
	}

	@Test
	@DisplayName("Rule test() reports an unknown device as not applicable, without logs")
	void ruleTestUnknownDevice() {
		RuleTestResult result = this.ruleService.test(99999999L, textRule("Test", 0));
		Assertions.assertEquals(ResultOption.NOTAPPLICABLE, result.result());
		Assertions.assertEquals("Unable to find the device", result.comment());
		Assertions.assertNull(result.log());
		// The kind of rule is not checked before the device is found
		Assertions.assertEquals(ResultOption.NOTAPPLICABLE, this.ruleService.test(99999999L,
			new RuleDefinition("NoSuchRule", null, 0, false, Map.of(), null, null, null, null, null, null, null, null, null, null)).result());
		// A device without configuration is not found either
		Assertions.assertEquals(ResultOption.NOTAPPLICABLE, this.ruleService.test(this.deviceId, textRule("Test", 0)).result());
	}

	@Test
	@DisplayName("Device results lists nothing for a device which doesn't exist or is not a member of a policy group")
	void deviceResults() {
		Assertions.assertTrue(this.ruleService.listDeviceResults(99999999L, Page.ALL).isEmpty());
		Policy policy = this.createPolicy(this.name("Results"));
		this.ruleService.create(textRule(this.name("Rule"), policy.getId()));
		Assertions.assertTrue(this.ruleService.listDeviceResults(this.deviceId, Page.ALL).stream()
			.noneMatch(r -> r.policyId() == policy.getId()));
		Policy policyWithGroup = this.createPolicy(this.name("Results group"), this.groupId);
		Rule rule = this.ruleService.create(textRule(this.name("Rule"), policyWithGroup.getId()));
		List<DeviceRuleResult> results = this.ruleService.listDeviceResults(this.deviceId, Page.ALL);
		DeviceRuleResult result = results.stream().filter(r -> r.ruleId() == rule.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(policyWithGroup.getName(), result.policyName());
		Assertions.assertNull(result.result(), "never checked");
		Assertions.assertNull(result.expirationDate());
	}

	// ------------------------------------------------------------------
	// software rules
	// ------------------------------------------------------------------

	private static SoftwareRuleDefinition software(Long groupId, String driver, String version) {
		return new SoftwareRuleDefinition(groupId, driver, "", false, version, false, "", false, ConformanceLevel.SILVER);
	}

	@Test
	@DisplayName("Software rule create() takes the lowest priority; an unknown driver is ignored, an unknown group refused")
	void softwareCreate() {
		SoftwareRule rule = this.softwareRuleService.create(software(this.groupId, "CiscoIOS12", "1.0"));
		Assertions.assertEquals(this.groupId, rule.getTargetGroup().getId());
		Assertions.assertEquals("CiscoIOS12", rule.getDriver());
		Assertions.assertEquals(ConformanceLevel.SILVER, rule.getLevel());
		SoftwareRule anything = this.softwareRuleService.create(software(null, "NoSuchDriver", "2.0"));
		Assertions.assertNull(anything.getTargetGroup());
		Assertions.assertNull(anything.getDriver());
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.softwareRuleService.create(software(99999999L, "", "3.0")));
		List<SoftwareRule> rules = this.softwareRuleService.list(Page.ALL);
		Assertions.assertEquals(Double.MAX_VALUE, rule.getPriority(), "the lowest priority, until the rules are sorted");
		SoftwareRule listed = rules.stream().filter(r -> r.getId() == rule.getId()).findFirst().orElseThrow();
		Assertions.assertEquals("Group", listed.getTargetGroup().getName(), "the group is loaded by the list");
		Assertions.assertEquals(1, this.softwareRuleService.list(new Page(0, 1)).size());
	}

	@Test
	@DisplayName("Software rule update() replaces all the properties, delete() tells whether the rule existed")
	void softwareUpdateDelete() {
		SoftwareRule rule = this.softwareRuleService.create(software(this.groupId, "CiscoIOS12", "1.0"));
		SoftwareRule updated = this.softwareRuleService.update(rule.getId(),
			new SoftwareRuleDefinition(null, "", "fam", true, "2.0", true, "pn", true, ConformanceLevel.BRONZE));
		Assertions.assertNull(updated.getTargetGroup());
		Assertions.assertNull(updated.getDriver());
		Assertions.assertEquals("fam", updated.getFamily());
		Assertions.assertTrue(updated.isFamilyRegExp());
		Assertions.assertEquals("2.0", updated.getVersion());
		Assertions.assertTrue(updated.isVersionRegExp());
		Assertions.assertEquals("pn", updated.getPartNumber());
		Assertions.assertTrue(updated.isPartNumberRegExp());
		Assertions.assertEquals(ConformanceLevel.BRONZE, updated.getLevel());
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.softwareRuleService.update(99999999L, software(null, "", "x")));
		Assertions.assertTrue(this.softwareRuleService.delete(rule.getId()));
		Assertions.assertFalse(this.softwareRuleService.delete(rule.getId()));
	}

	private List<Long> softwareOrder() {
		return this.softwareRuleService.list(Page.ALL).stream().map(SoftwareRule::getId).toList();
	}

	@Test
	@DisplayName("Software rule sort() moves a rule before another one or to the last position, and renumbers the priorities")
	void softwareSort() {
		long a = this.softwareRuleService.create(software(null, "", "a")).getId();
		long b = this.softwareRuleService.create(software(null, "", "b")).getId();
		long c = this.softwareRuleService.create(software(null, "", "c")).getId();
		// The new rules all have the same priority: sort them to get a known order
		this.softwareRuleService.sort(a, null);
		this.softwareRuleService.sort(b, null);
		this.softwareRuleService.sort(c, null);
		List<Long> initial = this.softwareOrder();
		Assertions.assertEquals(List.of(a, b, c), initial.subList(initial.size() - 3, initial.size()));

		this.softwareRuleService.sort(c, a);
		List<Long> order = this.softwareOrder();
		Assertions.assertEquals(List.of(c, a, b), order.subList(order.size() - 3, order.size()));
		Assertions.assertEquals(10.0 * order.size(), this.softwareRuleService.list(Page.ALL).get(order.size() - 1).getPriority());

		this.softwareRuleService.sort(c, null);
		order = this.softwareOrder();
		Assertions.assertEquals(List.of(a, b, c), order.subList(order.size() - 3, order.size()));
		long moved = b;
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.softwareRuleService.sort(moved, moved));
		Assertions.assertEquals(order, this.softwareOrder(), "a rule can't be moved before itself");
	}

	@Test
	@DisplayName("Software rule sort() reports an unknown rule or next rule, and changes nothing")
	void softwareSortErrors() {
		long a = this.softwareRuleService.create(software(null, "", "sort-a")).getId();
		List<Long> before = this.softwareOrder();
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.softwareRuleService.sort(99999999L, null));
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.softwareRuleService.sort(99999999L, a));
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.softwareRuleService.sort(a, 99999999L));
		Assertions.assertEquals(before, this.softwareOrder());
	}

	// ------------------------------------------------------------------
	// hardware rules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Hardware rule create(), update(), list() and delete()")
	void hardwareRules() {
		Date sale = new Date(1_700_000_000_000L);
		Date life = new Date(1_800_000_000_000L);
		HardwareRule rule = this.hardwareRuleService.create(
			new HardwareRuleDefinition(this.groupId, "CiscoIOS12", "fam", false, "pn", true, sale, life));
		Assertions.assertEquals(this.groupId, rule.getTargetGroup().getId());
		Assertions.assertEquals("CiscoIOS12", rule.getDriver());
		Assertions.assertEquals(sale, rule.getEndOfSale());
		Assertions.assertEquals(life, rule.getEndOfLife());
		HardwareRule listed = this.hardwareRuleService.list().stream().filter(r -> r.getId() == rule.getId()).findFirst().orElseThrow();
		Assertions.assertEquals("Group", listed.getTargetGroup().getName(), "the group is loaded by the list");

		// A group which doesn't exist is refused; an unknown driver is still taken for "any driver"
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.hardwareRuleService.create(
			new HardwareRuleDefinition(99999999L, "", "", false, "x", false, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.hardwareRuleService.update(rule.getId(),
			new HardwareRuleDefinition(99999999L, "", "", false, "x", false, null, null)));
		HardwareRule unknownDriver = this.hardwareRuleService.create(
			new HardwareRuleDefinition(null, "NoSuchDriver", "", false, "x", false, null, null));
		Assertions.assertNull(unknownDriver.getDriver());

		HardwareRule updated = this.hardwareRuleService.update(rule.getId(),
			new HardwareRuleDefinition(null, "", "", true, "other", false, null, life));
		Assertions.assertNull(updated.getTargetGroup());
		Assertions.assertNull(updated.getDriver());
		Assertions.assertNull(updated.getEndOfSale());
		Assertions.assertEquals(life, updated.getEndOfLife());
		Assertions.assertEquals("other", updated.getPartNumber());
		assertError(ErrorCode.NETSHOT_INVALID_RULE, () -> this.hardwareRuleService.update(99999999L,
			new HardwareRuleDefinition(null, "", "", true, "other", false, null, null)));
		Assertions.assertTrue(this.hardwareRuleService.delete(rule.getId()));
		Assertions.assertFalse(this.hardwareRuleService.delete(rule.getId()));
	}
}
