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
package net.netshot.netshot.inventory.device;

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.util.List;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.SoftwareRule;

/**
 * The few attributes of a device needed to list devices, projected by
 * {@link DeviceRepository#findSummaries} without loading the device entities.
 *
 * @param id the device ID
 * @param name the name
 * @param family the family
 * @param mgmtAddress the management address
 * @param status the status
 * @param driver the name of the device driver
 * @param eol whether the device is past its end of life date
 * @param eos whether the device is past its end of sale date
 * @param configCompliant whether the device has no non-conforming configuration compliance result
 * @param softwareLevel the software conformance level
 * @param networkClass the network class
 */
public record DeviceSummary(
	long id,
	String name,
	String family,
	String mgmtAddress,
	Device.Status status,
	String driver,
	Boolean eol,
	Boolean eos,
	Boolean configCompliant,
	SoftwareRule.ConformanceLevel softwareLevel,
	Device.NetworkClass networkClass) {

	/** Whether the device is past its end of life date (jOOQ). */
	private static final Field<Boolean> EOL = DSL.when(DSL.condition("{0} < current_date", DEVICE.EOL_DATE), DSL.inline(true))
		.otherwise(DSL.inline(false)).as("eol");

	/** Whether the device is past its end of sale date (jOOQ). */
	private static final Field<Boolean> EOS = DSL.when(DSL.condition("{0} < current_date", DEVICE.EOS_DATE), DSL.inline(true))
		.otherwise(DSL.inline(false)).as("eos");

	/** Whether the device has no non-conforming configuration compliance result (jOOQ). */
	private static final Field<Boolean> CONFIG_COMPLIANT = DSL.when(DSL.field(DSL.selectCount().from(CHECK_RESULT)
			.where(CHECK_RESULT.DEVICE.eq(DEVICE.ID), CHECK_RESULT.RESULT.eq(CheckResult.ResultOption.NONCONFORMING.name()))).eq(0),
		DSL.inline(true)).otherwise(DSL.inline(false)).as("config_compliant");

	/** The columns projecting the devices ({@code DEVICE} table) into summaries, read by {@link #fromRecord}. */
	public static final List<SelectField<?>> FIELDS = List.of(DEVICE.ID, DEVICE.NAME, DEVICE.FAMILY, DEVICE.MGMT_ADDRESS,
		DEVICE.STATUS, DEVICE.DRIVER, EOL, EOS, CONFIG_COMPLIANT, DEVICE.SOFTWARE_LEVEL, DEVICE.NETWORK_CLASS);

	private static <E extends Enum<E>> E toEnum(Class<E> type, String name) {
		return name == null ? null : Enum.valueOf(type, name);
	}

	/**
	 * Reads a summary from a row selected with {@link #FIELDS}.
	 *
	 * @param r the row
	 * @return the summary
	 */
	public static DeviceSummary fromRecord(Record r) {
		return new DeviceSummary(r.get(DEVICE.ID), r.get(DEVICE.NAME), r.get(DEVICE.FAMILY), r.get(DEVICE.MGMT_ADDRESS),
			toEnum(Device.Status.class, r.get(DEVICE.STATUS)), r.get(DEVICE.DRIVER), r.get(EOL), r.get(EOS), r.get(CONFIG_COMPLIANT),
			toEnum(SoftwareRule.ConformanceLevel.class, r.get(DEVICE.SOFTWARE_LEVEL)),
			toEnum(Device.NetworkClass.class, r.get(DEVICE.NETWORK_CLASS)));
	}
}
