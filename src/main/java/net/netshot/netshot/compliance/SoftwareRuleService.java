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

import java.util.List;
import java.util.ListIterator;

import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The software compliance rule use cases: list, add, edit, delete and sort the software rules.
 * The rules are evaluated by priority (lowest first), see {@link #sort}.
 *
 * <p>The rules it returns are detached entities whose target group is loaded.</p>
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
	public List<SoftwareRule> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findAllWithGroup(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the software rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the software rules.");
		}
	}

	/**
	 * Adds a software rule, with the lowest priority.
	 *
	 * @param definition the rule
	 * @return the new rule
	 * @throws NetshotException if the rule can't be stored
	 */
	public SoftwareRule create(SoftwareRuleDefinition definition) {
		SoftwareRule rule;
		try {
			rule = this.transactions.write(session -> {
				DeviceGroup group = this.findGroup(session, definition.groupId());
				SoftwareRule newRule = new SoftwareRule(Double.MAX_VALUE, group, knownDriver(definition.driver()),
					definition.family(), definition.familyRegExp(), definition.version(),
					definition.versionRegExp(), definition.partNumber(), definition.partNumberRegExp(),
					definition.level());
				session.persist(newRule);
				return newRule;
			});
		}
		catch (ObjectNotFoundException e) {
			log.error("The posted group doesn't exist", e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		catch (HibernateException e) {
			log.error("Error while saving the new rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been created", rule);
		return rule;
	}

	/**
	 * Edits a software rule (its priority is not changed, see {@link #sort}).
	 *
	 * @param id the rule ID
	 * @param definition the new properties
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, or can't be stored
	 */
	public SoftwareRule update(long id, SoftwareRuleDefinition definition) {
		SoftwareRule rule;
		try {
			rule = this.transactions.write(session -> {
				SoftwareRule existing = this.repository.find(session, id);
				if (existing == null) {
					log.error("Unable to find the rule {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				existing.setDriver(knownDriver(definition.driver()));
				existing.setTargetGroup(this.findGroup(session, definition.groupId()));
				existing.setFamily(definition.family());
				existing.setFamilyRegExp(definition.familyRegExp());
				existing.setVersion(definition.version());
				existing.setVersionRegExp(definition.versionRegExp());
				existing.setPartNumber(definition.partNumber());
				existing.setPartNumberRegExp(definition.partNumberRegExp());
				existing.setLevel(definition.level());
				session.merge(existing);
				return existing;
			});
		}
		catch (HibernateException e) {
			log.error("Error while saving the rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the rule.");
		}
		AAA_LOG.info("{} has been edited", rule);
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
			boolean deleted = this.transactions.write(session -> {
				SoftwareRule rule = this.repository.find(session, id);
				if (rule == null) {
					log.info("No such software rule of ID {}", id);
					return false;
				}
				session.remove(rule);
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Software rule of ID {} has been deleted", id);
			}
			return deleted;
		}
		catch (HibernateException e) {
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
			Sorted sorted = this.transactions.write(session -> {
				List<SoftwareRule> rules = this.repository.findAllByPriority(session);

				SoftwareRule target = null;
				int index = -1;
				ListIterator<SoftwareRule> ruleIt = rules.listIterator();
				while (ruleIt.hasNext()) {
					SoftwareRule rule = ruleIt.next();
					if (rule.getId() == id) {
						target = rule;
						ruleIt.remove();
					}
					else if (nextId != null && nextId.equals(rule.getId())) {
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
					rule.setPriority(priority);
					session.persist(rule);
				}
				return new Sorted(target, index);
			});
			targetRule = sorted.rule();
			nextIndex = sorted.nextIndex();
		}
		catch (HibernateException e) {
			log.error("Error while saving the new rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been sorted (moved before at index {})", targetRule, nextIndex);
	}

	private DeviceGroup findGroup(Session session, Long groupId) {
		if (groupId == null) {
			return null;
		}
		return this.repository.findGroup(session, groupId);
	}

	private String knownDriver(String driver) {
		if (this.driverRegistry.getDriverByName(driver) == null) {
			return null;
		}
		return driver;
	}
}
