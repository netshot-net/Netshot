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

import java.util.List;

import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The use cases of the hooks: list, add, edit and delete.
 *
 * <p>The inputs of {@link #create} and {@link #update} are <em>transient</em> hooks holding what the
 * client sent (the API mapper builds them). The hooks it returns are detached, with their triggers
 * loaded.</p>
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
	 * Lists the hooks.
	 *
	 * @param page the window on the result
	 * @return the hooks, with their triggers
	 * @throws NetshotException if the hooks can't be read
	 */
	public List<Hook> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findAll(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the hooks.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the hooks");
		}
	}

	/**
	 * Adds a hook.
	 *
	 * @param hook the hook to add (it becomes the persistent one)
	 * @return the new hook
	 * @throws NetshotException if the name is blank or already used, the Web hook target URL, action
	 *         or HTTPS trust policy is invalid, or the hook can't be stored
	 */
	public Hook create(Hook hook) {
		if (hook.getName() == null || "".equals(hook.getName().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_NAME, "Invalid name for the hook");
		}
		if (hook.getTriggers() != null) {
			for (HookTrigger trigger : hook.getTriggers()) {
				trigger.setHook(hook);
			}
		}
		if (hook instanceof WebHook webHook) {
			HookValidation.validateWebHook(webHook);
		}
		try {
			this.transactions.run(session -> this.repository.add(session, hook));
		}
		catch (HibernateException e) {
			log.error("Can't add the hook.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", hook);
		return hook;
	}

	/**
	 * Edits a hook.
	 *
	 * @param id the hook ID
	 * @param incoming the new definition, of the same kind as the existing hook (its ID is set to {@code id})
	 * @return the new definition, as it has been saved
	 * @throws NetshotException if the name is blank or already used, the Web hook target URL, action
	 *         or HTTPS trust policy is invalid, the hook doesn't exist or is of another kind, or
	 *         the hook can't be stored
	 */
	public Hook update(long id, Hook incoming) {
		incoming.setId(id);
		if (incoming.getName() == null || "".equals(incoming.getName().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_NAME, "Invalid name for the hook");
		}
		if (incoming instanceof WebHook rsWebHook) {
			HookValidation.validateWebHook(rsWebHook);
		}
		try {
			this.transactions.run(session -> {
				Hook hook = this.repository.find(session, incoming.getClass(), id);
				if (hook == null) {
					log.error("Unable to find the hook {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK, "Unable to find the hook.");
				}
				if (!hook.getClass().equals(incoming.getClass())) {
					log.error("Wrong posted type for hook {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_TYPE,
						"The posted hook type doesn't match the existing one.");
				}
				hook.setEnabled(incoming.isEnabled());
				hook.setName(incoming.getName());
				for (HookTrigger trigger : incoming.getTriggers()) {
					trigger.setHook(hook);
				}
				// Ensure we keep the same HookTrigger object instances
				hook.getTriggers().retainAll(incoming.getTriggers());
				hook.getTriggers().addAll(incoming.getTriggers());
				if (incoming instanceof WebHook rsWebHook
						&& hook instanceof WebHook webHook) {
					webHook.setAction(rsWebHook.getAction());
					webHook.setUrl(rsWebHook.getUrl());
					webHook.setHttpsCaTrustMode(rsWebHook.getHttpsCaTrustMode());
					webHook.setHttpsCustomCaCertificate(rsWebHook.getHttpsCustomCaCertificate());
				}
				this.repository.save(session, hook);
			});
		}
		catch (HibernateException e) {
			log.error("Unable to save the hook {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", incoming);
		return incoming;
	}

	private static NetshotException saveFailure(HibernateException e) {
		if (DatabaseErrors.causeMentions(e, "uplicate")) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_HOOK, "A hook with this name already exists.");
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the hook");
	}

	/**
	 * Deletes a hook.
	 *
	 * @param id the hook ID
	 * @return true if the hook has been deleted, false if there was no such hook
	 * @throws NetshotException if the hook can't be deleted
	 */
	public boolean delete(long id) {
		boolean deleted;
		try {
			deleted = this.transactions.write(session -> {
				Hook hook = this.repository.find(session, id);
				if (hook == null) {
					log.info("No such hook of ID {}", id);
					return false;
				}
				this.repository.remove(session, hook);
				return true;
			});
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the hook {}", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the hook");
		}
		if (deleted) {
			AAA_LOG.info("Hook of ID {} has been deleted.", id);
		}
		return deleted;
	}
}
