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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
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
	 * Lists the software rules, by priority.
	 *
	 * @param page the window on the result
	 * @return the rules
	 * @throws NetshotException if the rules can't be read
	 */
	public List<SoftwareRuleView> list(Page page) {
		try {
			return this.transactions.read(dsl -> {
				List<SoftwareRuleView> views = new ArrayList<>();
				for (SoftwareRule rule : this.repository.findAll(dsl, page)) {
					views.add(new SoftwareRuleView(rule, this.findGroup(dsl, rule.targetGroupId())));
				}
				return views;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the software rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the software rules.");
		}
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
	 * Adds a software rule, at a given position or (by default) as the last one.
	 *
	 * @param newRule the rule (its ID and priority are ignored)
	 * @param position the position of the new rule among the rules, from 1 (clamped to the number of rules), or null
	 *        for the last position
	 * @return the new rule, with its position
	 * @throws NetshotException if the group or the driver doesn't exist, or the rule can't be stored
	 */
	public RankedSoftwareRule add(SoftwareRule newRule, Integer position) {
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
	 * Replaces a software rule and, if a position is given, moves it there (the priorities of the rules are renumbered).
	 *
	 * @param id the rule ID
	 * @param newRule the new definition of the rule (its ID and priority are ignored)
	 * @param position the new position of the rule among the rules, from 1 (clamped to the number of rules), or null
	 *        to keep it where it is
	 * @return the rule, with its position
	 * @throws NetshotException if the rule, the group or the driver doesn't exist, or the rule can't be stored
	 */
	public RankedSoftwareRule replace(long id, SoftwareRule newRule, Integer position) {
		RankedSoftwareRule rule;
		try {
			rule = this.transactions.write(dsl -> {
				RankedSoftwareRule existing = this.require(dsl, id);
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

	private SoftwareRule fromDefinition(long id, double priority, SoftwareRuleDefinition definition) {
		return new SoftwareRule(id, priority, definition.groupId(), knownDriver(definition.driver()),
			definition.family(), definition.familyRegExp(), definition.version(),
			definition.versionRegExp(), definition.partNumber(), definition.partNumberRegExp(),
			definition.level());
	}

	/**
	 * Adds a software rule, with the lowest priority.
	 *
	 * @param definition the rule
	 * @return the new rule
	 * @throws NetshotException if the group doesn't exist, or the rule can't be stored
	 */
	public SoftwareRuleView create(SoftwareRuleDefinition definition) {
		SoftwareRuleView rule;
		try {
			rule = this.transactions.write(dsl -> {
				DeviceGroup group = this.findGroup(dsl, definition.groupId());
				SoftwareRule newRule = this.fromDefinition(0, Double.MAX_VALUE, definition);
				return new SoftwareRuleView(newRule.withId(this.repository.add(dsl, newRule)), group);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been created", rule.rule());
		return rule;
	}

	/**
	 * Edits a software rule (its priority is not changed, see {@link #sort}).
	 *
	 * @param id the rule ID
	 * @param definition the new properties
	 * @return the rule
	 * @throws NetshotException if the rule or the group doesn't exist, or the rule can't be stored
	 */
	public SoftwareRuleView update(long id, SoftwareRuleDefinition definition) {
		SoftwareRuleView rule;
		try {
			rule = this.transactions.write(dsl -> {
				SoftwareRule existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("Unable to find the rule {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				DeviceGroup group = this.findGroup(dsl, definition.groupId());
				SoftwareRule edited = this.fromDefinition(id, existing.priority(), definition);
				this.repository.save(dsl, edited);
				return new SoftwareRuleView(edited, group);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the rule.");
		}
		AAA_LOG.info("{} has been edited", rule.rule());
		return rule;
	}

	/**
	 * Deletes a software rule.
	 *
	 * @param id the rule ID
	 * @return true if the rule has been deleted, false if there was no such rule
	 * @throws NetshotException if the rule can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.write(dsl -> {
				if (!this.repository.remove(dsl, id)) {
					log.info("No such software rule of ID {}", id);
					return false;
				}
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Software rule of ID {} has been deleted", id);
			}
			return deleted;
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

	/**
	 * Moves a software rule before another one (or at the last position), and renumbers the priorities.
	 *
	 * @param id the ID of the rule to move
	 * @param nextId the ID of the rule to move it before, or null to move it to the last position
	 * @throws NetshotException if one of the rules doesn't exist, or the rules can't be stored
	 */
	public void sort(long id, Long nextId) {
		SoftwareRule targetRule;
		int nextIndex;
		try {
			record Sorted(SoftwareRule rule, int nextIndex) {
			}
			Sorted sorted = this.transactions.write(dsl -> {
				List<SoftwareRule> rules = new ArrayList<>(this.repository.findAll(dsl, Page.ALL));
				SoftwareRule target = null;
				int index = -1;
				ListIterator<SoftwareRule> ruleIt = rules.listIterator();
				while (ruleIt.hasNext()) {
					SoftwareRule rule = ruleIt.next();
					if (rule.id() == id) {
						target = rule;
						ruleIt.remove();
					}
					else if (nextId != null && nextId.equals(rule.id())) {
						index = ruleIt.previousIndex();
					}
				}
				if (target == null) {
					log.error("Unable to find the rule {} to be moved.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				if (index == -1 && nextId != null) {
					log.error("Unable to find the previous rule of ID {}.", nextId);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				if (index == -1) {
					rules.add(target);
				}
				else {
					rules.add(index, target);
				}
				double priority = 0;
				for (SoftwareRule rule : rules) {
					priority += 10;
					this.repository.save(dsl, rule.withPriority(priority));
				}
				return new Sorted(target, index);
			});
			targetRule = sorted.rule();
			nextIndex = sorted.nextIndex();
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been sorted (moved before at index {})", targetRule, nextIndex);
	}

	/**
	 * Finds a group.
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the ID of the group, or null
	 * @return the group, or null for no group
	 * @throws NetshotException if there is no such group
	 */
	private DeviceGroup findGroup(DSLContext dsl, Long groupId) {
		if (groupId == null) {
			return null;
		}
		DeviceGroup group = new GroupRepository().find(dsl, groupId);
		if (group == null) {
			log.error("Unable to find the group {}.", groupId);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		return group;
	}

	private String knownDriver(String driver) {
		if (this.driverRegistry.getDriverByName(driver) == null) {
			return null;
		}
		return driver;
	}
}
