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

import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
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
	public List<DiagnosticView> list(Page page) {
		try {
			return this.transactions.read(dsl -> {
				List<DiagnosticView> views = new ArrayList<>();
				for (Diagnostic diagnostic : this.repository.findAll(dsl, page)) {
					views.add(this.view(dsl, diagnostic));
				}
				return views;
			});
		}
		catch (DataAccessException e) {
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
	public DiagnosticView get(long id) {
		try {
			return this.transactions.read(dsl -> {
				Diagnostic diagnostic = this.repository.find(dsl, id);
				if (diagnostic == null) {
					log.warn("Unable to find the diagnostic object.");
					throw new NetshotException(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, "Unable to find the diagnostic");
				}
				return this.view(dsl, diagnostic);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the diagnostic.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the diagnostic");
		}
	}

	/**
	 * Gets the view of a diagnostic: the diagnostic with its target group.
	 *
	 * @param dsl the jOOQ context
	 * @param diagnostic the diagnostic
	 * @return the view
	 */
	private DiagnosticView view(DSLContext dsl, Diagnostic diagnostic) {
		return new DiagnosticView(diagnostic,
			diagnostic.targetGroupId() == null ? null : this.requireGroup(dsl, diagnostic.targetGroupId()));
	}

	/**
	 * Adds a diagnostic.
	 *
	 * @param definition the diagnostic
	 * @return the new diagnostic, with its target group
	 * @throws NetshotException if the name, the result type, the type or the type-specific
	 *         properties are invalid, if the name is already used, or if the diagnostic can't be stored
	 */
	public DiagnosticView create(DiagnosticDefinition definition) {
		String name = checkName(definition);
		AttributeType resultType = parseResultType(definition);
		DiagnosticView diagnostic;
		try {
			diagnostic = this.transactions.write(dsl -> {
				DeviceGroup group = null;
				if (definition.targetGroup() != -1) {
					group = this.requireGroup(dsl, definition.targetGroup());
				}
				Diagnostic newDiagnostic = build(definition, 0, name, group == null ? null : group.id(), resultType);
				return new DiagnosticView(newDiagnostic.withId(this.repository.add(dsl, newDiagnostic)), group);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new diagnostic.", e);
			if (e instanceof DataAccessException dae && DatabaseErrors.isUniqueViolation(dae)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, "A diagnostic with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the diagnostic to the database.");
		}
		AAA_LOG.info("{} has been created", diagnostic.diagnostic());
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
	public DiagnosticView update(long id, DiagnosticDefinition definition) {
		DiagnosticView diagnostic;
		try {
			diagnostic = this.transactions.write(dsl -> {
				Diagnostic existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("Unable to find the diagnostic {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, "Unable to find this diagnostic.");
				}
				String name = checkName(definition);
				AttributeType resultType = parseResultType(definition);
				if (existing.targetGroupId() != null && existing.targetGroupId() != definition.targetGroup()) {
					this.repository.deleteResults(dsl, existing.id());
				}
				DeviceGroup group = null;
				if (definition.targetGroup() != -1) {
					group = this.requireGroup(dsl, definition.targetGroup());
				}
				Long groupId = group == null ? null : group.id();
				Diagnostic edited = switch (existing) {
					case JavaScriptDiagnostic js -> {
						checkSameType("JavaScriptDiagnostic", definition);
						checkScript(definition);
						yield new JavaScriptDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.script());
					}
					case PythonDiagnostic python -> {
						checkSameType("PythonDiagnostic", definition);
						checkScript(definition);
						yield new PythonDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.script());
					}
					case SimpleDiagnostic simple -> {
						checkSameType("SimpleDiagnostic", definition);
						checkCommand(definition);
						yield new SimpleDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.deviceDriver(),
							definition.cliMode(), definition.command(), definition.modifierPattern(), definition.modifierReplacement());
					}
				};
				this.repository.save(dsl, edited);
				return new DiagnosticView(edited, group);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the diagnostic {}.", id, e);
			if (e instanceof DataAccessException dae && DatabaseErrors.isUniqueViolation(dae)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, "A diagnostic with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the diagnostic.");
		}
		AAA_LOG.info("{} has been edited", diagnostic.diagnostic());
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
			Diagnostic deleted = this.transactions.write(dsl -> {
				Diagnostic diagnostic = this.repository.find(dsl, id);
				if (diagnostic == null) {
					log.info("No such diagnostic of ID {}", id);
					return null;
				}
				this.repository.deleteResults(dsl, diagnostic.id());
				this.repository.remove(dsl, id);
				return diagnostic;
			});
			if (deleted != null) {
				AAA_LOG.info("{} has been deleted", deleted);
			}
			return deleted != null;
		}
		catch (DataAccessException e) {
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
			return this.transactions.read(dsl -> new DiagnosticResultRepository().findByDevice(dsl, deviceId, page));
		}
		catch (DataAccessException e) {
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
	private static Diagnostic build(DiagnosticDefinition definition, long id, String name, Long groupId, AttributeType resultType) {
		if ("JavaScriptDiagnostic".equals(definition.type())) {
			checkScript(definition);
			return new JavaScriptDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.script());
		}
		else if ("PythonDiagnostic".equals(definition.type())) {
			checkScript(definition);
			return new PythonDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.script());
		}
		else if ("SimpleDiagnostic".equals(definition.type())) {
			checkCommand(definition);
			return new SimpleDiagnostic(id, name, definition.enabled(), groupId, resultType, definition.deviceDriver(),
				definition.cliMode(), definition.command(), definition.modifierPattern(), definition.modifierReplacement());
		}
		throw new NetshotException(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_TYPE, "Invalid diagnostic type.");
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
