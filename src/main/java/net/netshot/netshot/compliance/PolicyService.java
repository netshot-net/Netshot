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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The compliance policy use cases: list, add, edit and delete policies.
 *
 * <p>The policies it returns are detached entities whose target groups are loaded: the caller
 * may read the policy and its groups (not the groups' own associations, nor the rules).</p>
 */
@Slf4j
public class PolicyService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final PolicyRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the policy repository
	 */
	public PolicyService(Transactions transactions, PolicyRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the policies, with their target groups.
	 *
	 * @param page the window on the result
	 * @return the policies
	 * @throws NetshotException if the policies can't be read
	 */
	public List<Policy> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findAllWithGroups(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the policies.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the policies");
		}
	}

	/**
	 * Adds a policy.
	 *
	 * @param definition the policy
	 * @return the new policy
	 * @throws NetshotException if the name is blank or already used, or if the policy can't be stored
	 */
	public Policy create(PolicyDefinition definition) {
		String name = definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty policy name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY_NAME, "Invalid policy name.");
		}
		Policy policy;
		try {
			policy = this.transactions.write(session -> {
				Set<DeviceGroup> groups = new HashSet<>();
				for (Long groupId : definition.targetGroupIds()) {
					groups.add(this.repository.findGroup(session, groupId));
				}
				Policy newPolicy = new Policy(name, groups);
				session.persist(newPolicy);
				return newPolicy;
			});
		}
		catch (ObjectNotFoundException e) {
			log.error("The posted group doesn't exist", e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		catch (HibernateException e) {
			log.error("Error while saving the new policy.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_POLICY, "A policy with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been created.", policy);
		return policy;
	}

	/**
	 * Edits a policy: its name and the full set of its target groups. The compliance results of
	 * the devices of a group removed from the policy are deleted.
	 *
	 * @param id the policy ID
	 * @param definition the new properties
	 * @return the policy
	 * @throws NetshotException if the policy doesn't exist, the name is blank or already used,
	 *         or if the policy can't be stored
	 */
	public Policy update(long id, PolicyDefinition definition) {
		Policy policy;
		try {
			policy = this.transactions.write(session -> {
				Policy existing = this.repository.find(session, id);
				if (existing == null) {
					log.error("Unable to find the policy {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY, "Unable to find this policy.");
				}
				String name = definition.name().trim();
				if (name.isEmpty()) {
					log.warn("User posted an empty policy name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY_NAME, "Invalid policy name.");
				}
				existing.setName(name);

				// Remove orphan check results
				for (DeviceGroup group : existing.getTargetGroups()) {
					if (!definition.targetGroupIds().contains(group.getId())) {
						this.repository.deleteCheckResults(session, existing.getId(), group.getId());
					}
				}
				existing.getTargetGroups().clear();
				for (Long groupId : definition.targetGroupIds()) {
					existing.getTargetGroups().add(this.repository.findGroup(session, groupId));
				}
				session.merge(existing);
				return existing;
			});
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find a group to be assigned to the policy {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Unable to find the group.");
		}
		catch (HibernateException e) {
			log.error("Unable to save the policy {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_POLICY, "A policy with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the policy.");
		}
		AAA_LOG.info("{} has been edited.", policy);
		return policy;
	}

	/**
	 * Deletes a policy, with its rules.
	 *
	 * @param id the policy ID
	 * @return true if the policy has been deleted, false if there was no such policy
	 * @throws NetshotException if the policy can't be deleted
	 */
	public boolean delete(long id) {
		try {
			boolean deleted = this.transactions.write(session -> {
				Policy policy = this.repository.find(session, id);
				if (policy == null) {
					log.info("No such policy of ID {}", id);
					return false;
				}
				session.remove(policy);
				return true;
			});
			if (deleted) {
				AAA_LOG.info("Policy of ID {} has been deleted.", id);
			}
			return deleted;
		}
		catch (HibernateException e) {
			log.error("Unable to delete the policy {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the policy");
		}
	}
}
