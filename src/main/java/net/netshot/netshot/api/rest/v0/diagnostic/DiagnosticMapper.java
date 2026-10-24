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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticDefinition;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.DiagnosticView;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;

/**
 * Maps the diagnostics and their results between the records and their REST representation.
 * All the methods are pure functions.
 */
public final class DiagnosticMapper {

	private DiagnosticMapper() {
		// Static helpers only
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	// The description of the driver of the diagnostic: empty for any driver, "Unknown driver" when it isn't loaded
	private static String deviceDriverDescription(DriverRegistry driverRegistry, SimpleDiagnostic diagnostic) {
		if ("".equals(diagnostic.deviceDriver())) {
			return "";
		}
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(diagnostic.deviceDriver());
		if (deviceDriver == null) {
			return "Unknown driver";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Maps a diagnostic to its representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param view the diagnostic, with its target group
	 * @return the DTO, of the kind of the diagnostic
	 */
	public static DiagnosticDto toDto(DriverRegistry driverRegistry, DiagnosticView view) {
		DeviceGroupDto group = view.targetGroup() == null ? null : GroupMapper.toDto(view.targetGroup());
		return switch (view.diagnostic()) {
			case SimpleDiagnostic simple -> new DiagnosticDto.Simple(simple.cliMode(), simple.command(), simple.deviceDriver(),
				deviceDriverDescription(driverRegistry, simple), simple.enabled(), simple.id(), simple.modifierPattern(),
				simple.modifierReplacement(), simple.name(), simple.resultType(), group);
			case JavaScriptDiagnostic js -> new DiagnosticDto.JavaScript(js.enabled(), js.id(), js.name(), js.resultType(), js.script(), group);
			case PythonDiagnostic python -> new DiagnosticDto.Python(python.enabled(), python.id(), python.name(), python.resultType(),
				python.script(), group);
		};
	}

	/**
	 * Maps the diagnostics to their representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param diagnostics the diagnostics, with their target group loaded
	 * @return the DTOs, in the same order
	 */
	public static List<DiagnosticDto> toDtos(DriverRegistry driverRegistry, List<DiagnosticView> diagnostics) {
		List<DiagnosticDto> dtos = new ArrayList<>(diagnostics.size());
		diagnostics.forEach(diagnostic -> dtos.add(toDto(driverRegistry, diagnostic)));
		return dtos;
	}

	/**
	 * Maps a diagnostic result to its representation.
	 *
	 * @param result the result
	 * @return the DTO, of the type of the result
	 */
	public static DiagnosticResultDto toDto(DiagnosticResult result) {
		return switch (result) {
			case DiagnosticTextResult text -> new DiagnosticResultDto.Text(date(text.creationDate()), text.diagnosticId(),
				text.diagnosticName(), date(text.lastCheckDate()), text.text());
			case DiagnosticLongTextResult longText -> new DiagnosticResultDto.LongText(date(longText.creationDate()),
				longText.diagnosticId(), longText.diagnosticName(), date(longText.lastCheckDate()));
			case DiagnosticNumericResult numeric -> new DiagnosticResultDto.Numeric(date(numeric.creationDate()),
				numeric.diagnosticId(), numeric.diagnosticName(), date(numeric.lastCheckDate()), numeric.number());
			case DiagnosticBinaryResult binary -> new DiagnosticResultDto.Binary(binary.assumption(), date(binary.creationDate()),
				binary.diagnosticId(), binary.diagnosticName(), date(binary.lastCheckDate()));
		};
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
