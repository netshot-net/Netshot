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
package net.netshot.netshot.inventory.diagnostic;

import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC;
import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;

import java.util.Collection;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The queries on the diagnostic results (one per device and diagnostic). The {@link DSLContext}
 * is always the first parameter: the caller (the service) owns the transaction.
 */
public class DiagnosticResultRepository {

	/** The discriminators of the diagnostic results. */
	private static final String TEXT = "S";
	private static final String NUMERIC = "N";
	private static final String BINARY = "B";
	private static final String LONG_TEXT = "T";

	private static DiagnosticResult toResult(Record r) {
		String type = r.get(DIAGNOSTIC_RESULT.TYPE);
		Long diagnosticId = r.get(DIAGNOSTIC_RESULT.DIAGNOSTIC);
		String diagnosticName = r.get(DIAGNOSTIC.NAME);
		return switch (type) {
			case TEXT -> new DiagnosticTextResult(diagnosticId, diagnosticName, r.get(DIAGNOSTIC_RESULT.CREATION_DATE),
				r.get(DIAGNOSTIC_RESULT.LAST_CHECK_DATE), r.get(DIAGNOSTIC_RESULT.TEXT));
			case NUMERIC -> new DiagnosticNumericResult(diagnosticId, diagnosticName, r.get(DIAGNOSTIC_RESULT.CREATION_DATE),
				r.get(DIAGNOSTIC_RESULT.LAST_CHECK_DATE), r.get(DIAGNOSTIC_RESULT.NUMBER));
			case BINARY -> new DiagnosticBinaryResult(diagnosticId, diagnosticName, r.get(DIAGNOSTIC_RESULT.CREATION_DATE),
				r.get(DIAGNOSTIC_RESULT.LAST_CHECK_DATE), r.get(DIAGNOSTIC_RESULT.ASSUMPTION));
			case LONG_TEXT -> new DiagnosticLongTextResult(diagnosticId, diagnosticName, r.get(DIAGNOSTIC_RESULT.CREATION_DATE),
				r.get(DIAGNOSTIC_RESULT.LAST_CHECK_DATE), r.get(LONG_TEXT_CONFIGURATION.TEXT));
			default -> throw new IllegalStateException("Unknown type of diagnostic result '%s'".formatted(type));
		};
	}

	/** The type of the result, by name (the discriminator is a letter). */
	private static final Field<String> TYPE_NAME = DSL.case_(DIAGNOSTIC_RESULT.TYPE)
		.when(TEXT, "TEXT").when(NUMERIC, "NUMERIC").when(BINARY, "BINARY").when(LONG_TEXT, "LONGTEXT").else_("TEXT");

