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
package net.netshot.netshot.diagnostic;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The diagnostic use cases.
 */
@Slf4j
public class DiagnosticService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final DiagnosticRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the diagnostic repository
	 */
	public DiagnosticService(Transactions transactions, DiagnosticRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the diagnostics, with their target group.
	 *
	 * @param page the window on the result
	 * @return the diagnostics
	 * @throws NetshotException if the diagnostics can't be read
	 */
	public List<Diagnostic> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findAllWithGroup(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostics.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the diagnostics");
		}
	}

	/**
	 * Gets a diagnostic, with its target group.
	 *
	 * @param id the diagnostic ID
	 * @return the diagnostic
	 * @throws NetshotException if the diagnostic doesn't exist or can't be read
	 */
	public Diagnostic get(long id) {
		try {
			return this.transactions.read(session -> {
				Diagnostic diagnostic = this.repository.findWithGroup(session, id);
				if (diagnostic == null) {
					log.warn("Unable to find the diagnostic object.");
					throw new NetshotException(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, "Unable to find the diagnostic");
				}
				return diagnostic;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostic.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the diagnostic");
		}
	}

	/**
	 * Adds a diagnostic.
	 *
	 * @param definition the diagnostic
	 * @return the new diagnostic, with its target group
	 * @throws NetshotException if the name, the result type, the type or the type-specific
	 *         properties are invalid, if the name is already used, or if the diagnostic can't be stored
	 */
	public Diagnostic create(DiagnosticDefinition definition) {
		String name = checkName(definition);
		AttributeType resultType = parseResultType(definition);
		Diagnostic diagnostic;
		try {
			diagnostic = this.transactions.write(session -> {
				DeviceGroup group = null;
				if (definition.targetGroup() != -1) {
					group = this.requireGroup(session, definition.targetGroup());
				}
				Diagnostic newDiagnostic = build(definition, name, group, resultType);
				session.persist(newDiagnostic);
				return newDiagnostic;
			});
		}
		catch (ObjectNotFoundException e) {
			log.error("The posted group doesn't exist", e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		catch (HibernateException e) {
			log.error("Error while saving the new diagnostic.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, "A diagnostic with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the diagnostic to the database.");
		}
		AAA_LOG.info("{} has been created", diagnostic);
		return diagnostic;
	}

	/**
	 * Edits a diagnostic. The kind of the diagnostic cannot change. The results of the diagnostic
	 * are deleted when its target group changes (unless it had none).
	 *
	 * @param id the diagnostic ID
	 * @param definition the new properties
	 * @return the diagnostic, with its target group
	 * @throws NetshotException if the diagnostic doesn't exist, the properties are invalid or of
	 *         another kind of diagnostic, the name is already used, or the diagnostic can't be stored
	 */
	public Diagnostic update(long id, DiagnosticDefinition definition) {
		Diagnostic diagnostic;
		try {
			diagnostic = this.transactions.write(session -> {
				Diagnostic existing = this.repository.find(session, id);
				if (existing == null) {
					log.error("Unable to find the diagnostic {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "Unable to find this diagnostic.");
				}
				String name = checkName(definition);
				AttributeType resultType = parseResultType(definition);
				existing.setName(name);
				if (existing.getTargetGroup() != null && existing.getTargetGroup().getId() != definition.targetGroup()) {
					this.repository.deleteResults(session, existing.getId());
				}
				DeviceGroup group = null;
				if (definition.targetGroup() != -1) {
					group = this.requireGroup(session, definition.targetGroup());
				}
				existing.setTargetGroup(group);
				existing.setResultType(resultType);
				existing.setEnabled(definition.enabled());
				if (existing instanceof JavaScriptDiagnostic javaScriptDiagnostic) {
					checkSameType("JavaScriptDiagnostic", definition);
					checkScript(definition);
					javaScriptDiagnostic.setScript(definition.script());
				}
				else if (existing instanceof PythonDiagnostic pythonDiagnostic) {
					checkSameType("PythonDiagnostic", definition);
					checkScript(definition);
					pythonDiagnostic.setScript(definition.script());
				}
				else if (existing instanceof SimpleDiagnostic simpleDiagnostic) {
					checkSameType("SimpleDiagnostic", definition);
					checkCommand(definition);
					simpleDiagnostic.setDeviceDriver(definition.deviceDriver());
					simpleDiagnostic.setCliMode(definition.cliMode());
					simpleDiagnostic.setCommand(definition.command());
					simpleDiagnostic.setModifierPattern(definition.modifierPattern());
					simpleDiagnostic.setModifierReplacement(definition.modifierReplacement());
				}
				session.merge(existing);
				return existing;
			});
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find the group {} to be assigned to the diagnostic {}.", definition.targetGroup(), id, e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Unable to find the group.");
		}
		catch (HibernateException e) {
			log.error("Unable to save the diagnostic {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, "A diagnostic with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the diagnostic.");
		}
		AAA_LOG.info("{} has been edited", diagnostic);
		return diagnostic;
	}

	/**
	 * Deletes a diagnostic, with its results.
	 *
	 * @param id the diagnostic ID
	 * @return true if the diagnostic has been deleted, false if there was no such diagnostic
	 * @throws NetshotException if the diagnostic can't be deleted
	 */
	public boolean delete(long id) {
		try {
			Diagnostic deleted = this.transactions.write(session -> {
				Diagnostic diagnostic = this.repository.find(session, id);
				if (diagnostic == null) {
					log.info("No such diagnostic of ID {}", id);
					return null;
				}
				this.repository.deleteResults(session, diagnostic.getId());
				session.remove(diagnostic);
				return diagnostic;
			});
			if (deleted != null) {
				AAA_LOG.info("{} has been deleted", deleted);
			}
			return deleted != null;
		}
		catch (ObjectNotFoundException e) {
			log.error("The diagnostic {} to be deleted doesn't exist.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "The diagnostic doesn't exist.");
		}
		catch (HibernateException e) {
			log.error("Unable to delete the diagnostic {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the diagnostic");
		}
	}

	/**
	 * Lists the diagnostic results of a device (the device is not checked to exist).
	 *
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the results, with their diagnostic
	 * @throws NetshotException if the results can't be read
	 */
	public List<DiagnosticResult> listResults(long deviceId, Page page) {
		try {
			return this.transactions.read(session -> this.repository.findResultsOfDevice(session, deviceId, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostic results.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the diagnostic results");
		}
	}

	// Checks and trims the name.
	private static String checkName(DiagnosticDefinition definition) {
		String name = definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty diagnostic name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, "Invalid diagnostic name.");
		}
		if (name.contains("\"")) {
			log.warn("Double-quotes are not allowed in the diagnostic name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, "Double-quotes are not allowed in the name.");
		}
		return name;
	}

	private static AttributeType parseResultType(DiagnosticDefinition definition) {
		try {
			return AttributeType.valueOf(definition.resultType());
		}
		catch (Exception e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "Invalid diagnostic result type.");
		}
	}

	private static void checkSameType(String expectedType, DiagnosticDefinition definition) {
		if (!expectedType.equals(definition.type())) {
			throw new NetshotException(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, "Incompatible posted diagnostic.");
		}
	}

	private static void checkScript(DiagnosticDefinition definition) {
		if (definition.script() == null || "".equals(definition.script().trim())) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "Invalid diagnostic script");
		}
	}

	private static void checkCommand(DiagnosticDefinition definition) {
		if (definition.cliMode() == null || "".equals(definition.cliMode().trim())) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "The CLI mode must be provided.");
		}
		if (definition.command() == null || "".equals(definition.command().trim())) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "The command cannot be empty.");
		}
	}

	// Builds a new diagnostic of the requested kind, after checking its specific properties.
	private static Diagnostic build(DiagnosticDefinition definition, String name, DeviceGroup group, AttributeType resultType) {
		if ("JavaScriptDiagnostic".equals(definition.type())) {
			checkScript(definition);
			return new JavaScriptDiagnostic(name, definition.enabled(), group, resultType, definition.script());
		}
		else if ("PythonDiagnostic".equals(definition.type())) {
			checkScript(definition);
			return new PythonDiagnostic(name, definition.enabled(), group, resultType, definition.script());
		}
		else if ("SimpleDiagnostic".equals(definition.type())) {
			checkCommand(definition);
			return new SimpleDiagnostic(name, definition.enabled(), group, resultType, definition.deviceDriver(),
				definition.cliMode(), definition.command(), definition.modifierPattern(), definition.modifierReplacement());
		}
		throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_TYPE, "Invalid diagnostic type.");
	}

	private DeviceGroup requireGroup(Session session, long groupId) {
		DeviceGroup group = this.repository.findGroup(session, groupId);
		if (group == null) {
			log.error("Unable to find the group {}.", groupId);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");
		}
		return group;
	}
}
