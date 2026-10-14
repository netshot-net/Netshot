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

import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The hardware compliance rule use cases: list, add, edit and delete the hardware rules (end of
 * sale and end of life dates by hardware).
 *
 * <p>The rules it returns are detached entities whose target group is loaded.</p>
 */
@Slf4j
public class HardwareRuleService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final HardwareRuleRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the hardware rule repository
	 */
	public HardwareRuleService(Transactions transactions, HardwareRuleRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists all the hardware rules.
	 *
	 * @return the rules
	 * @throws NetshotException if the rules can't be read
	 */
	public List<HardwareRule> list() {
		try {
			return this.transactions.read(this.repository::findAllWithGroup);
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the hardware rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hardware rules.");
		}
	}

	/**
	 * Adds a hardware rule.
	 *
	 * @param definition the rule
	 * @return the new rule
	 * @throws NetshotException if the rule can't be stored
	 */
	public HardwareRule create(HardwareRuleDefinition definition) {
		HardwareRule rule;
		try {
			rule = this.transactions.write(session -> {
				DeviceGroup group = this.findGroup(session, definition.groupId());
				HardwareRule newRule = new HardwareRule(knownDriver(definition.driver()), group,
					definition.family(), definition.familyRegExp(), definition.partNumber(),
					definition.partNumberRegExp(), definition.endOfSale(), definition.endOfLife());
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
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the rule to the database");
		}
		AAA_LOG.info("{} has been created", rule);
		return rule;
	}

	/**
	 * Edits a hardware rule.
	 *
	 * @param id the rule ID
	 * @param definition the new properties
	 * @return the rule
	 * @throws NetshotException if the rule doesn't exist, or can't be stored
	 */
	public HardwareRule update(long id, HardwareRuleDefinition definition) {
		HardwareRule rule;
		try {
			rule = this.transactions.write(session -> {
				HardwareRule existing = this.repository.find(session, id);
				if (existing == null) {
					log.error("Unable to find the rule {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				existing.setDriver(knownDriver(definition.driver()));
				existing.setTargetGroup(this.findGroup(session, definition.groupId()));
				existing.setFamily(definition.family());
				existing.setFamilyRegExp(definition.familyRegExp());
				existing.setEndOfLife(definition.endOfLife());
				existing.setEndOfSale(definition.endOfSale());
				existing.setPartNumber(definition.partNumber());
				existing.setPartNumberRegExp(definition.partNumberRegExp());
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
	 * Deletes a hardware rule.
	 *
	 * @param id the rule ID
	 * @return true if the rule has been deleted, false if there was no such rule
	 * @throws NetshotException if the rule can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.write(session -> {
				HardwareRule rule = this.repository.find(session, id);
				if (rule == null) {
					log.info("No such hardware rule of ID {}", id);
					return false;
				}
				session.remove(rule);
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Hardware rule of ID {} has been deleted", id);
			}
			return deleted;
		}
		catch (HibernateException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
	}

	private DeviceGroup findGroup(Session session, Long groupId) {
		if (groupId == null) {
			return null;
		}
		return this.repository.findGroup(session, groupId);
	}

	private static String knownDriver(String driver) {
		if (DeviceDriver.getDriverByName(driver) == null) {
			return null;
		}
		return driver;
	}
}
