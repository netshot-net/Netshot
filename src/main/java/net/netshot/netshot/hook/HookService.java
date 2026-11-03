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
package net.netshot.netshot.hook;

import java.util.HashSet;
import java.util.List;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * The use cases of the hooks: list, add, edit and delete.
 *
 * <p>The inputs of {@link #create} and {@link #update} hold what the client sent (the API mapper
 * builds them).</p>
 */
@Slf4j
public class HookService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final HookRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the hook repository
	 */
	public HookService(Transactions transactions, HookRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the hooks, with their triggers.
	 *
	 * @param page the window on the result
	 * @return the hooks
	 */
	public List<Hook> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findAll(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hooks.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hooks");
		}
	}

	/**
	 * Gets what the hook lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return HookRepository.LIST.schema();
	}

	/**
	 * Lists the hooks matching a query, with their triggers.
	 *
	 * @param query the query
	 * @return the hooks of the window, with the total number of matching hooks if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the hooks can't be read
	 */
	public ListResult<Hook> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hooks.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the hooks");
		}
	}

	/**
	 * Gets a hook, with its triggers.
	 *
	 * @param id the ID of the hook
	 * @return the hook
	 * @throws NetshotException if there is no such hook, or it can't be read
	 */
	public Hook get(long id) {
		Hook hook;
		try {
			hook = this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hook {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hook");
		}
		if (hook == null) {
			throw new NetshotException(ErrorCode.NETSHOT_HOOK_NOT_FOUND, "The hook doesn't exist.");
		}
		return hook;
	}

	/**
	 * Lists the enabled hooks having a given trigger.
	 *
	 * @param type the type of trigger
	 * @param item the item of the trigger
	 * @return the hooks
	 */
	public List<Hook> findEnabled(HookTrigger.TriggerType type, String item) {
		try {
			return this.transactions.read(dsl -> this.repository.findEnabledByTrigger(dsl, type, item));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the hooks.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hooks");
		}
	}

	/**
	 * Fails as the former code did on a null trigger (a NullPointerException, answered as an
	 * unexpected error), before the validation of the hook.
	 *
	 * @param hook the hook
	 */
	private static void refuseNullTrigger(Hook hook) {
		if (hook.triggers() != null && hook.triggers().contains(null)) {
			throw new NullPointerException("Null hook trigger");
		}
	}

	/**
	 * Adds a hook.
	 *
	 * @param hook the hook to add
	 * @return the added hook, with its ID
	 */
	public Hook create(Hook hook) {
		if (hook.name() == null || "".equals(hook.name().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_NAME, "Invalid name for the hook");
		}
		refuseNullTrigger(hook);
		Hook valid = validate(hook);
		Hook created;
		try {
			created = valid.withId(this.transactions.write(dsl -> this.repository.add(dsl, valid)));
		}
		catch (DataAccessException e) {
			log.error("Can't add the hook.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", created);
		return created;
	}

	private static Hook validate(Hook hook) {
		return switch (hook) {
			case WebHook webHook -> HookValidation.validateWebHook(webHook);
		};
	}

	/**
	 * Edits a hook: its properties and triggers are replaced by the given ones.
	 *
	 * @param id the ID of the hook
	 * @param incoming the new values (null triggers meaning none, as on creation)
	 * @return the hook as saved
	 */
	public Hook update(long id, Hook incoming) {
		Hook hook = incoming.withId(id);
		if (hook.triggers() == null) {
			// No triggers (null) means none, as on creation
			hook = hook.withTriggers(new HashSet<>());
		}
		if (hook.name() == null || "".equals(hook.name().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_NAME, "Invalid name for the hook");
		}
		Hook valid = validate(hook);
		try {
			this.transactions.run(dsl -> {
				Hook stored = this.repository.find(dsl, id);
				// A hook of another type is not found, as with the former query by type
				if (stored == null || !stored.getClass().equals(valid.getClass())) {
					log.error("Unable to find the hook {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK, "Unable to find the hook.");
				}
				refuseNullTrigger(valid);
				this.repository.save(dsl, valid);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the hook {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", valid);
		return valid;
	}

	private static NetshotException saveFailure(DataAccessException e) {
		if (DatabaseErrors.isUniqueViolation(e)) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_HOOK, "A hook with this name already exists.");
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the hook");
	}

	/**
	 * Deletes a hook.
	 *
	 * @param id the ID of the hook
	 * @return false if there is no such hook
	 */
	public boolean delete(long id) {
		boolean deleted;
		try {
			deleted = this.transactions.write(dsl -> this.repository.remove(dsl, id));
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the hook {}", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the hook");
		}
		if (deleted) {
			AAA_LOG.info("Hook of ID {} has been deleted.", id);
		}
		else {
			log.info("No such hook of ID {}", id);
		}
		return deleted;
	}
}
