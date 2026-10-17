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
package net.netshot.netshot.userscript;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The use cases of the command scripts (scripts to be later run over devices).
 */
@Slf4j
public class ScriptService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final ScriptRepository repository;
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the script repository
	 * @param driverRegistry the loaded device drivers
	 */
	public ScriptService(Transactions transactions, ScriptRepository repository, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the scripts, without their content.
	 *
	 * @param page the window on the result
	 * @return the scripts, detached, with a null script
	 * @throws NetshotException if the scripts can't be read
	 */
	public List<DeviceJsScript> list(Page page) {
		try {
			return this.transactions.read(session -> {
				List<DeviceJsScript> scripts = this.repository.findAll(session, page);
				for (DeviceJsScript script : scripts) {
					// Detached first, so that blanking the content can never be written back
					session.detach(script);
					script.setScript(null);
				}
				return scripts;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the scripts.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the scripts");
		}
	}

	/**
	 * Gets a script.
	 *
	 * @param id the script ID
	 * @return the script, or null if there is no such script
	 * @throws NetshotException if the script can't be read
	 */
	public DeviceJsScript get(long id) {
		try {
			return this.transactions.read(session -> this.repository.find(session, id));
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find the script {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, "Script not found.");
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the script {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the script.");
		}
	}

	/**
	 * Validates a script without saving it: checks the driver and the name, and evaluates the
	 * script to extract its user input definitions.
	 *
	 * @param creation the script
	 * @return the script, not saved (ID 0), with its user input definitions
	 * @throws NetshotException if the driver, the name or the content is invalid, or if the script
	 *         can't be evaluated
	 */
	public DeviceJsScript validate(ScriptCreation creation) {
		DeviceDriver driver = this.driverRegistry.getDriverByName(creation.deviceDriver());
		if (driver == null) {
			log.warn("Invalid driver name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SCRIPT, "Invalid driver name.");
		}
		if (creation.name() == null || "".equals(creation.name().trim())) {
			log.warn("Invalid script name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SCRIPT, "Invalid script name.");
		}
		if (creation.script() == null) {
			log.warn("Invalid script.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SCRIPT, "The script content can't be empty.");
		}
		DeviceJsScript script = new DeviceJsScript(creation.name(), creation.deviceDriver(), creation.script(), creation.author());
		script.setFolder(creation.folder());
		script.setUserInputDefinitions(creation.userInputDefinitions());
		try {
			script.extractUserInputDefinitions();
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid script.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_SCRIPT, e.getMessage());
		}
		return script;
	}

	/**
	 * Validates and saves a script.
	 *
	 * @param creation the script
	 * @return the new script, with its user input definitions
	 * @throws NetshotException if the script is invalid (see {@link #validate}), if its name is
	 *         already used, or if it can't be stored
	 */
	public DeviceJsScript create(ScriptCreation creation) {
		DeviceJsScript script = this.validate(creation);
		try {
			this.transactions.run(session -> session.persist(script));
		}
		catch (HibernateException e) {
			log.error("Error while saving the new rule.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_SCRIPT, "A script with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the script to the database");
		}
		AAA_LOG.info("{} has been created", script);
		return script;
	}

	/**
	 * Deletes a script.
	 *
	 * @param id the script ID
	 * @throws NetshotException if the script doesn't exist or can't be deleted
	 */
	public void delete(long id) {
		try {
			this.transactions.run(session -> {
				DeviceJsScript script = this.repository.find(session, id);
				if (script == null) {
					log.warn("No such script of ID {}", id);
					throw new NetshotException(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, "The script doesn't exist.");
				}
				session.remove(script);
			});
		}
		catch (NetshotException e) {
			log.error("Cannot delete the script.", e);
			throw e;
		}
		catch (HibernateException e) {
			log.error("Unable to delete the script {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the script.");
		}
		AAA_LOG.info("Script of ID {} has been deleted", id);
	}
}
