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

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC;
import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC_RESULT;

import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the diagnostics. The {@link DSLContext} is always the first parameter: the
 * caller (the service) owns the transaction. The results are queried by {@link DiagnosticResultRepository}.
 */
public class DiagnosticRepository {

	/** The types (discriminator) of the diagnostics. */
	static final String SIMPLE = "SIMPLE";
	static final String JAVASCRIPT = "JAVASCRIPT";
	static final String PYTHON = "PYTHON";

	private static Diagnostic toDiagnostic(Record r) {
		String type = r.get(DIAGNOSTIC.TYPE);
		long id = r.get(DIAGNOSTIC.ID);
		String name = r.get(DIAGNOSTIC.NAME);
		boolean enabled = r.get(DIAGNOSTIC.ENABLED);
		Long targetGroupId = r.get(DIAGNOSTIC.TARGET_GROUP);
		AttributeType resultType = r.get(DIAGNOSTIC.RESULT_TYPE) == null ? null : AttributeType.valueOf(r.get(DIAGNOSTIC.RESULT_TYPE));
		return switch (type) {
			case SIMPLE -> new SimpleDiagnostic(id, name, enabled, targetGroupId, resultType, r.get(DIAGNOSTIC.DEVICE_DRIVER),
				r.get(DIAGNOSTIC.CLI_MODE), r.get(DIAGNOSTIC.COMMAND), r.get(DIAGNOSTIC.MODIFIER_PATTERN),
				r.get(DIAGNOSTIC.MODIFIER_REPLACEMENT));
			case JAVASCRIPT -> new JavaScriptDiagnostic(id, name, enabled, targetGroupId, resultType, r.get(DIAGNOSTIC.SCRIPT));
			case PYTHON -> new PythonDiagnostic(id, name, enabled, targetGroupId, resultType, r.get(DIAGNOSTIC.SCRIPT));
			default -> throw new IllegalStateException("Unknown type of diagnostic '%s'".formatted(type));
		};
	}

	private static List<Diagnostic> query(DSLContext dsl, Condition condition, Page page) {
		return page.apply(dsl.select(DIAGNOSTIC.fields()).from(DIAGNOSTIC).where(condition).orderBy(DIAGNOSTIC.ID))
			.fetch(DiagnosticRepository::toDiagnostic);
	}

	/**
	 * Lists the diagnostics, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the diagnostics
	 */
	public List<Diagnostic> findAll(DSLContext dsl, Page page) {
		return query(dsl, DSL.noCondition(), page);
	}

	/**
	 * Lists the enabled diagnostics, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @return the diagnostics
	 */
	public List<Diagnostic> findEnabled(DSLContext dsl) {
		return query(dsl, DIAGNOSTIC.ENABLED.isTrue(), Page.ALL);
	}

