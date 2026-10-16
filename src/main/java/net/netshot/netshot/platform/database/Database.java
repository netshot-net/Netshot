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
package net.netshot.netshot.platform.database;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.C3p0Settings;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.MarkerFactory;

import com.mchange.v2.c3p0.C3P0Registry;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.PersistenceException;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;

/**
 * The Database class, utilities to access the database.
 */
@Slf4j
public final class Database {

	/** The session factory. */
	private static SessionFactory sessionFactory;

	/** The service registry. */
	private static ServiceRegistry serviceRegistry;

	/** The connection pools of the current session factory. */
	private static CustomConnectionProvider connectionProvider;

	/**
	 * List classes in a given package.
	 *
	 * @param packageName
	 *                      the package name
	 * @return the list
	 * @throws ClassNotFoundException
	 *                                  the class not found exception
	 * @throws IOException
	 *                                  Signals that an I/O exception has occurred.
	 */
	public static List<Class<?>> listClassesInPackage(String packageName) throws ClassNotFoundException, IOException {
		String path = packageName.replace('.', '/');
		Enumeration<URL> resources = ClassLoader.getSystemResources(path);
		List<String> dirs = new ArrayList<>();
		while (resources.hasMoreElements()) {
			URL resource = resources.nextElement();
			dirs.add(URLDecoder.decode(resource.getFile(), "UTF-8"));
		}
		TreeSet<String> classes = new TreeSet<>();
		for (String directory : dirs) {
			classes.addAll(findClasses(directory, packageName));
		}
		ArrayList<Class<?>> classList = new ArrayList<>();
		for (String clazz : classes) {
			classList.add(Class.forName(clazz));
		}
		return classList;
	}

