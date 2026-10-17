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
package net.netshot.netshot.api.rest.v0.diagnostic;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticDefinition;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * Maps the diagnostics and their results between the entities and their REST representation.
 * All the methods are pure functions, reading detached entities with the fetch plan of
 * {@code DiagnosticRepository}.
 */
public final class DiagnosticMapper {

	private DiagnosticMapper() {
		// Static helpers only
	}

	// The description of the driver of the diagnostic: empty for any driver, "Unknown driver" when it isn't loaded
	private static String deviceDriverDescription(DriverRegistry driverRegistry, SimpleDiagnostic diagnostic) {
		if ("".equals(diagnostic.getDeviceDriver())) {
			return "";
		}
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(diagnostic.getDeviceDriver());
		if (deviceDriver == null) {
			return "Unknown driver";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Maps a diagnostic to its representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param diagnosticOrProxy the diagnostic (or a proxy of it), with its target group loaded
	 * @return the DTO, of the kind of the diagnostic
	 */
	public static DiagnosticDto toDto(DriverRegistry driverRegistry, Diagnostic diagnosticOrProxy) {
		Diagnostic diagnostic = (Diagnostic) Hibernate.unproxy(diagnosticOrProxy);
		DeviceGroupDto group = diagnostic.getTargetGroup() == null ? null : GroupMapper.toDto(diagnostic.getTargetGroup());
		if (diagnostic instanceof SimpleDiagnostic simple) {
			return new DiagnosticDto.Simple(simple.getCliMode(), simple.getCommand(), simple.getDeviceDriver(),
				deviceDriverDescription(driverRegistry, simple), simple.isEnabled(), simple.getId(), simple.getModifierPattern(),
				simple.getModifierReplacement(), simple.getName(), simple.getResultType(), group);
		}
		else if (diagnostic instanceof JavaScriptDiagnostic js) {
			return new DiagnosticDto.JavaScript(js.isEnabled(), js.getId(), js.getName(), js.getResultType(), js.getScript(), group);
		}
		else if (diagnostic instanceof PythonDiagnostic python) {
			return new DiagnosticDto.Python(python.isEnabled(), python.getId(), python.getName(), python.getResultType(),
				python.getScript(), group);
		}
		throw new IllegalArgumentException("Unknown kind of diagnostic: " + diagnostic.getClass().getName());
	}

	/**
	 * Maps the diagnostics to their representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param diagnostics the diagnostics, with their target group loaded
	 * @return the DTOs, in the same order
	 */
	public static List<DiagnosticDto> toDtos(DriverRegistry driverRegistry, List<Diagnostic> diagnostics) {
		List<DiagnosticDto> dtos = new ArrayList<>(diagnostics.size());
		diagnostics.forEach(diagnostic -> dtos.add(toDto(driverRegistry, diagnostic)));
		return dtos;
	}

	/**
	 * Maps a diagnostic result to its representation.
	 *
	 * @param resultOrProxy the result (or a proxy of it), with its diagnostic loaded
	 * @return the DTO, of the type of the result
	 */
	public static DiagnosticResultDto toDto(DiagnosticResult resultOrProxy) {
		DiagnosticResult result = (DiagnosticResult) Hibernate.unproxy(resultOrProxy);
		if (result instanceof DiagnosticTextResult text) {
			return new DiagnosticResultDto.Text(text.getCreationDate(), text.getDiagnosticId(), text.getDiagnosticName(),
				text.getLastCheckDate(), text.getText());
		}
		else if (result instanceof DiagnosticLongTextResult longText) {
			return new DiagnosticResultDto.LongText(longText.getCreationDate(), longText.getDiagnosticId(),
				longText.getDiagnosticName(), longText.getLastCheckDate());
		}
		else if (result instanceof DiagnosticNumericResult numeric) {
			return new DiagnosticResultDto.Numeric(numeric.getCreationDate(), numeric.getDiagnosticId(),
				numeric.getDiagnosticName(), numeric.getLastCheckDate(), numeric.getNumber());
		}
		else if (result instanceof DiagnosticBinaryResult binary) {
			return new DiagnosticResultDto.Binary(binary.getAssumption(), binary.getCreationDate(), binary.getDiagnosticId(),
				binary.getDiagnosticName(), binary.getLastCheckDate());
		}
		throw new IllegalArgumentException("Unknown kind of diagnostic result: " + result.getClass().getName());
	}

	/**
	 * Maps the diagnostic results to their representation.
	 *
	 * @param results the results, with their diagnostic loaded
	 * @return the DTOs, in the same order
	 */
	public static List<DiagnosticResultDto> toResultDtos(List<DiagnosticResult> results) {
		List<DiagnosticResultDto> dtos = new ArrayList<>(results.size());
		results.forEach(result -> dtos.add(toDto(result)));
		return dtos;
	}

	/**
	 * Maps a request to add or edit a diagnostic to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static DiagnosticDefinition toDefinition(DiagnosticRequest request) {
		return new DiagnosticDefinition(request.type(), request.name(), request.targetGroup(), request.enabled(),
			request.resultType(), request.script(), request.deviceDriver(), request.cliMode(), request.command(),
			request.modifierPattern(), request.modifierReplacement());
	}
}