	/**
	 * Lists the enabled diagnostics run on a device (through the target groups the device is in), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the ID of the device
	 * @return the diagnostics
	 */
	public List<Diagnostic> findEnabledForDevice(DSLContext dsl, long deviceId) {
		return query(dsl, DIAGNOSTIC.ENABLED.isTrue().and(DIAGNOSTIC.TARGET_GROUP.in(
			DSL.select(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS).from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(deviceId)))), Page.ALL);
	}

	/**
	 * Gets a diagnostic.
	 *
	 * @param dsl the jOOQ context
	 * @param id the diagnostic ID
	 * @return the diagnostic, or null if there is no such diagnostic
	 */
	public Diagnostic find(DSLContext dsl, long id) {
		List<Diagnostic> diagnostics = query(dsl, DIAGNOSTIC.ID.eq(id), Page.ALL);
		return diagnostics.isEmpty() ? null : diagnostics.get(0);
	}

	/**
	 * Gets a diagnostic by name.
	 *
	 * @param dsl the jOOQ context
	 * @param name the name
	 * @return the diagnostic, or null if there is no such diagnostic
	 */
	public Diagnostic findByName(DSLContext dsl, String name) {
		List<Diagnostic> diagnostics = query(dsl, DIAGNOSTIC.NAME.eq(name), Page.ALL);
		return diagnostics.isEmpty() ? null : diagnostics.get(0);
	}

	/**
	 * Stores a new diagnostic.
	 *
	 * @param dsl the jOOQ context
	 * @param diagnostic the diagnostic (its ID is ignored)
	 * @return the ID of the new diagnostic
	 */
	public long add(DSLContext dsl, Diagnostic diagnostic) {
		var insert = dsl.insertInto(DIAGNOSTIC)
			.set(DIAGNOSTIC.NAME, diagnostic.name())
			.set(DIAGNOSTIC.ENABLED, diagnostic.enabled())
			.set(DIAGNOSTIC.TARGET_GROUP, diagnostic.targetGroupId())
			.set(DIAGNOSTIC.RESULT_TYPE, diagnostic.resultType() == null ? null : diagnostic.resultType().name());
		var full = switch (diagnostic) {
			case SimpleDiagnostic simple -> insert.set(DIAGNOSTIC.TYPE, SIMPLE)
				.set(DIAGNOSTIC.DEVICE_DRIVER, simple.deviceDriver())
				.set(DIAGNOSTIC.CLI_MODE, simple.cliMode())
				.set(DIAGNOSTIC.COMMAND, simple.command())
				.set(DIAGNOSTIC.MODIFIER_PATTERN, simple.modifierPattern())
				.set(DIAGNOSTIC.MODIFIER_REPLACEMENT, simple.modifierReplacement());
			case JavaScriptDiagnostic js -> insert.set(DIAGNOSTIC.TYPE, JAVASCRIPT).set(DIAGNOSTIC.SCRIPT, js.script());
			case PythonDiagnostic python -> insert.set(DIAGNOSTIC.TYPE, PYTHON).set(DIAGNOSTIC.SCRIPT, python.script());
		};
		return full.returning(DIAGNOSTIC.ID).fetchOne(DIAGNOSTIC.ID);
	}

	/**
	 * Saves an existing diagnostic (its type is not changed).
	 *
	 * @param dsl the jOOQ context
	 * @param diagnostic the diagnostic
	 * @return true if the diagnostic was found and saved
	 */
	public boolean save(DSLContext dsl, Diagnostic diagnostic) {
		var update = dsl.update(DIAGNOSTIC)
			.set(DIAGNOSTIC.NAME, diagnostic.name())
			.set(DIAGNOSTIC.ENABLED, diagnostic.enabled())
			.set(DIAGNOSTIC.TARGET_GROUP, diagnostic.targetGroupId())
			.set(DIAGNOSTIC.RESULT_TYPE, diagnostic.resultType() == null ? null : diagnostic.resultType().name());
		var full = switch (diagnostic) {
			case SimpleDiagnostic simple -> update
				.set(DIAGNOSTIC.DEVICE_DRIVER, simple.deviceDriver())
				.set(DIAGNOSTIC.CLI_MODE, simple.cliMode())
				.set(DIAGNOSTIC.COMMAND, simple.command())
				.set(DIAGNOSTIC.MODIFIER_PATTERN, simple.modifierPattern())
				.set(DIAGNOSTIC.MODIFIER_REPLACEMENT, simple.modifierReplacement());
			case JavaScriptDiagnostic js -> update.set(DIAGNOSTIC.SCRIPT, js.script());
			case PythonDiagnostic python -> update.set(DIAGNOSTIC.SCRIPT, python.script());
		};
		return full.where(DIAGNOSTIC.ID.eq(diagnostic.id())).execute() > 0;
	}

	/**
	 * Removes a diagnostic.
	 *
	 * @param dsl the jOOQ context
	 * @param id the diagnostic ID
	 * @return true if the diagnostic was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(DIAGNOSTIC).where(DIAGNOSTIC.ID.eq(id)).execute() > 0;
	}

	/**
	 * Deletes all the results of a diagnostic.
	 *
	 * @param dsl the jOOQ context
	 * @param diagnosticId the diagnostic ID
	 */
	public void deleteResults(DSLContext dsl, long diagnosticId) {
		dsl.deleteFrom(DIAGNOSTIC_RESULT).where(DIAGNOSTIC_RESULT.DIAGNOSTIC.eq(diagnosticId)).execute();
	}
}
