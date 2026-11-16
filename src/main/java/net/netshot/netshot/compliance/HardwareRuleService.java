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

import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * The hardware compliance rule use cases: list, add, edit and delete the hardware rules, which give
 * the end-of-sale and end-of-life dates of the modules.
 *
 * <p>The rules are returned with their target group.</p>
 */
@Slf4j
public class HardwareRuleService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final HardwareRuleRepository repository;
	private final DriverRegistry driverRegistry;
	private final GroupRepository groupRepository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the hardware rule repository
	 * @param driverRegistry the loaded device drivers
	 * @param groupRepository the group repository (to check the target group)
	 */
	public HardwareRuleService(Transactions transactions, HardwareRuleRepository repository, DriverRegistry driverRegistry,
		GroupRepository groupRepository) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
		this.groupRepository = groupRepository;
	}

	/**
	 * Gets what the hardware rule lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return HardwareRuleRepository.LIST.schema();
	}

	/**
	 * Lists the hardware rules matching a query.
	 *
	 * @param query the query
	 * @return the rules of the window, with the total number of matching rules if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the rules can't be read
	 */
	public ListResult<HardwareRule> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hardware rules.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the hardware rules");
		}
	}

	/**
	 * Gets a hardware rule.
	 *
	 * @param id the rule ID
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, or can't be read
	 */
	public HardwareRule get(long id) {
		try {
			return this.transactions.read(dsl -> this.require(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hardware rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hardware rule.");
		}
	}

	private HardwareRule require(DSLContext dsl, long id) {
		HardwareRule rule = this.repository.find(dsl, id);
		if (rule == null) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unable to find the hardware rule");
		}
		return rule;
	}

	private void check(DSLContext dsl, HardwareRule rule) {
		if (rule.targetGroupId() != null && this.groupRepository.find(dsl, rule.targetGroupId()) == null) {
			throw NetshotException.invalidParameter("group", "The group doesn't exist.");
		}
		if (rule.driver() != null && this.driverRegistry.getDriverByName(rule.driver()) == null) {
			throw NetshotException.invalidParameter("driver", "Unknown device driver.");
		}
	}

	/**
	 * Creates a hardware rule.
	 *
	 * @param newRule the rule (its ID is ignored)
	 * @return the new rule
	 * @throws NetshotException if the group or the driver doesn't exist, or the rule can't be stored
	 */
	public HardwareRule create(HardwareRule newRule) {
		HardwareRule rule;
		try {
			rule = this.transactions.write(dsl -> {
				this.check(dsl, newRule);
				return this.require(dsl, this.repository.add(dsl, newRule));
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new hardware rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the rule to the database");
		}
		AAA_LOG.info("{} has been created", rule);
		return rule;
	}

	/**
	 * Replaces a hardware rule, if it is at a given version: the new definition is computed from the rule as it is,
	 * which can't change meanwhile (to apply a patch).
	 *
	 * @param id the rule ID
	 * @param change gives the new definition of the rule (its ID is ignored) from the current one
	 * @param expectedVersion the version the rule must be at, or null for any
	 * @return the rule
	 * @throws NetshotException if the rule, the group or the driver doesn't exist, the rule isn't at the expected
	 *         version, or the rule can't be stored
	 */
	public HardwareRule update(long id, Function<HardwareRule, HardwareRule> change, Long expectedVersion) {
		HardwareRule rule;
		try {
			rule = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				HardwareRule existing = this.require(dsl, id);
				Versions.check("hardware rule", version, expectedVersion);
				HardwareRule newRule = change.apply(existing);
				this.check(dsl, newRule);
				this.repository.save(dsl, newRule.withId(id));
				return this.require(dsl, id);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the hardware rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the rule.");
		}
		AAA_LOG.info("{} has been edited", rule);
		return rule;
	}

	/**
	 * Deletes a hardware rule, if it is at a given version.
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
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The hardware rule doesn't exist.");
				}
				Versions.check("hardware rule", version, expectedVersion);
				this.repository.remove(dsl, id);
			});
			AAA_LOG.info("Hardware rule of ID {} has been deleted", id);
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

}
