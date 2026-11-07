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
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

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
	public List<PolicyView> list(Page page) {
		try {
			return this.transactions.read(dsl -> {
				List<PolicyView> views = new ArrayList<>();
				for (Policy policy : this.repository.findAll(dsl, page)) {
					views.add(this.view(dsl, policy));
				}
				return views;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the policies.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the policies");
		}
	}

	/**
	 * Gets what the policy lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return PolicyRepository.LIST.schema();
	}

	/**
	 * Lists the policies matching a query.
	 *
	 * @param query the query
	 * @return the policies of the window, with the total number of matching policies if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the policies can't be read
	 */
	public ListResult<Policy> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the policies.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the policies");
		}
	}

	/**
	 * Gets a policy.
	 *
	 * @param id the policy ID
	 * @return the policy
	 * @throws NetshotException if the policy doesn't exist, or can't be read
	 */
	public Policy get(long id) {
		try {
			return this.transactions.read(dsl -> {
				Policy policy = this.repository.find(dsl, id);
				if (policy == null) {
					throw new NetshotException(ErrorCode.NETSHOT_POLICY_NOT_FOUND, "Unable to find the policy");
				}
				return policy;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the policy.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the policy");
		}
	}

	/**
	 * Adds a policy.
	 *
	 * @param definition the policy
	 * @return the new policy
	 * @throws NetshotException if the name is blank or already used, a group doesn't exist, or if the policy can't be stored
	 */
	public Policy add(PolicyDefinition definition) {
		return this.create(definition).policy();
	}

	/**
	 * Replaces a policy, if it is at a given version: its name and the full set of its target groups (the compliance
	 * results of the devices of a group removed from the policy are deleted). The new properties are computed from the
	 * policy as it is, which can't change meanwhile (to apply a patch).
	 *
	 * @param id the policy ID
	 * @param change gives the new properties from the current policy
	 * @param expectedVersion the version the policy must be at, or null for any
	 * @return the policy
	 * @throws NetshotException if the policy or a group doesn't exist, the policy isn't at the expected version, the
	 *         name is blank or already used, or if the policy can't be stored
	 */
	public Policy replace(long id, Function<Policy, PolicyDefinition> change, Long expectedVersion) {
		return this.update(id, change, expectedVersion,
			new NetshotException(ErrorCode.NETSHOT_POLICY_NOT_FOUND, "Unable to find the policy")).policy();
	}

	/**
	 * Gets the view of a policy: the policy with its target groups, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param policy the policy
	 * @return the view
	 */
	private PolicyView view(DSLContext dsl, Policy policy) {
		List<DeviceGroup> groups = new ArrayList<>();
		for (Long groupId : policy.targetGroupIds().stream().sorted().toList()) {
			groups.add(this.requireGroup(dsl, groupId));
		}
		return new PolicyView(policy, groups);
	}

	/**
	 * Adds a policy.
	 *
	 * @param definition the policy
	 * @return the new policy
	 * @throws NetshotException if the name is blank or already used, a group doesn't exist, or if the policy can't be stored
	 */
	public PolicyView create(PolicyDefinition definition) {
		String name = definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty policy name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY_NAME, "Invalid policy name.");
		}
		PolicyView policy;
		try {
			policy = this.transactions.write(dsl -> {
				Policy newPolicy = new Policy(0, 0, null, name, definition.targetGroupIds());
				PolicyView view = this.view(dsl, newPolicy);
				return new PolicyView(this.repository.find(dsl, this.repository.add(dsl, newPolicy)), view.targetGroups());
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new policy.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_POLICY, "A policy with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the policy to the database");
		}
		AAA_LOG.info("{} has been created.", policy.policy());
		return policy;
	}

	/**
	 * Edits a policy: its name and the full set of its target groups. The compliance results of
	 * the devices of a group removed from the policy are deleted.
	 *
	 * @param id the policy ID
	 * @param definition the new properties
	 * @return the policy
	 * @throws NetshotException if the policy or a group doesn't exist, the name is blank or already used,
	 *         or if the policy can't be stored
	 */
	public PolicyView update(long id, PolicyDefinition definition) {
		return this.update(id, _ -> definition, null, new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY, "Unable to find this policy."));
	}

	private PolicyView update(long id, Function<Policy, PolicyDefinition> change, Long expectedVersion, NetshotException notFound) {
		PolicyView policy;
		try {
			policy = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					log.error("Unable to find the policy {} to be edited.", id);
					throw notFound;
				}
				Versions.check("policy", version, expectedVersion);
				Policy existing = this.repository.find(dsl, id);
				PolicyDefinition definition = change.apply(existing);
				String name = definition.name().trim();
				if (name.isEmpty()) {
					log.warn("User posted an empty policy name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_POLICY_NAME, "Invalid policy name.");
				}
				// Remove orphan check results
				for (Long groupId : existing.targetGroupIds()) {
					if (!definition.targetGroupIds().contains(groupId)) {
						this.repository.deleteCheckResults(dsl, existing.id(), groupId);
					}
				}
				Policy edited = new Policy(id, 0, null, name, definition.targetGroupIds());
				PolicyView view = this.view(dsl, edited);
				this.repository.save(dsl, edited);
				return new PolicyView(this.repository.find(dsl, id), view.targetGroups());
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the policy {}.", id, e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_POLICY, "A policy with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the policy.");
		}
		AAA_LOG.info("{} has been edited.", policy.policy());
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
		return this.delete(id, null);
	}

	/**
	 * Deletes a policy, with its rules, if it is at a given version.
	 *
	 * @param id the policy ID
	 * @param expectedVersion the version the policy must be at, or null for any
	 * @return true if the policy has been deleted, false if there was no such policy
	 * @throws NetshotException if the policy isn't at the expected version, or can't be deleted
	 */
	public boolean delete(long id, Long expectedVersion) {
		try {
			boolean deleted = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					log.info("No such policy of ID {}", id);
					return false;
				}
				Versions.check("policy", version, expectedVersion);
				return this.repository.remove(dsl, id);
			});
			if (deleted) {
				AAA_LOG.info("Policy of ID {} has been deleted.", id);
			}
			return deleted;
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the policy {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the policy");
		}
	}

	private DeviceGroup requireGroup(DSLContext dsl, long groupId) {
		DeviceGroup group = new GroupRepository().find(dsl, groupId);
		if (group == null) {
			log.error("Unable to find the group {}.", groupId);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		return group;
	}
}
