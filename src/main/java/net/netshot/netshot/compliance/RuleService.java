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

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * The compliance rule use cases: list, read, add, edit, delete and test the rules, and read their
 * exemptions and the compliance state of a device.
 *
 * <p>The rules it returns are detached entities loaded with their own properties only. Checking
 * a rule against a device (see {@link #test}) goes through the {@link RuleEvaluators}.</p>
 */
@Slf4j
public class RuleService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private static final String TEXT_RULE = "TextRule";
	private static final String JAVASCRIPT_RULE = "JavaScriptRule";
	private static final String PYTHON_RULE = "PythonRule";

	private final Transactions transactions;
	private final RuleRepository repository;
	private final PolicyRepository policyRepository;
	private final RuleEvaluators evaluators;
	private final DeviceRepository deviceRepository = new DeviceRepository();

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the rule repository
	 * @param policyRepository the policy repository (to find the policy of a rule)
	 * @param evaluators the rule evaluators (to test a rule)
	 */
	public RuleService(Transactions transactions, RuleRepository repository, PolicyRepository policyRepository,
		RuleEvaluators evaluators) {
		this.transactions = transactions;
		this.evaluators = evaluators;
		this.repository = repository;
		this.policyRepository = policyRepository;
	}

	/**
	 * Lists all the rules, across all the policies, with minimal details.
	 *
	 * @param page the window on the result
	 * @return the rule summaries
	 * @throws NetshotException if the rules can't be read
	 */
	public List<RuleSummary> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findSummaries(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the rules");
		}
	}

	/**
	 * Lists the rules of a policy.
	 *
	 * @param policyId the policy ID
	 * @param page the window on the result
	 * @return the rules
	 * @throws NetshotException if the policy doesn't exist, or the rules can't be read
	 */
	public List<Rule> listOfPolicy(long policyId, Page page) {
		try {
			return this.transactions.read(dsl -> {
				if (this.policyRepository.find(dsl, policyId) == null) {
					log.error("Invalid policy.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY, "Invalid policy");
				}
				return this.repository.findByPolicy(dsl, policyId, page);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the rules");
		}
	}

	/**
	 * Gets a rule of a policy.
	 *
	 * @param policyId the policy ID
	 * @param id the rule ID
	 * @return the rule
	 * @throws NetshotException if the policy or the rule of the policy doesn't exist, or the rule can't be read
	 */
	public Rule getOfPolicy(long policyId, long id) {
		try {
			return this.transactions.read(dsl -> {
				if (this.policyRepository.find(dsl, policyId) == null) {
					log.error("Invalid policy.");
					throw new NetshotException(ErrorCode.NETSHOT_POLICY_NOT_FOUND, "Unknown policy");
				}
				Rule rule = this.repository.findOfPolicy(dsl, policyId, id);
				if (rule == null) {
					log.warn("Unable to find the rule object.");
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unknown rule");
				}
				return rule;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the rule");
		}
	}

	/**
	 * Adds a rule to a policy. The rule is created disabled.
	 *
	 * @param definition the rule
	 * @return the new rule
	 * @throws NetshotException if the name is blank or already used in the policy, the policy doesn't
	 *         exist, the kind of rule is invalid, or the rule can't be stored
	 */
	public Rule create(RuleDefinition definition) {
		if (definition.name() == null || definition.name().trim().isEmpty()) {
			log.warn("User posted an empty rule name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
		}
		String name = definition.name().trim();
		Rule rule;
		try {
			rule = this.transactions.write(dsl -> {
				Policy policy = this.policyRepository.find(dsl, definition.policyId());
				if (policy == null) {
					log.error("The policy of ID {} doesn't exist.", definition.policyId());
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY, "Invalid policy.");
				}
				Rule newRule;
				if (TEXT_RULE.equals(definition.type())) {
					newRule = applyTextRule(TextRule.builder(name, policy.id()), definition).build();
				}
				else if (JAVASCRIPT_RULE.equals(definition.type())) {
					JavaScriptRule jsRule = JavaScriptRule.of(name, policy.id());
					if (definition.script() != null) {
						jsRule = new JavaScriptRule(0, name, false, policy.id(), definition.script().trim());
					}
					newRule = jsRule;
				}
				else if (PYTHON_RULE.equals(definition.type())) {
					PythonRule pyRule = PythonRule.of(name, policy.id());
					if (definition.script() != null) {
						pyRule = new PythonRule(0, name, false, policy.id(), definition.script().trim());
					}
					newRule = pyRule;
				}
				else {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Invalid rule type.");
				}
				return newRule.withId(this.repository.add(dsl, newRule));
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_RULE, "A rule with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the rule to the database");
		}
		catch (NetshotException e) {
			log.error("Error while saving the new rule.", e);
			throw e;
		}
		AAA_LOG.info("{} has been created.", rule);
		return rule;
	}

	/**
	 * Edits a rule. The name (if provided), the enabled flag, the exemptions (all of them are
	 * replaced) and the properties of the kind of the rule are changed; its policy and kind are not.
	 *
	 * @param id the rule ID
	 * @param definition the new properties
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, the name is blank or already used in the
	 *         policy, an exempted device doesn't exist, or the rule can't be stored
	 */
	public Rule update(long id, RuleDefinition definition) {
		Rule rule;
		try {
			rule = this.transactions.write(dsl -> {
				Rule existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("Unable to find the rule {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				String name = existing.name();
				if (definition.name() != null) {
					name = definition.name().trim();
					if (name.isEmpty()) {
						log.warn("User posted an empty rule name.");
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
					}
				}
				Map<Long, Instant> exemptions = new HashMap<>();
				for (Map.Entry<Long, Date> postedExemption : definition.exemptions().entrySet()) {
					if (!this.repository.deviceExists(dsl, postedExemption.getKey())) {
						log.warn("Unknown device of ID {}", postedExemption.getKey());
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE,
							"Unknown device of ID %d.".formatted(postedExemption.getKey()));
					}
					exemptions.put(postedExemption.getKey(),
						postedExemption.getValue() == null ? null : postedExemption.getValue().toInstant());
				}
				final String newName = name;
				Rule edited = switch (existing) {
					case JavaScriptRule jsRule -> new JavaScriptRule(id, newName, definition.enabled(), jsRule.policyId(),
						definition.script() != null ? definition.script().trim() : jsRule.script());
					case PythonRule pyRule -> new PythonRule(id, newName, definition.enabled(), pyRule.policyId(),
						definition.script() != null ? definition.script().trim() : pyRule.script());
					case TextRule textRule -> applyTextRule(textRule.toBuilder().name(newName).enabled(definition.enabled()),
						definition).build();
				};
				this.repository.save(dsl, edited);
				this.repository.replaceExemptions(dsl, id, exemptions);
				return edited;
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_RULE, "A rule with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the rule.");
		}
		AAA_LOG.info("{} has been edited.", rule);
		return rule;
	}

	/**
	 * Deletes a rule.
	 *
	 * @param id the rule ID
	 * @return true if the rule has been deleted, false if there was no such rule
	 * @throws NetshotException if the rule can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.write(dsl -> {
				if (!this.repository.remove(dsl, id)) {
					log.info("No such rule of ID {}", id);
					return false;
				}
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Rule of ID {} has been deleted.", id);
			}
			return deleted;
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

	/**
	 * Tests a rule against a device, in dry run mode: the rule is built from the definition (it is not
	 * stored), enabled and checked against the device. The check is the {@link Rule#check} of the
	 * entity. If the device doesn't exist (or has no configuration) the result is "not applicable".
	 *
	 * @param deviceId the ID of the device to check
	 * @param definition the rule to test (only its kind-specific properties are used)
	 * @return the result of the check, with what it logged
	 * @throws NetshotException if the kind of rule is invalid, or the device can't be read or checked
	 */
	public RuleTestResult test(long deviceId, RuleDefinition definition) {
		try {
			Device found = this.transactions.read(dsl -> this.deviceRepository.find(dsl, deviceId));
			Device device = found == null || found.lastConfigId() == null ? null : found;
			if (device == null) {
				log.warn("Unable to find the device {}.", deviceId);
				return new RuleTestResult(CheckResult.ResultOption.NOTAPPLICABLE, null, "Unable to find the device");
			}

			Rule rule;
			if (TEXT_RULE.equals(definition.type())) {
				// As on creation, an option which is not given keeps the default value of the rule
				TextRule.TextRuleBuilder txRule = TextRule.builder("TEST", 0)
					.deviceDriver(definition.driver())
					.field(definition.field())
					.context(definition.context())
					.text(definition.text());
				if (definition.invert() != null) {
					txRule.invert(definition.invert());
				}
				if (definition.regExp() != null) {
					txRule.regExp(definition.regExp());
				}
				if (definition.anyBlock() != null) {
					txRule.anyBlock(definition.anyBlock());
				}
				if (definition.matchAll() != null) {
					txRule.matchAll(definition.matchAll());
				}
				if (definition.normalize() != null) {
					txRule.normalize(definition.normalize());
				}
				rule = txRule.enabled(true).build();
			}
			else if (JAVASCRIPT_RULE.equals(definition.type())) {
				rule = new JavaScriptRule(0, "TEST", true, 0, definition.script());
			}
			else if (PYTHON_RULE.equals(definition.type())) {
				rule = new PythonRule(0, "TEST", true, 0, definition.script());
			}
			else {
				log.warn("Invalid rule type {}.", definition.type());
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Invalid rule type");
			}
			StringBuilder taskLog = new StringBuilder();
			TaskContext taskContext = new TaskContext() {
				@Override
				public void log(Level level, String message, Object... params) {
					taskLog.append("[%s] ".formatted(level.toString()));
					taskLog.append(
						MessageFormatter.arrayFormat(message, params).getMessage());
					taskLog.append("\n");
				}

				@Override
				public String getIdentifier() {
					return "RuleTest";
				}
			};

			// Dry run: what the rule changes on the device (tags...) is not stored
			CheckResult check = this.evaluators.evaluate(rule, new DeviceDraft(device), false, taskContext);
			return new RuleTestResult(check.result(), taskLog.toString(), check.comment());
		}
		catch (NetshotException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Unable to test the rule on the device {}.", deviceId, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to test the rule on the device.");
		}
	}

	/**
	 * Lists the devices exempted from a rule.
	 *
	 * @param ruleId the rule ID
	 * @param page the window on the result
	 * @return the exempted devices (empty if the rule doesn't exist)
	 * @throws NetshotException if the exemptions can't be read
	 */
	public List<ExemptedDevice> listExemptedDevices(long ruleId, Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findExemptedDevices(dsl, ruleId, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the exemptions.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the exemptions");
		}
	}

	/**
	 * Lists the compliance state of a device: the rules of the policies applying to it, with their last result.
	 *
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the rule results (empty if the device doesn't exist)
	 * @throws NetshotException if the results can't be read
	 */
	public List<DeviceRuleResult> listDeviceResults(long deviceId, Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findDeviceResults(dsl, deviceId, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the rules");
		}
	}

	private static TextRule.TextRuleBuilder applyTextRule(TextRule.TextRuleBuilder textRule, RuleDefinition definition) {
		if (definition.text() != null) {
			textRule.text(definition.text());
		}
		if (definition.regExp() != null) {
			textRule.regExp(definition.regExp());
		}
		if (definition.context() != null) {
			textRule.context(definition.context());
		}
		if (definition.field() != null) {
			textRule.field(definition.field());
		}
		if (definition.driver() != null) {
			textRule.deviceDriver(definition.driver());
		}
		if (definition.invert() != null) {
			textRule.invert(definition.invert());
		}
		if (definition.matchAll() != null) {
			textRule.matchAll(definition.matchAll());
		}
		if (definition.anyBlock() != null) {
			textRule.anyBlock(definition.anyBlock());
		}
		if (definition.normalize() != null) {
			textRule.normalize(definition.normalize());
		}
		return textRule;
	}
}
