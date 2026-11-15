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

import java.util.ArrayList;
import java.util.List;
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
 * The software compliance rule use cases: list, add, edit, delete and sort the software rules.
 * The rules are evaluated by priority (lowest first), see {@link #sort}.
 *
 * <p>The rules are returned with their target group.</p>
 */
@Slf4j
public class SoftwareRuleService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final SoftwareRuleRepository repository;
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the software rule repository
	 * @param driverRegistry the loaded device drivers
	 */
	public SoftwareRuleService(Transactions transactions, SoftwareRuleRepository repository, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets what the software rule lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return SoftwareRuleRepository.LIST.schema();
	}

	/**
	 * Lists the software rules matching a query, with their position.
	 *
	 * @param query the query
	 * @return the rules of the window, with the total number of matching rules if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the rules can't be read
	 */
	public ListResult<RankedSoftwareRule> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the software rules.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the software rules");
		}
	}

	/**
	 * Gets a software rule with its position.
	 *
	 * @param id the rule ID
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, or can't be read
	 */
	public RankedSoftwareRule get(long id) {
		try {
			return this.transactions.read(dsl -> this.require(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the software rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the software rule.");
		}
	}

	private RankedSoftwareRule require(DSLContext dsl, long id) {
		RankedSoftwareRule rule = this.repository.findRanked(dsl, id);
		if (rule == null) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unable to find the software rule");
		}
		return rule;
	}

	private void check(DSLContext dsl, SoftwareRule rule) {
		if (rule.targetGroupId() != null && new GroupRepository().find(dsl, rule.targetGroupId()) == null) {
			throw NetshotException.invalidParameter("group", "The group doesn't exist.");
		}
		if (rule.driver() != null && this.driverRegistry.getDriverByName(rule.driver()) == null) {
			throw NetshotException.invalidParameter("driver", "Unknown device driver.");
		}
	}

	/**
	 * Creates a software rule, at a given position or (by default) as the last one.
	 *
	 * @param newRule the rule (its ID and priority are ignored)
	 * @param position the position of the new rule among the rules, from 1 (clamped to the number of rules), or null
	 *        for the last position
	 * @return the new rule, with its position
	 * @throws NetshotException if the group or the driver doesn't exist, or the rule can't be stored
	 */
	public RankedSoftwareRule create(SoftwareRule newRule, Integer position) {
		RankedSoftwareRule rule;
		try {
			rule = this.transactions.write(dsl -> {
				this.check(dsl, newRule);
				long id = this.repository.add(dsl, newRule.withPriority(Double.MAX_VALUE));
				if (position != null) {
					this.move(dsl, id, position);
				}
				return this.require(dsl, id);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new software rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the software rule to the database");
		}
		AAA_LOG.info("{} has been created", rule.rule());
		return rule;
	}

	/**
	 * The new definition of a software rule, and where to move it.
	 *
	 * @param rule the new definition of the rule (its ID and priority are ignored)
	 * @param position the new position of the rule among the rules, from 1 (clamped to the number of rules), or null
	 *        to keep it where it is
	 */
	public record Replacement(SoftwareRule rule, Integer position) {
	}

	/**
	 * Replaces a software rule, if it is at a given version, and, if a position is given, moves it there (the
	 * priorities of the rules are renumbered). The new definition is computed from the rule as it is, which can't
	 * change meanwhile (to apply a patch).
	 *
	 * @param id the rule ID
	 * @param change gives the new definition of the rule and its position from the current rule
	 * @param expectedVersion the version the rule must be at, or null for any
	 * @return the rule, with its position
	 * @throws NetshotException if the rule, the group or the driver doesn't exist, the rule isn't at the expected
	 *         version, or the rule can't be stored
	 */
	public RankedSoftwareRule update(long id, Function<RankedSoftwareRule, Replacement> change, Long expectedVersion) {
		RankedSoftwareRule rule;
		try {
			rule = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				RankedSoftwareRule existing = this.require(dsl, id);
				Versions.check("software rule", version, expectedVersion);
				Replacement replacement = change.apply(existing);
				SoftwareRule newRule = replacement.rule();
				Integer position = replacement.position();
				this.check(dsl, newRule);
				this.repository.save(dsl, newRule.withId(id).withPriority(existing.rule().priority()));
				if (position != null && position != existing.rank()) {
					this.move(dsl, id, position);
				}
				return this.require(dsl, id);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the software rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the software rule.");
		}
		AAA_LOG.info("{} has been edited", rule.rule());
		return rule;
	}

	// Moves a rule to a position (from 1), and renumbers the priorities of the rules
	private void move(DSLContext dsl, long id, int position) {
		List<SoftwareRule> rules = new ArrayList<>(this.repository.findAllOrdered(dsl));
		SoftwareRule target = rules.stream().filter(r -> r.id() == id).findFirst().orElseThrow();
		rules.remove(target);
		rules.add(Math.max(0, Math.min(position - 1, rules.size())), target);
		double priority = 0;
		for (SoftwareRule rule : rules) {
			priority += 10;
			if (rule.priority() != priority) {
				this.repository.setPriority(dsl, rule.id(), priority);
			}
		}
	}

	/**
	 * Deletes a software rule, if it is at a given version.
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
					throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The software rule doesn't exist.");
				}
				Versions.check("software rule", version, expectedVersion);
				this.repository.remove(dsl, id);
			});
			AAA_LOG.info("Software rule of ID {} has been deleted", id);
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

}
