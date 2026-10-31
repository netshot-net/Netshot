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
package net.netshot.netshot.api.rest.v1.diagnostic;

import java.util.Set;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.diagnostic.DiagnosticRequest.GroupReference;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticDefinition;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Maps the diagnostics and their results to their v1 representation, and the requests to the definitions the
 * service takes.
 */
public final class DiagnosticMapper {

	/** The path of the diagnostics. */
	public static final String PATH = "diagnostics";

	/** The types of result a diagnostic can have. */
	private static final Set<String> RESULT_TYPES = Set.of("TEXT", "LONGTEXT", "NUMERIC", "BINARY");

	private DiagnosticMapper() {
	}

	/**
	 * Builds the reference to a group known by its ID only.
	 *
	 * @param id the group ID
	 * @return the representation
	 */
	static GroupDto groupById(long id) {
		return new GroupDto(id, V1Links.of(GroupMapper.PATH, id), null, 0, null, null, null, null, false, null);
	}

	private static String driverDescription(DriverRegistry driverRegistry, String driverName) {
		if (driverName == null || driverName.isEmpty()) {
			return "";
		}
		DeviceDriver driver = driverRegistry.getDriverByName(driverName);
		return driver == null ? "Unknown driver" : driver.getDescription();
	}

	/**
	 * Maps a diagnostic.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param diagnostic the diagnostic
	 * @param groups the target groups, already mapped
	 * @return the representation
	 */
	public static DiagnosticDto toDto(DriverRegistry driverRegistry, Diagnostic diagnostic, LongFunction<GroupDto> groups) {
		GroupDto group = diagnostic.targetGroupId() == null ? null : groups.apply(diagnostic.targetGroupId());
		String resultType = diagnostic.resultType() == null ? null : diagnostic.resultType().name();
		String url = V1Links.of(PATH, diagnostic.id());
		return switch (diagnostic) {
			case SimpleDiagnostic d -> new DiagnosticDto(d.id(), url, d.name(), d.name(), "SIMPLE", resultType, d.enabled(), group,
				null, d.deviceDriver(), driverDescription(driverRegistry, d.deviceDriver()), d.cliMode(), d.command(),
				d.modifierPattern(), d.modifierReplacement());
			case JavaScriptDiagnostic d -> new DiagnosticDto(d.id(), url, d.name(), d.name(), "JAVASCRIPT", resultType, d.enabled(),
				group, d.script(), null, null, null, null, null, null);
			case PythonDiagnostic d -> new DiagnosticDto(d.id(), url, d.name(), d.name(), "PYTHON", resultType, d.enabled(),
				group, d.script(), null, null, null, null, null, null);
		};
	}

	/**
	 * Maps a diagnostic to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param diagnostic the diagnostic
	 * @return the request
	 */
	public static DiagnosticRequest toRequest(Diagnostic diagnostic) {
		GroupReference group = diagnostic.targetGroupId() == null ? null : new GroupReference(diagnostic.targetGroupId());
		String resultType = diagnostic.resultType() == null ? null : diagnostic.resultType().name();
		return switch (diagnostic) {
			case SimpleDiagnostic d -> new DiagnosticRequest(d.name(), "SIMPLE", resultType, d.enabled(), group, null,
				d.deviceDriver(), d.cliMode(), d.command(), d.modifierPattern(), d.modifierReplacement());
			case JavaScriptDiagnostic d -> new DiagnosticRequest(d.name(), "JAVASCRIPT", resultType, d.enabled(), group, d.script(),
				null, null, null, null, null);
			case PythonDiagnostic d -> new DiagnosticRequest(d.name(), "PYTHON", resultType, d.enabled(), group, d.script(),
				null, null, null, null, null);
		};
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 * @throws NetshotException if the name, type or result type is invalid
	 */
	public static DiagnosticDefinition toDefinition(DiagnosticRequest request) {
		if (request.name() == null) {
			throw NetshotException.invalidParameter("name", "The name is required");
		}
		String type = switch (request.type() == null ? "" : request.type()) {
			case "SIMPLE" -> "SimpleDiagnostic";
			case "JAVASCRIPT" -> "JavaScriptDiagnostic";
			case "PYTHON" -> "PythonDiagnostic";
			default -> throw NetshotException.invalidParameter("type", "Invalid diagnostic type: SIMPLE, JAVASCRIPT or PYTHON is expected");
		};
		if (request.resultType() == null || !RESULT_TYPES.contains(request.resultType())) {
			throw NetshotException.invalidParameter("resultType", "Invalid result type: TEXT, LONGTEXT, NUMERIC or BINARY is expected");
		}
		String pattern = request.modifierPattern();
		String replacement = request.modifierReplacement() == null && pattern != null ? "" : request.modifierReplacement();
		return new DiagnosticDefinition(type, request.name(), request.targetGroup() == null ? -1 : request.targetGroup().id(),
			request.enabled(), request.resultType(), request.script(), request.deviceDriver() == null ? "" : request.deviceDriver(),
			request.cliMode(), request.command(), pattern, replacement);
	}

	/**
	 * Maps a diagnostic result.
	 *
	 * @param result the result
	 * @param diagnostics the diagnostics, already mapped
	 * @return the representation
	 */
	public static DiagnosticResultDto toDto(DiagnosticResult result, LongFunction<DiagnosticDto> diagnostics) {
		DiagnosticDto diagnostic = diagnostics.apply(result.diagnosticId());
		return switch (result) {
			case DiagnosticTextResult r -> new DiagnosticResultDto(r.diagnosticName(), diagnostic, "TEXT", r.text(),
				r.creationDate(), r.lastCheckDate());
			case DiagnosticLongTextResult r -> new DiagnosticResultDto(r.diagnosticName(), diagnostic, "LONGTEXT", r.text(),
				r.creationDate(), r.lastCheckDate());
			case DiagnosticNumericResult r -> new DiagnosticResultDto(r.diagnosticName(), diagnostic, "NUMERIC", r.number(),
				r.creationDate(), r.lastCheckDate());
			case DiagnosticBinaryResult r -> new DiagnosticResultDto(r.diagnosticName(), diagnostic, "BINARY", r.assumption(),
				r.creationDate(), r.lastCheckDate());
		};
	}
}
