/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.platform.database.migrator;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Custom DB migration class to remove the driver column from dynamic device groups,
 * moving the possible driver name to the search query.
 */
@Slf4j
public class DynamicDeviceGroupDriverRemoval implements CustomSqlChange {

	/**
	 * Names and descriptions of the embedded drivers which existed when the driver column was removed. The query given
	 * to a group selects the driver by description ([Driver] is "..."), which must be the description of the
	 * driver at the time of the migration: an upgrade from an old version runs this migration with the current
	 * drivers (DynamicDeviceGroupDriverRemovalTest checks this).
	 */
	static final Map<String, String> WELL_KNOWN_DRIVERS = Map.ofEntries(
		Map.entry("A10ACOS", "A10 ACOS"),
		Map.entry("AlcatelLucentTiMOS", "Alcatel-Lucent TiMOS"),
		Map.entry("ArborArbOS", "Arbor ArbOS 7.2+"),
		Map.entry("AristaEOS", "Arista EOS"),
		Map.entry("AristaMOS", "Arista Metamako OS"),
		Map.entry("AudioCodesMediant", "AudioCodes Mediant MG/SBC"),
		Map.entry("AvayaERS", "Avaya ERS"),
		Map.entry("AvocentACS", "Avocent ACS"),
		Map.entry("BrocadeFastIron", "Brocade FastIron"),
		Map.entry("CheckpointGaia", "Checkpoint GAiA OS"),
		Map.entry("CheckpointSPLAT", "Checkpoint SPLAT"),
		Map.entry("CienaSAOS", "Ciena SAOS"),
		Map.entry("CiscoACE", "Cisco ACE"),
		Map.entry("CiscoASA", "Cisco ASA"),
		Map.entry("CiscoAireOS", "Cisco AireOS"),
		Map.entry("CiscoFirepowerMC", "Cisco Firepower Management Center"),
		Map.entry("CiscoIOS12", "Cisco IOS and IOS-XE"),
		Map.entry("CiscoIOSXR", "Cisco IOS-XR"),
		Map.entry("CiscoNXOS", "Cisco NX-OS 5+"),
		Map.entry("CiscoStarOS", "Cisco StarOS"),
		Map.entry("CiscoSx", "Cisco Small Business 2/3/5xx and Sx220 series"),
		Map.entry("CiscoViptela", "Viptela Operating System"),
		Map.entry("CitrixNetscaler", "Citrix NetScaler"),
		Map.entry("CitrixNetscalerSDX", "Citrix NetScaler SDX"),
		Map.entry("F5TMOS", "F5 TM-OS, 11.x and newer"),
		Map.entry("FortinetFortiMail", "Fortinet FortiMail"),
		Map.entry("FortinetFortiManager", "Fortinet FortiManager and FortiAnalyzer"),
		Map.entry("FortinetFortiOS", "Fortinet FortiOS"),
		Map.entry("GenericSNMP", "Generic SNMP device"),
		Map.entry("GigamonGigaVUE", "Gigamon GigaVUE"),
		Map.entry("HPEArubaOSCX", "HPE ArubaOS CX"),
		Map.entry("HPEArubaOSSwitch", "HPE ArubaOS-Switch"),
		Map.entry("HPEComware", "HPE Comware OS"),
		Map.entry("HuaweiNE", "Huawei NE Router"),
		Map.entry("JuniperJunos", "Juniper Junos"),
		Map.entry("JuniperScreenOS", "Juniper ScreenOS"),
		Map.entry("PaloAltoPANOS", "Palo Alto PAN-OS"),
		Map.entry("RiverbedRiOS", "Riverbed RiOS"),
		Map.entry("SkyhighSWG", "Skyhigh Secure Web Gateway"),
		Map.entry("SmartEdgeOS", "Redback Networks SmartEdge OS"),
		Map.entry("ZPENodeGrid", "ZPE NodeGrid"));

	/** Driver names to descriptions. */
	private Map<String, String> driverDescriptions = new HashMap<>();

	@Override
	public String getConfirmationMessage() {
		return null;
	}

	@Override
	public void setUp() throws SetupException {
		this.driverDescriptions.putAll(WELL_KNOWN_DRIVERS);
		this.readAdditionalDrivers();
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
		// Nothing to do
	}

	@Override
	public ValidationErrors validate(Database database) {
		return null;
	}

	private void readAdditionalDrivers() {
		// netshot.drivers.path, declared by the driver loader (the migrations don't depend on the features)
		final String addPath = net.netshot.netshot.platform.database.Database.migrationConfig().get("netshot.drivers.path");
		if (addPath != null) {
			final File folder = new File(addPath);
			if (folder.isDirectory()) {
				File[] files = folder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(".js");
					}
				});
				try (Engine engine = Engine.create()) {
					for (File file : files) {
						log.info("Found user device driver file {}.", file);
						try (
							InputStream stream = new FileInputStream(file);
							Reader reader = new InputStreamReader(stream);
							Context context = Context.newBuilder("js").engine(engine).build();
						) {
							Source source = Source.newBuilder("js", reader, file.getName()).buildLiteral();
							context.eval(source);
							Value info = context.getBindings("js").getMember("Info");
							String name = info.getMember("name").asString();
							String description = info.getMember("description").asString();
							if (name != null && description != null) {
								log.info("Found additional driver, name '{}', description '{}'", name, description);
								this.driverDescriptions.put(name, description);
							}
						}
						catch (Exception e) {
							log.error("Error while loading user device driver {}.", file, e);
						}
					}
				}
			}
		}
	}

	@Override
	public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
		List<SqlStatement> statements = new ArrayList<>();

		final String tableName = "dynamic_device_group";
		final String idColumn = "id";
		final String driverColumn = "driver";
		final String queryColumn = "query";

		JdbcConnection connection = (JdbcConnection) database.getConnection();
		String select = "select %s, %s, %s from %s".formatted(idColumn, driverColumn, queryColumn, tableName);

		try {
			ResultSet dynamicGroups = connection.createStatement().executeQuery(select);
			while (dynamicGroups.next()) {
				final long id = dynamicGroups.getLong(idColumn);
				final String driver = dynamicGroups.getString(driverColumn);
				final String query = dynamicGroups.getString(queryColumn);
				if (driver == null || "".equals(driver.trim())) {
					// No driver set on this group so ignore
					continue;
				}
				final String driverDescription = this.driverDescriptions.getOrDefault(driver, driver);
				String newQuery = "[Driver] is \"%s\"".formatted(driverDescription);
				if (query != null && !"".equals(query.trim())) {
					newQuery = "(%s) and (%s)".formatted(newQuery, query);
				}
				UpdateStatement update = new UpdateStatement(
					database.getDefaultCatalogName(), database.getDefaultSchemaName(), tableName)
					.setWhereClause("id = ?").addWhereParameter(id);
				update.addNewColumnValue(queryColumn, newQuery);
				statements.add(update);
			}
		}
		catch (DatabaseException | SQLException e) {
			throw new CustomChangeException(
				"Database error while preparing device senstive data re-encryption", e);
		}

		return statements.toArray(new SqlStatement[0]);
	}


}
