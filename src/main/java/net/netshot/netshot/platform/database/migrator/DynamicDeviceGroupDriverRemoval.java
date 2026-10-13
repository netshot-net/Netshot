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
import net.netshot.netshot.Netshot;
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

	/** Driver names to descriptions. */
	private Map<String, String> driverDescriptions = new HashMap<>();

	@Override
	public String getConfirmationMessage() {
		return null;
	}

	@Override
	public void setUp() throws SetupException {
		this.addWellKnownDrivers();
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

	private void addWellKnownDrivers() {
		this.driverDescriptions.putAll(new HashMap<>() {{
			this.put("A10ACOS", "A10 ACOS");
			this.put("AlcatelLucentTiMOS", "Alcatel-Lucent TiMOS");
			this.put("ArborArbOS", "Arbor ArbOS 7.2+");
			this.put("AristaEOS", "Arista EOS");
			this.put("AristaMOS", "Arista Metamako OS");
			this.put("AudioCodesMediant", "AudioCodes Mediant MG/SBC");
			this.put("AvayaERS", "Avaya ERS");
			this.put("AvocentACS", "Avocent ACS");
			this.put("BrocadeFastIron", "Brocade FastIron");
			this.put("CheckpointGaia", "Checkpoint Gaia");
			this.put("CheckpointSPLAT", "Checkpoint SPLAT");
			this.put("CienaSAOS", "Ciena SAOS");
			this.put("CiscoACE", "Cisco ACE");
			this.put("CiscoASA", "Cisco ASA");
			this.put("CiscoAireOS", "Cisco AireOS");
			this.put("CiscoFirepowerMC", "Cisco Firepower Management Center");
			this.put("CiscoIOS12", "Cisco IOS and IOS-XE");
			this.put("CiscoIOSXR", "Cisco IOS-XR");
			this.put("CiscoNXOS", "Cisco NX-OS 5+");
			this.put("CiscoStarOS", "Cisco StarOS");
			this.put("CiscoSx", "Cisco Small Business 2/3/500 series");
			this.put("CiscoViptela", "Viptela Operating System");
			this.put("CitrixNetscaler", "Citrix NetScaler");
			this.put("CitrixNetscalerSDX", "Citrix NetScaler SDX");
			this.put("F5TMOS", "F5 TM-OS, 11.x and newer");
			this.put("FortinetFortiMail", "Fortinet FortiMail");
			this.put("FortinetFortiManager", "Fortinet FortiManager and FortiAnalyzer");
			this.put("FortinetFortiOS", "Fortinet FortiOS");
			this.put("GenericSNMP", "Generic SNMP device");
			this.put("GigamonGigaVUE", "Gigamon GigaVUE");
			this.put("HPEArubaOSCX", "HPE ArubaOS CX");
			this.put("HPEArubaOSSwitch", "HPE ArubaOS-Switch");
			this.put("HPEComware", "HPE Comware OS");
			this.put("HuaweiNE", "Huawei NE Router");
			this.put("JuniperJunos", "Juniper Junos");
			this.put("JuniperScreenOS", "Juniper ScreenOS");
			this.put("PaloAltoPANOS", "Palo Alto PAN-OS");
			this.put("RiverbedRiOS", "Riverbed RiOS");
			this.put("SkyhighSWG", "Skyhigh Secure Web Gateway");
			this.put("SmartEdgeOS", "Redback Networks SmartEdge OS");
			this.put("ZPENodeGrid", "ZPE NodeGrid");
		}});
	}

	private void readAdditionalDrivers() {
		final String addPath = Netshot.getConfig("netshot.drivers.path");
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