	/** The fields the lists of results can be filtered and sorted on. */
	public static final QueryMapping LIST = QueryMapping.builder(DIAGNOSTIC_RESULT.ID)
		.integer("device", DIAGNOSTIC_RESULT.DEVICE)
		.integer("diagnostic", DIAGNOSTIC_RESULT.DIAGNOSTIC)
		.string("diagnosticName", DIAGNOSTIC.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("type", TYPE_NAME, List.of("TEXT", "LONGTEXT", "NUMERIC", "BINARY"), Option.SORTABLE)
		.date("creationDate", DIAGNOSTIC_RESULT.CREATION_DATE, Option.SORTABLE)
		.date("lastCheckDate", DIAGNOSTIC_RESULT.LAST_CHECK_DATE, Option.SORTABLE)
		.build();

	/**
	 * Lists the diagnostic results matching a query (with their long texts).
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the results of the window of the query
	 */
	public List<DiagnosticResult> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(DIAGNOSTIC_RESULT.TYPE, DIAGNOSTIC_RESULT.DIAGNOSTIC, DIAGNOSTIC.NAME,
					DIAGNOSTIC_RESULT.CREATION_DATE, DIAGNOSTIC_RESULT.LAST_CHECK_DATE, DIAGNOSTIC_RESULT.TEXT, DIAGNOSTIC_RESULT.NUMBER,
					DIAGNOSTIC_RESULT.ASSUMPTION, LONG_TEXT_CONFIGURATION.TEXT)
				.from(DIAGNOSTIC_RESULT)
				.leftJoin(DIAGNOSTIC).on(DIAGNOSTIC.ID.eq(DIAGNOSTIC_RESULT.DIAGNOSTIC))
				.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(DIAGNOSTIC_RESULT.LONG_TEXT))
				.where(LIST.condition(query))
				.orderBy(LIST.orderBy(query)))
			.fetch(DiagnosticResultRepository::toResult);
	}

	/**
	 * Counts the diagnostic results matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of results
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(dsl.select(DIAGNOSTIC_RESULT.ID).from(DIAGNOSTIC_RESULT)
			.leftJoin(DIAGNOSTIC).on(DIAGNOSTIC.ID.eq(DIAGNOSTIC_RESULT.DIAGNOSTIC))
			.where(LIST.condition(query)));
	}

	/**
	 * Finds the diagnostic results of a device (with their long texts), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the results
	 */
	public List<DiagnosticResult> findByDevice(DSLContext dsl, long deviceId, Page page) {
		return page.apply(dsl.select(DIAGNOSTIC_RESULT.TYPE, DIAGNOSTIC_RESULT.DIAGNOSTIC, DIAGNOSTIC.NAME, DIAGNOSTIC_RESULT.CREATION_DATE,
					DIAGNOSTIC_RESULT.LAST_CHECK_DATE, DIAGNOSTIC_RESULT.TEXT, DIAGNOSTIC_RESULT.NUMBER, DIAGNOSTIC_RESULT.ASSUMPTION,
					LONG_TEXT_CONFIGURATION.TEXT)
				.from(DIAGNOSTIC_RESULT)
				.leftJoin(DIAGNOSTIC).on(DIAGNOSTIC.ID.eq(DIAGNOSTIC_RESULT.DIAGNOSTIC))
				.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(DIAGNOSTIC_RESULT.LONG_TEXT))
				.where(DIAGNOSTIC_RESULT.DEVICE.eq(deviceId))
				.orderBy(DIAGNOSTIC_RESULT.ID))
			.fetch(DiagnosticResultRepository::toResult);
	}

	/**
	 * Finds the diagnostic results of a device (with their long texts), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @return the results
	 */
	public List<DiagnosticResult> findByDevice(DSLContext dsl, long deviceId) {
		return this.findByDevice(dsl, deviceId, Page.ALL);
	}

	/**
	 * Stores new results of a device: a result whose value is the same as the stored one (for the
	 * same diagnostic) only updates its last check date; otherwise the stored result is replaced.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param results the new results
	 */
	public void save(DSLContext dsl, long deviceId, Collection<DiagnosticResult> results) {
		if (results.isEmpty()) {
			return;
		}
		List<DiagnosticResult> existing = this.findByDevice(dsl, deviceId);
		for (DiagnosticResult result : results) {
			DiagnosticResult stored = existing.stream()
				.filter(r -> r.diagnosticId().equals(result.diagnosticId())).findFirst().orElse(null);
			if (stored != null && result.valueEquals(stored)) {
				dsl.update(DIAGNOSTIC_RESULT)
					.set(DIAGNOSTIC_RESULT.LAST_CHECK_DATE, result.lastCheckDate())
					.where(DIAGNOSTIC_RESULT.DEVICE.eq(deviceId), DIAGNOSTIC_RESULT.DIAGNOSTIC.eq(result.diagnosticId()))
					.execute();
				continue;
			}
			if (stored != null) {
				List<Long> longTextIds = new java.util.ArrayList<>(dsl.deleteFrom(DIAGNOSTIC_RESULT)
					.where(DIAGNOSTIC_RESULT.DEVICE.eq(deviceId), DIAGNOSTIC_RESULT.DIAGNOSTIC.eq(result.diagnosticId()))
					.returning(DIAGNOSTIC_RESULT.LONG_TEXT)
					.fetch(DIAGNOSTIC_RESULT.LONG_TEXT));
				longTextIds.removeIf(java.util.Objects::isNull);
				DeviceRepository.deleteLongTexts(dsl, longTextIds);
			}
			var insert = dsl.insertInto(DIAGNOSTIC_RESULT)
				.set(DIAGNOSTIC_RESULT.DEVICE, deviceId)
				.set(DIAGNOSTIC_RESULT.DIAGNOSTIC, result.diagnosticId())
				.set(DIAGNOSTIC_RESULT.CREATION_DATE, result.creationDate())
				.set(DIAGNOSTIC_RESULT.LAST_CHECK_DATE, result.lastCheckDate());
			switch (result) {
				case DiagnosticTextResult r -> insert.set(DIAGNOSTIC_RESULT.TYPE, TEXT).set(DIAGNOSTIC_RESULT.TEXT, r.text()).execute();
				case DiagnosticNumericResult r -> insert.set(DIAGNOSTIC_RESULT.TYPE, NUMERIC).set(DIAGNOSTIC_RESULT.NUMBER, r.number()).execute();
				case DiagnosticBinaryResult r -> insert.set(DIAGNOSTIC_RESULT.TYPE, BINARY).set(DIAGNOSTIC_RESULT.ASSUMPTION, r.assumption()).execute();
				case DiagnosticLongTextResult r -> insert.set(DIAGNOSTIC_RESULT.TYPE, LONG_TEXT)
					.set(DIAGNOSTIC_RESULT.LONG_TEXT, DeviceRepository.insertLongText(dsl, r.text())).execute();
			}
		}
	}
}
