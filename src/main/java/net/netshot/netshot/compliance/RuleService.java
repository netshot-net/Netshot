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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;

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
	 * Deletes a rule, if it is at a given version.
	 *
	 * @param id the rule ID
	 * @param expectedVersion the version the rule must be at, or null for any
	 * @throws NetshotException if the rule doesn't exist, isn't at the expected version, or can't be deleted
	 */
	public void delete(long id, Long expectedVersion) {
		try {
			this.transactions.run(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The rule doesn't exist.");
				}
				Versions.check("rule", version, expectedVersion);
				this.repository.remove(dsl, id);
			});
			AAA_LOG.info("Rule of ID {} has been deleted.", id);
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

	/**
	 * Tests a rule (not stored) against a device, in dry run mode: what the rule changes on the device (tags...) is
	 * not stored. If the device has no configuration the result is "not applicable".
	 *
	 * @param deviceId the ID of the device to check
	 * @param rule the rule to test (it must be enabled)
	 * @return the result of the check, with what it logged
	 * @throws NetshotException if the device doesn't exist, or can't be read or checked
	 */
	public RuleTestResult test(long deviceId, Rule rule) {
		try {
			Device device = this.transactions.read(dsl -> this.deviceRepository.find(dsl, deviceId));
			if (device == null) {
				throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "Unable to find the device");
			}
			if (device.lastConfigId() == null) {
				return new RuleTestResult(CheckResult.ResultOption.NOTAPPLICABLE, null, "The device has no configuration");
			}
			return this.evaluate(device, rule);
		}
		catch (NetshotException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Unable to test the rule on the device {}.", deviceId, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to test the rule on the device.");
		}
	}

	private RuleTestResult evaluate(Device device, Rule rule) {
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

	/**
	 * Gets what the rule lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return RuleRepository.LIST.schema();
	}

	/**
	 * Lists the rules matching a query.
	 *
	 * @param query the query
	 * @return the rules of the window, with the total number of matching rules if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the rules can't be read
	 */
	public ListResult<Rule> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rules.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the rules");
		}
	}

	/**
	 * Gets a rule.
	 *
	 * @param id the rule ID
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, or can't be read
	 */
	public Rule get(long id) {
		try {
			return this.transactions.read(dsl -> this.require(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the rule");
		}
	}

	private Rule require(DSLContext dsl, long id) {
		Rule rule = this.repository.find(dsl, id);
		if (rule == null) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unable to find the rule");
		}
		return rule;
	}

	/**
	 * Adds a rule (enabled or not as given).
	 *
	 * @param newRule the rule (its ID is ignored)
	 * @return the new rule
	 * @throws NetshotException if the name is blank or already used in the policy, the policy doesn't
	 *         exist, or the rule can't be stored
	 */
	public Rule add(Rule newRule) {
		if (newRule.name() == null || newRule.name().isBlank()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
		}
		Rule rule;
		try {
			rule = this.transactions.write(dsl -> {
				if (this.policyRepository.find(dsl, newRule.policyId()) == null) {
					throw NetshotException.invalidParameter("policy", "The policy doesn't exist.");
				}
				return this.require(dsl, this.repository.add(dsl, newRule));
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_RULE, "A rule with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the rule to the database");
		}
		AAA_LOG.info("{} has been created.", rule);
		return rule;
	}

	/**
	 * The replacement of a rule: its new definition, and optionally its new exemptions.
	 *
	 * @param rule the new definition of the rule (its ID is ignored; its policy, if not 0, must be the current one)
	 * @param exemptions all the new exemptions (expiration dates by device ID), or null to keep the current ones
	 */
	public record Replacement(Rule rule, Map<Long, Instant> exemptions) {

		/**
		 * Replaces the definition of a rule only, keeping its exemptions.
		 *
		 * @param rule the new definition of the rule
		 * @return the replacement
		 */
		public static Replacement of(Rule rule) {
			return new Replacement(rule, null);
		}
	}

	/**
	 * Replaces the definition of a rule, and optionally all its exemptions, if it is at a given version (its policy and
	 * its kind can't change). The replacement is computed from the rule as it is, which can't change meanwhile (to apply
	 * a patch); the rule and its exemptions are changed together, or not at all.
	 *
	 * @param id the rule ID
	 * @param change gives the replacement of the rule from the current one
	 * @param expectedVersion the version the rule must be at, or null for any
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, isn't at the expected version, is of another kind or policy, the
	 *         name is blank or already used in the policy, an exempted device doesn't exist, or the rule can't be stored
	 */
	public Rule replace(long id, Function<Rule, Replacement> change, Long expectedVersion) {
		Rule rule;
		try {
			rule = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unable to find the rule");
				}
				Versions.check("rule", version, expectedVersion);
				Rule existing = this.require(dsl, id);
				Replacement replacement = change.apply(existing);
				Rule newRule = replacement.rule();
				if (newRule.name() == null || newRule.name().isBlank()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
				}
				if (existing.getClass() != newRule.getClass()) {
					throw NetshotException.invalidParameter("type", "The type of a rule can't change.");
				}
				if (newRule.policyId() != 0 && newRule.policyId() != existing.policyId()) {
					throw NetshotException.invalidParameter("policy", "The policy of a rule can't change.");
				}
				if (replacement.exemptions() != null) {
					this.checkExemptedDevices(dsl, replacement.exemptions());
				}
				this.repository.save(dsl, newRule.withId(id));
				if (replacement.exemptions() != null) {
					this.repository.replaceExemptions(dsl, id, replacement.exemptions());
				}
				return this.require(dsl, id);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the rule.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_RULE, "A rule with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the rule.");
		}
		AAA_LOG.info("{} has been edited.", rule);
		return rule;
	}

	/**
	 * Lists the exemptions of a rule.
	 *
	 * @param ruleId the rule ID
	 * @param query the query (on the exemptions)
	 * @return the exemptions of the window, with the total number if the query asks for it
	 * @throws NetshotException if the rule doesn't exist, a filter is invalid, or the exemptions can't be read
	 */
	public ListResult<Exemption> listExemptions(long ruleId, ListQuery query) {
		ListQuery ofRule = query.and(new Criterion("rule", Lookup.EXACT, List.of(ruleId)));
		try {
			return this.transactions.read(dsl -> {
				this.require(dsl, ruleId);
				return new ListResult<>(this.repository.findExemptions(dsl, ofRule),
					query.count() ? this.repository.countExemptions(dsl, ofRule) : null);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the exemptions.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the exemptions");
		}
	}

	/**
	 * Gets what the lists of exemptions can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema exemptionSchema() {
		return RuleRepository.EXEMPTION_LIST.schema();
	}

	/**
	 * Replaces all the exemptions of a rule, if it is at a given version (the version of the rule is incremented).
	 *
	 * @param ruleId the rule ID
	 * @param expirations the expiration dates, by device ID
	 * @param expectedVersion the version the rule must be at, or null for any
	 * @throws NetshotException if the rule or a device doesn't exist, the rule isn't at the expected version, or the
	 *         exemptions can't be stored
	 */
	public void replaceExemptions(long ruleId, Map<Long, Instant> expirations, Long expectedVersion) {
		try {
			this.transactions.run(dsl -> {
				Integer version = this.repository.lock(dsl, ruleId);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unable to find the rule");
				}
				Versions.check("rule", version, expectedVersion);
				this.checkExemptedDevices(dsl, expirations);
				this.repository.replaceExemptions(dsl, ruleId, expirations);
				this.repository.touch(dsl, ruleId);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the exemptions of the rule {}.", ruleId, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the exemptions.");
		}
		AAA_LOG.info("The exemptions of the rule of ID {} have been replaced.", ruleId);
	}

	private void checkExemptedDevices(DSLContext dsl, Map<Long, Instant> expirations) {
		for (Long deviceId : expirations.keySet()) {
			if (!this.repository.deviceExists(dsl, deviceId)) {
				throw NetshotException.invalidParameter("exemptions", "Unknown device of ID %d.".formatted(deviceId));
			}
		}
	}

	/**
	 * Gets what the lists of check results of a device can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema resultSchema() {
		return RuleRepository.RESULT_LIST.schema();
	}

	/**
	 * Lists the compliance state of a device matching a query: the rules of the policies applying to it, with
	 * their last result.
	 *
	 * @param deviceId the device ID
	 * @param query the query
	 * @return the results of the window, with the total number if the query asks for it
	 * @throws NetshotException if the device doesn't exist, a filter is invalid, or the results can't be read
	 */
	public ListResult<DeviceCheckResult> listDeviceResults(long deviceId, ListQuery query) {
		try {
			return this.transactions.read(dsl -> {
				if (!this.repository.deviceExists(dsl, deviceId)) {
					throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "Unable to find the device");
				}
				return new ListResult<>(this.repository.findDeviceResults(dsl, deviceId, query),
					query.count() ? this.repository.countDeviceResults(dsl, deviceId, query) : null);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the compliance results.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the compliance results");
		}
	}

	/**
	 * Gets what the lists of check results across devices can be filtered, sorted and counted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema allResultsSchema() {
		return RuleRepository.ALL_RESULT_LIST.schema();
	}

	/**
	 * Lists the check results across all the devices matching a query (one per device and rule that has been checked).
	 *
	 * @param query the query
	 * @return the results of the window, with the total number if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the results can't be read
	 */
	public ListResult<DeviceCheckResult> listResults(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findResults(dsl, query),
				query.count() ? this.repository.countResults(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the compliance results.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the compliance results");
		}
	}

	/**
	 * Counts the check results across all the devices matching a query, by field values and/or time buckets.
	 *
	 * @param query the filters
	 * @param stats what to count by
	 * @return the rows, with the number of matching results
	 * @throws NetshotException if a filter is invalid, or the results can't be read
	 */
	public ListResult<StatsRow> resultStats(ListQuery query, StatsQuery stats) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.resultStats(dsl, query, stats),
				this.repository.countResults(dsl, query)));
		}
		catch (DataAccessException e) {
			log.error("Unable to count the compliance results.", e);
			throw DatabaseErrors.listFailure(e, "Unable to count the compliance results");
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
}