	/**
	 * Find classes with a path.
	 *
	 * @param path the path
	 * @param packageName the package name
	 * @return the tree set
	 */
	private static TreeSet<String> findClasses(String path, String packageName)
		throws IOException {
		TreeSet<String> classes = new TreeSet<>();
		if (path.startsWith("file:") && path.contains("!")) {
			String[] split = path.split("!");
			try {
				URL jar = new URI(split[0]).toURL();

				ZipInputStream zip = new ZipInputStream(jar.openStream());
				ZipEntry entry;
				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().endsWith(".class")) {
						String className = entry.getName().replaceAll("[$].*", "").replaceAll("[.]class", "").replace('/', '.');
						if (className.startsWith(packageName)) {
							classes.add(className);
						}
					}
				}
			}
			catch (URISyntaxException e) {
				log.error("Unable to parse path as URI", e);
			}
		}
		File dir = new File(path);
		if (!dir.exists()) {
			return classes;
		}
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				assert !file.getName().contains(".");
				classes.addAll(findClasses(file.getAbsolutePath(), packageName + "." + file.getName()));
			}
			else if (file.getName().endsWith(".class")) {
				String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
				classes.add(className);
			}
		}
		return classes;
	}

	/**
	 * Update the database schema (to be run at startup).
	 * @throws Exception 
	 */
	public static void update() throws Exception {
		// Check the encryption password is available before touching the DB
		Database.getEncryptionPassword();
		try {
			System.setProperty("liquibase.analytics.enabled", "false");
			Class.forName("org.postgresql.Driver");
			try (Connection connection = Database.getConnection(false)) {
				liquibase.database.Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
				new CommandScope(UpdateCommandStep.COMMAND_NAME)
					.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
					.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, "migration/netshot0.xml")
					.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
					.execute();
			}
		}
		catch (Exception e) {
			log.error("Unable to perform initial schema update", e);
			throw e;
		}
	}

	/**
	 * Initializes the database access, with Hibernate.
	 *
	 * @param model the persistent model (entities and converters), provided by the caller
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void init(DatabaseModel model) {
		// Initialized again (tests): release the connections of the previous initialization
		shutdown();
		try {
			Properties serviceProperties = new Properties();
			serviceProperties.setProperty(AvailableSettings.TRANSACTION_COORDINATOR_STRATEGY, "jdbc");
			serviceProperties.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread");
			// serviceProperties.setProperty(AvailableSettings.HBM2DDL_AUTO, "update"); // "update" or "validate" or ""
			// serviceProperties.setProperty(AvailableSettings.SHOW_SQL, "true");
			serviceProperties.setProperty(AvailableSettings.FORMAT_SQL, "true");
			// Dates/times stored in UTC in the DB, without timezone, up to Java to convert to server local time
			serviceProperties.setProperty(AvailableSettings.JDBC_TIME_ZONE, "UTC");

			// Use the custom Multi Tenant connection provider (to handle read vs read-write connections)
			connectionProvider = new CustomConnectionProvider();


			final Map<String, Object> connectionProviderProperties = new HashMap<>();
			connectionProviderProperties.put("hibernate.connection.driver_class", getDriverClass());
			connectionProviderProperties.put("hibernate.connection.username", getUsername());
			connectionProviderProperties.put("hibernate.connection.password", getPassword());

			// Each pool gets its own copy of the base properties
			final Map<String, Object> readWriteProperties = new HashMap<>(connectionProviderProperties);
			readWriteProperties.put(AvailableSettings.JAKARTA_JDBC_URL, getUrl());
			connectionProvider.registerConnectionProvider(TenantIdentifier.READ_WRITE, readWriteProperties, true);

			// Small dedicated pool for task dispatch and cluster bookkeeping, so that these can't be
			// starved by long-running tasks holding connections from the main pool
			final Map<String, Object> systemProperties = new HashMap<>(connectionProviderProperties);
			systemProperties.put(AvailableSettings.JAKARTA_JDBC_URL, getUrl());
			systemProperties.put(C3p0Settings.C3P0_MIN_SIZE,
				Netshot.getConfig("netshot.db.pooler.system.minpoolsize", 1, 0, Integer.MAX_VALUE));
			systemProperties.put(C3p0Settings.C3P0_MAX_SIZE,
				Netshot.getConfig("netshot.db.pooler.system.maxpoolsize", 5, 1, Integer.MAX_VALUE));
			connectionProvider.registerConnectionProvider(TenantIdentifier.SYSTEM, systemProperties, false);

			String readDbUrl = getReadUrl();
			if (readDbUrl != null) {
				final Map<String, Object> readOnlyProperties = new HashMap<>(connectionProviderProperties);
				readOnlyProperties.put(AvailableSettings.JAKARTA_JDBC_URL, readDbUrl);
				connectionProvider.registerConnectionProvider(TenantIdentifier.READ_ONLY, readOnlyProperties, true);
			}

			serviceProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
			if (getDriverClass().contains("postgresql")) {
				serviceProperties.setProperty(AvailableSettings.DIALECT, "net.netshot.netshot.platform.database.CustomPostgreSQLDialect");
			}
			serviceRegistry = new StandardServiceRegistryBuilder().applySettings(serviceProperties).build();

			MetadataSources sources = new MetadataSources(serviceRegistry);
			for (Class<?> clazz : model.entityClasses()) {
				sources.addAnnotatedClass(clazz);
			}

			MetadataBuilder metadataBuilder = sources.getMetadataBuilder()
				.applyImplicitNamingStrategy(new ImprovedImplicitNamingStrategy())
				.applyPhysicalNamingStrategy(new ImprovedPhysicalNamingStrategy());
			for (Class<? extends AttributeConverter<?, ?>> converter : model.attributeConverters()) {
				metadataBuilder.applyAttributeConverter((Class) converter);
			}
			Metadata metadata = metadataBuilder.build();

			SessionFactoryBuilder sessionFactoryBuilder = metadata.getSessionFactoryBuilder();
			sessionFactoryBuilder.applyInterceptor(new DatabaseInterceptor());
			sessionFactory = sessionFactoryBuilder.build();

		}
		catch (HibernateException e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to instantiate Hibernate", e);
			throw new RuntimeException("Unable to instantiate Hibernate, see logs for more details");
		}
	}

	/**
	 * Closes the session factory and its connection pools, if initialized.
	 */
	public static synchronized void shutdown() {
		if (sessionFactory != null) {
			sessionFactory.close();
			sessionFactory = null;
		}
		if (serviceRegistry != null) {
			StandardServiceRegistryBuilder.destroy(serviceRegistry);
			serviceRegistry = null;
		}
		if (connectionProvider != null) {
			connectionProvider.stop();
			connectionProvider = null;
		}
	}


	/**
	 * Retrieve the configured driver class.
	 * 
	 * @return the configured driver class
	 */
	private static String getDriverClass() {
		return Netshot.getConfig("netshot.db.driverclass",
			Netshot.getConfig("netshot.db.driver_class", "org.postgresql.Driver"));
	}

	/**
	 * Retrieve the DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	private static String getUrl() {
		return Netshot.getSecretConfig("netshot.db.url", "jdbc:postgresql://localhost:5432/netshot01?sslmode=disable");
	}

	/**
	 * Retrieve the read-only DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	private static String getReadUrl() {
		return Netshot.getSecretConfig("netshot.db.readurl");
	}

	/**
	 * Retrieve the DB username.
	 * 
	 * @return the configured DB username
	 */
	private static String getUsername() {
		return Netshot.getSecretConfig("netshot.db.username", "netshot");
	}

	/**
	 * Retrieve the DB password.
	 * 
	 * @return the configured DB password
	 */
	private static String getPassword() {
		return Netshot.getSecretConfig("netshot.db.password", "netshot");
	}

	/**
	 * Retrieve the password used to encrypt the sensitive data (e.g. device credentials) in the DB.
	 * Unless {@code netshot.db.encryptionpassword.required} is enabled, defaults to {@code NETSHOT}.
	 *
	 * @return the configured encryption password
	 * @throws IllegalStateException if the password is required but not configured
	 */
	public static String getEncryptionPassword() {
		String password = Netshot.getSecretConfig("netshot.db.encryptionpassword");
		if (password == null) {
			// With capital P, for historical reasons
			password = Netshot.getSecretConfig("netshot.db.encryptionPassword");
		}
		if (password == null) {
			if (Netshot.getConfig("netshot.db.encryptionpassword.required", false)) {
				throw new IllegalStateException("The DB encryption password (netshot.db.encryptionpassword) must be configured, "
					+ "e.g. via the NETSHOT_DB_ENCRYPTIONPASSWORD_FILE environment variable. Note that the Netshot container "
					+ "image up to version 0.25.0 had 'netshot' as built-in value.");
			}
			password = "NETSHOT";
		}
		return password;
	}

	/**
	 * Gets the session.
	 *
	 * @return the session
	 * @throws HibernateException the hibernate exception
	 */
	public static Session getSession() throws HibernateException {
		return getSession(false);
	}

	/**
	 * Gets a database session.
	 *
	 * @param readOnly = true when requesting a read-only session
	 * @return the session
	 * @throws HibernateException the hibernate exception
	 */
	public static Session getSession(boolean readOnly) throws HibernateException {
		return sessionFactory
			.withOptions()
			.tenantIdentifier(
				readOnly ? TenantIdentifier.READ_ONLY : TenantIdentifier.READ_WRITE)
			.openSession();
	}

	/**
	 * Gets a read-write database session from the small dedicated SYSTEM pool.
	 * Reserved for short task dispatch and cluster bookkeeping transactions: never hold such
	 * a session across device I/O, hooks or any other potentially long operation.
	 *
	 * @return the session
	 * @throws HibernateException the hibernate exception
	 */
	public static Session getSystemSession() throws HibernateException {
		return sessionFactory
			.withOptions()
			.tenantIdentifier(TenantIdentifier.SYSTEM)
			.openSession();
	}

	/**
	 * Gets a stateless database session.
	 *
	 * @return the session
	 * @throws HibernateException the hibernate exception
	 */
	public static StatelessSession getStalessSession() throws HibernateException {
		return sessionFactory
			.withStatelessOptions()
			.tenantIdentifier(TenantIdentifier.READ_ONLY)
			.openStatelessSession();
	}

	/**
	 * Retrieves a raw SQL connection.
	 * 
	 * @return the connection
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
		final DataSource dataSource = C3P0Registry.pooledDataSourceByName(TenantIdentifier.READ_WRITE.getSourceName());
		return dataSource.getConnection();
	}

	/**
	 * Retrieves a raw SQL connection.
	 * @param pooled = whether to take the connection from the (C3P0) pool or not
	 *                  (pooled connection expire with C3P0 timeout mechanisms).
	 * @return the connection
	 * @throws SQLException
	 */
	public static Connection getConnection(boolean pooled) throws SQLException {
		if (pooled) {
			return Database.getConnection();
		}
		return DriverManager.getConnection(getUrl(), getUsername(), getPassword());
	}

	/**
	 * Gets the real object from the Hibernate proxy.
	 *
	 * @param <T>
	 *                 the generic type
	 * @param entity
	 *                 the entity
	 * @return the t
	 */
	@SuppressWarnings("unchecked")
	public static <T> T unproxy(T entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof HibernateProxy proxy) {
			Hibernate.initialize(entity);
			entity = (T) proxy.getHibernateLazyInitializer().getImplementation();
		}
		return entity;
	}

	/**
	 * Check whether a PersistenceException is caused by key conflict (duplicate key).
	 * @param e = the exception to check
	 * @return true if duplicate key
	 */
	public static boolean isDuplicateException(PersistenceException e) {
		Throwable t = e;
		for (int i = 0; i < 2; i++) {
			if (t == null) {
				return false;
			}
			if (t instanceof ConstraintViolationException) {
				return true;
			}
			if (t.getMessage().toLowerCase().contains("duplicate")) {
				return true;
			}
			t = t.getCause();
		}
		return false;
	}

	/**
	 * Silently rolls back the current transaction of the given session.
	 * If the rollback fails, the exception is logged but not rethrown,
	 * so as not to mask the original exception that triggered the rollback.
	 *
	 * @param session the Hibernate session
	 */
	public static void rollbackSilently(Session session) {
		try {
			session.getTransaction().rollback();
		}
		catch (Exception e) {
			log.error("Error during transaction rollback.", e);
		}
	}

	private Database() {
	}

}
