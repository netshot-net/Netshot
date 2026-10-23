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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

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

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the hardware rule repository
	 * @param driverRegistry the loaded device drivers
	 */
	public HardwareRuleService(Transactions transactions, HardwareRuleRepository repository, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the hardware rules, by ID.
	 *
	 * @return the rules
	 * @throws NetshotException if the rules can't be read
	 */
	public List<HardwareRuleView> list() {
		try {
			return this.transactions.readDsl(dsl -> {
				List<HardwareRuleView> views = new ArrayList<>();
				for (HardwareRule rule : this.repository.findAll(dsl)) {
					views.add(new HardwareRuleView(rule, this.findGroup(dsl, rule.targetGroupId())));
				}
				return views;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hardware rules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hardware rules.");
		}
	}

	private static Instant toInstant(Date date) {
		return date == null ? null : date.toInstant();
	}

	private HardwareRule fromDefinition(long id, HardwareRuleDefinition definition) {
		return new HardwareRule(id, knownDriver(definition.driver()), definition.groupId(),
			definition.family(), definition.familyRegExp(), definition.partNumber(),
			definition.partNumberRegExp(), toInstant(definition.endOfSale()), toInstant(definition.endOfLife()));
	}

	/**
	 * Adds a hardware rule.
	 *
	 * @param definition the rule
	 * @return the new rule
	 * @throws NetshotException if the group doesn't exist, or the rule can't be stored
	 */
	public HardwareRuleView create(HardwareRuleDefinition definition) {
		HardwareRuleView rule;
		try {
			rule = this.transactions.writeDsl(dsl -> {
				DeviceGroup group = this.findGroup(dsl, definition.groupId());
				HardwareRule newRule = this.fromDefinition(0, definition);
				return new HardwareRuleView(newRule.withId(this.repository.add(dsl, newRule)), group);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new rule.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the rule to the database");
		}
		AAA_LOG.info("{} has been created", rule.rule());
		return rule;
	}

	/**
	 * Edits a hardware rule.
	 *
	 * @param id the rule ID
	 * @param definition the new properties
	 * @return the rule
	 * @throws NetshotException if the rule or the group doesn't exist, or the rule can't be stored
	 */
	public HardwareRuleView update(long id, HardwareRuleDefinition definition) {
		HardwareRuleView rule;
		try {
			rule = this.transactions.writeDsl(dsl -> {
				if (this.repository.find(dsl, id) == null) {
					log.error("Unable to find the rule {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
				}
				DeviceGroup group = this.findGroup(dsl, definition.groupId());
				HardwareRule edited = this.fromDefinition(id, definition);
				this.repository.save(dsl, edited);
				return new HardwareRuleView(edited, group);
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
	 * Deletes a hardware rule.
	 *
	 * @param id the rule ID
	 * @return true if the rule has been deleted, false if there was no such rule
	 * @throws NetshotException if the rule can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.writeDsl(dsl -> {
				if (!this.repository.remove(dsl, id)) {
					log.info("No such hardware rule of ID {}", id);
					return false;
				}
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Hardware rule of ID {} has been deleted", id);
			}
			return deleted;
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the rule.");
		}
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
