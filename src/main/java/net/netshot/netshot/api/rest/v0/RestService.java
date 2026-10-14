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
package net.netshot.netshot.api.rest.v0;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.quartz.SchedulerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.Headers;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.NetshotBadRequestException;
import net.netshot.netshot.api.rest.NetshotNotAuthorizedException;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.NetshotWebApplication;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.credentials.CredentialSets;
import net.netshot.netshot.platform.tls.CaCertificates;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device.Status;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.inventory.search.Finder;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.task.DebugLog;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.ScheduleType;
import net.netshot.netshot.task.engine.TaskContext;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.ChildOrchestratingTask;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultManager;

/**
 * The RestService class exposes the Netshot methods as a REST service.
 */
@Path("/")
@DenyAll
@Slf4j
public class RestService extends Thread {

		/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** The static instance service. */
	private static RestService nsRestService;

	/** Pagination size for dump queries. */
	private static final int PAGINATION_SIZE = 1000;

	private static final String HTTP_STATIC_PATH = Netshot.getConfig("netshot.http.staticpath", "/");
	public static final String HTTP_API_PATH = Netshot.getConfig("netshot.http.apipath", "/api");

	/** Name of the session cookie. */
	public static final String SESSION_COOKIE_NAME = Netshot.getConfig("netshot.http.sessioncookiename", "NetshotSessionID");

	/**
	 * Initializes the service.
	 *
	 * @param binder the dependency injection binder exposing the services to the REST resources
	 *        (given by the composition root, see {@code net.netshot.netshot.boot.Wiring})
	 */
	public static void init(AbstractBinder binder) {
		if (!Netshot.getConfig("netshot.http.enabled", true)) {
			log.info("HTTP server is not enabled.");
			return;
		}
		nsRestService = new RestService(binder);
		nsRestService.setUncaughtExceptionHandler(Netshot.exceptionHandler);
		nsRestService.start();
	}

	private URI httpBaseUri;
	private boolean httpUseSsl;
	private String httpSslKeystoreFile;
	private String httpSslKeystorePass;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/** The dependency injection binder for the web application (only set on the server thread instance). */
	private AbstractBinder binder;

	/**
	 * Instantiates a new Netshot REST service, as a resource (the JAX-RS runtime creates it for each request).
	 */
	public RestService() {
		this(null);
	}

	/**
	 * Instantiates the Netshot REST service, as the server thread.
	 *
	 * @param binder the dependency injection binder for the web application
	 */
	private RestService(AbstractBinder binder) {
		this.binder = binder;
		this.setName("REST Service");
		HttpServerAddress address = HttpServerAddress.fromConfig();
		this.httpBaseUri = address.baseUri();
		httpUseSsl = address.useSsl();
		if (httpUseSsl) {
			httpSslKeystoreFile = Netshot.getConfig("netshot.http.ssl.keystore.file", "netshot.jks");
			httpSslKeystorePass = Netshot.getSecretConfig("netshot.http.ssl.keystore.pass", "netshotpass");
		}
	}

	/**
	 * Override the resource JSON view.
	 * @param view = The view to use to serialize data
	 */
	protected void setWriterView(Class<? extends RestApiView> view) {
		ObjectWriterInjector.set(new ObjectWriterModifier() {
			@Override
			public ObjectWriter modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, Object> responseHeaders,
				Object valueToWrite, ObjectWriter w, JsonGenerator g) throws IOException {
				return w.withView(view);
			}
		});
	}

	/**
	 * Attach a suggested return code to the request, so the ResponseCodeFilter
	 * can in turn change the return code of the response.
	 * e.g. 201 or 204 instead of default 200
	 * @param status = the status code
	 */
	private void suggestReturnCode(Response.Status status) {
		this.request.setAttribute(ResponseCodeFilter.SUGGESTED_RESPONSE_CODE, status);
	}


	/**
	 * Get the name of the current user making the request.
	 * @return the current username or unknown
	 */
	private String getUsername() {
		User user = (User) securityContext.getUserPrincipal();
		try {
			return user.getUsername();
		}
		catch (Exception e) {
			// Whatever may happen
		}
		return "<Unknown>";
	}

	/*(non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		log.info("Starting the Web/REST service thread.");
		try {
			Undertow.Builder builder = Undertow.builder()
				.setServerOption(UndertowOptions.ENABLE_HTTP2, true);

			// === SSL Setup ===
			if (httpUseSsl) {
				KeyStore keyStore = KeyStore.getInstance(
					httpSslKeystoreFile.endsWith(".jks") ? "JKS" : "PKCS12"
				);
				try (InputStream is = new FileInputStream(httpSslKeystoreFile)) {
					keyStore.load(is, httpSslKeystorePass.toCharArray());
				}
				KeyManagerFactory kmf = KeyManagerFactory.getInstance(
					KeyManagerFactory.getDefaultAlgorithm()
				);
				kmf.init(keyStore, httpSslKeystorePass.toCharArray());

				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(kmf.getKeyManagers(), null, null);

				builder.addHttpsListener(httpBaseUri.getPort(), httpBaseUri.getHost(), sslContext);
			}
			else {
				builder.addHttpListener(httpBaseUri.getPort(), httpBaseUri.getHost());
			}

			// === Session cookie ===
			// Secure by default when serving HTTPS, or when behind a (HTTPS) reverse proxy
			boolean behindProxy = Netshot.getConfig("netshot.http.trustxforwardedfor", false);
			boolean secureCookie = Netshot.getConfig("netshot.http.securecookie", httpUseSsl || behindProxy);
			if (!secureCookie) {
				log.warn("The session cookie is not marked as Secure. If Netshot is accessed over HTTPS "
					+ "via a reverse proxy, set netshot.http.securecookie to true.");
			}
			else if (!httpUseSsl) {
				log.info("The session cookie is marked as Secure while Netshot serves plain HTTP (expecting a HTTPS reverse proxy). "
					+ "If users access Netshot over plain HTTP, set netshot.http.securecookie to false.");
			}

			// === Servlet (Jersey REST API) Deployment ===
			DeploymentInfo servletBuilder = Servlets.deployment()
				.setClassLoader(Netshot.class.getClassLoader())
				.setContextPath(HTTP_API_PATH) // same as Grizzly context path
				.setDeploymentName("NetshotREST")
				.addServlets(
					Servlets.servlet("Jersey", ServletContainer.class,
						new ImmediateInstanceFactory<>(new ServletContainer(new NetshotWebApplication(this.binder))))
						.setLoadOnStartup(1)
						.addMapping("/*")
				)
				.setServletSessionConfig(new ServletSessionConfig()
					.setName(RestService.SESSION_COOKIE_NAME)
					.setHttpOnly(true)
					.setSecure(secureCookie)
					.setAttribute("SameSite", "Strict")
				)
				.setDefaultSessionTimeout(UiUser.SETTINGS.getMaxIdleTime());

			DeploymentManager manager = Servlets
				.defaultContainer()
				.addDeployment(servletBuilder);
			manager.deploy();
			HttpHandler servletHandler = manager.start();

			// === Static files from /www/ (classpath) ===
			ClassPathResourceManager staticResourceManager =
				new ClassPathResourceManager(Netshot.class.getClassLoader(), "www");
			// The WebUI uses client-side routing (React Router), so a direct request or a
			// refresh on a route like /devices/42 won't match any file on disk. Fall back to
			// index.html for such requests (i.e. those without a file extension), and leave
			// genuinely missing static assets (e.g. /assets/missing.js) as real 404s.
			HttpHandler spaFallbackHandler = exchange -> {
				String path = exchange.getRelativePath();
				String lastSegment = path.substring(path.lastIndexOf('/') + 1);
				Resource index = lastSegment.contains(".") ? null : staticResourceManager.getResource("index.html");
				if (index != null) {
					index.serve(exchange.getResponseSender(), exchange, IoCallback.END_EXCHANGE);
				}
				else {
					ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
				}
			};
			ResourceHandler staticHandler = new ResourceHandler(staticResourceManager, spaFallbackHandler)
				.addWelcomeFiles("index.html");

			// === Routing ===
			PathHandler pathHandler = Handlers.path()
				.addPrefixPath(HTTP_STATIC_PATH, staticHandler)
				.addPrefixPath(HTTP_API_PATH, servletHandler);

			builder.setHandler(pathHandler);

			// === Start Undertow ===
			Undertow server = builder.build();
			server.start();

			synchronized (this) {
				while (true) {
					this.wait();
				}
			}
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"),
				"Fatal error with the REST service.", e);
			throw new RuntimeException(
				"Error with the REST service, see logs for more details.");
		}
	}


	/**
	 * Gets the domains.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the domains
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/domains")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the device domains",
		description = "Returns the list of device domains."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<RsDomain> getDomains(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, domains.");
		Session session = Database.getSession(true);
		try {
			Query<RsDomain> query = session.createQuery(
				"select new RsDomain(d.id, d.name, d.description, d.server4Address) from Domain d order by d.id asc",
				RsDomain.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the domains.", e);
			throw new NetshotBadRequestException("Unable to fetch the domains",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsDomain.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	@NoArgsConstructor
	public static class RsDomain {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id = -1;

		/** The name. */

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name = "";

		/** The description. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String description = "";

		/** The ip address. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class),
			@JsonSerialize(using = Network4Address.AddressOnlySerializer.class),
			@JsonDeserialize(using = Network4Address.AddressOnlyDeserializer.class),
		}))
		@Setter
		private Network4Address ipAddress;

		/**
		 * Instantiates a new RS domain from normal domain.
		 *
		 * @param domain the domain
		 */
		public RsDomain(Domain domain) {
			this.id = domain.getId();
			this.name = domain.getName();
			this.description = domain.getDescription();
			this.ipAddress = domain.getServer4Address();
		}

	}

	/**
	 * Adds the domain.
	 *
	 * @param newDomain the new domain
	 * @return the rs domain
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/domains")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a device domain",
		description = "Creates a device domain."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public RsDomain addDomain(RsDomain newDomain) throws WebApplicationException {
		log.debug("REST request, add a domain");
		String name = newDomain.getName() == null ? "" : newDomain.getName().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty domain name.");
			throw new NetshotBadRequestException("Invalid domain name.",
				ErrorCode.NETSHOT_INVALID_DOMAIN_NAME);
		}
		String description = newDomain.getDescription() == null ? "" : newDomain.getDescription().trim();
		try {
			Network4Address v4Address = newDomain.getIpAddress();
			Network6Address v6Address = new Network6Address("::");
			if (!v4Address.isNormalUnicast()) {
				log.warn("User posted an invalid IP address.");
				throw new NetshotBadRequestException("Invalid IP address",
					ErrorCode.NETSHOT_INVALID_IP_ADDRESS);
			}
			Domain domain = new Domain(name, description, v4Address, v6Address);
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				session.persist(domain);
				session.getTransaction().commit();
				AAA_LOG.info("{} has been created.", domain);
				this.suggestReturnCode(Response.Status.CREATED);
				return new RsDomain(domain);
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Error while adding a domain.", e);
				if (Database.isDuplicateException(e)) {
					throw new NetshotBadRequestException(
						"A domain with this name already exists.",
						ErrorCode.NETSHOT_DUPLICATE_DOMAIN);
				}
				throw new NetshotBadRequestException(
					"Unable to add the domain to the database",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		catch (UnknownHostException e) {
			log.warn("User posted an invalid IP address.");
			throw new NetshotBadRequestException("Malformed IP address",
				ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS);
		}
	}

	/**
	 * Sets the domain.
	 *
	 * @param id the id
	 * @param rsDomain the rs domain
	 * @return the rs domain
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/domains/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a device domain",
		description = "Edits a device domain, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public RsDomain setDomain(@PathParam("id") @Parameter(description = "Domain ID") Long id, RsDomain rsDomain)
		throws WebApplicationException {
		log.debug("REST request, edit domain {}.", id);
		String name = rsDomain.getName() == null ? "" : rsDomain.getName().trim();
		if (name.isEmpty()) {
			log.warn("User posted an invalid domain name.");
			throw new NetshotBadRequestException("Invalid domain name.",
				ErrorCode.NETSHOT_INVALID_DOMAIN_NAME);
		}
		String description = rsDomain.getDescription() == null ? "" : rsDomain.getDescription().trim();
		Network4Address v4Address = rsDomain.getIpAddress();
		if (!v4Address.isNormalUnicast()) {
			log.warn("User posted an invalid IP address");
			throw new NetshotBadRequestException("Invalid IP address",
				ErrorCode.NETSHOT_INVALID_IP_ADDRESS);
		}
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Domain domain = session.get(Domain.class, id);
			if (domain == null) {
				log.error("The domain of ID {} doesn't exist.", id);
				throw new NetshotBadRequestException("The domain doesn't exist.",
					ErrorCode.NETSHOT_DOMAIN_NOT_FOUND);
			}
			domain.setName(name);
			domain.setDescription(description);
			domain.setServer4Address(v4Address);
			session.merge(domain);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited.", domain);
			return new RsDomain(domain);
		}
		catch (NetshotBadRequestException e) {
			Database.rollbackSilently(session);
			log.error("Cannot edit the domain.", e);
			throw e;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while editing the domain.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A domain with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_DOMAIN);
			}
			throw new NetshotBadRequestException(
				"Unable to save the domain... is the name already in use?",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete a domain.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/domains/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a device domain",
		description = "Remove the given device domain, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteDomain(@PathParam("id") @Parameter(description = "Domain ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete domain {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Domain domain = session.get(Domain.class, id);
			if (domain == null) {
				log.error("The domain of ID {} doesn't exist.", id);
				throw new NetshotBadRequestException("The domain doesn't exist.",
					ErrorCode.NETSHOT_DOMAIN_NOT_FOUND);
			}
			session.remove(domain);
			session.getTransaction().commit();
			AAA_LOG.info("Domain of ID {} has been deleted.", domain.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			if (e instanceof ConstraintViolationException) {
				throw new NetshotBadRequestException(
					"Unable to delete the domain, there must be devices or credential sets using it.",
					ErrorCode.NETSHOT_DOMAIN_IN_USE);
			}
			log.error("Unable to delete the domain {}", id, e);
			throw new NetshotBadRequestException("Unable to delete the domain",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device interfaces.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device interfaces
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/interfaces")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get device interfaces",
		description = "Returns the list of interfaces of a given device (by ID)."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<NetworkInterface> getDeviceInterfaces(@PathParam("id") @Parameter(description = "Device ID") Long id,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} interfaces.", id);
		Session session = Database.getSession(true);
		try {
			List<NetworkInterface> deviceInterfaces;
			Query<NetworkInterface> query = session
				.createQuery(
					"select ni from NetworkInterface ni "
						+ "left join fetch ni.ip4Addresses "
						+ "left join fetch ni.ip6Addresses "
						+ "where device.id = :deviceId", NetworkInterface.class)
				.setParameter("deviceId", id);
			paginationParams.apply(query);
			deviceInterfaces = query.list();
			return deviceInterfaces;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the interfaces.", e);
			throw new NetshotBadRequestException("Unable to fetch the interfaces",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device modules.
	 *
	 * @param id the id
	 * @param includeHistory = whether to include history
	 * @param paginationParams = the pagination parameters
	 * @return the device modules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/modules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get device modules",
		description = "Returns the list of hardware modules of a given device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<Module> getDeviceModules(@PathParam("id") @Parameter(description = "Device ID") Long id,
		@Parameter(description = "Whether to include history (removed modules with dates)") @QueryParam("history") boolean includeHistory,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} modules.", id);
		Session session = Database.getSession(true);
		try {
			String hqlQuery = "select m from Module m where m.device.id = :device";
			if (!includeHistory) {
				hqlQuery += " and m.removed = :falsy";
			}
			Query<Module> query = session
				.createQuery(hqlQuery, Module.class)
				.setParameter("device", id);
			if (!includeHistory) {
				query.setParameter("falsy", false);
			}
			paginationParams.apply(query);
			List<Module> deviceModules = query.list();
			return deviceModules;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the modules.", e);
			throw new NetshotBadRequestException("Unable to fetch the modules",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device last n (default 20) tasks.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device tasks
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/tasks")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get device tasks",
		description = "Returns the list of tasks of a given device (by ID). Tasks are returned sorted by status and significant date."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(name = "Tasks", description = "Task management")
	public List<RsLightTask> getDeviceTasks(@PathParam("id") @Parameter(description = "Device ID") Long id, @BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} tasks.", id);
		if (paginationParams.getLimit() == null) {
			paginationParams.setLimit(100);
		}
		Session session = Database.getSession(true);
		try {
			List<String> deviceTaskConditions = new ArrayList<>();
			for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
				if (DeviceBasedTask.class.isAssignableFrom(taskClass)) {
					deviceTaskConditions.add(String.format(
						"t.id IN (select sub.id from %s sub where sub.device.id = :deviceId)",
						taskClass.getSimpleName()));
				}
			}
			if (deviceTaskConditions.isEmpty()) {
				return Collections.emptyList();
			}
			String hql = String.format(
				LIGHT_TASK_SELECT
				+ "from Task t where "
				+ String.join(" or ", deviceTaskConditions) + " "
				+ "order by "
				+ "case t.status "
				+ "when :running then 1 "
				+ "when :waiting then 2 "
				+ "when :scheduled then 3 "
				+ "when :newStatus then 4 "
				+ "else 10 end asc, "
				+ "coalesce(t.executionDate, t.changeDate) desc nulls first");
			Query<RsLightTask> query = session.createQuery(hql, RsLightTask.class)
				.setParameter("deviceId", id)
				.setParameter("running", Task.Status.RUNNING)
				.setParameter("waiting", Task.Status.WAITING)
				.setParameter("scheduled", Task.Status.SCHEDULED)
				.setParameter("newStatus", Task.Status.NEW)
				.setMaxResults(paginationParams.getLimit());
			paginationParams.apply(query);
			return query.list();
		}
		catch (Exception e) {
			log.error("Unable to fetch the tasks.", e);
			throw new NetshotBadRequestException("Unable to fetch the tasks",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device configs.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device configs
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/configs")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get device configs",
		description = "Returns the list of configurations of the given device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<Config> getDeviceConfigs(@PathParam("id") @Parameter(description = "Device ID") Long id, @BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} configs.", id);
		Session session = Database.getSession(true);
		try {
			Query<Config> query = session
				.createQuery(
					"select c from Config c left join fetch c.attributes ca where c.device.id = :device order by c.changeDate desc",
					Config.class)
				.setParameter("device", id);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the configs.", e);
			throw new NetshotBadRequestException("Unable to fetch the configs",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device config as plain text.
	 *
	 * @param id the id
	 * @param item the item
	 * @return the device config plain
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/configs/{id}/{item}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_OCTET_STREAM})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a device configuration item",
		description = "Retrieves a device configuration item, in plain text."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public Response getDeviceConfigPlain(@PathParam("id") @Parameter(description = "Config ID") Long id,
		@PathParam("item") @Parameter(description = "Config item name") String item) throws WebApplicationException {
		log.debug("REST request, get device {} config {}.", id, item);
		Session session = Database.getSession(true);
		try {
			Config config = session.get(Config.class, id);
			if (config == null) {
				log.warn("Unable to find the config object.");
				throw new WebApplicationException(
					"Unable to find the configuration set",
					Response.Status.NOT_FOUND);
			}
			for (ConfigAttribute attribute : config.getAttributes()) {
				if (attribute.getName().equals(item)) {
					if (attribute instanceof ConfigLongTextAttribute) {
						String text = ((ConfigLongTextAttribute) attribute).getLongText().getText();
						if (text == null) {
							throw new WebApplicationException("Configuration item not available",
								Response.Status.BAD_REQUEST);
						}
						String fileName = "%s.cfg".formatted(attribute.getName());
						try {
							SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
							fileName = String.format("%s_%s_%s", config.getDevice().getName(),
								formatter.format(config.getChangeDate()), fileName);
						}
						catch (Exception e) {
						}
						byte[] content = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
						return Response.ok(content, MediaType.APPLICATION_OCTET_STREAM)
							.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
							.header(Headers.CONTENT_LENGTH_STRING, content.length)
							.build();
					}
					else if (attribute instanceof ConfigBinaryFileAttribute fileAttribute) {
						String fileName = fileAttribute.getOriginalName();
						if (fileName == null) {
							fileName = "%s.dat".formatted(attribute.getName());
						}
						else {
							fileName = "%s_%s".formatted(attribute.getName(), fileName);
						}
						try {
							SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
							fileName = String.format("%s_%s_%s", config.getDevice().getName(),
								formatter.format(config.getChangeDate()), fileName);
						}
						catch (Exception e) {
						}
						java.io.File file = fileAttribute.getFilePath().toFile();
						return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM)
							.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
							.header(Headers.CONTENT_LENGTH_STRING, file.length())
							.build();
					}
				}
			}
			throw new WebApplicationException("Invalid configuration item '%s'".formatted(item),
				Response.Status.BAD_REQUEST);
		}
		catch (HibernateException e) {
			log.error("Unable to get the configuration", e);
			throw new WebApplicationException("Unable to get the configuration",
				Response.Status.INTERNAL_SERVER_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsConfigDiff.
	 */
	@XmlRootElement
	public static class RsConfigDiff {

		/** The original date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date originalDate;

		/** The revised date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date revisedDate;

		/** The original config ID. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long originalConfigId;

		/** The revised config ID. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long revisedConfigId;

		/** The original config. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Config originalConfig;

		/** The revised config. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Config revisedConfig;

		/** The deltas. */
		@Getter(onMethod = @__({
			@XmlElement(), @JsonView(DefaultView.class),
			@JsonInclude(JsonInclude.Include.NON_NULL)
		}))
		@Setter
		private Map<String, List<RsConfigDelta>> deltas = new HashMap<>();

		/**
		 * Instantiates a new rs config diff.
		 *
		 * @param originalDate the original date
		 * @param revisedDate the revised date
		 * @param originalConfigId the original config ID
		 * @param revisedConfigId the revised config ID
		 */
		public RsConfigDiff(Date originalDate, Date revisedDate,
			Long originalConfigId, Long revisedConfigId) {
			this.originalDate = originalDate;
			this.revisedDate = revisedDate;
			this.originalConfigId = originalConfigId;
			this.revisedConfigId = revisedConfigId;
		}

		/**
		 * Adds the delta.
		 *
		 * @param item the item
		 * @param delta the delta
		 */
		public void addDelta(String item, RsConfigDelta delta) {
			if (!deltas.containsKey(item)) {
				deltas.put(item, new ArrayList<>());
			}
			deltas.get(item).add(delta);
		}
	}

	/**
	 * The Class RsConfigDelta.
	 */
	@XmlRootElement
	public static class RsConfigDelta {

		/**
		 * Type of difference.
		 */
		public enum Type {
			CHANGE,
			DELETE,
			INSERT;
		}

		/**
		 * Class representing a line in configuration with its number (position).
		 */
		@XmlRootElement
		public static class LineWithPosition {
			@Getter(onMethod = @__({
				@XmlElement, @JsonView(DefaultView.class)
			}))
			@Setter
			private String line;

			@Getter(onMethod = @__({
				@XmlElement, @JsonView(DefaultView.class)
			}))
			@Setter
			private int position;

			public LineWithPosition(String line, int position) {
				this.line = line;
				this.position = position;
			}
		}

		/** The item. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String item;

		/** The diff type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Type diffType;

		/** The original position. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int originalPosition;

		/** The revised position. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int revisedPosition;

		/** The original lines. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<String> originalLines;

		/** The revised lines. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<String> revisedLines;

		/** The pre context. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<String> preContext;

		/** The post context. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<String> postContext;

		/** The (indentation-based) hierarchy lines and positions. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<LineWithPosition> hierarchy;

		/**
		 * Instantiates a new rs config delta.
		 *
		 * @param delta the delta
		 * @param allOldLines all the old lines
		 * @param oldLineParents the parents of the old lines
		 */
		public RsConfigDelta(AbstractDelta<String> delta, List<String> allOldLines, int[] oldLineParents) {
			switch (delta.getType()) {
				case INSERT:
					this.diffType = Type.INSERT;
					break;
				case DELETE:
					this.diffType = Type.DELETE;
					break;
				case CHANGE:
				default:
					this.diffType = Type.CHANGE;
			}
			this.originalPosition = delta.getSource().getPosition();
			this.originalLines = delta.getSource().getLines();
			this.revisedPosition = delta.getTarget().getPosition();
			this.revisedLines = delta.getTarget().getLines();
			this.preContext = allOldLines.subList(Math.max(this.originalPosition - 3, 0),
				this.originalPosition);
			this.postContext = allOldLines.subList(Math.min(this.originalPosition
					+ this.originalLines.size(), allOldLines.size() - 1),
				Math.min(this.originalPosition + this.originalLines.size() + 3,
					allOldLines.size() - 1));
			this.hierarchy = new ArrayList<>();
			int p = this.originalPosition;
			if (p < oldLineParents.length) {
				p = oldLineParents[p];
				while (p >= 0) {
					this.hierarchy.add(new LineWithPosition(allOldLines.get(p), p));
					p = oldLineParents[p];
				}
			}
			Collections.reverse(this.hierarchy);
		}
	}

	/**
	 * Gets the device config diff.
	 *
	 * @param id1 the id1
	 * @param id2 the id2
	 * @param includeDeltas whether to include deltas
	 * @param includeConfigs whether to include configs
	 * @return the device config diff
	 */
	@GET
	@Path("/configs/{id1}/vs/{id2}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the diff between two configuration objects",
		description = "Retrieves the differences between two given device configuration objets, identified by full IDs."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public RsConfigDiff getDeviceConfigDiff(
		@PathParam("id1") @Parameter(description = "First config ID") Long id1,
		@PathParam("id2") @Parameter(description = "Second config ID") Long id2,
		@DefaultValue("true") @QueryParam("deltas") @Parameter(description = "Include/compute deltas") boolean includeDeltas,
		@DefaultValue("false") @QueryParam("fullconfigs") @Parameter(description = "Include full configs") boolean includeConfigs) {
		log.debug("REST request, get device config diff, id {} and {} ({} deltas).",
			id1, id2, includeDeltas ? "with" : "without");
		RsConfigDiff configDiffs;
		Session session = Database.getSession(true);
		Config config1;
		Config config2;
		try {
			config2 = session.get(Config.class, id2);
			if (config2 != null && id1 == 0) {
				config1 = session
					.createQuery(
						"from Config c where c.device = :device and c.changeDate < :date2 order by c.changeDate desc",
						Config.class)
					.setParameter("device", config2.getDevice())
					.setParameter("date2", config2.getChangeDate())
					.setMaxResults(1)
					.uniqueResult();
				if (config1 == null) {
					config1 = new Config(config2.getDevice());
				}
			}
			else {
				config1 = session.get(Config.class, id1);
			}
			if (config1 == null || config2 == null) {
				log.error("Non existing config, {} or {}.", id1, id2);
				throw new NetshotBadRequestException("Config not found",
					ErrorCode.NETSHOT_CONFIG_NOT_FOUND);
			}
			DeviceDriver driver1;
			DeviceDriver driver2;
			try {
				driver1 = config1.getDevice().getDeviceDriver();
				driver2 = config2.getDevice().getDeviceDriver();
			}
			catch (MissingDeviceDriverException e) {
				log.error("Missing driver.");
				throw new NetshotBadRequestException("Missing driver",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			if (!driver1.equals(driver2)) {
				log.error("Incompatible configurations, {} and {} (different drivers).", id1, id2);
				throw new NetshotBadRequestException("Incompatible configurations",
					ErrorCode.NETSHOT_INCOMPATIBLE_CONFIGS);
			}

			configDiffs = new RsConfigDiff(config1.getChangeDate(), config2.getChangeDate(),
				config1.getId(), config2.getId());
			if (includeDeltas) {
				Map<String, ConfigAttribute> attributes1 = config1.getAttributeMap();
				Map<String, ConfigAttribute> attributes2 = config2.getAttributeMap();
				for (AttributeDefinition definition : driver1.getAttributes()) {
					if (definition.isComparable()) {
						ConfigAttribute attribute1 = attributes1.get(definition.getName());
						ConfigAttribute attribute2 = attributes2.get(definition.getName());
						String text1 = attribute1 == null ? "" : attribute1.getAsText();
						String text2 = attribute2 == null ? "" : attribute2.getAsText();
						List<String> lines1 = Arrays.asList(text1.replace("\r", "").split("\n"));
						int[] lineParents1 = Config.getLineParents(lines1);
						List<String> lines2 = Arrays.asList(text2.replace("\r", "").split("\n"));
						Patch<String> patch = DiffUtils.diff(lines1, lines2);
						for (AbstractDelta<String> delta : patch.getDeltas()) {
							configDiffs.addDelta(definition.getTitle(), new RsConfigDelta(delta, lines1, lineParents1));
						}
					}
				}
			}
			else {
				configDiffs.setDeltas(null);
			}

			if (includeConfigs) {
				configDiffs.setOriginalConfig(config1);
				configDiffs.setRevisedConfig(config2);
			}
			return configDiffs;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the configs", e);
			throw new NetshotBadRequestException("Unable to fetch the configs",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightDevice.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	@NoArgsConstructor
	public static class RsLightDevice {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name;

		/** The family. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String family;

		/** The mgmt address. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String mgmtAddress;

		/** The status. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Device.Status status;

		/** The device deviceDriver name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected String driver;

		/** End of Life. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected Boolean eol;

		/** End of Sale. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected Boolean eos;

		/** Configuration compliant. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected Boolean configCompliant;

		/** The software level. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected SoftwareRule.ConformanceLevel softwareLevel = ConformanceLevel.UNKNOWN;

		/** The network class. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		protected Device.NetworkClass networkClass = Device.NetworkClass.UNKNOWN;
	}

	/**
	 * Gets the device types.
	 *
	 * @param refresh = whether to refresh the devices types at same time
	 * @return the device types
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devicetypes")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the device types",
		description = "Returns the list of device types (drivers)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<DeviceDriver> getDeviceTypes(
			@DefaultValue("false") @QueryParam("refresh") @Parameter(description = "Whether to reload all drivers from sources") boolean refresh)
		throws WebApplicationException {
		log.debug("REST request, device types.");
		if (refresh) {
			if (!securityContext.isUserInRole(User.ROLE_ADMIN)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to reload the device drivers.", 0);
			}
			ClusterManager.requestDriverReload();
			try {
				DeviceDriver.refreshDrivers();
			}
			catch (Exception e) {
				log.error("Error in REST service while refreshing the device types.", e);
			}
		}
		List<DeviceDriver> deviceTypes = new ArrayList<>();
		deviceTypes.addAll(DeviceDriver.getAllDrivers());
		return deviceTypes;
	}

	/**
	 * The Class RsDeviceFamily.
	 */
	@XmlRootElement(name = "deviceType")
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsDeviceFamily {

		/** The device type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String driver;

		/** The device family. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String deviceFamily;
	}

	/**
	 * Gets the device families.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the device families
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devicefamilies")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the existing device families",
		description = "Returns the list of device families (driver specific) currenly known in the database."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<RsDeviceFamily> getDeviceFamilies(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, device families.");
		Session session = Database.getSession(true);
		try {
			Query<RsDeviceFamily> query = session
				.createQuery(
					"select new RsDeviceFamily(d.driver as driver, d.family as deviceFamily) from Device d",
					RsDeviceFamily.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Error while loading device families.", e);
			throw new NetshotBadRequestException("Database error",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@XmlRootElement(name = "partNumber")
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsPartNumber {
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String partNumber;
	}


	/**
	 * Gets the known part numbers.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the part numbers
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/partnumbers")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the known part numbers",
		description = "Returns the list of all known part numbers currently existing in the module table."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<RsPartNumber> getPartNumbers(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, part numbers.");
		Session session = Database.getSession(true);
		try {
			Query<RsPartNumber> query = session
				.createQuery(
					"select new RsPartNumber(m.partNumber as partNumber) from Module m",
					RsPartNumber.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Error while loading part numbers.", e);
			throw new NetshotBadRequestException("Database error",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@XmlRootElement(name = "softwareVersion")
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsSoftwareVersion {
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String version;
	}

	@XmlRootElement(name = "deviceTag")
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsDeviceTag {
		/** The tag. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name;

		/** The number of devices having this tag. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long deviceCount;
	}

	@GET
	@Path("/devicetags")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the existing device tags",
		description = "Returns the list of distinct tags currently attached to at least one device, with the number of devices for each."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<RsDeviceTag> getDeviceTags(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, device tags.");
		Session session = Database.getSession(true);
		try {
			Query<RsDeviceTag> query = session
				.createQuery(
					"select new RsDeviceTag(t as name, count(d.id) as deviceCount) from Device d join d.tags t group by t order by t",
					RsDeviceTag.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Error while loading device tags.", e);
			throw new NetshotBadRequestException("Database error",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@GET
	@Path("/softwareversions")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the known software versions",
		description = "Returns the list of all known software versions currently existing in the device table."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<RsSoftwareVersion> getSoftwareVersions(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, software versions.");
		Session session = Database.getSession(true);
		try {
			Query<RsSoftwareVersion> query = session
				.createQuery(
					"select new RsSoftwareVersion(d.softwareVersion as version) from Device d where d.softwareVersion is not null and d.softwareVersion != ''",
					RsSoftwareVersion.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Error while loading software versions.", e);
			throw new NetshotBadRequestException("Database error",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Validates a Web hook's HTTPS trust policy: defaults a missing CA trust
	 * mode to {@code SYSTEM_TRUSTSTORE}, and requires/validates a custom CA
	 * certificate when the mode is {@code CUSTOM_CA}.
	 * @param webHook the Web hook to validate
	 * @throws NetshotBadRequestException if the CUSTOM_CA mode is selected without a valid certificate
	 */
	private static void validateWebHookTrustPolicy(WebHook webHook) throws NetshotBadRequestException {
		if (webHook.getHttpsCaTrustMode() == null) {
			webHook.setHttpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE);
		}
		if (webHook.getHttpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA) {
			if (webHook.getHttpsCustomCaCertificate() == null || webHook.getHttpsCustomCaCertificate().isBlank()) {
				throw new NetshotBadRequestException(
					"The webhook requires a custom CA certificate when the HTTPS trust mode is CUSTOM_CA.",
					ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER);
			}
			CaCertificates.validateCustomCa(webHook.getHttpsCustomCaCertificate(), "the webhook");
		}
	}

	/**
	 * Gets the task.
	 *
	 * @param id the id
	 * @return the task
	 */
	@GET
	@Path("/tasks/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a task.",
		description = "Retrieves the status of a given task, by ID."
	)
	@Tag(name = "Tasks", description = "Task management")
	public Task getTask(@PathParam("id") @Parameter(description = "Task ID") Long id) {
		log.debug("REST request, get task {}", id);
		Session session = Database.getSession(true);
		Task task;
		try {
			task = session.get(Task.class, id);
			return task;
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find the task {}.", id, e);
			throw new NetshotBadRequestException("Task not found",
				ErrorCode.NETSHOT_TASK_NOT_FOUND);
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotBadRequestException("Unable to fetch the task",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param id the id
	 * @return the device config plain
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/tasks/{id}/debuglog")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_OCTET_STREAM})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the debug log of a task",
		description = "Retrieves the full debug log of a given task, by ID."
	)
	@Tag(name = "Tasks", description = "Task management")
	public Response getTaskDebugLog(@PathParam("id") @Parameter(description = "Task ID") Long id) throws WebApplicationException {
		log.debug("REST request, get task {} debug log.", id);
		Session session = Database.getSession(true);
		Task task;
		try {
			task = session.get(Task.class, id);
			if (task == null) {
				log.error("Unable to find the task {}.", id);
				throw new NetshotBadRequestException("Task not found",
					ErrorCode.NETSHOT_TASK_NOT_FOUND);
			}
			DebugLog debugLog = task.getDebugLog();
			String text = debugLog == null ? "" : debugLog.getText();
			String fileName = String.format("debug_%d.log", id);
			byte[] content = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			return Response.ok(content, MediaType.APPLICATION_OCTET_STREAM)
				.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
				.header(Headers.CONTENT_LENGTH_STRING, content.length)
				.build();
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find the task {}.", id, e);
			throw new NetshotBadRequestException("Task not found",
				ErrorCode.NETSHOT_TASK_NOT_FOUND);
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new WebApplicationException("Unable to get the task",
				Response.Status.INTERNAL_SERVER_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightTask, a light-weight projection of {@link Task}, carrying only
	 * the fields needed to list tasks -- notably excluding the script and log fields,
	 * which can be arbitrarily large. Task listing endpoints return this instead of
	 * the full {@link Task}; the frontend fetches the full task when it needs details.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLightTask {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The task type (simple class name). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type;

		/** The status. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.Status status;

		/** The author. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String author;

		/** The target. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String target;

		/** The comments. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String comments;

		/** The creation date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date creationDate;

		/** The change date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date changeDate;

		/** The execution date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date executionDate;

		/** The schedule reference. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date scheduleReference;

		/** The schedule type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.ScheduleType scheduleType;

		/** The schedule factor. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int scheduleFactor;

		/** The priority. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int priority;

		/** The runner ID (clustering mode). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String runnerId;

		/** Debug enabled. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean debugEnabled;

		/** The target device ID, if any. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long deviceId;

		/** The target device group ID, if any. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long deviceGroupId;

		/** The parent task ID, when this task is a per-device child of a group-based task. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long parentTaskId;

		/** Position of this task among its siblings, when it has a parent task. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int childOrder;

		/** Parallel vs sequential scheduling of child tasks (group-based tasks only). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.ScheduleMode scheduleMode;

		/** Whether to stop scheduling further child tasks after one fails (sequential mode only). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean stopOnFailure;

		public RsLightTask(long id, Class<? extends Task> type, Task.Status status, String author,
				String target, String comments, Date creationDate, Date changeDate, Date executionDate,
				Date scheduleReference, Task.ScheduleType scheduleType, int scheduleFactor, int priority,
				String runnerId, boolean debugEnabled, Long deviceId, Long deviceGroupId,
				Long parentTaskId, int childOrder, Task.ScheduleMode scheduleMode, boolean stopOnFailure) {
			this.id = id;
			this.type = type == null ? null : type.getSimpleName();
			this.status = status;
			this.author = author;
			this.target = target;
			this.comments = comments;
			this.creationDate = creationDate;
			this.changeDate = changeDate;
			this.executionDate = executionDate;
			this.scheduleReference = scheduleReference;
			this.scheduleType = scheduleType;
			this.scheduleFactor = scheduleFactor;
			this.priority = priority;
			this.runnerId = runnerId;
			this.debugEnabled = debugEnabled;
			this.deviceId = deviceId;
			this.deviceGroupId = deviceGroupId;
			this.parentTaskId = parentTaskId;
			this.childOrder = childOrder;
			this.scheduleMode = scheduleMode;
			this.stopOnFailure = stopOnFailure;
		}
	}

	/** Common HQL select clause for {@link RsLightTask}, to project a {@code Task} row without its heavy script/log fields. */
	private static final String LIGHT_TASK_SELECT = "select new RsLightTask("
		+ "t.id, "
		+ "type(t), "
		+ "t.status, "
		+ "t.author, "
		+ "t.target, "
		+ "t.comments, "
		+ "t.creationDate, "
		+ "t.changeDate, "
		+ "t.executionDate, "
		+ "t.scheduleReference, "
		+ "t.scheduleType, "
		+ "t.scheduleFactor, "
		+ "t.priority, "
		+ "t.runnerId, "
		+ "t.debugEnabled, "
		+ "t.device.id, "
		+ "t.deviceGroup.id, "
		+ "t.parentTaskId, "
		+ "t.childOrder, "
		+ "t.scheduleMode, "
		+ "t.stopOnFailure"
		+ ") ";

	/**
	 * Resolves task type simple class names (as used in the JSON "type" discriminator)
	 * to the corresponding task classes, ignoring names that don't match any known type.
	 *
	 * @param types the simple class names
	 * @return the matching task classes
	 */
	private static Set<Class<? extends Task>> resolveTaskClasses(Set<String> types) {
		Set<Class<? extends Task>> classes = new HashSet<>();
		for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
			if (types.contains(taskClass.getSimpleName())) {
				classes.add(taskClass);
			}
		}
		return classes;
	}

	/**
	 * Gets the tasks.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param statuses = the statuses to include
	 * @param types = the task type(s) to include
	 * @param startDate = the start date
	 * @param endDate = the end date
	 * @param parentTaskId = the parent task ID
	 * @return the tasks
	 */
	@GET
	@Path("/tasks")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the tasks",
		description = "Returns the list of tasks. Limited to 100 if no specific limit is provided."
	)
	@Tag(name = "Tasks", description = "Task management")
	public List<RsLightTask> getTasks(@BeanParam PaginationParams paginationParams,
		@QueryParam("status") @Parameter(description = "Include tasks of given status(es)") Set<Task.Status> statuses,
		@QueryParam("type") @Parameter(description = "Include tasks of given type(s)") Set<String> types,
		@QueryParam("after") @Parameter(description = "Tasks executed or changed after this date (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "Tasks executed or changed before this date (as milliseconds since 1970)") Long endDate,
		@QueryParam("parentTaskId") @Parameter(description = "Only include child tasks of the given parent task ID") Long parentTaskId) {

		log.debug("REST request, get tasks.");
		if (paginationParams.getLimit() == null) {
			paginationParams.setLimit(100);
		}

		Session session = Database.getSession(true);
		try {
			StringBuilder hqlQuery = new StringBuilder(LIGHT_TASK_SELECT + "from Task t where (1 = 1)");
			Map<String, Object> hqlParams = new HashMap<>();

			if (!statuses.isEmpty()) {
				hqlQuery.append(" and t.status in :statuses");
				hqlParams.put("statuses", statuses);
			}
			if (!types.isEmpty()) {
				hqlQuery.append(" and type(t) in :classes");
				hqlParams.put("classes", resolveTaskClasses(types));
			}
			if (startDate != null) {
				hqlQuery.append(" and (((t.executionDate is not null) and (t.executionDate >= :startDate)) "
					+ "or ((t.executionDate is null) and (t.changeDate >= :startDate)))");
				hqlParams.put("startDate", new Date(startDate));
			}
			if (endDate != null) {
				hqlQuery.append(" and (((t.executionDate is not null) and (t.executionDate <= :endDate)) "
					+ "or ((t.executionDate is null) and (t.changeDate < :endDate)))");
				hqlParams.put("endDate", new Date(endDate));
			}
			if (parentTaskId != null) {
				hqlQuery.append(" and t.parentTaskId = :parentTaskId");
				hqlParams.put("parentTaskId", parentTaskId);
			}
			hqlQuery.append(parentTaskId != null ? " order by t.childOrder asc" : " order by id desc");

			Query<RsLightTask> query = session.createQuery(hqlQuery.toString(), RsLightTask.class);
			for (Entry<String, Object> k : hqlParams.entrySet()) {
				query.setParameter(k.getKey(), k.getValue());
			}

			paginationParams.apply(query);
			List<RsLightTask> tasks = query.list();
			return tasks;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the tasks.", e);
			throw new NetshotBadRequestException("Unable to fetch the tasks",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the credential sets.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the credential sets
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/credentialsets")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the global credential sets",
		description = "Returns the list of global credential sets (SSH, SNMP, etc. accounts) for authentication against the devices."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<DeviceCredentialSet> getCredentialSets(@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get credentials.");
		Session session = Database.getSession(true);
		try {
			Query<DeviceCredentialSet> query = session
				.createQuery("select cs from DeviceCredentialSet cs where not (cs.deviceSpecific)",
					DeviceCredentialSet.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the credentials.", e);
			throw new NetshotBadRequestException("Unable to fetch the credentials",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete credential set.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/credentialsets/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a credential set",
		description = "Removes the given credential set, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete credential set {}", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceCredentialSet credentialSet = session.get(
				DeviceCredentialSet.class, id);
			if (credentialSet == null) {
				log.info("No such credential set of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			if (credentialSet.isDeviceSpecific()) {
				throw new NetshotBadRequestException(
					"Can't delete a device-specific credential set.",
					ErrorCode.NETSHOT_CREDENTIALS_IN_USE);
			}
			session.remove(credentialSet);
			session.getTransaction().commit();
			AAA_LOG.info("Credential set of ID {} has been deleted.", credentialSet.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the credentials {}", id, e);
			if (e instanceof NetshotBadRequestException) {
				throw e;
			}
			if (e instanceof ConstraintViolationException) {
				throw new NetshotBadRequestException(
					"Unable to delete the credential set, there must be devices or tasks using it.",
					ErrorCode.NETSHOT_CREDENTIALS_IN_USE);
			}
			throw new NetshotBadRequestException(
				"Unable to delete the credential set",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Adds the credential set.
	 *
	 * @param credentialSet the credential set
	 * @return the device credential set
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/credentialsets")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a credential set",
		description = "Creates a credential set, which then can be used to authenticate against the devices."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public DeviceCredentialSet addCredentialSet(DeviceCredentialSet credentialSet)
		throws WebApplicationException {
		log.debug("REST request, add credentials.");
		if (credentialSet.getName() == null || "".equals(credentialSet.getName().trim())) {
			log.error("Invalid credential set name.");
			throw new NetshotBadRequestException("Invalid name for the credential set",
				ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME);
		}
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			if (credentialSet.getMgmtDomain() != null) {
				Domain domain = session.get(Domain.class, credentialSet.getMgmtDomain().getId());
				if (domain == null) {
					throw new NetshotBadRequestException("Invalid domain",
						ErrorCode.NETSHOT_INVALID_DOMAIN);
				}
				credentialSet.setMgmtDomain(domain);
			}
			credentialSet.setDeviceSpecific(false);
			CredentialSets.applyVaultableFields(session, credentialSet, credentialSet);
			session.persist(credentialSet);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", credentialSet);
			session.refresh(credentialSet);
			this.suggestReturnCode(Response.Status.CREATED);
			return credentialSet;
		}
		catch (NetshotBadRequestException | NetshotException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			Throwable t = e.getCause();
			log.error("Can't add the credentials.", e);
			if (t != null && t.getMessage().contains("uplicate")) {
				throw new NetshotBadRequestException(
					"A credential set with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS);
			}
			else if (t != null && t.getMessage().contains("mgmt_domain")) {
				throw new NetshotBadRequestException(
					"The domain doesn't exist.",
					ErrorCode.NETSHOT_INVALID_DOMAIN);
			}
			throw new NetshotBadRequestException("Unable to save the credential set",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Sets the credential set.
	 *
	 * @param id the id
	 * @param rsCredentialSet the rs credential set
	 * @return the device credential set
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/credentialsets/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a credential set",
		description = "Edits a credential set, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public DeviceCredentialSet setCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") Long id,
		DeviceCredentialSet rsCredentialSet) throws WebApplicationException {
		log.debug("REST request, edit credentials {}", id);
		Session session = Database.getSession();
		DeviceCredentialSet credentialSet;
		try {
			session.beginTransaction();
			credentialSet = session.get(
				rsCredentialSet.getClass(), id);
			if (credentialSet == null) {
				log.error("Unable to find the credential set {}.", id);
				throw new NetshotBadRequestException(
					"Unable to find the credential set.",
					ErrorCode.NETSHOT_INVALID_CREDENTIALS);
			}
			if (!credentialSet.getClass().equals(rsCredentialSet.getClass())) {
				log.error("Wrong posted credential type for credential set {}.", id);
				throw new NetshotBadRequestException(
					"The posted credential type doesn't match the existing one.",
					ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE);
			}
			if (rsCredentialSet.getMgmtDomain() == null) {
				credentialSet.setMgmtDomain(null);
			}
			else {
				credentialSet.setMgmtDomain(session.get(Domain.class, rsCredentialSet.getMgmtDomain().getId()));
			}
			credentialSet.setName(rsCredentialSet.getName());
			if (credentialSet.getName() == null || "".equals(credentialSet.getName().trim())) {
				log.error("Invalid credential set name.");
				throw new NetshotBadRequestException("Invalid name for the credential set",
					ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME);
			}
			if (credentialSet instanceof DeviceCliAccount cliAccount
					&& rsCredentialSet instanceof DeviceCliAccount rsCliAccount) {
				cliAccount.setUsername(rsCliAccount.getUsername());
				if (rsCliAccount.getPassword() != null) {
					cliAccount.setPassword(rsCliAccount.getPassword());
				}
				if (rsCliAccount.getSuperPassword() != null) {
					cliAccount.setSuperPassword(rsCliAccount.getSuperPassword());
				}
				if (cliAccount instanceof DeviceSshKeyAccount sshKeyAccount
						&& rsCliAccount instanceof DeviceSshKeyAccount rsSshKeyAccount) {
					if (rsSshKeyAccount.getPrivateKey() != null) {
						sshKeyAccount.setPrivateKey(rsSshKeyAccount.getPrivateKey());
					}
				}
			}
			else if (credentialSet instanceof DeviceSnmpv3Community snmp3
					&& rsCredentialSet instanceof DeviceSnmpv3Community rsSnmp3) {
				snmp3.setUsername(rsSnmp3.getUsername());
				snmp3.setAuthType(rsSnmp3.getAuthType());
				if (rsSnmp3.getAuthKey() != null) {
					snmp3.setAuthKey(rsSnmp3.getAuthKey());
				}
				snmp3.setPrivType(rsSnmp3.getPrivType());
				if (rsSnmp3.getPrivKey() != null) {
					snmp3.setPrivKey(rsSnmp3.getPrivKey());
				}

			}
			else if (credentialSet instanceof DeviceSnmpCommunity snmpCommunity
					&& rsCredentialSet instanceof DeviceSnmpCommunity rsSnmpCommunity) {
				String community = rsSnmpCommunity.getCommunity();
				if (community != null) {
					snmpCommunity.setCommunity(community);
				}
			}
			else if (credentialSet instanceof DeviceHttpAccount httpAccount
					&& rsCredentialSet instanceof DeviceHttpAccount rsHttpAccount) {
				httpAccount.setUsername(rsHttpAccount.getUsername());
				if (rsHttpAccount.getPassword() != null) {
					httpAccount.setPassword(rsHttpAccount.getPassword());
				}
			}
			// Applied last so that switching a field to Vault mode always wins over
			// the unconditional local-value copies above (e.g. setUsername(...)).
			CredentialSets.applyVaultableFields(session, credentialSet, rsCredentialSet);
			session.merge(credentialSet);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", credentialSet);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			Throwable t = e.getCause();
			log.error("Unable to save the credentials {}.", id, e);
			if (t != null && t.getMessage().contains("uplicate")) {
				throw new NetshotBadRequestException(
					"A credential set with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS);
			}
			else if (t != null && t.getMessage().contains("mgmt_domain")) {
				throw new NetshotBadRequestException(
					"The domain doesn't exist.",
					ErrorCode.NETSHOT_INVALID_DOMAIN);
			}
			throw new NetshotBadRequestException("Unable to save the credential set",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (NetshotBadRequestException | NetshotException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
		return credentialSet;
	}

	/**
	 * Gets the Vault instances.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the Vault instances
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/vaultinstances")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the Vault instances",
		description = "Returns the list of configured Vault instances, used to resolve Vault-backed device credential fields."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<VaultInstance> getVaultInstances(@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get Vault instances.");
		Session session = Database.getSession(true);
		try {
			Query<VaultInstance> query = session
				.createQuery("select v from VaultInstance v", VaultInstance.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the Vault instances.", e);
			throw new NetshotBadRequestException("Unable to fetch the Vault instances",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete a Vault instance.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/vaultinstances/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a Vault instance",
		description = "Removes the given Vault instance, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete Vault instance {}", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			VaultInstance vaultInstance = session.get(VaultInstance.class, id);
			if (vaultInstance == null) {
				log.info("No such Vault instance of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(vaultInstance);
			session.getTransaction().commit();
			AAA_LOG.info("Vault instance of ID {} has been deleted.", id);
			VaultManager.invalidate(id);
			ClusterManager.requestVaultInstanceReload(id);
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the Vault instance {}", id, e);
			if (e instanceof NetshotBadRequestException nbre) {
				throw nbre;
			}
			if (e instanceof ConstraintViolationException) {
				throw new NetshotBadRequestException(
					"Unable to delete the Vault instance, it is referenced by one or more credential fields.",
					ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE);
			}
			throw new NetshotBadRequestException(
				"Unable to delete the Vault instance",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Adds a Vault instance.
	 *
	 * @param vaultInstance the Vault instance
	 * @return the Vault instance
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/vaultinstances")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a Vault instance",
		description = "Creates a Vault instance, which can then be used to resolve Vault-backed device credential fields."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public VaultInstance addVaultInstance(VaultInstance vaultInstance)
		throws WebApplicationException {
		log.debug("REST request, add Vault instance.");
		if (vaultInstance.getName() == null || "".equals(vaultInstance.getName().trim())) {
			log.error("Invalid Vault instance name.");
			throw new NetshotBadRequestException("Invalid name for the Vault instance",
				ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME);
		}
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.persist(vaultInstance);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", vaultInstance);
			session.refresh(vaultInstance);
			this.suggestReturnCode(Response.Status.CREATED);
			return vaultInstance;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			Throwable t = e.getCause();
			log.error("Can't add the Vault instance.", e);
			if (t != null && t.getMessage().contains("uplicate")) {
				throw new NetshotBadRequestException(
					"A Vault instance with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE);
			}
			throw new NetshotBadRequestException("Unable to save the Vault instance",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Sets/edits a Vault instance.
	 *
	 * @param id the id
	 * @param rsVaultInstance the posted Vault instance
	 * @return the Vault instance
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/vaultinstances/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a Vault instance",
		description = "Edits a Vault instance, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public VaultInstance setVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") Long id,
			VaultInstance rsVaultInstance) throws WebApplicationException {
		log.debug("REST request, edit Vault instance {}", id);
		Session session = Database.getSession();
		VaultInstance vaultInstance;
		try {
			session.beginTransaction();
			vaultInstance = session.get(rsVaultInstance.getClass(), id);
			if (vaultInstance == null) {
				log.error("Unable to find the Vault instance {}.", id);
				throw new NetshotBadRequestException(
					"Unable to find the Vault instance.",
					ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND);
			}
			if (!vaultInstance.getClass().equals(rsVaultInstance.getClass())) {
				log.error("Wrong posted Vault instance type for Vault instance {}.", id);
				throw new NetshotBadRequestException(
					"The posted Vault instance type doesn't match the existing one.",
					ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE);
			}
			vaultInstance.setName(rsVaultInstance.getName());
			if (vaultInstance.getName() == null || "".equals(vaultInstance.getName().trim())) {
				log.error("Invalid Vault instance name.");
				throw new NetshotBadRequestException("Invalid name for the Vault instance",
					ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME);
			}
			vaultInstance.setBaseUrl(rsVaultInstance.getBaseUrl());
			vaultInstance.setNamespace(rsVaultInstance.getNamespace());
			vaultInstance.setHttpsCaTrustMode(rsVaultInstance.getHttpsCaTrustMode());
			vaultInstance.setHttpsCustomCaCertificate(rsVaultInstance.getHttpsCustomCaCertificate());
			if (vaultInstance instanceof HashicorpVaultKv2Instance hcInstance
					&& rsVaultInstance instanceof HashicorpVaultKv2Instance rsHcInstance) {
				hcInstance.setKvMountPath(rsHcInstance.getKvMountPath());
				hcInstance.setAuthMethod(rsHcInstance.getAuthMethod());
				hcInstance.setAppRoleId(rsHcInstance.getAppRoleId());
				if (rsHcInstance.getAppRoleSecretId() != null) {
					hcInstance.setAppRoleSecretId(rsHcInstance.getAppRoleSecretId());
				}
				hcInstance.setJwtIdpTokenEndpoint(rsHcInstance.getJwtIdpTokenEndpoint());
				hcInstance.setJwtClientId(rsHcInstance.getJwtClientId());
				if (rsHcInstance.getJwtClientSecret() != null) {
					hcInstance.setJwtClientSecret(rsHcInstance.getJwtClientSecret());
				}
				hcInstance.setJwtVaultRole(rsHcInstance.getJwtVaultRole());
				hcInstance.setJwtScope(rsHcInstance.getJwtScope());
			}
			session.merge(vaultInstance);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", vaultInstance);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			Throwable t = e.getCause();
			log.error("Unable to save the Vault instance {}.", id, e);
			if (t != null && t.getMessage().contains("uplicate")) {
				throw new NetshotBadRequestException(
					"A Vault instance with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE);
			}
			throw new NetshotBadRequestException("Unable to save the Vault instance",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (NetshotBadRequestException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
		VaultManager.invalidate(id);
		ClusterManager.requestVaultInstanceReload(id);
		return vaultInstance;
	}

	/**
	 * Tests connectivity/authentication to a Vault instance, used by the
	 * Admin UI's "Test connection" action. Works both for a brand-new,
	 * not-yet-saved instance (no {@code id}) and for re-testing an existing
	 * one without having to re-enter an unchanged secret (masked "="
	 * secret fields fall back to the value currently stored for that ID).
	 *
	 * @param id the ID of the existing Vault instance being tested, or null when testing a new one
	 * @param rsVaultInstance the Vault instance configuration to test
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/vaultinstances/test")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Test a Vault instance connection",
		description = "Attempts to log in to the given Vault instance to validate reachability and authentication, without persisting anything."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void testVaultInstance(@QueryParam("id") @Parameter(description = "Existing Vault instance ID, if any") Long id,
			VaultInstance rsVaultInstance) throws WebApplicationException {
		log.debug("REST request, test Vault instance '{}'.", rsVaultInstance.getName());
		if (!(rsVaultInstance instanceof HashicorpVaultKv2Instance rsHcInstance)) {
			throw new NetshotBadRequestException("Unsupported Vault instance type",
				ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE);
		}
		HashicorpVaultKv2Instance hcInstance = rsHcInstance;
		if (id != null) {
			// Overlay onto the persisted secrets, so re-testing an already-saved
			// instance without touching its masked secret field(s) still works.
			Session session = Database.getSession(true);
			try {
				VaultInstance existing = session.get(VaultInstance.class, id);
				if (!(existing instanceof HashicorpVaultKv2Instance existingHc)) {
					throw new NetshotBadRequestException("Unable to find the Vault instance.",
						ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND);
				}
				hcInstance = new HashicorpVaultKv2Instance(rsHcInstance.getName());
				hcInstance.setBaseUrl(rsHcInstance.getBaseUrl());
				hcInstance.setNamespace(rsHcInstance.getNamespace());
				hcInstance.setHttpsCaTrustMode(rsHcInstance.getHttpsCaTrustMode());
				hcInstance.setHttpsCustomCaCertificate(rsHcInstance.getHttpsCustomCaCertificate());
				hcInstance.setKvMountPath(rsHcInstance.getKvMountPath());
				hcInstance.setAuthMethod(rsHcInstance.getAuthMethod());
				hcInstance.setAppRoleMountPath(rsHcInstance.getAppRoleMountPath());
				hcInstance.setAppRoleId(rsHcInstance.getAppRoleId());
				hcInstance.setAppRoleSecretId(rsHcInstance.getAppRoleSecretId() != null
					? rsHcInstance.getAppRoleSecretId() : existingHc.getAppRoleSecretId());
				hcInstance.setJwtMountPath(rsHcInstance.getJwtMountPath());
				hcInstance.setJwtIdpTokenEndpoint(rsHcInstance.getJwtIdpTokenEndpoint());
				hcInstance.setJwtClientId(rsHcInstance.getJwtClientId());
				hcInstance.setJwtClientSecret(rsHcInstance.getJwtClientSecret() != null
					? rsHcInstance.getJwtClientSecret() : existingHc.getJwtClientSecret());
				hcInstance.setJwtVaultRole(rsHcInstance.getJwtVaultRole());
				hcInstance.setJwtScope(rsHcInstance.getJwtScope());
			}
			finally {
				session.close();
			}
		}
		try {
			VaultManager.testConnection(hcInstance);
			AAA_LOG.info("Test connection to Vault instance '{}' succeeded.", hcInstance.getName());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (VaultException e) {
			AAA_LOG.warn("Test connection to Vault instance '{}' failed: {}", hcInstance.getName(), e.getMessage());
			throw new NetshotBadRequestException(
				"Vault connection test failed: " + e.getMessage(),
				ErrorCode.NETSHOT_VAULT_CONNECTION_FAILED);
		}
	}

	/**
	 * The Class RsSearchCriteria.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsSearchCriteria {

		/** The query. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String query;
	}

	/**
	 * The Class RsSearchResults.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsSearchResults {

		/** The query. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String query;

		/** The devices. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<RsLightDevice> devices;
	}

	/**
	 * Search devices.
	 *
	 * @param criteria the search criteria
	 * @param paginationParams = the pagination parameters
	 * @return the rs search results
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/devices/search")
	@RolesAllowed(User.ROLE_READONLY)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Search for devices",
		description = "Find devices using a string-based query."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public RsSearchResults searchDevices(RsSearchCriteria criteria, @BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, search devices, query '{}'.", criteria.getQuery());

		try {
			Finder finder = new Finder(criteria.getQuery());
			Session session = Database.getSession();
			try {
				Query<RsLightDevice> query = session.createQuery("select new RsLightDevice("
					+ "d.id, "
					+ "d.name, "
					+ "d.family, "
					+ "d.mgmtAddress, "
					+ "d.status, "
					+ "d.driver, "
					+ "case when (d.eolDate < current_date()) then true else false end,  "
					+ "case when (d.eosDate < current_date()) then true else false end, "
					+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
					+ "d.softwareLevel, "
					+ "d.networkClass) " + finder.getHql(), RsLightDevice.class);
				finder.setVariables(query);
				query.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
				paginationParams.apply(query);
				List<RsLightDevice> devices = query.list();
				RsSearchResults results = new RsSearchResults();
				results.setDevices(devices);
				results.setQuery(finder.getFormattedQuery());
				return results;
			}
			catch (HibernateException e) {
				log.error("Error while searching for the devices.", e);
				throw new NetshotBadRequestException("Unable to fetch the devices",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		catch (FinderParseException e) {
			log.warn("User's query is invalid.", e);
			throw new NetshotBadRequestException("Invalid search string. "
				+ e.getMessage(),
				ErrorCode.NETSHOT_INVALID_SEARCH_STRING);
		}
	}

	/**
	 * Adds the group.
	 *
	 * @param rsGroup the device group
	 * @return the device group
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/groups")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a device group",
		description = "Creates a device group. A group can be either static (fixed list) or dynamic (query-based list)."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public DeviceGroup addGroup(RsDeviceGroup rsGroup)
		throws WebApplicationException {
		log.debug("REST request, add group.");
		String name = rsGroup.getName() == null ? "" : rsGroup.getName().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty group name.");
			throw new NetshotBadRequestException("Invalid group name.",
				ErrorCode.NETSHOT_INVALID_GROUP_NAME);
		}

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceGroup deviceGroup;
			if ("StaticDeviceGroup".equals(rsGroup.getType())) {
				StaticDeviceGroup staticGroup = new StaticDeviceGroup(name);
				List<Device> devices = new ArrayList<>();
				for (Long deviceId : rsGroup.getStaticDevices()) {
					Device device = session.get(Device.class, deviceId);
					if (device == null) {
						log.warn("Device of ID {} doesn't exist.", deviceId);
						throw new NetshotBadRequestException(
							"Device of ID %d doesn't exist.".formatted(deviceId),
							ErrorCode.NETSHOT_INVALID_DEVICE);
					}
					devices.add(device);
				}
				staticGroup.updateCachedDevices(devices);
				deviceGroup = staticGroup;
			}
			else if ("DynamicDeviceGroup".equals(rsGroup.getType())) {
				DynamicDeviceGroup dynamicGroup = new DynamicDeviceGroup(name);
				if (rsGroup.getQuery() != null) {
					dynamicGroup.setQuery(rsGroup.getQuery());
				}
				try {
					dynamicGroup.refreshCache(session);
				}
				catch (FinderParseException e) {
					throw new NetshotBadRequestException(
						"Invalid query for the group definition.",
						ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY);
				}
				deviceGroup = dynamicGroup;
			}
			else {
				throw new NetshotBadRequestException(
					"Invalid group type.",
					ErrorCode.NETSHOT_INVALID_GROUP);
			}
			deviceGroup.setFolder(rsGroup.getFolder());
			deviceGroup.setHiddenFromReports(rsGroup.isHiddenFromReports());
			session.persist(deviceGroup);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", deviceGroup);
			this.suggestReturnCode(Response.Status.CREATED);
			return deviceGroup;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new device group.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A group with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_GROUP);
			}
			throw new NetshotBadRequestException(
				"Unable to add the group to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the groups.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the groups
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/groups")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the device groups",
		description = "Returns the list of device groups, including their definition."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<DeviceGroup> getGroups(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get groups.");
		Session session = Database.getSession(true);
		try {
			Query<DeviceGroup> query =
				session.createQuery("select g from DeviceGroup g", DeviceGroup.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the groups.", e);
			throw new NetshotBadRequestException("Unable to fetch the groups",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets a specific device group.
	 *
	 * @param id = the ID of the group
	 * @return the group
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/groups/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a device group",
		description = "Returns a specific group, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public DeviceGroup getGroup(@PathParam("id") @Parameter(description = "Group ID") Long id) throws WebApplicationException {
		log.debug("REST request, get group.");
		Session session = Database.getSession(true);
		try {
			Query<DeviceGroup> query = session
				.createQuery("select g from DeviceGroup g where g.id = :id", DeviceGroup.class)
				.setParameter("id", id);
			DeviceGroup group = query.uniqueResult();
			if (group == null) {
				log.warn("Unable to find the device group.");
				throw new NetshotBadRequestException("Unable to find the device group",
					ErrorCode.NETSHOT_GROUP_NOT_FOUND);
			}
			return group;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the group.", e);
			throw new NetshotBadRequestException("Unable to fetch the group",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete group.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/groups/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a device group",
		description = "Removes a device group. This doesn't remove the devices themselves."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public void deleteGroup(@PathParam("id") @Parameter(description = "Group ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete group {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceGroup deviceGroup = session.get(DeviceGroup.class, id);
			if (deviceGroup == null) {
				log.info("No such device group of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(deviceGroup);
			session.getTransaction().commit();
			AAA_LOG.info("Device group of ID {} has been deleted.", deviceGroup.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the group {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the group",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsDeviceGroup.
	 */
	@XmlRootElement(name = "group")
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsDeviceGroup {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id = -1;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name;

		/** The type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type;

		/** The static devices. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<Long> staticDevices = new ArrayList<>();

		/** The query. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String query;

		/** The folder. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String folder = "";

		/** Hide the group in reports. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean hiddenFromReports;

	}

	/**
	 * Sets the group.
	 *
	 * @param id the id
	 * @param rsGroup the rs group
	 * @return the device group
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/groups/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a device group",
		description = "Edits a device group, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public DeviceGroup setGroup(@PathParam("id") @Parameter(description = "Group ID") Long id, RsDeviceGroup rsGroup)
		throws WebApplicationException {
		log.debug("REST request, edit group {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceGroup group = session.get(DeviceGroup.class, id);
			if (group == null) {
				log.error("Unable to find the group {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this group.",
					ErrorCode.NETSHOT_GROUP_NOT_FOUND);
			}
			if (group instanceof StaticDeviceGroup staticGroup) {
				List<Device> devices = new ArrayList<>();
				for (Long deviceId : rsGroup.getStaticDevices()) {
					Device device = session.get(Device.class, deviceId);
					if (device == null) {
						log.warn("Device of ID {} doesn't exist.", deviceId);
						throw new NetshotBadRequestException(
							"Device of ID %d doesn't exist.".formatted(deviceId),
							ErrorCode.NETSHOT_INVALID_DEVICE);
					}
					devices.add(device);
				}
				staticGroup.updateCachedDevices(devices);
			}
			else if (group instanceof DynamicDeviceGroup dynamicGroup) {
				dynamicGroup.setQuery(rsGroup.getQuery());
				try {
					dynamicGroup.refreshCache(session);
				}
				catch (FinderParseException e) {
					throw new NetshotBadRequestException(
						"Invalid query for the group definition.",
						ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY);
				}
			}
			else {
				throw new NetshotBadRequestException("Unknown group type.",
					ErrorCode.NETSHOT_INCOMPATIBLE_GROUP_TYPE);
			}
			String name = rsGroup.getName() == null ? "" : rsGroup.getName().trim();
			if (name.isEmpty()) {
				log.warn("User posted an empty group name.");
				throw new NetshotBadRequestException("Invalid group name.",
					ErrorCode.NETSHOT_INVALID_GROUP_NAME);
			}
			group.setName(name);
			group.setFolder(rsGroup.getFolder());
			group.setHiddenFromReports(rsGroup.isHiddenFromReports());
			session.merge(group);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited.", group);
			return group;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("Unable to find a device while editing group {}.", id, e);
			throw new NetshotBadRequestException(
				"Unable to find a device. Refresh and try again.",
				ErrorCode.NETSHOT_INVALID_DEVICE_IN_STATICGROUP);
		}
		catch (PersistenceException e) {
			Database.rollbackSilently(session);
			log.error("Unable to save the group {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A group with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_GROUP);
			}
			throw new NetshotBadRequestException("Unable to save the group.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsTask.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsTask {

		/** The id. */
		@Schema(description = "Task unique ID")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The cancelled status. */
		@Schema(description = "Set to cancel the task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean cancelled;

		/** The type. */
		@Schema(description = "Type of task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type = "";

		/** The group. */
		@Schema(description = "The group ID for group-based task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long group = 0L;

		/** The one-time, ordered device list, as an alternative to a device group. */
		@Schema(description = "The ordered list of device IDs for a one-time device list, as an alternative to a group")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<Long> deviceList;

		/** Parallel vs sequential scheduling of child tasks (group-based tasks only). */
		@Schema(description = "Parallel vs sequential scheduling of the per-device child tasks (group-based tasks only)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.ScheduleMode scheduleMode = Task.ScheduleMode.PARALLEL;

		/** Whether to stop scheduling further child tasks after one fails (sequential mode only). */
		@Schema(description = "Whether to stop scheduling further child tasks after one fails (sequential mode only)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean stopOnFailure;

		/** The device. */
		@Schema(description = "The device ID for device-based task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long device = 0L;

		/** The domain. */
		@Schema(description = "The domain ID when applicable")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Long domain = 0L;

		/** The subnets. */
		@Schema(description = "Subnets to scan (comma, space or new line separated)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String subnets = "";

		/** The schedule reference. */
		@Schema(description = "Scheduling reference date for scheduled task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date scheduleReference = new Date();

		/** The schedule type. */
		@Schema(description = "Scheduling mode (ASAP, once, recurring...)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.ScheduleType scheduleType = ScheduleType.ASAP;

		/** The schedule factor. */
		@Schema(description = "Scheduling factor for recurring task")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int scheduleFactor = 1;

		/** The task priority. */
		@Schema(description = "Priority for this task, from 1 to 9 (5 is default, more is better)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int schedulePriority = 5;

		/** The comments. */
		@Schema(description = "Task comment")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String comments = "";

		@Schema(description = "Ignore devices that had a successful snapshot in the last given hours")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int limitToOutofdateDeviceHours = -1;

		@Schema(description = "Purge tasks older than this number of days")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int daysToPurge = 90;

		@Schema(description = "Purge configurations older than this number of days")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int configDaysToPurge = -1;

		@Schema(description = "Purge configurations bigger than this size (KB)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int configSizeToPurge;

		@Schema(description = "When purging old configurations, keep a configuration every n days")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int configKeepDays;

		@Schema(description = "Purge removed hardware modules older than this number of days")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int moduleDaysToPurge = -1;

		@Schema(description = "Remove orphan binary attribute files (no matching database record)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean removeOrphanFiles = false;

		@Schema(description = "The script to execute")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String script = "";

		@Schema(description = "The device driver to use")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String driver;

		@Schema(description = "The user inputs for the script")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<String, String> userInputs;

		@Schema(description = "Enable task debugging")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean debugEnabled;

		/** Disable automatic diagnostic task (applies to snapshot tasks). */
		@Schema(description = "Disable automatic diagnostic task (applies to snapshot tasks)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean dontRunDiagnostics;

		/** Disable automatic check compliance task (applies to snapshot and diagnostic tasks).  */
		@Schema(description = "Disable automatic check compliance task (applies to snapshot and diagnostic tasks)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean dontCheckCompliance;

		/** Automatically run a snapshot after successful script execution (applies to script tasks). */
		@Schema(description = "Automatically run a snapshot after successful script execution (applies to script tasks)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean runSnapshot;

		/** Automatically run diagnostics after successful script execution (applies to script tasks). */
		@Schema(description = "Automatically run diagnostics after successful script execution (applies to script tasks)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean runDiagnostics;

		/** Automatically check compliance after successful script execution (applies to script tasks). */
		@Schema(description = "Automatically check compliance after successful script execution (applies to script tasks)")
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean checkCompliance;
	}

	/**
	 * Sets the task.
	 *
	 * @param id the id
	 * @param rsTask the rs task
	 * @return the task
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/tasks/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a task",
		description = "Edits a task, by ID. Set 'cancel' property to true to cancel the task."
	)
	@Tag(name = "Tasks", description = "Task management")
	public Task setTask(@PathParam("id") @Parameter(description = "Task ID") Long id, RsTask rsTask)
		throws WebApplicationException {
		log.debug("REST request, edit task {}.", id);
		Task task = null;
		Session session = Database.getSession();
		try {
			task = session.get(Task.class, id);
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the task {}.", id, e);
			throw new NetshotBadRequestException("Unable to fetch the task.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}

		if (task == null) {
			log.error("Unable to find the task {}.", id);
			throw new NetshotBadRequestException("Unable to find the task.",
				ErrorCode.NETSHOT_INVALID_TASK);
		}

		if (rsTask.isCancelled()) {
			// A group-based task that orchestrates per-device children (either schedule mode)
			// spends its whole run in RUNNING, so the normal SCHEDULED-only cancel path doesn't
			// apply -- it's cancelled by flagging cancelRequested, polled by its own
			// orchestration loop, rather than by pulling it out of the Quartz scheduler.
			boolean runningOrchestrator = task.getStatus() == Task.Status.RUNNING
				&& task instanceof ChildOrchestratingTask;
			// Same for a running device task, which may be waiting for its turn on the device
			// (the request is only honored while waiting, not once the device session started)
			boolean runningDeviceTask = task.getStatus() == Task.Status.RUNNING
				&& task.deviceLockPolicy() != Task.DeviceLockPolicy.NONE;
			if (task.getStatus() != Task.Status.SCHEDULED && !runningOrchestrator && !runningDeviceTask) {
				log.error("User is trying to cancel task {} not in SCHEDULED state (or RUNNING group task).",
					id);
				throw new NetshotBadRequestException(
					"The task isn't in 'SCHEDULED' state.",
					ErrorCode.NETSHOT_TASK_NOT_CANCELLABLE);
			}

			try {
				if (runningOrchestrator) {
					TaskManager.requestCancel(id);
					AAA_LOG.info("{} has been requested to cancel (running group task).", task);
				}
				else if (runningDeviceTask) {
					TaskManager.requestCancel(id);
					AAA_LOG.info("{} has been requested to cancel (running device task, if still waiting for the device).", task);
				}
				else {
					String message = "Task manually cancelled by user %s.".formatted(this.getUsername());
					TaskManager.cancelTask(task, message);
					AAA_LOG.info("{} has been manually cancelled.", task);
				}
			}
			catch (Exception e) {
				log.error("Unable to cancel the task {}.", id, e);
				throw new NetshotBadRequestException("Cannot cancel the task.",
					ErrorCode.NETSHOT_TASK_CANCEL_ERROR);
			}
		}

		return task;
	}

	/**
	 * Task status and corresponding count.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsTaskStatusCount {

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Task.Status status;


		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long count;
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsTaskSummary {
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<Task.Status, Long> countByStatus = new HashMap<>();


		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int threadCount = TaskManager.SETTINGS.getThreadCount();
	}

	@GET
	@Path("/tasks/summary")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get summary/overview of tasks",
		description = "Retrieves global info about tasks."
	)
	@Tag(name = "Tasks", description = "Task management")
	public RsTaskSummary getTaskSummary(
		@QueryParam("parentTaskId") @Parameter(description = "Only summarize child tasks of the given parent task ID") Long parentTaskId)
		throws WebApplicationException {

		log.debug("REST request, get task summary.");

		RsTaskSummary summary = new RsTaskSummary();
		Session session = Database.getSession(true);
		try {
			String hql = "select new RsTaskStatusCount(t.status, count(t.id)) from Task t"
				+ (parentTaskId != null ? " where t.parentTaskId = :parentTaskId" : "")
				+ " group by t.status";
			Query<RsTaskStatusCount> countQuery = session.createQuery(hql, RsTaskStatusCount.class);
			if (parentTaskId != null) {
				countQuery.setParameter("parentTaskId", parentTaskId);
			}
			List<RsTaskStatusCount> counts = countQuery.list();
			for (Task.Status status : Task.Status.values()) {
				summary.getCountByStatus().put(status, 0L);
			}
			for (RsTaskStatusCount taskCount : counts) {
				summary.getCountByStatus().put(taskCount.getStatus(), taskCount.getCount());
			}
			return summary;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the task counts", e);
			throw new NetshotBadRequestException("Unable to fetch the task counts",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/** The final (completed) task statuses, i.e. the ones shown on the tasks time-based statistics. */
	private static final Set<Task.Status> FINAL_TASK_STATUSES =
		Set.of(Task.Status.SUCCESS, Task.Status.FAILURE, Task.Status.CANCELLED);

	/**
	 * One bucket of the task time-based statistics.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsTaskStatsBin {
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date from;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date to;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<Task.Status, Long> countByStatus = new HashMap<>();
	}

	/**
	 * Time-based statistics (histogram) about completed tasks over a given date range.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsTaskStats {
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private List<RsTaskStatsBin> bins = new ArrayList<>();

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<Task.Status, Long> statusCounts = new HashMap<>();

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<String, Long> typeCounts = new HashMap<>();
	}

	/**
	 * Gets time-based statistics (histogram) of completed tasks over a date range.
	 *
	 * @param startDate the start date (defaults to epoch if not provided)
	 * @param endDate the end date (defaults to now if not provided)
	 * @param buckets the number of time buckets to split the range into
	 * @return the task stats
	 */
	@GET
	@Path("/tasks/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get time-based statistics about completed tasks",
		description = "Returns, for the given date range, the number of completed tasks (success, failure, cancelled) "
			+ "bucketed over time, plus totals by status and by task type over the whole range."
	)
	@Tag(name = "Tasks", description = "Task management")
	public RsTaskStats getTaskStats(
		@QueryParam("after") @Parameter(description = "Start of the range (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "End of the range (as milliseconds since 1970)") Long endDate,
		@QueryParam("buckets") @Parameter(description = "Number of time buckets to split the range into") Integer buckets)
		throws WebApplicationException {

		log.debug("REST request, get task stats.");
		long from = startDate == null ? 0L : startDate;
		long to = endDate == null ? System.currentTimeMillis() : endDate;
		int binCount = buckets == null ? 48 : Math.max(1, Math.min(366, buckets));
		long span = Math.max(1L, to - from);
		double step = ((double) span) / binCount;

		Session session = Database.getSession(true);
		try {
			RsTaskStats stats = new RsTaskStats();
			for (Task.Status status : FINAL_TASK_STATUSES) {
				stats.getStatusCounts().put(status, 0L);
			}

			for (int i = 0; i < binCount; i++) {
				Date bucketStart = new Date(from + Math.round(i * step));
				Date bucketEnd = new Date(i == binCount - 1 ? to : from + Math.round((i + 1) * step));
				RsTaskStatsBin bin = new RsTaskStatsBin();
				bin.setFrom(bucketStart);
				bin.setTo(bucketEnd);
				for (Task.Status status : FINAL_TASK_STATUSES) {
					bin.getCountByStatus().put(status, 0L);
				}
				List<RsTaskStatusCount> counts = session
					.createQuery(
						"select new RsTaskStatusCount(t.status, count(t.id)) from Task t "
							+ "where t.status in :statuses and ("
							+ "((t.executionDate is not null) and (t.executionDate >= :bucketStart) and (t.executionDate < :bucketEnd)) "
							+ "or ((t.executionDate is null) and (t.changeDate >= :bucketStart) and (t.changeDate < :bucketEnd))) "
							+ "group by t.status",
						RsTaskStatusCount.class)
					.setParameter("statuses", FINAL_TASK_STATUSES)
					.setParameter("bucketStart", bucketStart)
					.setParameter("bucketEnd", bucketEnd)
					.list();
				for (RsTaskStatusCount count : counts) {
					bin.getCountByStatus().put(count.getStatus(), count.getCount());
					stats.getStatusCounts().merge(count.getStatus(), count.getCount(), Long::sum);
				}
				stats.getBins().add(bin);
			}

			Date rangeStart = new Date(from);
			Date rangeEnd = new Date(to);
			for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
				Long count = session
					.createQuery(
						"select count(t.id) from Task t where type(t) = :class and t.status in :statuses and ("
							+ "((t.executionDate is not null) and (t.executionDate >= :rangeStart) and (t.executionDate < :rangeEnd)) "
							+ "or ((t.executionDate is null) and (t.changeDate >= :rangeStart) and (t.changeDate < :rangeEnd)))",
						Long.class)
					.setParameter("class", taskClass)
					.setParameter("statuses", FINAL_TASK_STATUSES)
					.setParameter("rangeStart", rangeStart)
					.setParameter("rangeEnd", rangeEnd)
					.uniqueResult();
				stats.getTypeCounts().put(taskClass.getSimpleName(), count == null ? 0L : count);
			}

			return stats;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the task stats", e);
			throw new NetshotBadRequestException("Unable to fetch the task stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Checks whether the given task creation request specifies a device group.
	 * @param rsTask the request
	 * @return true if a group ID is set
	 */
	private static boolean hasGroup(RsTask rsTask) {
		return rsTask.getGroup() != null && rsTask.getGroup() != 0L;
	}

	/**
	 * Checks whether the given task creation request specifies a one-time device list.
	 * @param rsTask the request
	 * @return true if a non-empty device list is set
	 */
	private static boolean hasDeviceList(RsTask rsTask) {
		return rsTask.getDeviceList() != null && !rsTask.getDeviceList().isEmpty();
	}

	/**
	 * Validates that exactly one of a device group or a one-time device list was given
	 * for a group-based task creation request.
	 * @param rsTask the request
	 * @throws NetshotBadRequestException if both or neither were given
	 */
	private static void validateGroupOrDeviceList(RsTask rsTask) {
		if (hasGroup(rsTask) == hasDeviceList(rsTask)) {
			throw new NetshotBadRequestException(
				"Exactly one of 'group' or 'deviceList' must be provided.",
				ErrorCode.NETSHOT_INVALID_TASK);
		}
	}

	/**
	 * Resolves the ordered list of devices for a one-time device list task creation
	 * request.
	 * @param session the Hibernate session
	 * @param rsTask the request
	 * @return the ordered list of devices
	 * @throws NetshotBadRequestException if any device ID doesn't exist
	 */
	private static List<Device> resolveDeviceList(Session session, RsTask rsTask) {
		List<Device> devices = new ArrayList<>();
		for (Long deviceId : rsTask.getDeviceList()) {
			Device device = session.get(Device.class, deviceId);
			if (device == null) {
				log.error("Unable to find the device {}.", deviceId);
				throw new NetshotBadRequestException(String.format("Unable to find device %d.", deviceId),
					ErrorCode.NETSHOT_INVALID_DEVICE);
			}
			devices.add(device);
		}
		return devices;
	}

	/**
	 * Adds a new task.
	 *
	 * @param rsTask the rs task
	 * @return the task
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/tasks")
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a task",
		description = "Creates a task and schedule it for execution."
	)
	@Tag(name = "Tasks", description = "Task management")
	public Task addTask(RsTask rsTask) throws WebApplicationException {
		log.debug("REST request, add task.");

		final String userName = this.getUsername();

		Task task;
		if ("TakeSnapshotTask".equals(rsTask.getType())) {
			log.trace("Adding a TakeSnapshotTask");
			Device device;
			Session session = Database.getSession();
			try {
				device = session.get(Device.class, rsTask.getDevice());
				if (device == null) {
					log.error("Unable to find the device {}.", rsTask.getDevice());
					throw new NetshotBadRequestException("Unable to find the device.",
						ErrorCode.NETSHOT_INVALID_DEVICE);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the device.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
			task = new TakeSnapshotTask(device, rsTask.getComments(), userName, false,
				rsTask.isDontRunDiagnostics(), rsTask.isDontCheckCompliance());
		}
		else if ("RunDeviceScriptTask".equals(rsTask.getType())) {
			if (!securityContext.isUserInRole(User.ROLE_EXECUTEREADWRITE)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to run scripts on devices.", 0);
			}
			log.trace("Adding a RunDeviceScriptTask");
			DeviceDriver driver = DeviceDriver.getDriverByName(rsTask.getDriver());
			if (driver == null) {
				log.error("Unknown device driver {}.", rsTask.getType());
				throw new NetshotBadRequestException("Unknown device driver.",
					ErrorCode.NETSHOT_INVALID_DEVICE);
			}
			if (rsTask.getScript() == null) {
				log.error("The script can't be empty.");
				throw new NetshotBadRequestException("The script can't be empty.",
					ErrorCode.NETSHOT_INVALID_DEVICE);
			}
			Device device;
			Session session = Database.getSession();
			try {
				device = session.get(Device.class, rsTask.getDevice());
				if (device == null) {
					log.error("Unable to find the device {}.", rsTask.getDevice());
					throw new NetshotBadRequestException("Unable to find the device.",
						ErrorCode.NETSHOT_INVALID_DEVICE);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the device.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
			try {
				DeviceJsScript jsScript = new DeviceJsScript("__toValidate", rsTask.getDriver(), rsTask.getScript(), userName);
				jsScript.validateUserInputs(rsTask.getUserInputs());
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid script.");
				throw new NetshotBadRequestException(e.getMessage(),
					ErrorCode.NETSHOT_INVALID_SCRIPT);
			}
			task = new RunDeviceScriptTask(device, rsTask.getScript(), driver, rsTask.getComments(), userName);
			((RunDeviceScriptTask) task).setUserInputValues(rsTask.getUserInputs());
			((RunDeviceScriptTask) task).setRunSnapshot(rsTask.isRunSnapshot());
			((RunDeviceScriptTask) task).setRunDiagnostics(rsTask.isRunDiagnostics());
			((RunDeviceScriptTask) task).setCheckCompliance(rsTask.isCheckCompliance());
		}
		else if ("RunDeviceGroupScriptTask".equals(rsTask.getType())) {
			if (!securityContext.isUserInRole(User.ROLE_EXECUTEREADWRITE)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to run scripts on devices.", 0);
			}
			log.trace("Adding a RunDeviceGroupScriptTask");
			DeviceDriver driver = DeviceDriver.getDriverByName(rsTask.getDriver());
			if (driver == null) {
				log.error("Unknown device driver {}.", rsTask.getType());
				throw new NetshotBadRequestException("Unknown device driver.",
					ErrorCode.NETSHOT_INVALID_DEVICE);
			}
			if (rsTask.getScript() == null) {
				log.error("The script can't be empty.");
				throw new NetshotBadRequestException("The script can't be empty.",
					ErrorCode.NETSHOT_INVALID_DEVICE);
			}
			try {
				DeviceJsScript jsScript = new DeviceJsScript("__toValidate", rsTask.getDriver(), rsTask.getScript(), userName);
				jsScript.validateUserInputs(rsTask.getUserInputs());
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid script.");
				throw new NetshotBadRequestException(e.getMessage(),
					ErrorCode.NETSHOT_INVALID_SCRIPT);
			}
			validateGroupOrDeviceList(rsTask);
			Session session = Database.getSession();
			try {
				if (hasGroup(rsTask)) {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new RunDeviceGroupScriptTask(group, rsTask.getScript(), driver, rsTask.getComments(), userName);
				}
				else {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new RunDeviceGroupScriptTask(devices, rsTask.getScript(), driver, rsTask.getComments(), userName);
				}
				((RunDeviceGroupScriptTask) task).setUserInputValues(rsTask.getUserInputs());
				((RunDeviceGroupScriptTask) task).setRunSnapshot(rsTask.isRunSnapshot());
				((RunDeviceGroupScriptTask) task).setRunDiagnostics(rsTask.isRunDiagnostics());
				((RunDeviceGroupScriptTask) task).setCheckCompliance(rsTask.isCheckCompliance());
				task.setScheduleMode(rsTask.getScheduleMode());
				task.setStopOnFailure(rsTask.isStopOnFailure());
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the group.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		else if ("CheckComplianceTask".equals(rsTask.getType())) {
			log.trace("Adding a CheckComplianceTask");
			Device device;
			Session session = Database.getSession();
			try {
				device = session.get(Device.class, rsTask.getDevice());
				if (device == null) {
					log.error("Unable to find the device {}.", rsTask.getDevice());
					throw new NetshotBadRequestException("Unable to find the device.",
						ErrorCode.NETSHOT_INVALID_DEVICE);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the device.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
			task = new CheckComplianceTask(device, rsTask.getComments(), userName);
		}
		else if ("TakeGroupSnapshotTask".equals(rsTask.getType())) {
			log.trace("Adding a TakeGroupSnapshotTask");
			validateGroupOrDeviceList(rsTask);
			Session session = Database.getSession();
			try {
				if (hasGroup(rsTask)) {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new TakeGroupSnapshotTask(group, rsTask.getComments(), userName,
						rsTask.getLimitToOutofdateDeviceHours(), rsTask.isDontRunDiagnostics(),
						rsTask.isDontCheckCompliance());
				}
				else {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new TakeGroupSnapshotTask(devices, rsTask.getComments(), userName,
						rsTask.getLimitToOutofdateDeviceHours(), rsTask.isDontRunDiagnostics(),
						rsTask.isDontCheckCompliance());
				}
				task.setScheduleMode(rsTask.getScheduleMode());
				task.setStopOnFailure(rsTask.isStopOnFailure());
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the group.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		else if ("CheckGroupComplianceTask".equals(rsTask.getType())) {
			log.trace("Adding a CheckGroupComplianceTask");
			validateGroupOrDeviceList(rsTask);
			Session session = Database.getSession();
			try {
				if (hasGroup(rsTask)) {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new CheckGroupComplianceTask(group, rsTask.getComments(), userName);
				}
				else {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new CheckGroupComplianceTask(devices, rsTask.getComments(), userName);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the group.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		else if ("CheckGroupSoftwareTask".equals(rsTask.getType())) {
			log.trace("Adding a CheckGroupSoftwareTask");
			validateGroupOrDeviceList(rsTask);
			Session session = Database.getSession();
			try {
				if (hasGroup(rsTask)) {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new CheckGroupSoftwareTask(group, rsTask.getComments(), userName);
				}
				else {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new CheckGroupSoftwareTask(devices, rsTask.getComments(), userName);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the group.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		else if ("RunGroupDiagnosticsTask".equals(rsTask.getType())) {
			log.trace("Adding a RunGroupDiagnosticsTask");
			validateGroupOrDeviceList(rsTask);
			Session session = Database.getSession();
			try {
				if (hasGroup(rsTask)) {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new RunGroupDiagnosticsTask(group, rsTask.getComments(), userName,
						rsTask.isDontCheckCompliance());
				}
				else {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new RunGroupDiagnosticsTask(devices, rsTask.getComments(), userName,
						rsTask.isDontCheckCompliance());
				}
				task.setScheduleMode(rsTask.getScheduleMode());
				task.setStopOnFailure(rsTask.isStopOnFailure());
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the group.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}
		else if ("ScanSubnetsTask".equals(rsTask.getType())) {
			if (!securityContext.isUserInRole(User.ROLE_READWRITE)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to scan for devices.", 0);
			}
			log.trace("Adding a ScanSubnetsTask");
			Set<Network4Address> subnets = new HashSet<>();
			String[] rsSubnets = rsTask.getSubnets().split("(\r\n|\n|;| |,)");
			Pattern pattern = Pattern.compile("^(?<ip>[0-9\\.]+)(/(?<mask>[0-9]+))?$");
			for (String rsSubnet : rsSubnets) {
				Matcher matcher = pattern.matcher(rsSubnet);
				if (!matcher.find()) {
					log.warn("User posted an invalid subnet '{}'.", rsSubnet);
					throw new NetshotBadRequestException(String.format("Invalid subnet '%s'.", rsSubnet),
						ErrorCode.NETSHOT_INVALID_SUBNET);
				}
				Network4Address subnet;
				try {
					int mask = 32;
					if (matcher.group("mask") != null) {
						mask = Integer.parseInt(matcher.group("mask"));
					}
					subnet = new Network4Address(matcher.group("ip"), mask);
					subnets.add(subnet);
				}
				catch (Exception e) {
					log.warn("User posted an invalid subnet '{}'.", rsSubnet, e);
					throw new NetshotBadRequestException(String.format("Invalid subnet '%s'.", rsSubnet),
						ErrorCode.NETSHOT_INVALID_SUBNET);
				}
				if (subnet.getPrefixLength() < 22 || subnet.getPrefixLength() > 32) {
					log.warn("User posted an invalid prefix length {}.",
						subnet.getPrefix());
					throw new NetshotBadRequestException(String.format("Invalid prefix length for '%s'.", rsSubnet),
						ErrorCode.NETSHOT_SCAN_SUBNET_TOO_BIG);
				}
			}
			if (subnets.isEmpty()) {
				log.warn("User posted an invalid subnet list '{}'.", rsTask.getSubnets());
				throw new NetshotBadRequestException(String.format("Invalid subnet list '%s'.", rsTask.getSubnets()),
					ErrorCode.NETSHOT_INVALID_SUBNET);
			}
			if (rsTask.getDomain() == 0) {
				log.error("Domain {} is invalid (0).", rsTask.getDomain());
				throw new NetshotBadRequestException("Invalid domain",
					ErrorCode.NETSHOT_INVALID_DOMAIN);
			}
			Session session = Database.getSession();
			Domain domain;
			try {
				domain = session.get(Domain.class, rsTask.getDomain());
				if (domain == null) {
					log.error("No such domain of ID {}.", rsTask.getDomain());
					throw new NetshotBadRequestException("Invalid domain",
						ErrorCode.NETSHOT_INVALID_DOMAIN);
				}
			}
			catch (Exception e) {
				log.error("Unable to load the domain {}.", rsTask.getDomain());
				throw new NetshotBadRequestException("Invalid domain",
					ErrorCode.NETSHOT_INVALID_DOMAIN);
			}
			finally {
				session.close();
			}
			StringBuilder target = new StringBuilder();
			target.append("{");
			for (Network4Address subnet : subnets) {
				if (target.length() > 1) {
					target.append(", ");
				}
				target.append(subnet.getPrefix());
			}
			target.append("}");
			task = new ScanSubnetsTask(subnets, domain, rsTask.getComments(), target.toString(), userName);
		}
		else if ("PurgeDatabaseTask".equals(rsTask.getType())) {
			if (!securityContext.isUserInRole(User.ROLE_ADMIN)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to purge database.", 0);
			}
			log.trace("Adding a PurgeDatabaseTask");
			if (rsTask.getDaysToPurge() < 2) {
				log.error(String.format("Invalid number of days %d for the PurgeDatabaseTask task.", rsTask.getDaysToPurge()));
				throw new NetshotBadRequestException("Invalid number of days.",
					ErrorCode.NETSHOT_INVALID_TASK);
			}
			int configDays = rsTask.getConfigDaysToPurge();
			int configSize = rsTask.getConfigSizeToPurge();
			int configKeepDays = rsTask.getConfigKeepDays();
			if (configDays == -1) {
				configSize = 0;
				configKeepDays = 0;
			}
			else if (configDays <= 3) {
				log.error("The number of days of configurations to purge must be greater than 3.");
				throw new NetshotBadRequestException("The number of days of configurations to purge must be greater than 3.",
					ErrorCode.NETSHOT_INVALID_TASK);
			}
			else {
				if (configSize < 0) {
					log.error("The configuration size limit can't be negative.");
					throw new NetshotBadRequestException("The limit on the configuration size can't be negative.",
						ErrorCode.NETSHOT_INVALID_TASK);
				}
				if (configKeepDays < 0) {
					log.error("The interval of days between configurations to keep can't be negative.");
					throw new NetshotBadRequestException("The number of days of configurations to purge can't be negative.",
						ErrorCode.NETSHOT_INVALID_TASK);
				}
				if (configDays <= configKeepDays) {
					log.error("The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.");
					throw new NetshotBadRequestException(
						"The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.",
						ErrorCode.NETSHOT_INVALID_TASK);
				}
			}
			int moduleDays = rsTask.getModuleDaysToPurge();
			if (moduleDays != -1 && moduleDays < 3) {
				log.error("The number of days of removed modules to purge must be greater than 3.");
				throw new NetshotBadRequestException("The number of days of removed modules to purge must be greater than 3.",
					ErrorCode.NETSHOT_INVALID_TASK);
			}
			if (hasGroup(rsTask) && hasDeviceList(rsTask)) {
				throw new NetshotBadRequestException(
					"Only one of 'group' or 'deviceList' may be provided.",
					ErrorCode.NETSHOT_INVALID_TASK);
			}
			if (hasGroup(rsTask)) {
				Session session = Database.getSession();
				try {
					DeviceGroup group = session.get(DeviceGroup.class, rsTask.getGroup());
					if (group == null) {
						log.error("Unable to find the group {}.", rsTask.getGroup());
						throw new NetshotBadRequestException("Unable to find the group.",
							ErrorCode.NETSHOT_INVALID_GROUP);
					}
					task = new PurgeDatabaseTask(rsTask.getComments(), userName, rsTask.getDaysToPurge(),
						configDays, configSize, configKeepDays, moduleDays, group);
				}
				catch (HibernateException e) {
					log.error("Error while retrieving the group.", e);
					throw new NetshotBadRequestException("Database error.",
						ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
				}
				finally {
					session.close();
				}
			}
			else if (hasDeviceList(rsTask)) {
				Session session = Database.getSession();
				try {
					List<Device> devices = resolveDeviceList(session, rsTask);
					task = new PurgeDatabaseTask(rsTask.getComments(), userName, rsTask.getDaysToPurge(),
						configDays, configSize, configKeepDays, moduleDays, devices);
				}
				finally {
					session.close();
				}
			}
			else {
				task = new PurgeDatabaseTask(rsTask.getComments(), userName, rsTask.getDaysToPurge(),
					configDays, configSize, configKeepDays, moduleDays, (DeviceGroup) null);
			}
			((PurgeDatabaseTask) task).setRemoveOrphanFiles(rsTask.isRemoveOrphanFiles());
		}
		else if ("RunDiagnosticsTask".equals(rsTask.getType())) {
			log.trace("Adding a RunDiagnosticsTask");
			Device device;
			Session session = Database.getSession();
			try {
				device = session.get(Device.class, rsTask.getDevice());
				if (device == null) {
					log.error("Unable to find the device {}.", rsTask.getDevice());
					throw new NetshotBadRequestException("Unable to find the device.",
						ErrorCode.NETSHOT_INVALID_DEVICE);
				}
			}
			catch (HibernateException e) {
				log.error("Error while retrieving the device.", e);
				throw new NetshotBadRequestException("Database error.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
			task = new RunDiagnosticsTask(device, rsTask.getComments(), userName, rsTask.isDontCheckCompliance());
		}
		else {
			log.error("User posted an invalid task type '{}'.", rsTask.getType());
			throw new NetshotBadRequestException("Invalid task type.",
				ErrorCode.NETSHOT_INVALID_TASK);
		}
		if (rsTask.getScheduleReference() != null) {
			task.setDebugEnabled(rsTask.isDebugEnabled());
			task.setScheduleReference(rsTask.getScheduleReference());
			task.setScheduleType(rsTask.getScheduleType());
			task.setScheduleFactor(rsTask.getScheduleFactor());
			if (task.getScheduleType() == ScheduleType.AT) {
				Calendar inOneMinute = Calendar.getInstance();
				inOneMinute.add(Calendar.MINUTE, 1);
				if (task.getScheduleReference().before(inOneMinute.getTime())) {
					log.error(
						"The schedule for the task occurs in less than one minute ({} vs {}).",
						task.getScheduleReference(), inOneMinute.getTime());
					throw new NetshotBadRequestException(
						"The schedule occurs in the past.",
						ErrorCode.NETSHOT_INVALID_TASK);
				}
			}
			else if (task.getScheduleFactor() < 1) {
				log.error("The repeating factor {} is invalid.", task.getScheduleFactor());
				throw new NetshotBadRequestException(
					"The repeating factor is invalid.",
					ErrorCode.NETSHOT_INVALID_TASK);
			}
		}
		if (rsTask.getSchedulePriority() < 1 || rsTask.getSchedulePriority() > 9) {
			log.error("Invalid priority {} for the task", rsTask.getSchedulePriority());
			throw new NetshotBadRequestException("Invalid priority value.",
				ErrorCode.NETSHOT_INVALID_TASK);
		}
		task.setPriority(rsTask.getSchedulePriority());
		try {
			TaskManager.addTask(task);
			AAA_LOG.info("The task {} has been created.", task);
			this.suggestReturnCode(Response.Status.CREATED);
			return task;
		}
		catch (HibernateException e) {
			log.error("Unable to add the task.", e);
			throw new NetshotBadRequestException(
				"Unable to add the task to the database.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (SchedulerException e) {
			log.error("Unable to schedule the task.", e);
			throw new NetshotBadRequestException("Unable to schedule the task.",
				ErrorCode.NETSHOT_SCHEDULE_ERROR);
		}
	}

	/**
	 * The Class RsLightConfig.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsLightConfig {

		/** The config id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The device name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String deviceName;

		/** The device id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long deviceId;

		/** The change date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date changeDate;

		/** The author. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String author;

		/** The device network class. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Device.NetworkClass deviceNetworkClass;
	}

	/**
	 * Get configuration changes with optinal filtering criteria.
	 *
	 * @param startDate = after date filter
	 * @param endDate = before date filter
	 * @param domains = domain filter
	 * @param deviceGroups = group filter
	 * @param paginationParams = the pagination parameters
	 * @return the changes
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/configs")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get configuration changes",
		description = "Retrieves a list of configurations, based on given criteria."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<RsLightConfig> getConfigs(
		@QueryParam("after") @Parameter(description = "Configs gathered after this date (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "Configs gathered before this date (as milliseconds since 1970)") Long endDate,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, config changes.");
		try (Session session = Database.getSession(true)) {
			String hqlQuery = "select new RsLightConfig("
				+ "c.id, "
				+ "c.device.name, "
				+ "c.device.id, "
				+ "c.changeDate, "
				+ "c.author, "
				+ "d.networkClass"
				+ ") from Config c join c.device d";
			Map<String, Object> hqlParams = new HashMap<>();
			if (!deviceGroups.isEmpty()) {
				hqlQuery += " join d.groupMemberships gm";
				hqlParams.put("groupIds", deviceGroups);
			}
			hqlQuery += " where 1 = 1";
			if (!domains.isEmpty()) {
				hqlQuery += " and d.mgmtDomain.id in (:domainIds)";
				hqlParams.put("domainIds", domains);
			}
			if (!deviceGroups.isEmpty()) {
				hqlQuery += " and gm.key.group.id in (:groupIds)";
			}
			if (startDate != null) {
				hqlQuery += " and c.changeDate >= :startDate";
				hqlParams.put("startDate", new Date(startDate));
			}
			if (endDate != null) {
				hqlQuery += " and c.changeDate < :endDate";
				hqlParams.put("endDate", new Date(endDate));
			}
			Query<RsLightConfig> query = session.createQuery(hqlQuery, RsLightConfig.class);
			for (Entry<String, Object> k : hqlParams.entrySet()) {
				query.setParameter(k.getKey(), k.getValue());
			}
			paginationParams.apply(query);
			List<RsLightConfig> configs = query.list();
			return configs;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the configs", e);
			throw new NetshotBadRequestException("Unable to fetch the configs",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
	}

	/**
	 * Gets the policies.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the policies
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the compliance policies",
		description = "Returns the list of compliance policies."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<Policy> getPolicies(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get policies.");
		Session session = Database.getSession(true);
		try {
			Query<Policy> query = session
				.createQuery("select distinct p from Policy p left join fetch p.targetGroups", Policy.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the policies.", e);
			throw new NetshotBadRequestException("Unable to fetch the policies",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightRule.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	@NoArgsConstructor
	public static class RsLightRule {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name;

		/** The rule type (simple class name, e.g. TextRule, JavaScriptRule, PythonRule). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type;

		/** Whether the rule is enabled. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean enabled;

		/** The ID of the owning policy. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long policyId;

		public RsLightRule(long id, String name, Class<? extends Rule> type, boolean enabled, long policyId) {
			this.id = id;
			this.name = name;
			this.type = type.getSimpleName();
			this.enabled = enabled;
			this.policyId = policyId;
		}
	}

	/**
	 * Gets all the compliance rules, across all policies, with minimal details.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/rules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the compliance rules",
		description = "Retrieves the list of compliance rules, across all policies, with minimal details."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightRule> getRules(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get rules.");
		Session session = Database.getSession(true);
		try {
			String hqlQuery = "select new RsLightRule(r.id, r.name, type(r), r.enabled, r.policy.id) from Rule r";
			Query<RsLightRule> query = session.createQuery(hqlQuery, RsLightRule.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotBadRequestException("Unable to fetch the rules",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the rules of a policy.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the policy rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies/{id}/rules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the compliance rules of a policy",
		description = "Returns the rules owned by a given compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<Rule> getPolicyRules(
		@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Policy ID") Long id) throws WebApplicationException {
		log.debug("REST request, get rules for policy {}.", id);
		Session session = Database.getSession(true);
		try {
			Policy policy = session.get(Policy.class, id);
			if (policy == null) {
				log.error("Invalid policy.");
				throw new NetshotBadRequestException("Invalid policy",
					ErrorCode.NETSHOT_INVALID_POLICY);
			}
			Query<Rule> query = session
				.createQuery("from Rule r where r.policy.id = :pid", Rule.class)
				.setParameter("pid", id);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotBadRequestException("Unable to fetch the rules",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets a specific rule of a specific policy.
	 *
	 * @param pid the policy id
	 * @param id the rule id
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies/{pid}/rules/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a specific compliance rule of a specific policy",
		description = "Returns the specific rule owned by a given compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public Rule getPolicyRule(
		@PathParam("pid") @Parameter(description = "Policy ID") Long pid,
		@PathParam("id") @Parameter(description = "Rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, get rule {}, policy {}.", id, pid);
		Session session = Database.getSession(true);
		try {
			Policy policy = session.get(Policy.class, pid);
			if (policy == null) {
				log.error("Invalid policy.");
				throw new NetshotBadRequestException("Unknown policy",
					ErrorCode.NETSHOT_POLICY_NOT_FOUND);
			}
			Rule rule = session
				.createQuery("from Rule r where r.policy.id = :pid and r.id = :rid", Rule.class)
				.setParameter("pid", pid)
				.setParameter("rid", id)
				.uniqueResult();
			if (rule == null) {
				log.warn("Unable to find the rule object.");
				throw new NetshotBadRequestException("Unknown rule",
					ErrorCode.NETSHOT_RULE_NOT_FOUND);
			}
			return rule;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the rule.", e);
			throw new NetshotBadRequestException("Unable to fetch the rule",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsPolicy.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsPolicy {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name = "";

		/** The groups. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Set<Long> targetGroups = new HashSet<>();
	}

	/**
	 * Adds the policy.
	 *
	 * @param rsPolicy the rs policy
	 * @return the policy
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/policies")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a compliance policy",
		description = "Creates a compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public Policy addPolicy(RsPolicy rsPolicy) throws WebApplicationException {
		log.debug("REST request, add policy.");
		String name = rsPolicy.getName().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty policy name.");
			throw new NetshotBadRequestException("Invalid policy name.",
				ErrorCode.NETSHOT_INVALID_POLICY_NAME);
		}
		Policy policy;
		Session session = Database.getSession();
		try {
			session.beginTransaction();

			Set<DeviceGroup> groups = new HashSet<>();
			for (Long groupId : rsPolicy.getTargetGroups()) {
				DeviceGroup group = session.get(DeviceGroup.class, groupId);
				groups.add(group);
			}

			policy = new Policy(name, groups);

			session.persist(policy);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", policy);
			this.suggestReturnCode(Response.Status.CREATED);
			return policy;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The posted group doesn't exist", e);
			throw new NetshotBadRequestException(
				"Invalid group",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new policy.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A policy with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_POLICY);
			}
			throw new NetshotBadRequestException(
				"Unable to add the policy to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete policy.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/policies/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a compliance policy",
		description = "Removes a given compliance policy, by ID"
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deletePolicy(@PathParam("id") @Parameter(description = "Policy ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete policy {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Policy policy = session.get(Policy.class, id);
			if (policy == null) {
				log.info("No such policy of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(policy);
			session.getTransaction().commit();
			AAA_LOG.info("Policy of ID {} has been deleted.", policy.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the policy {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the policy",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Sets the policy.
	 *
	 * @param id the id
	 * @param rsPolicy the rs policy
	 * @return the policy
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/policies/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a compliance policy",
		description = "Edits a compliance policy, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public Policy setPolicy(@PathParam("id") @Parameter(description = "Policy ID") Long id, RsPolicy rsPolicy)
		throws WebApplicationException {
		log.debug("REST request, edit policy {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Policy policy = session.get(Policy.class, id);
			if (policy == null) {
				log.error("Unable to find the policy {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this policy.",
					ErrorCode.NETSHOT_INVALID_POLICY);
			}

			String name = rsPolicy.getName().trim();
			if (name.isEmpty()) {
				log.warn("User posted an empty policy name.");
				throw new NetshotBadRequestException("Invalid policy name.",
					ErrorCode.NETSHOT_INVALID_POLICY_NAME);
			}
			policy.setName(name);

			// Remove orphan check results
			for (DeviceGroup group : policy.getTargetGroups()) {
				if (!rsPolicy.getTargetGroups().contains(group.getId())) {
					session.createMutationQuery(
						"delete CheckResult cr where cr.key.rule in (select r from Rule r where r.policy.id = :policyId) "
							+ "and cr.key.device in (select dm.key.device from DeviceGroup g join g.cachedMemberships dm where g.id = :groupId)")
						.setParameter("policyId", policy.getId())
						.setParameter("groupId", group.getId())
						.executeUpdate();
				}
			}
			policy.getTargetGroups().clear();
			for (Long groupId : rsPolicy.getTargetGroups()) {
				DeviceGroup group = session.get(DeviceGroup.class, groupId);
				policy.getTargetGroups().add(group);
			}

			session.merge(policy);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited.", policy);
			return policy;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("Unable to find a group to be assigned to the policy {}.", id, e);
			throw new NetshotBadRequestException(
				"Unable to find the group.",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to save the policy {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A policy with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_POLICY);
			}
			throw new NetshotBadRequestException("Unable to save the policy.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}


	/**
	 * The Class RsRule.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsRule {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name;

		/** The type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type = "";

		/** The script. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String script;

		/** The policy. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long policy;

		/** The enabled. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean enabled;

		/** The exemptions. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Map<Long, Date> exemptions = new HashMap<>();

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String text;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Boolean regExp;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String context;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String driver;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String field;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Boolean anyBlock;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Boolean matchAll;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Boolean invert;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Boolean normalize;
	}

	/**
	 * Adds a compliance rule.
	 *
	 * @param rsRule the rs rule
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/rules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a compliance rule",
		description = "Creates a compliance rule. The associated policy must already exist."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public Rule addRule(RsRule rsRule) throws WebApplicationException {
		log.debug("REST request, add rule.");
		if (rsRule.getName() == null || rsRule.getName().trim().isEmpty()) {
			log.warn("User posted an empty rule name.");
			throw new NetshotBadRequestException("Invalid rule name.",
				ErrorCode.NETSHOT_INVALID_RULE_NAME);
		}
		String name = rsRule.getName().trim();

		Session session = Database.getSession();
		try {
			session.beginTransaction();

			Policy policy = session.get(Policy.class, rsRule.getPolicy());
			if (policy == null) {
				log.error("The policy of ID {} doesn't exist.", rsRule.getPolicy());
				throw new NetshotBadRequestException(
					"Invalid policy.",
					ErrorCode.NETSHOT_INVALID_POLICY);
			}

			Rule rule;
			if ("TextRule".equals(rsRule.getType())) {
				TextRule textRule = new TextRule(name, policy);
				rule = textRule;
				if (rsRule.getText() != null) {
					textRule.setText(rsRule.getText());
				}
				if (rsRule.getRegExp() != null) {
					textRule.setRegExp(rsRule.getRegExp());
				}
				if (rsRule.getContext() != null) {
					textRule.setContext(rsRule.getContext());
				}
				if (rsRule.getField() != null) {
					textRule.setField(rsRule.getField());
				}
				if (rsRule.getDriver() != null) {
					textRule.setDeviceDriver(rsRule.getDriver());
				}
				if (rsRule.getInvert() != null) {
					textRule.setInvert(rsRule.getInvert());
				}
				if (rsRule.getMatchAll() != null) {
					textRule.setMatchAll(rsRule.getMatchAll());
				}
				if (rsRule.getAnyBlock() != null) {
					textRule.setAnyBlock(rsRule.getAnyBlock());
				}
				if (rsRule.getNormalize() != null) {
					textRule.setNormalize(rsRule.getNormalize());
				}
			}
			else if ("JavaScriptRule".equals(rsRule.getType())) {
				JavaScriptRule jsRule = new JavaScriptRule(name, policy);
				rule = jsRule;
				if (rsRule.getScript() != null) {
					String script = rsRule.getScript().trim();
					jsRule.setScript(script);
				}
			}
			else if ("PythonRule".equals(rsRule.getType())) {
				PythonRule pyRule = new PythonRule(name, policy);
				rule = pyRule;
				if (rsRule.getScript() != null) {
					String script = rsRule.getScript().trim();
					pyRule.setScript(script);
				}
			}
			else {
				throw new NetshotBadRequestException(
					"Invalid rule type.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}

			session.persist(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", rule);
			this.suggestReturnCode(Response.Status.CREATED);
			return rule;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A rule with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_RULE);
			}
			throw new NetshotBadRequestException(
				"Unable to add the rule to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (NetshotBadRequestException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Sets the rule.
	 *
	 * @param id the id
	 * @param rsRule the rs rule
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/rules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a compliance rule",
		description = "Edits a compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public Rule setRule(@PathParam("id") @Parameter(description = "Rule ID") Long id, RsRule rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit rule {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Rule rule = session.get(Rule.class, id);
			if (rule == null) {
				log.error("Unable to find the rule {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this rule.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}

			if (rsRule.getName() != null) {
				String name = rsRule.getName().trim();
				if (name.isEmpty()) {
					log.warn("User posted an empty rule name.");
					throw new NetshotBadRequestException("Invalid rule name.",
						ErrorCode.NETSHOT_INVALID_RULE_NAME);
				}
				rule.setName(name);
			}
			rule.setEnabled(rsRule.isEnabled());

			Map<Long, Date> postedExemptions = new HashMap<>();
			postedExemptions.putAll(rsRule.getExemptions());
			Iterator<Exemption> i = rule.getExemptions().iterator();
			while (i.hasNext()) {
				Exemption exemption = i.next();
				Long deviceId = exemption.getDevice().getId();
				if (postedExemptions.containsKey(deviceId)) {
					exemption.setExpirationDate(postedExemptions.get(deviceId));
					postedExemptions.remove(deviceId);
				}
				else {
					i.remove();
				}
			}
			for (Map.Entry<Long, Date> postedExemption : postedExemptions.entrySet()) {
				Device device = session.get(Device.class, postedExemption.getKey());
				if (device == null) {
					log.warn("Unknown device of ID {}", postedExemption.getKey());
					throw new NetshotBadRequestException(
						"Unknown device of ID %d.".formatted(postedExemption.getKey()),
						ErrorCode.NETSHOT_INVALID_DEVICE);
				}
				Exemption exemption = new Exemption(rule, device, postedExemption.getValue());
				rule.addExemption(exemption);
			}

			if (rule instanceof JavaScriptRule jsRule) {
				if (rsRule.getScript() != null) {
					String script = rsRule.getScript().trim();
					jsRule.setScript(script);
				}
			}
			else if (rule instanceof PythonRule pyRule) {
				if (rsRule.getScript() != null) {
					String script = rsRule.getScript().trim();
					pyRule.setScript(script);
				}
			}
			else if (rule instanceof TextRule textRule) {
				if (rsRule.getText() != null) {
					textRule.setText(rsRule.getText());
				}
				if (rsRule.getRegExp() != null) {
					textRule.setRegExp(rsRule.getRegExp());
				}
				if (rsRule.getContext() != null) {
					textRule.setContext(rsRule.getContext());
				}
				if (rsRule.getField() != null) {
					textRule.setField(rsRule.getField());
				}
				if (rsRule.getDriver() != null) {
					textRule.setDeviceDriver(rsRule.getDriver());
				}
				if (rsRule.getInvert() != null) {
					textRule.setInvert(rsRule.getInvert());
				}
				if (rsRule.getMatchAll() != null) {
					textRule.setMatchAll(rsRule.getMatchAll());
				}
				if (rsRule.getAnyBlock() != null) {
					textRule.setAnyBlock(rsRule.getAnyBlock());
				}
				if (rsRule.getNormalize() != null) {
					textRule.setNormalize(rsRule.getNormalize());
				}
			}

			session.merge(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited.", rule);
			return rule;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A rule with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_RULE);
			}
			throw new NetshotBadRequestException(
				"Unable to save the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/rules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a compliance rule",
		description = "Removes a compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteRule(@PathParam("id") @Parameter(description = "Rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete rule {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Rule rule = session.get(Rule.class, id);
			if (rule == null) {
				log.info("No such rule of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(rule);
			session.getTransaction().commit();
			AAA_LOG.info("Rule of ID {} has been deleted.", rule.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsJsRuleTest.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsRuleTest extends RsRule {

		/** The device. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long device;

	}

	/**
	 * The Class RsRuleTestResult.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsRuleTestResult {

		/** The result. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private CheckResult.ResultOption result;

		/** The script error. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String scriptError;

		/** Result comment. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String comment;

	}

	/**
	 * Test rule.
	 *
	 * @param rsRule the rs rule
	 * @return the rule test result
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/rules/test")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Test a compliance rule",
		description = "Test a compliance rule against a given device, in dry run mode."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public RsRuleTestResult testRule(RsRuleTest rsRule) throws WebApplicationException {
		log.debug("REST request, rule test.");
		Device device;
		Session session = Database.getSession(true);
		RsRuleTestResult result = new RsRuleTestResult();
		try {
			device = session
				.createQuery("from Device d join fetch d.lastConfig where d.id = :id", Device.class)
				.setParameter("id", rsRule.getDevice())
				.uniqueResult();
			if (device == null) {
				log.warn("Unable to find the device {}.", rsRule.getDevice());
				result.setResult(CheckResult.ResultOption.NOTAPPLICABLE);
				result.setComment("Unable to find the device");
				return result;
			}

			Rule rule;

			if ("TextRule".equals(rsRule.getType())) {
				TextRule txRule = new TextRule("TEST", null);
				txRule.setDeviceDriver(rsRule.getDriver());
				txRule.setField(rsRule.getField());
				txRule.setInvert(rsRule.getInvert());
				txRule.setContext(rsRule.getContext());
				txRule.setRegExp(rsRule.getRegExp());
				txRule.setText(rsRule.getText());
				txRule.setAnyBlock(rsRule.getAnyBlock());
				txRule.setMatchAll(rsRule.getMatchAll());
				txRule.setNormalize(rsRule.getNormalize());
				rule = txRule;
			}
			else if ("JavaScriptRule".equals(rsRule.getType())) {
				JavaScriptRule jsRule = new JavaScriptRule("TEST", null);
				jsRule.setScript(rsRule.getScript());
				rule = jsRule;
			}
			else if ("PythonRule".equals(rsRule.getType())) {
				PythonRule pyRule = new PythonRule("TEST", null);
				pyRule.setScript(rsRule.getScript());
				rule = pyRule;
			}
			else {
				log.warn("Invalid rule type {}.", rsRule.getType());
				throw new NetshotBadRequestException("Invalid rule type",
					ErrorCode.NETSHOT_INVALID_RULE);
			}


			StringBuilder taskLog = new StringBuilder();
			TaskContext taskContext = new TaskContext() {
				@Override
				public void log(Level level, String message, Object... params) {
					taskLog.append("[%s] ".formatted(level.toString()));
					taskLog.append(
						MessageFormatter.arrayFormat(message, params).getMessage());
					taskLog.append("\n");
				}

				@Override
				public String getIdentifier() {
					return "RuleTest";
				}
			};

			rule.setEnabled(true);
			CheckResult check = rule.check(device, session, taskContext);
			result.setResult(check.getResult());
			result.setScriptError(taskLog.toString());
			result.setComment(check.getComment());

			return result;
		}
		catch (NetshotBadRequestException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Unable to retrieve the device {}.", rsRule.getDevice(), e);
			throw new NetshotBadRequestException("Unable to retrieve the device.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightExemptedDevice.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsLightExemptedDevice extends RsLightDevice {

		/** The expiration date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date expirationDate;

		public RsLightExemptedDevice(long id, String name, String family, String mgmtAddress, Status status,
			String driver, Boolean eol, Boolean eos, Boolean configCompliant, ConformanceLevel softwareLevel,
			Device.NetworkClass networkClass, Date expirationDate) {
			super(id, name, family, mgmtAddress, status, driver, eol, eos, configCompliant, softwareLevel, networkClass);
			this.expirationDate = expirationDate;
		}

	}

	/**
	 * Gets the exempted devices.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the exempted devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/rule/{id}/exempteddevices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the exempted devices of a compliance rule",
		description = "Returns the list of devices which have an exemption against a given compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightExemptedDevice> getExemptedDevices(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Rule ID") Long id) throws WebApplicationException {
		log.debug("REST request, get exemptions for rule {}.", id);
		Session session = Database.getSession(true);
		try {
			Query<RsLightExemptedDevice> query = session.createQuery(
				"select new RsLightExemptedDevice("
					+ "d.id, "
					+ "d.name, "
					+ "d.family, "
					+ "d.mgmtAddress, "
					+ "d.status, "
					+ "d.driver, "
					+ "case when (d.eolDate < current_date()) then true else false end,  "
					+ "case when (d.eosDate < current_date()) then true else false end, "
					+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
					+ "d.softwareLevel, "
					+ "d.networkClass, "
					+ "e.expirationDate as expirationDate "
					+ ") from Exemption e join e.key.device d where e.key.rule.id = :id",
				RsLightExemptedDevice.class)
				.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
				.setParameter("id", id);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the exemptions.", e);
			throw new NetshotBadRequestException("Unable to fetch the exemptions",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsDeviceRule.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsDeviceRule {

		/** The rule id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long ruleId;

		/** The rule name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String ruleName = "";

		/** The policy id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long policyId;

		/** The policy name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String policyName = "";

		/** The result. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private CheckResult.ResultOption result;

		/** The comment. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String comment = "";

		/** The check date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date checkDate;

		/** The expiration date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date expirationDate;
	}

	/**
	 * Gets the device compliance results.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device compliance
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/complianceresults")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the compliance results for a device",
		description = "Returns the compliance results for a give device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsDeviceRule> getDeviceComplianceResults(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Device ID") Long id) throws WebApplicationException {
		log.debug("REST request, get compliance results for device {}.", id);
		Session session = Database.getSession(true);
		try {
			Query<RsDeviceRule> query = session.createQuery(
				"select new RsDeviceRule("
					+ "r.id as ruleId, "
					+ "r.name as ruleName, "
					+ "p.id as policyId, "
					+ "p.name as policyName, "
					+ "cr.result as result, "
					+ "cr.comment as comment, "
					+ "cr.checkDate as checkDate, "
					+ "e.expirationDate as expirationDate "
					+ ") from Rule r "
					+ "join r.policy p join p.targetGroups g join g.cachedMemberships dm with dm.key.device.id = :id "
					+ "left join CheckResult cr with cr.key.rule.id = r.id and cr.key.device.id = :id "
					+ "left join r.exemptions e with e.key.device.id = :id",
				RsDeviceRule.class)
				.setParameter("id", id);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the rules.", e);
			throw new NetshotBadRequestException("Unable to fetch the rules",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsConfigChangeNumberByDateStat.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsConfigChangeNumberByDateStat {

		/** The change count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long changeCount;

		/** The change day. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date changeDay;
	}

	/**
	 * Gets the last7 days changes by day stats.
	 *
	 * @param jsTimeZone the time zone to base dates on
	 * @return the last7 days changes by day stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/last7dayschangesbyday")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the number of configuration changes for the last 7 days",
		description = "Returns the number of device configuration changes per day, for the last 7 days."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Deprecated
	public List<RsConfigChangeNumberByDateStat> getLast7DaysChangesByDayStats(@QueryParam("tz") String jsTimeZone) throws WebApplicationException {
		log.debug("REST request, get last 7 day changes by day stats.");
		Session session = Database.getSession(true);

		TimeZone timeZone = TimeZone.getDefault();
		try {
			timeZone = TimeZone.getTimeZone(jsTimeZone);
		}
		catch (Exception e) {
			log.warn("Unable to parse timezone '{}'", jsTimeZone);
		}
		Calendar today = Calendar.getInstance(timeZone);
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		try {
			List<RsConfigChangeNumberByDateStat> stats = new ArrayList<>();
			for (int d = 7; d > 0; d--) {
				Calendar dayStart = (Calendar) today.clone();
				Calendar dayEnd = (Calendar) today.clone();
				dayStart.add(Calendar.DATE, -d + 1);
				dayEnd.add(Calendar.DATE, -d + 2);
				Long changeCount = session
					.createQuery(
						"select count(*) from Config c where c.changeDate >= :dayStart and c.changeDate < :dayEnd",
						Long.class)
					.setParameter("dayStart", dayStart.getTime())
					.setParameter("dayEnd", dayEnd.getTime())
					.uniqueResult();
				stats.add(new RsConfigChangeNumberByDateStat(changeCount == null ? 0 : changeCount, dayStart.getTime()));
			}
			return stats;
		}
		catch (HibernateException e) {
			log.error("Unable to get the stats.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsGroupConfigComplianceStat.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsGroupConfigComplianceStat {

		/** The group id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long groupId;

		/** The group name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String groupName;

		/** The group folder. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String groupFolder;

		/** The compliant device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long compliantDeviceCount;

		/** The device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long deviceCount;

	}

	/**
	 * Gets the group config compliance stats.
	 *
	 * @param domains the domains
	 * @param deviceGroups the device groups
	 * @param policies the policies
	 * @return the group config compliance stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupconfigcompliancestats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the compliance status of a device group",
		description = "Returns the compliance status of a given device group, by ID."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsGroupConfigComplianceStat> getGroupConfigComplianceStats(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies)
		throws WebApplicationException {
		log.debug("REST request, group config compliance stats.");
		Session session = Database.getSession(true);
		try {
			String domainFilter = "";
			if (domains.size() > 0) {
				domainFilter = " d.mgmtDomain.id in (:domainIds) and";
			}
			String ccrFilter = "";
			if (policies.size() > 0) {
				ccrFilter = " rule.policy.id in (:policyIds) and";
			}
			String groupFilter = "";
			if (deviceGroups.size() > 0) {
				groupFilter = " g.id in (:groupIds) and";
			}

			Query<RsGroupConfigComplianceStat> query = session
				.createQuery(
					"select new RsGroupConfigComplianceStat("
						+ "g.id as groupId, "
						+ "g.name as groupName, "
						+ "g.folder as groupFolder, "
						+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where" + domainFilter + " d.status = :enabled and "
						+ "(select count(ccr.result) from d.complianceCheckResults ccr join ccr.key.rule rule where"
						+ ccrFilter + " ccr.result = :nonConforming) = 0) as compliantDeviceCount, "
						+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where" + domainFilter + " d.status = :enabled) as deviceCount "
						+ ") from DeviceGroup g where" + groupFilter + " g.hiddenFromReports <> true",
					RsGroupConfigComplianceStat.class)
				.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
				.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				query.setParameterList("domainIds", domains);
			}
			if (policies.size() > 0) {
				query.setParameterList("policyIds", policies);
			}
			if (deviceGroups.size() > 0) {
				query.setParameterList("groupIds", deviceGroups);
			}
			List<RsGroupConfigComplianceStat> stats = query.list();
			return stats;
		}
		catch (HibernateException e) {
			log.error("Unable to get the stats.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@AllArgsConstructor
	@NoArgsConstructor
	public abstract static class RsHardwareSupportStat {

		/** Date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date eoxDate;

		/** Number of devices which changed status that day. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long deviceCount;
	}

	@XmlType
	@AllArgsConstructor
	public static class RsHardwareSupportEoSStat extends RsHardwareSupportStat {

		public RsHardwareSupportEoSStat(Date eoxDate, long deviceCount) {
			super(eoxDate, deviceCount);
		}
	}

	@XmlType
	@AllArgsConstructor
	public static class RsHardwareSupportEoLStat extends RsHardwareSupportStat {

		public RsHardwareSupportEoLStat(Date eoxDate, long deviceCount) {
			super(eoxDate, deviceCount);
		}
	}

	@GET
	@Path("/reports/hardwaresupportstats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the global hardware support status",
		description = "Returns the global hardware support status, i.e. a list of End-of-Life and End-of-Sale dates with the corresponding device count; "
		+ "optionally filtered by domain or group."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsHardwareSupportStat> getHardwareSupportStats(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups)
		throws WebApplicationException {
		log.debug("REST request, hardware support stats.");
		Session session = Database.getSession(true);
		try {
			String domainFilter = domains.size() > 0 ? " and d.mgmtDomain.id in (:domainIds)" : "";
			String groupFilter = deviceGroups.size() > 0
				? " and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))"
				: "";

			Query<RsHardwareSupportEoSStat> eosQuery = session
				.createQuery(
					"select new RsHardwareSupportEoSStat(d.eosDate AS eoxDate, count(d) as deviceCount) "
						+ "from Device d where d.status = :enabled" + domainFilter + groupFilter + " group by d.eosDate",
					RsHardwareSupportEoSStat.class)
				.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				eosQuery.setParameterList("domainIds", domains);
			}
			if (deviceGroups.size() > 0) {
				eosQuery.setParameterList("groupIds", deviceGroups);
			}
			List<RsHardwareSupportEoSStat> eosStats = eosQuery.list();
			Query<RsHardwareSupportEoLStat> eolQuery = session
				.createQuery(
					"select new RsHardwareSupportEoLStat(d.eolDate AS eoxDate, count(d) as deviceCount) "
						+ "from Device d where d.status = :enabled" + domainFilter + groupFilter + " group by d.eolDate",
					RsHardwareSupportEoLStat.class)
				.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				eolQuery.setParameterList("domainIds", domains);
			}
			if (deviceGroups.size() > 0) {
				eolQuery.setParameterList("groupIds", deviceGroups);
			}
			List<RsHardwareSupportEoLStat> eolStats = eolQuery.list();
			List<RsHardwareSupportStat> stats = new ArrayList<>();
			stats.addAll(eosStats);
			stats.addAll(eolStats);
			return stats;
		}
		catch (HibernateException e) {
			log.error("Unable to get the stats.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsGroupSoftwareComplianceStat.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsGroupSoftwareComplianceStat {

		/** The group id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long groupId;

		/** The group name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String groupName;

		/** The gold device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long goldDeviceCount;

		/** The silver device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long silverDeviceCount;

		/** The bronze device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long bronzeDeviceCount;

		/** The device count. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long deviceCount;

	}

	/**
	 * Gets the group software compliance stats.
	 *
	 * @param domains the domains
	 * @return the group software compliance stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupsoftwarecompliancestats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the global software compliance status",
		description = "Returns the software compliance status of devices, optionally filtered by a list of device domains."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsGroupSoftwareComplianceStat> getGroupSoftwareComplianceStats(
			@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains)
		throws WebApplicationException {
		log.debug("REST request, group software compliance stats.");
		Session session = Database.getSession(true);
		try {
			String domainFilter = "";
			if (domains.size() > 0) {
				domainFilter = " and d.mgmtDomain.id in (:domainIds)";
			}
			Query<RsGroupSoftwareComplianceStat> query = session
				.createQuery("select new RsGroupSoftwareComplianceStat("
					+ "g.id as groupId, "
					+ "g.name as groupName, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
					+ "d.status = :enabled and d.softwareLevel = :gold" + domainFilter + ") as goldDeviceCount, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
					+ "d.status = :enabled and d.softwareLevel = :silver" + domainFilter + ") as silverDeviceCount, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
					+ "d.status = :enabled and d.softwareLevel = :bronze" + domainFilter + ") as bronzeDeviceCount, "
					+ "(select count(d) from g.cachedMemberships dm join dm.key.device d where "
					+ "d.status = :enabled" + domainFilter + ") as deviceCount "
					+ ") from DeviceGroup g where g.hiddenFromReports <> true",
					RsGroupSoftwareComplianceStat.class)
				.setParameter("gold", ConformanceLevel.GOLD)
				.setParameter("silver", ConformanceLevel.SILVER)
				.setParameter("bronze", ConformanceLevel.BRONZE)
				.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				query.setParameterList("domainIds", domains);
			}
			List<RsGroupSoftwareComplianceStat> stats = query.list();
			return stats;
		}
		catch (HibernateException e) {
			log.error("Unable to get the stats.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightPolicyRuleDevice.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsLightPolicyRuleDevice extends RsLightDevice {

		/** The policy id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long policyId;

		/** The policy name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String policyName;

		/** The rule id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long ruleId;

		/** The rule name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String ruleName;

		/** The check date. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date checkDate;

		/** The result. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private ResultOption result;

		public RsLightPolicyRuleDevice(long id, String name, String family, String mgmtAddress, Status status,
			String driver, Boolean eol, Boolean eos, Boolean configCompliant, ConformanceLevel softwareLevel,
			Device.NetworkClass networkClass, long policyId, String policyName, long ruleId, String ruleName,
			Date checkDate, ResultOption result) {
			super(id, name, family, mgmtAddress, status, driver, eol, eos, configCompliant, softwareLevel, networkClass);
			this.policyId = policyId;
			this.policyName = policyName;
			this.ruleId = ruleId;
			this.ruleName = ruleName;
			this.checkDate = checkDate;
			this.result = result;
		}

	}


	/**
	 * Gets the configuration compliance results.
	 *
	 * @param domains optional filter on device domain
	 * @param groups optional filter on device groups
	 * @param policies optional filter on compliance policy
	 * @param results optional filter on compliance result
	 * @return the config compliance results
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/configcompliancedevicestatuses")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the configuration compliance status of devices",
		description = "Returns the configuration compliance status of devices; optionally filtered by domain, group, policy or compliance level."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightPolicyRuleDevice> getConfigComplianceDeviceStatuses(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> groups,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies,
		@QueryParam("result") @Parameter(description = "Filter on given result(s)") Set<CheckResult.ResultOption> results)
		throws WebApplicationException {

		log.debug("REST request, config compliant device statuses.");
		Session session = Database.getSession(true);
		try {
			String hqlQuery = "select new RsLightPolicyRuleDevice("
				+ "d.id, "
				+ "d.name, "
				+ "d.family, "
				+ "d.mgmtAddress, "
				+ "d.status, "
				+ "d.driver, "
				+ "case when (d.eolDate < current_date()) then true else false end,  "
				+ "case when (d.eosDate < current_date()) then true else false end, "
				+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
				+ "d.softwareLevel, "
				+ "d.networkClass, "
				+ "p.id as policyId, "
				+ "p.name as policyName, "
				+ "r.id as ruleId, "
				+ "r.name as ruleName, "
				+ "ccr.checkDate as checkDate, "
				+ "ccr.result as result "
				+ ") from Device d "
				+ "left join d.groupMemberships gm join d.complianceCheckResults ccr join ccr.key.rule r join r.policy p "
				+ "where d.status = :enabled";
			if (domains.size() > 0) {
				hqlQuery += " and d.mgmtDomain.id in (:domainIds)";
			}
			if (groups.size() > 0) {
				hqlQuery += " and gm.key.group.id in (:groupIds)";
			}
			if (policies.size() > 0) {
				hqlQuery += " and p.id in (:policyIds)";
			}
			if (results.size() > 0) {
				hqlQuery += " and ccr.result in (:results)";
			}
			Query<RsLightPolicyRuleDevice> query = session.createQuery(hqlQuery, RsLightPolicyRuleDevice.class);
			query.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
			query.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				query.setParameterList("domainIds", domains);
			}
			if (groups.size() > 0) {
				query.setParameterList("groupIds", groups);
			}
			if (policies.size() > 0) {
				query.setParameterList("policyIds", policies);
			}
			if (results.size() > 0) {
				query.setParameterList("results", results);
			}
			List<RsLightPolicyRuleDevice> devices = query.list();
			return devices;
		}
		catch (HibernateException e) {
			log.error("Unable to get the devices.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the group config non compliant devices.
	 *
	 * @param id the id
	 * @param domains the domains
	 * @param policies the policies
	 * @return the group config non compliant devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupconfignoncompliantdevices/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the non compliant devices of a group",
		description = "Returns the list of non-compliance devices part of a given group, optionally filtered by domains and policies"
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightPolicyRuleDevice> getGroupConfigNonCompliantDevices(
		@PathParam("id") @Parameter(description = "Group ID") Long id,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies)
		throws WebApplicationException {
		log.debug("REST request, group config non compliant devices.");
		Session session = Database.getSession(true);
		try {
			String domainFilter = "";
			if (domains.size() > 0) {
				domainFilter = " and d.mgmtDomain.id in (:domainIds)";
			}
			String policyFilter = "";
			if (policies.size() > 0) {
				policyFilter = " and p.id in (:policyIds)";
			}
			Query<RsLightPolicyRuleDevice> query = session
				.createQuery(
					"select new RsLightPolicyRuleDevice("
						+ "d.id, "
						+ "d.name, "
						+ "d.family, "
						+ "d.mgmtAddress, "
						+ "d.status, "
						+ "d.driver, "
						+ "case when (d.eolDate < current_date()) then true else false end,  "
						+ "case when (d.eosDate < current_date()) then true else false end, "
						+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
						+ "d.softwareLevel, "
						+ "d.networkClass, "
						+ "p.id as policyId, "
						+ "p.name as policyName, "
						+ "r.id as ruleId, "
						+ "r.name as ruleName, "
						+ "ccr.checkDate as checkDate, "
						+ "ccr.result as result"
						+ ") from Device d "
						+ "join d.groupMemberships gm join d.complianceCheckResults ccr join ccr.key.rule r join r.policy p "
						+ "where gm.key.group.id = :id and ccr.result = :nonConforming and d.status = :enabled" + domainFilter + policyFilter,
					RsLightPolicyRuleDevice.class)
				.setParameter("id", id)
				.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
				.setParameter("enabled", Device.Status.INPRODUCTION);
			if (domains.size() > 0) {
				query.setParameterList("domainIds", domains);
			}
			if (policies.size() > 0) {
				query.setParameterList("policyIds", policies);
			}
			List<RsLightPolicyRuleDevice> devices = query.list();
			return devices;
		}
		catch (HibernateException e) {
			log.error("Unable to get the devices.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@GET
	@Path("/reports/hardwaresupportdevices/{type}/{date}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the End-of-Life or End-of-Sale devices matching a date.",
		description = "Returns the list of devices getting End-of-Life (type 'eol') or End-of-Sale (type 'eos') at the given date (or never if 'date' is not given); "
		+ "optionally filtered by domain or group."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightDevice> getHardwareStatusDevices(
		@PathParam("type") @Parameter(description = "eos (end-of-sale) or eol (end-of-life), type of date") String type,
		@PathParam("date") @Parameter(description = "EoX date to filter on") Long date,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups) throws WebApplicationException {
		log.debug("REST request, EoX devices by type and date.");
		if (!"eol".equals(type) && !"eos".equals(type)) {
			log.error("Invalid requested EoX type.");
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		Date eoxDate = new Date(date);
		Session session = Database.getSession(true);
		try {
			String domainFilter = domains.size() > 0 ? " and d.mgmtDomain.id in (:domainIds)" : "";
			String groupFilter = deviceGroups.size() > 0
				? " and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))"
				: "";
			if (date == 0) {
				Query<RsLightDevice> query = session
					.createQuery(
						"select new RsLightDevice("
							+ "d.id, "
							+ "d.name, "
							+ "d.family, "
							+ "d.mgmtAddress, "
							+ "d.status, "
							+ "d.driver, "
							+ "case when (d.eolDate < current_date()) then true else false end,  "
							+ "case when (d.eosDate < current_date()) then true else false end, "
							+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
							+ "d.softwareLevel, "
							+ "d.networkClass "
							+ ") from Device d where d." + type + "Date is null and d.status = :enabled" + domainFilter + groupFilter,
						RsLightDevice.class)
					.setParameter("enabled", Device.Status.INPRODUCTION)
					.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
				if (domains.size() > 0) {
					query.setParameterList("domainIds", domains);
				}
				if (deviceGroups.size() > 0) {
					query.setParameterList("groupIds", deviceGroups);
				}
				return query.list();
			}
			else {
				Query<RsLightDevice> query = session
					.createQuery(
						"select new RsLightDevice("
							+ "d.id, "
							+ "d.name, "
							+ "d.family, "
							+ "d.mgmtAddress, "
							+ "d.status, "
							+ "d.driver, "
							+ "case when (d.eolDate < current_date()) then true else false end,  "
							+ "case when (d.eosDate < current_date()) then true else false end, "
							+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
							+ "d.softwareLevel, "
							+ "d.networkClass "
							+ ") from Device d where date(d." + type + "Date) = :eoxDate and d.status = :enabled" + domainFilter + groupFilter,
						RsLightDevice.class)
					.setParameter("eoxDate", eoxDate)
					.setParameter("enabled", Device.Status.INPRODUCTION)
					.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
				if (domains.size() > 0) {
					query.setParameterList("domainIds", domains);
				}
				if (deviceGroups.size() > 0) {
					query.setParameterList("groupIds", deviceGroups);
				}
				return query.list();
			}
		}
		catch (HibernateException e) {
			log.error("Unable to get the devices.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the hardware rules.
	 *
	 * @return the harware rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/hardwarerules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the hardware compliance rules",
		description = "Returns the list of hardware compliance rules."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<HardwareRule> getHardwareRules() throws WebApplicationException {
		log.debug("REST request, hardware rules.");
		Session session = Database.getSession(true);
		try {
			List<HardwareRule> rules = session
				.createQuery("from HardwareRule r left join fetch r.targetGroup g", HardwareRule.class)
				.list();
			return rules;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the hardware rules.", e);
			throw new NetshotBadRequestException("Unable to fetch the hardware rules.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsHardwareRule.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsHardwareRule {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The group. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long group = -1;

		/** The device class name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String driver = "";

		/** The part number. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String partNumber = "";

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean partNumberRegExp;

		/** The family. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String family = "";

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean familyRegExp;

		@Getter(onMethod = @__({
			@XmlElement(nillable = true), @JsonView(DefaultView.class)
		}))
		@Setter
		private Date endOfSale;

		@Getter(onMethod = @__({
			@XmlElement(nillable = true), @JsonView(DefaultView.class)
		}))
		@Setter
		private Date endOfLife;
	}

	/**
	 * Adds an hardware rule.
	 *
	 * @param rsRule the rs rule
	 * @return the hardware rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/hardwarerules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add an hardware compliance rule",
		description = "Creates an hardware compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public HardwareRule addHardwareRule(RsHardwareRule rsRule) throws WebApplicationException {
		log.debug("REST request, add hardware rule.");

		HardwareRule rule;
		Session session = Database.getSession();
		try {
			session.beginTransaction();

			DeviceGroup group = null;
			if (rsRule.getGroup() != -1) {
				group = session.get(DeviceGroup.class, rsRule.getGroup());
			}

			String driver = rsRule.getDriver();
			if (DeviceDriver.getDriverByName(driver) == null) {
				driver = null;
			}

			rule = new HardwareRule(driver, group,
				rsRule.getFamily(), rsRule.isFamilyRegExp(), rsRule.getPartNumber(),
				rsRule.isPartNumberRegExp(), rsRule.getEndOfSale(), rsRule.getEndOfLife());

			session.persist(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created", rule);
			this.suggestReturnCode(Response.Status.CREATED);
			return rule;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The posted group doesn't exist", e);
			throw new NetshotBadRequestException(
				"Invalid group",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			throw new NetshotBadRequestException(
				"Unable to add the rule to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Delete hardware rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/hardwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove an hardware compliance rule",
		description = "Removes an hardware compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete hardware rule {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			HardwareRule rule = session.get(HardwareRule.class, id);
			if (rule == null) {
				log.info("No such hardware rule of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(rule);
			session.getTransaction().commit();
			AAA_LOG.info("Hardware rule of ID {} has been deleted", rule.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Sets the hardware rule.
	 *
	 * @param id the id
	 * @param rsRule the rs rule
	 * @return the hardware rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/hardwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update an hardware compliance rule",
		description = "Edits an hardware compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public HardwareRule setHardwareRule(
		@PathParam("id") @Parameter(description = "Hardware rule ID") Long id,
		RsHardwareRule rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit hardware rule {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			HardwareRule rule = session.get(HardwareRule.class, id);
			if (rule == null) {
				log.error("Unable to find the rule {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this rule.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}

			String driver = rsRule.getDriver();
			if (DeviceDriver.getDriverByName(driver) == null) {
				driver = null;
			}
			rule.setDriver(driver);

			DeviceGroup group = null;
			if (rsRule.getGroup() != -1) {
				group = session.get(DeviceGroup.class, rsRule.getGroup());
			}
			rule.setTargetGroup(group);

			rule.setFamily(rsRule.getFamily());
			rule.setFamilyRegExp(rsRule.isFamilyRegExp());
			rule.setEndOfLife(rsRule.getEndOfLife());
			rule.setEndOfSale(rsRule.getEndOfSale());
			rule.setPartNumber(rsRule.getPartNumber());
			rule.setPartNumberRegExp(rsRule.isPartNumberRegExp());

			session.merge(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", rule);
			return rule;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the rule.", e);
			throw new NetshotBadRequestException(
				"Unable to save the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the software rules.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the software rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the software compliance rules",
		description = "Returns the list of software compliance rules."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<SoftwareRule> getSoftwareRules(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, software rules.");
		Session session = Database.getSession(true);
		try {
			Query<SoftwareRule> query = session
				.createQuery("from SoftwareRule r left join fetch r.targetGroup g order by r.priority asc", SoftwareRule.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the software rules.", e);
			throw new NetshotBadRequestException("Unable to fetch the software rules.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsSoftwareRule.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsSoftwareRule {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The group. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long group = -1;

		/** The device class name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String driver = "";

		/** The version. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String version = "";

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean versionRegExp;

		/** The family. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String family = "";

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean familyRegExp;

		/** The part number. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String partNumber = "";

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean partNumberRegExp;

		/** The level. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private SoftwareRule.ConformanceLevel level = ConformanceLevel.GOLD;
	}

	/**
	 * Creates a software rule.
	 *
	 * @param rsRule the rs rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a software compliance rule",
		description = "Creates a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRule addSoftwareRule(RsSoftwareRule rsRule) throws WebApplicationException {
		log.debug("REST request, add software rule.");

		SoftwareRule rule;
		Session session = Database.getSession();
		try {
			session.beginTransaction();

			DeviceGroup group = null;
			if (rsRule.getGroup() != -1) {
				group = session.get(DeviceGroup.class, rsRule.getGroup());
			}

			String driver = rsRule.getDriver();
			if (DeviceDriver.getDriverByName(driver) == null) {
				driver = null;
			}

			rule = new SoftwareRule(Double.MAX_VALUE, group, driver,
				rsRule.getFamily(), rsRule.isFamilyRegExp(), rsRule.getVersion(),
				rsRule.isVersionRegExp(), rsRule.getPartNumber(), rsRule.isPartNumberRegExp(),
				rsRule.getLevel());

			session.persist(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created", rule);
			this.suggestReturnCode(Response.Status.CREATED);
			return rule;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The posted group doesn't exist", e);
			throw new NetshotBadRequestException(
				"Invalid group",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			throw new NetshotBadRequestException(
				"Unable to add the policy to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete software rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a software compliance rule",
		description = "Removes a software compliance rule, by ID"
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete software rule {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			SoftwareRule rule = session.get(SoftwareRule.class, id);
			if (rule == null) {
				log.info("No such software rule of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(rule);
			session.getTransaction().commit();
			AAA_LOG.info("Software rule of ID {} has been deleted", rule.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the rule {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Updates the software rule.
	 *
	 * @param id the id
	 * @param rsRule the rs rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a software compliance rule",
		description = "Edits a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRule setSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID") Long id,
		RsSoftwareRule rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit software rule {}.", id);
		Session session = Database.getSession();
		SoftwareRule rule = null;
		try {
			session.beginTransaction();
			rule = session.get(SoftwareRule.class, id);
			if (rule == null) {
				log.error("Unable to find the rule {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this rule.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}

			String driver = rsRule.getDriver();
			if (DeviceDriver.getDriverByName(driver) == null) {
				driver = null;
			}
			rule.setDriver(driver);

			DeviceGroup group = null;
			if (rsRule.getGroup() != -1) {
				group = session.get(DeviceGroup.class, rsRule.getGroup());
			}
			rule.setTargetGroup(group);

			rule.setFamily(rsRule.getFamily());
			rule.setFamilyRegExp(rsRule.isFamilyRegExp());
			rule.setVersion(rsRule.getVersion());
			rule.setVersionRegExp(rsRule.isVersionRegExp());
			rule.setPartNumber(rsRule.getPartNumber());
			rule.setPartNumberRegExp(rsRule.isPartNumberRegExp());
			rule.setLevel(rsRule.getLevel());

			session.merge(rule);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", rule);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the rule.", e);
			throw new NetshotBadRequestException(
				"Unable to save the rule.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
		return rule;
	}


	/**
	 * Sort a software rule.
	 *
	 * @param id the ID of the rule
	 * @param nextId the next ID
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules/{id}/sort")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Sort a software compliance rule",
		description = "Sorts a software compliance rule by moving it before another one."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void sortSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID to move") Long id,
		@QueryParam("next") @Parameter(description = "ID of the other rule to move this rule before (omit parameter to place the rule at last position)") Long nextId)
		throws WebApplicationException {
		log.debug("REST request, sort software rule.");

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Query<SoftwareRule> query = session
				.createQuery("from SoftwareRule r order by r.priority asc", SoftwareRule.class);
			List<SoftwareRule> rules = query.list();

			SoftwareRule targetRule = null;
			int nextIndex = -1;
			ListIterator<SoftwareRule> ruleIt = rules.listIterator();
			while (ruleIt.hasNext()) {
				SoftwareRule rule = ruleIt.next();
				if (rule.getId() == id) {
					targetRule = rule;
					ruleIt.remove();
				}
				else if (nextId != null && nextId.equals(rule.getId())) {
					nextIndex = ruleIt.previousIndex();
				}
			}

			if (targetRule == null) {
				log.error("Unable to find the rule {} to be moved.", id);
				throw new NetshotBadRequestException("Unable to find this rule.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}
			if (nextIndex == -1 && nextId != null) {
				log.error("Unable to find the previous rule of ID {}.", nextId);
				throw new NetshotBadRequestException("Unable to find this rule.",
					ErrorCode.NETSHOT_INVALID_RULE);
			}
			if (nextIndex == -1) {
				rules.add(targetRule);
			}
			else {
				rules.add(nextIndex, targetRule);
			}
			double priority = 0;
			for (SoftwareRule rule : rules) {
				priority += 10;
				rule.setPriority(priority);
				session.persist(rule);
			}
			session.getTransaction().commit();
			AAA_LOG.info("{} has been sorted (moved before at index {})", targetRule, nextIndex);

			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new rule.", e);
			throw new NetshotBadRequestException(
				"Unable to add the policy to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightSoftwareLevelDevice.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsLightSoftwareLevelDevice extends RsLightDevice {

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String softwareVersion;

		public RsLightSoftwareLevelDevice(long id, String name, String family, String mgmtAddress, Status status,
			String driver, Boolean eol, Boolean eos, Boolean configCompliant, ConformanceLevel softwareLevel,
			Device.NetworkClass networkClass, String softwareVersion) {
			super(id, name, family, mgmtAddress, status, driver, eol, eos, configCompliant, softwareLevel, networkClass);
			this.softwareVersion = softwareVersion;
		}
	}

	/**
	 * Gets the group devices by software level.
	 *
	 * @param id the id
	 * @param level the level
	 * @param domains the domains
	 * @return the group devices by software level
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupdevicesbysoftwarelevel/{id}/{level}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the devices of a group based on software compliance level",
		description = "Returns the list of devices of a given group by ID, and matching the given software compliance level."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RsLightSoftwareLevelDevice> getGroupDevicesBySoftwareLevel(
		@PathParam("id") @Parameter(description = "Group ID") Long id,
		@PathParam("level") @Parameter(description = "Software compliance level") String level,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains)
		throws WebApplicationException {
		log.debug("REST request, group {} devices by software level {}.", id, level);
		Session session = Database.getSession(true);

		ConformanceLevel filterLevel = ConformanceLevel.UNKNOWN;
		for (ConformanceLevel l : ConformanceLevel.values()) {
			if (l.toString().equalsIgnoreCase(level)) {
				filterLevel = l;
				break;
			}
		}

		try {
			String domainFilter = "";
			if (domains.size() > 0) {
				domainFilter = " and d.mgmtDomain.id in (:domainIds)";
			}
			Query<RsLightSoftwareLevelDevice> query = session
				.createQuery(
					"select new RsLightSoftwareLevelDevice("
						+ "d.id, "
						+ "d.name, "
						+ "d.family, "
						+ "d.mgmtAddress, "
						+ "d.status, "
						+ "d.driver, "
						+ "case when (d.eolDate < current_date()) then true else false end,  "
						+ "case when (d.eosDate < current_date()) then true else false end, "
						+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
						+ "d.softwareLevel, "
						+ "d.networkClass, "
						+ "d.softwareVersion "
						+ ") from Device d join d.groupMemberships gm where gm.key.group.id = :id and d.softwareLevel = :level and d.status = :enabled" + domainFilter,
					RsLightSoftwareLevelDevice.class)
				.setParameter("id", id)
				.setParameter("level", filterLevel)
				.setParameter("enabled", Device.Status.INPRODUCTION)
				.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
			if (domains.size() > 0) {
				query.setParameterList("domainIds", domains);
			}
			List<RsLightSoftwareLevelDevice> devices = query.list();
			return devices;
		}
		catch (HibernateException e) {
			log.error("Unable to get the devices.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsLightSoftwareLevelDevice.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	@AllArgsConstructor
	public static class RsLightAccessFailureDevice extends RsLightDevice {

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date lastSuccess;

		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Date lastFailure;

		public RsLightAccessFailureDevice(long id, String name, String family, String mgmtAddress, Status status,
			String driver, Boolean eol, Boolean eos, Boolean configCompliant, ConformanceLevel softwareLevel,
			Device.NetworkClass networkClass, Date lastSuccess, Date lastFailure) {
			super(id, name, family, mgmtAddress, status, driver, eol, eos, configCompliant, softwareLevel, networkClass);
			this.lastSuccess = lastSuccess;
			this.lastFailure = lastFailure;
		}
	}

	/**
	 * Returns the devices with recent access failure.
	 * @param days = the number of days
	 * @param domains = the domains to filter on
	 * @param paginationParams = the pagination parameters
	 * @return the list of devices
	 */
	@GET
	@Path("/reports/accessfailuredevices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the devices without successful snapshot over a given period",
		description = "Returns the list of devices which didn't have a successful snapshot over the given number of days, optionally "
			+ "filtered by device domain."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	public List<RsLightAccessFailureDevice> getAccessFailureDevices(
		@QueryParam("days") @Parameter(description = "Look for the given number of last days") Integer days,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, devices without successful snapshot over the last {} days.", days);

		if (days == null || days < 1) {
			log.warn("Invalid number of days {} to find the unreachable devices, using 3.", days);
			days = 3;
		}

		Session session = Database.getSession(true);

		try {
			Calendar when = Calendar.getInstance();
			when.add(Calendar.DATE, -days);

			String domainFilter = "";
			if (domains.size() > 0) {
				domainFilter = "and d.mgmtDomain.id in (:domainIds) ";
			}

			Query<RsLightAccessFailureDevice> query = session
				.createQuery(
					"select new RsLightAccessFailureDevice("
					+ "d.id, "
					+ "d.name, "
					+ "d.family, "
					+ "d.mgmtAddress, "
					+ "d.status, "
					+ "d.driver, "
					+ "case when (d.eolDate < current_date()) then true else false end, "
					+ "case when (d.eosDate < current_date()) then true else false end, "
					+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
					+ "d.softwareLevel, "
					+ "d.networkClass, "
					+ "max(case when t.status = :success then t.executionDate end), "
					+ "max(case when t.status = :failure then t.executionDate end) "
					+ ") from Device d "
					+ "left join TakeSnapshotTask t on t.device = d "
					+ "where d.status = :enabled " + domainFilter
					+ "group by d.id, d.name, d.family, d.mgmtAddress, d.status, d.driver, d.eolDate, d.eosDate, d.softwareLevel, d.networkClass "
					+ "having max(case when t.status = :success then t.executionDate end) < :when "
					+ "or max(case when t.status = :success then t.executionDate end) is null "
					+ "order by max(case when t.status = :success then t.executionDate end) desc nulls last",
					RsLightAccessFailureDevice.class)
				.setParameter("when", when.getTime())
				.setParameter("success", Task.Status.SUCCESS)
				.setParameter("failure", Task.Status.FAILURE)
				.setParameter("enabled", Device.Status.INPRODUCTION)
				.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
			if (domainFilter.length() > 0) {
				query.setParameterList("domainIds", domains);
			}
			paginationParams.apply(query);
			List<RsLightAccessFailureDevice> devices = query.list();
			return devices;
		}
		catch (HibernateException e) {
			log.error("Unable to get the devices.", e);
			throw new NetshotBadRequestException("Unable to get the stats",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	@GET
	@Path("/reports/export")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Export data",
		description = "Exports data as Excel datasheet. The devices can be filtered by groups or domains. "
			+ "The report can be customized to include or not interfaces, inventory, locations, compliance, groups. "
			+ "The only supported and default output format is xlsx (Excel file)."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	public Response getDataXLSX(
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> groups,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@DefaultValue("false") @QueryParam("interfaces") @Parameter(description = "Whether to export interface data") boolean exportInterfaces,
		@DefaultValue("false") @QueryParam("inventory") @Parameter(description = "Whether to export inventory data") boolean exportInventory,
		@DefaultValue("false") @QueryParam("inventoryhistory") @Parameter(description = "Whether to export interface history") boolean exportInventoryHistory,
		@DefaultValue("false") @QueryParam("locations") @Parameter(description = "Whether to export locations") boolean exportLocations,
		@DefaultValue("false") @QueryParam("compliance") @Parameter(description = "Whether to export compliance results") boolean exportCompliance,
		@DefaultValue("false") @QueryParam("groups") @Parameter(description = "Whether to export group info") boolean exportGroups,
		@DefaultValue("false") @QueryParam("devicedriverattributes") @Parameter(description = "Whether to export driver-specific attributes for devices") boolean exportDeviceDriverAttributes,
		@DefaultValue("xlsx") @QueryParam("format") @Parameter(description = "Export format (xlsx is supported)") String fileFormat) throws WebApplicationException {
		log.debug("REST request, export data.");
		User user = (User) securityContext.getUserPrincipal();

		if (fileFormat.compareToIgnoreCase("xlsx") == 0) {
			String fileName = String.format("netshot-export_%s.xlsx", new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));

			Session session = Database.getSession(true);
			SXSSFWorkbook workBook = new SXSSFWorkbook(100);
			try {
				Row row;
				Cell cell;

				CreationHelper createHelper = workBook.getCreationHelper();
				CellStyle datetimeCellStyle = workBook.createCellStyle();
				datetimeCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
				datetimeCellStyle.setAlignment(HorizontalAlignment.LEFT);
				CellStyle dateCellStyle = workBook.createCellStyle();
				dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
				CellStyle titleCellStyle = workBook.createCellStyle();
				Font titleFont = workBook.createFont();
				titleFont.setBold(true);
				titleCellStyle.setFont(titleFont);
				CellStyle percentCellStyle = workBook.createCellStyle();
				percentCellStyle.setDataFormat(workBook.createDataFormat()
					.getFormat(BuiltinFormats.getBuiltinFormat(0x09)));

				Sheet summarySheet = workBook.createSheet("Summary");
				summarySheet.setColumnWidth(0, 5000);
				summarySheet.setColumnWidth(1, 8000);

				{
					int y = -1;
					row = summarySheet.createRow(++y);
					row.createCell(0).setCellValue("Netshot version");
					row.getCell(0).setCellStyle(titleCellStyle);
					row.createCell(1).setCellValue(Netshot.VERSION);
					row = summarySheet.createRow(++y);
					row.createCell(0).setCellValue("Exported by");
					row.getCell(0).setCellStyle(titleCellStyle);
					row.createCell(1).setCellValue(user.getName());
					row = summarySheet.createRow(++y);
					row.createCell(0).setCellValue("Date and time");
					row.getCell(0).setCellStyle(titleCellStyle);
					cell = row.createCell(1);
					cell.setCellValue(new Date());
					cell.setCellStyle(datetimeCellStyle);

					row = summarySheet.createRow(++y);
					row.createCell(0).setCellValue("Selected Domain");
					row.getCell(0).setCellStyle(titleCellStyle);
					if (domains.isEmpty()) {
						row.createCell(1).setCellValue("Any");
					}
					else {
						List<Domain> deviceDomains = session
							.createQuery("select d from Domain d where d.id in (:domainIds)", Domain.class)
							.setParameterList("domainIds", domains)
							.list();
						List<String> domainNames = new ArrayList<>();
						for (Domain deviceDomain : deviceDomains) {
							domainNames.add(String.format("%s (%d)", deviceDomain.getName(), deviceDomain.getId()));
						}
						row.createCell(1).setCellValue(String.join(", ", domainNames));
					}
					row = summarySheet.createRow(++y);
					row.createCell(0).setCellValue("Selected Group");
					row.getCell(0).setCellStyle(titleCellStyle);
					if (groups.isEmpty()) {
						row.createCell(1).setCellValue("Any");
					}
					else {
						List<DeviceGroup> deviceGroups = session
							.createQuery("select g from DeviceGroup g where g.id in (:groupIds)", DeviceGroup.class)
							.setParameterList("groupIds", groups)
							.list();
						List<String> groupNames = new ArrayList<>();
						for (DeviceGroup group : deviceGroups) {
							groupNames.add(String.format("%s (%d)", group.getName(), group.getId()));
						}
						row.createCell(1).setCellValue(String.join(", ", groupNames));
					}
					summarySheet.setDefaultColumnStyle(0, titleCellStyle);
				}

				{
					StringBuilder deviceHqlQuery = new StringBuilder(
						"select d from Device d left join d.groupMemberships gm "
							+ "left join fetch d.mgmtDomain "
							+ "where 1 = 1");
					if (domains.size() > 0) {
						deviceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
					}
					if (groups.size() > 0) {
						deviceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
					}
					deviceHqlQuery.append(" order by d.name asc");
					Query<Device> deviceQuery = session.createQuery(deviceHqlQuery.toString(), Device.class);
					if (domains.size() > 0) {
						deviceQuery.setParameterList("domainIds", domains);
					}
					if (groups.size() > 0) {
						deviceQuery.setParameterList("groupIds", groups);
					}
					deviceQuery.setMaxResults(PAGINATION_SIZE);

					Sheet deviceSheet = workBook.createSheet("Devices");
					((SXSSFSheet) deviceSheet).setRandomAccessWindowSize(100);
					int y = -1;
					{
						row = deviceSheet.createRow(++y);
						int x = -1;
						row.createCell(++x).setCellValue("ID");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 2200);
						row.createCell(++x).setCellValue("Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Management IP");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 4000);
						row.createCell(++x).setCellValue("Domain");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 4000);
						row.createCell(++x).setCellValue("Network Class");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 4000);
						row.createCell(++x).setCellValue("Family");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Creation");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 4200);
						row.createCell(++x).setCellValue("Last Change");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 4200);
						row.createCell(++x).setCellValue("Software");
						row.getCell(x).setCellStyle(titleCellStyle);
						deviceSheet.setColumnWidth(x, 5000);
						if (exportCompliance) {
							row.createCell(++x).setCellValue("Software Level");
							row.getCell(x).setCellStyle(titleCellStyle);
							deviceSheet.setColumnWidth(x, 3500);
							row.createCell(++x).setCellValue("End of Sale Date");
							row.getCell(x).setCellStyle(titleCellStyle);
							deviceSheet.setColumnWidth(x, 4200);
							row.createCell(++x).setCellValue("End Of Life Date");
							row.getCell(x).setCellStyle(titleCellStyle);
							deviceSheet.setColumnWidth(x, 4200);
						}
						if (exportLocations) {
							row.createCell(++x).setCellValue("Location");
							row.getCell(x).setCellStyle(titleCellStyle);
							deviceSheet.setColumnWidth(x, 5000);
							row.createCell(++x).setCellValue("Contact");
							row.getCell(x).setCellStyle(titleCellStyle);
							deviceSheet.setColumnWidth(x, 5000);
						}
						row.setRowStyle(titleCellStyle);
						deviceSheet.createFreezePane(0, y + 1);
						deviceSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
					}

					for (int n = 0; true; n += PAGINATION_SIZE) {
						List<Device> devices = deviceQuery.setFirstResult(n).list();
						if (devices.size() == 0) {
							break;
						}
						for (Device device : devices) {
							int x = -1;
							row = deviceSheet.createRow(++y);
							row.createCell(++x).setCellValue(device.getId());
							row.createCell(++x).setCellValue(device.getName());
							row.createCell(++x).setCellValue(device.getMgmtAddress());
							row.createCell(++x).setCellValue(device.getMgmtDomain().getName());
							row.createCell(++x).setCellValue(device.getNetworkClass().toString());
							row.createCell(++x).setCellValue(device.getFamily());
							cell = row.createCell(++x);
							cell.setCellValue(device.getCreatedDate());
							cell.setCellStyle(datetimeCellStyle);
							cell = row.createCell(++x);
							cell.setCellValue(device.getChangeDate());
							cell.setCellStyle(datetimeCellStyle);
							row.createCell(++x).setCellValue(device.getSoftwareVersion());
							if (exportCompliance) {
								row.createCell(++x).setCellValue((device.getSoftwareLevel() == null
									? ConformanceLevel.UNKNOWN : device.getSoftwareLevel()).toString());
								cell = row.createCell(++x);
								if (device.getEosDate() != null) {
									cell.setCellValue(device.getEosDate());
									cell.setCellStyle(dateCellStyle);
								}
								cell = row.createCell(++x);
								if (device.getEolDate() != null) {
									cell.setCellValue(device.getEolDate());
									cell.setCellStyle(dateCellStyle);
								}
							}
							if (exportLocations) {
								row.createCell(++x).setCellValue(device.getLocation());
								row.createCell(++x).setCellValue(device.getContact());
							}
						}
					}
				}

				if (exportInterfaces) {
					log.debug("Exporting interface data");
					StringBuilder interfaceHqlQuery = new StringBuilder(
						"select ni from NetworkInterface ni "
							+ "left join fetch ni.ip4Addresses left join fetch ni.ip6Addresses "
							+ "join ni.device d left join d.groupMemberships gm where 1 = 1");
					if (domains.size() > 0) {
						interfaceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
					}
					if (groups.size() > 0) {
						interfaceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
					}
					interfaceHqlQuery.append(" order by d.name asc, ni.id asc");
					Query<NetworkInterface> interfaceQuery = session.createQuery(interfaceHqlQuery.toString(),
						NetworkInterface.class);
					if (domains.size() > 0) {
						interfaceQuery.setParameterList("domainIds", domains);
					}
					if (groups.size() > 0) {
						interfaceQuery.setParameterList("groupIds", groups);
					}

					Sheet interfaceSheet = workBook.createSheet("Interfaces");
					((SXSSFSheet) interfaceSheet).setRandomAccessWindowSize(100);
					int y = -1;
					{
						row = interfaceSheet.createRow(++y);
						int x = -1;
						row.createCell(++x).setCellValue("Device ID");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 2200);
						row.createCell(++x).setCellValue("Device Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Virtual Device");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Description");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 7000);
						row.createCell(++x).setCellValue("VRF");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("MAC Address");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 4000);
						row.createCell(++x).setCellValue("Enabled");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 2000);
						row.createCell(++x).setCellValue("Level 3");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 2000);
						row.createCell(++x).setCellValue("IP Address");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 4000);
						row.createCell(++x).setCellValue("Length");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 2000);
						row.createCell(++x).setCellValue("Usage");
						row.getCell(x).setCellStyle(titleCellStyle);
						interfaceSheet.setColumnWidth(x, 4000);
						row.setRowStyle(titleCellStyle);
						interfaceSheet.createFreezePane(0, y + 1);
						interfaceSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
					}

					List<NetworkInterface> networkInterfaces = interfaceQuery.list();
					for (NetworkInterface networkInterface : networkInterfaces) {
						Device device = networkInterface.getDevice();
						if (networkInterface.getIpAddresses().isEmpty()) {
							row = interfaceSheet.createRow(++y);
							int x = -1;
							row.createCell(++x).setCellValue(device.getId());
							row.createCell(++x).setCellValue(device.getName());
							row.createCell(++x).setCellValue(networkInterface.getVirtualDevice());
							row.createCell(++x).setCellValue(networkInterface.getInterfaceName());
							row.createCell(++x).setCellValue(networkInterface.getDescription());
							row.createCell(++x).setCellValue(networkInterface.getVrfInstance());
							row.createCell(++x).setCellValue(networkInterface.getMacAddress());
							row.createCell(++x).setCellValue(networkInterface.isEnabled());
							row.createCell(++x).setCellValue(networkInterface.isLevel3());
							row.createCell(++x).setCellValue("");
							row.createCell(++x).setCellValue("");
							row.createCell(++x).setCellValue("");
						}
						for (NetworkAddress address : networkInterface.getIpAddresses()) {
							row = interfaceSheet.createRow(++y);
							int x = -1;
							row.createCell(++x).setCellValue(device.getId());
							row.createCell(++x).setCellValue(device.getName());
							row.createCell(++x).setCellValue(networkInterface.getVirtualDevice());
							row.createCell(++x).setCellValue(networkInterface.getInterfaceName());
							row.createCell(++x).setCellValue(networkInterface.getDescription());
							row.createCell(++x).setCellValue(networkInterface.getVrfInstance());
							row.createCell(++x).setCellValue(networkInterface.getMacAddress());
							row.createCell(++x).setCellValue(networkInterface.isEnabled());
							row.createCell(++x).setCellValue(networkInterface.isLevel3());
							row.createCell(++x).setCellValue(address.getIp());
							row.createCell(++x).setCellValue(address.getPrefixLength());
							row.createCell(++x).setCellValue(address.getAddressUsage() == null ? "" : address.getAddressUsage().toString());
						}
					}
					session.clear();
				}

				if (exportInventory) {
					log.debug("Exporting device inventory");
					StringBuilder moduleHqlQuery = new StringBuilder(
						"select m from Module m "
							+ "join m.device d left join d.groupMemberships gm where 1 = 1");
					if (!exportInventoryHistory) {
						moduleHqlQuery.append(" and m.removed is not true");
					}
					if (domains.size() > 0) {
						moduleHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
					}
					if (groups.size() > 0) {
						moduleHqlQuery.append(" and gm.key.group.id in (:groupIds)");
					}
					moduleHqlQuery.append(" order by d.name asc, m.id asc");
					Query<Module> moduleQuery = session.createQuery(moduleHqlQuery.toString(), Module.class);
					if (domains.size() > 0) {
						moduleQuery.setParameterList("domainIds", domains);
					}
					if (groups.size() > 0) {
						moduleQuery.setParameterList("groupIds", groups);
					}
					moduleQuery.setMaxResults(PAGINATION_SIZE);

					Sheet inventorySheet = workBook.createSheet("Inventory");
					((SXSSFSheet) inventorySheet).setRandomAccessWindowSize(100);
					int y = -1;
					{
						row = inventorySheet.createRow(++y);
						int x = -1;
						row.createCell(++x).setCellValue("Device ID");
						row.getCell(x).setCellStyle(titleCellStyle);
						inventorySheet.setColumnWidth(x, 2200);
						row.createCell(++x).setCellValue("Device Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						inventorySheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Slot");
						row.getCell(x).setCellStyle(titleCellStyle);
						inventorySheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Part Number");
						row.getCell(x).setCellStyle(titleCellStyle);
						inventorySheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Serial Number");
						row.getCell(x).setCellStyle(titleCellStyle);
						inventorySheet.setColumnWidth(x, 4000);
						row.setRowStyle(titleCellStyle);
						if (exportInventoryHistory) {
							row.createCell(++x).setCellValue("First seen");
							row.getCell(x).setCellStyle(titleCellStyle);
							inventorySheet.setColumnWidth(x, 4200);
							row.createCell(++x).setCellValue("Last seen");
							row.getCell(x).setCellStyle(titleCellStyle);
							inventorySheet.setColumnWidth(x, 4200);
							row.createCell(++x).setCellValue("Removed");
							row.getCell(x).setCellStyle(titleCellStyle);
							inventorySheet.setColumnWidth(x, 4200);
						}
						inventorySheet.createFreezePane(0, y + 1);
						inventorySheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
					}
					for (int n = 0; true; n += PAGINATION_SIZE) {
						List<Module> modules = moduleQuery.setFirstResult(n).list();
						if (modules.size() == 0) {
							break;
						}
						for (Module module : modules) {
							Device device = module.getDevice();
							int x = -1;
							row = inventorySheet.createRow(++y);
							row.createCell(++x).setCellValue(device.getId());
							row.createCell(++x).setCellValue(device.getName());
							row.createCell(++x).setCellValue(module.getSlot());
							row.createCell(++x).setCellValue(module.getPartNumber());
							row.createCell(++x).setCellValue(module.getSerialNumber());
							if (exportInventoryHistory) {
								cell = row.createCell(++x);
								cell.setCellValue(module.getFirstSeenDate());
								cell.setCellStyle(datetimeCellStyle);
								cell = row.createCell(++x);
								cell.setCellValue(module.getLastSeenDate());
								cell.setCellStyle(datetimeCellStyle);
								cell = row.createCell(++x, CellType.BOOLEAN);
								cell.setCellValue(module.isRemoved());
							}
						}
						session.clear();
					}
				}

				if (exportCompliance) {
			log.debug("Exporting compliance data");
					List<RsLightPolicyRuleDevice> checkResults = getConfigComplianceDeviceStatuses(domains, groups,
						new HashSet<Long>(), new HashSet<>(
							Arrays.asList(new CheckResult.ResultOption[] { CheckResult.ResultOption.CONFORMING,
								CheckResult.ResultOption.NONCONFORMING, CheckResult.ResultOption.EXEMPTED })));

					Sheet complianceSheet = workBook.createSheet("Configuration Compliance");
					((SXSSFSheet) complianceSheet).setRandomAccessWindowSize(100);
					int y = -1;
					{
						row = complianceSheet.createRow(++y);
						int x = -1;
						row.createCell(++x).setCellValue("Device ID");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 2200);
						row.createCell(++x).setCellValue("Device Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 5000);

						row.createCell(++x).setCellValue("Policy");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Rule");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 8000);
						row.createCell(++x).setCellValue("Check Date");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 4200);
						row.createCell(++x).setCellValue("Result");
						row.getCell(x).setCellStyle(titleCellStyle);
						complianceSheet.setColumnWidth(x, 4000);
						row.setRowStyle(titleCellStyle);
						complianceSheet.createFreezePane(0, y + 1);
						complianceSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
					}

					for (RsLightPolicyRuleDevice checkResult : checkResults) {
						int x = -1;
						row = complianceSheet.createRow(++y);
						row.createCell(++x).setCellValue(checkResult.getId());
						row.createCell(++x).setCellValue(checkResult.getName());
						row.createCell(++x).setCellValue(checkResult.getPolicyName());
						row.createCell(++x).setCellValue(checkResult.getRuleName());
						row.createCell(++x).setCellValue(checkResult.getCheckDate());
						row.getCell(x).setCellStyle(datetimeCellStyle);
						row.createCell(++x).setCellValue(checkResult.getResult().toString());
					}
					session.clear();
				}

				if (exportGroups) {
					{
						log.debug("Exporting group data");
						List<RsGroupConfigComplianceStat> groupConfigComplianceStats =
							this.getGroupConfigComplianceStats(domains, groups, new HashSet<>());

						Sheet groupSheet = workBook.createSheet("Device Groups");
						((SXSSFSheet) groupSheet).setRandomAccessWindowSize(100);
						int y = -1;
						{
							row = groupSheet.createRow(++y);
							int x = -1;
							row.createCell(++x).setCellValue("Group ID");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 2200);
							row.createCell(++x).setCellValue("Group Name");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 5000);
							row.createCell(++x).setCellValue("Folder");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 8000);
							row.createCell(++x).setCellValue("Device Count");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 4000);
							if (exportCompliance) {
								row.createCell(++x).setCellValue("Config Compliant Count");
								row.getCell(x).setCellStyle(titleCellStyle);
								groupSheet.setColumnWidth(x, 6000);
								row.createCell(++x).setCellValue("Config Compliance");
								row.getCell(x).setCellStyle(titleCellStyle);
								groupSheet.setColumnWidth(x, 5000);
							}
							groupSheet.createFreezePane(0, y + 1);
							groupSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
						}

						for (RsGroupConfigComplianceStat stat : groupConfigComplianceStats) {
							row = groupSheet.createRow(++y);
							int x = -1;
							row.createCell(++x).setCellValue(stat.getGroupId());
							row.createCell(++x).setCellValue(stat.getGroupName());
							row.createCell(++x).setCellValue(stat.getGroupFolder());
							row.createCell(++x).setCellValue(stat.getDeviceCount());
							if (exportCompliance) {
								row.createCell(++x).setCellValue(stat.getCompliantDeviceCount());
								row.createCell(++x).setCellFormula(String.format("%s%d / %s%d",
									CellReference.convertNumToColString(x - 1), y + 1,
									CellReference.convertNumToColString(x - 2), y + 1));
								row.getCell(x).setCellStyle(percentCellStyle);
							}
						}
						session.clear();
					}
					{
						log.debug("Exporting group memberships");
						StringBuilder deviceHqlQuery = new StringBuilder(
							"select d from Device d left join d.groupMemberships gm join gm.key.group g"
								+ "left join d.mgmtDomain "
								+ "where 1 = 1");
						if (domains.size() > 0) {
							deviceHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
						}
						if (groups.size() > 0) {
							deviceHqlQuery.append(" and gm.key.group.id in (:groupIds)");
						}
						deviceHqlQuery.append(" order by d.name asc");
						Query<Device> deviceQuery = session.createQuery(deviceHqlQuery.toString(), Device.class);
						if (domains.size() > 0) {
							deviceQuery.setParameterList("domainIds", domains);
						}
						if (groups.size() > 0) {
							deviceQuery.setParameterList("groupIds", groups);
						}
						deviceQuery.setMaxResults(PAGINATION_SIZE);

						Sheet groupSheet = workBook.createSheet("Group Memberships");
						((SXSSFSheet) groupSheet).setRandomAccessWindowSize(100);
						int y = -1;
						{
							row = groupSheet.createRow(++y);
							int x = -1;
							row.createCell(++x).setCellValue("Device ID");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 2200);
							row.createCell(++x).setCellValue("Device Name");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 5000);
							row.createCell(++x).setCellValue("Group ID");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 2200);
							row.createCell(++x).setCellValue("Group Name");
							row.getCell(x).setCellStyle(titleCellStyle);
							groupSheet.setColumnWidth(x, 5000);
							groupSheet.createFreezePane(0, y + 1);
							groupSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
						}

						for (int n = 0; true; n += PAGINATION_SIZE) {
							List<Device> devices = deviceQuery.setFirstResult(n).list();
							if (devices.size() == 0) {
								break;
							}
							for (Device device : devices) {
								for (DeviceGroup group : device.getOwnerGroups()) {
									if (group.isHiddenFromReports()) {
										continue;
									}
									row = groupSheet.createRow(++y);
									int x = -1;
									row.createCell(++x).setCellValue(device.getId());
									row.createCell(++x).setCellValue(device.getName());
									row.createCell(++x).setCellValue(group.getId());
									row.createCell(++x).setCellValue(group.getName());
								}
							}
							session.clear();
						}
					}

				}

				if (exportDeviceDriverAttributes) {
					log.debug("Exporting driver-specific device attributes");
					StringBuilder attributeHqlQuery = new StringBuilder(
						"select da from DeviceAttribute da "
							+ "join fetch da.device d where 1 = 1");
					if (domains.size() > 0) {
						attributeHqlQuery.append(" and d.mgmtDomain.id in (:domainIds)");
					}
					if (groups.size() > 0) {
						attributeHqlQuery.append(" and d.id in (select d.id from Device d left join d.groupMemberships gm where gm.key.group.id in (:groupIds))");
					}
					attributeHqlQuery.append(" order by d.name asc, da.id asc");
					Query<DeviceAttribute> attributeQuery = session.createQuery(attributeHqlQuery.toString(),
						DeviceAttribute.class);
					if (domains.size() > 0) {
						attributeQuery.setParameterList("domainIds", domains);
					}
					if (groups.size() > 0) {
						attributeQuery.setParameterList("groupIds", groups);
					}
					attributeQuery.setMaxResults(PAGINATION_SIZE);

					Sheet attributeSheet = workBook.createSheet("Device Attributes");
					((SXSSFSheet) attributeSheet).setRandomAccessWindowSize(100);
					int y = -1;
					{
						row = attributeSheet.createRow(++y);
						int x = -1;
						row.createCell(++x).setCellValue("Device ID");
						row.getCell(x).setCellStyle(titleCellStyle);
						attributeSheet.setColumnWidth(x, 2200);
						row.createCell(++x).setCellValue("Device Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						attributeSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Attribute Name");
						row.getCell(x).setCellStyle(titleCellStyle);
						attributeSheet.setColumnWidth(x, 5000);
						row.createCell(++x).setCellValue("Attribute Value");
						row.getCell(x).setCellStyle(titleCellStyle);
						attributeSheet.setColumnWidth(x, 7000);
						row.setRowStyle(titleCellStyle);
						attributeSheet.createFreezePane(0, y + 1);
						attributeSheet.setAutoFilter(new CellRangeAddress(0, y, 0, x));
					}

					for (int n = 0; true; n += PAGINATION_SIZE) {
						List<DeviceAttribute> attributes = attributeQuery.setFirstResult(n).list();
						if (attributes.size() == 0) {
							break;
						}
						for (DeviceAttribute attribute : attributes) {
							try {
								String value = attribute.getData().toString();
								Device device = attribute.getDevice();
								DeviceDriver driver = device.getDeviceDriver();
								AttributeDefinition definition = driver.getAttributeDefinition(AttributeLevel.DEVICE, attribute.getName());
								int x = -1;
								row = attributeSheet.createRow(++y);
								row.createCell(++x).setCellValue(device.getId());
								row.createCell(++x).setCellValue(device.getName());
								row.createCell(++x).setCellValue(definition.getTitle());
								row.createCell(++x).setCellValue(value);
								attribute.getData();
							}
							catch (Exception e) {
								log.warn("Error while exporting attribute (ID {}, name {})... skipping",
									attribute.getId(), attribute.getName(), e);
							}
						}
						session.clear();
					}
				}

				ByteArrayOutputStream output = new ByteArrayOutputStream();
				workBook.write(output);
				byte[] content = output.toByteArray();
				return Response
					.ok(content, MediaType.APPLICATION_OCTET_STREAM)
					.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
					.header(Headers.CONTENT_LENGTH_STRING, content.length)
					.build();
			}
			catch (IOException e) {
				log.error("Unable to write the resulting file.", e);
				throw new WebApplicationException(
					"Unable to write the resulting file.",
					Response.Status.INTERNAL_SERVER_ERROR);
			}
			catch (Exception e) {
				log.error("Unable to generate the report.", e);
				throw new WebApplicationException("Unable to generate the report.",
					Response.Status.INTERNAL_SERVER_ERROR);
			}
			finally {
				session.close();
				try {
					workBook.close();
				}
				catch (IOException e) {
					log.warn("Error while closing work book", e);
				}
			}
		}

		log.warn("Invalid requested file format.");
		throw new WebApplicationException(
			"The requested file format is invalid or not supported.",
			Response.Status.BAD_REQUEST);
	}

	@POST
	@Path("/scripts")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add/validate a command script",
		description = "Create a command script (script to be later run over devices)."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public DeviceJsScript addScript(
		DeviceJsScript rsScript,
		@DefaultValue("false") @QueryParam("validateonly") @Parameter(description = "True to validate script without saving") boolean validateOnly)
		throws WebApplicationException {
		log.debug("REST request, add device script.");
		DeviceDriver driver = DeviceDriver.getDriverByName(rsScript.getDeviceDriver());
		if (driver == null) {
			log.warn("Invalid driver name.");
			throw new NetshotBadRequestException("Invalid driver name.",
				ErrorCode.NETSHOT_INVALID_SCRIPT);
		}
		if (rsScript.getName() == null || "".equals(rsScript.getName().trim())) {
			log.warn("Invalid script name.");
			throw new NetshotBadRequestException("Invalid script name.",
				ErrorCode.NETSHOT_INVALID_SCRIPT);
		}
		if (rsScript.getScript() == null) {
			log.warn("Invalid script.");
			throw new NetshotBadRequestException("The script content can't be empty.",
				ErrorCode.NETSHOT_INVALID_SCRIPT);
		}
		try {
			User user = (User) securityContext.getUserPrincipal();
			rsScript.setAuthor(user.getUsername());
		}
		catch (Exception e) {
		}
		rsScript.setId(0);

		try {
			rsScript.extractUserInputDefinitions();
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid script.");
			throw new NetshotBadRequestException(e.getMessage(),
				ErrorCode.NETSHOT_INVALID_SCRIPT);
		}

		if (!validateOnly) {
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				session.persist(rsScript);
				session.getTransaction().commit();
				AAA_LOG.info("{} has been created", rsScript);
				this.suggestReturnCode(Response.Status.CREATED);
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Error while saving the new rule.", e);
				if (Database.isDuplicateException(e)) {
					throw new NetshotBadRequestException(
						"A script with this name already exists.",
						ErrorCode.NETSHOT_DUPLICATE_SCRIPT);
				}
				throw new NetshotBadRequestException(
					"Unable to add the script to the database",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}

		return rsScript;
	}

	@DELETE
	@Path("/scripts/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a script",
		description = "Removes a given script, by ID."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public void deleteScript(@PathParam("id") @Parameter(description = "Script ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete script {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceJsScript script = session.get(DeviceJsScript.class, id);
			if (script == null) {
				log.warn("No such script of ID {}", id);
				throw new NetshotBadRequestException("The script doesn't exist.",
					ErrorCode.NETSHOT_SCRIPT_NOT_FOUND);
			}
			session.remove(script);
			session.getTransaction().commit();
			AAA_LOG.info("Script of ID {} has been deleted", script.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (NetshotBadRequestException e) {
			Database.rollbackSilently(session);
			log.error("Cannot delete the script.", e);
			throw e;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the script {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the script.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	@GET
	@Path("/scripts/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a command script",
		description = "Returns a given command script, by ID."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public DeviceJsScript getScript(@PathParam("id") @Parameter(description = "Script ID") Long id) {
		log.debug("REST request, get script {}", id);
		Session session = Database.getSession(true);
		try {
			DeviceJsScript script = session.get(DeviceJsScript.class, id);
			return script;
		}
		catch (ObjectNotFoundException e) {
			log.error("Unable to find the script {}.", id, e);
			throw new NetshotBadRequestException("Script not found.",
				ErrorCode.NETSHOT_SCRIPT_NOT_FOUND);
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the script {}.", id, e);
			throw new NetshotBadRequestException("Unable to fetch the script.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Get the saved scripts.
	 * @param paginationParams = the pagination parameters
	 * @return the list of scripts
	 */
	@GET
	@Path("/scripts")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get command scripts",
		description = "Returns the list of command scripts."
	)
	@Tag(name = "Scripts", description = "Script management (push changes to devices)")
	public List<DeviceJsScript> getScripts(@BeanParam PaginationParams paginationParams) {
		log.debug("REST request, get scripts.");
		Session session = Database.getSession(true);
		try {
			Query<DeviceJsScript> query = session.createQuery("from DeviceJsScript s", DeviceJsScript.class);
			paginationParams.apply(query);
			List<DeviceJsScript> scripts = query.list();
			for (DeviceJsScript script : scripts) {
				script.setScript(null);
			}
			return scripts;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the scripts.", e);
			throw new NetshotBadRequestException("Unable to fetch the scripts",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * The Class RsDiagnostic.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsDiagnostic {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String name = "";

		/** The group. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long targetGroup;

		/** Whether the diagnostic is enabled or not. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean enabled;

		/** The type of result (boolean, text, etc.). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String resultType;

		/** The type of diagnostic. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String type;

		/** The Javascript script (in case of JavaScriptDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String script;

		/** The device driver name (in case of SimpleDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String deviceDriver;

		/** The mode in which to run the command (in case of SimpleDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String cliMode;

		/** The CLI command to execute (in case of SimpleDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String command;

		/** The pattern to match (in case of SimpleDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String modifierPattern;

		/** The replacement text (in case of SimpleDiagnostic). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String modifierReplacement;

		/**
		 * Instantiates a new rs policy.
		 */
		public RsDiagnostic() {

		}
	}


	/**
	 * Gets the diagnotics.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the diagnotics
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/diagnostics")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the diagnostics",
		description = "Returns the list of diagnostics."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public List<Diagnostic> getDiagnostics(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get diagnotics.");
		Session session = Database.getSession(true);
		try {
			Query<Diagnostic> query =
				session.createQuery("select d from Diagnostic d left join fetch d.targetGroup", Diagnostic.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostics.", e);
			throw new NetshotBadRequestException("Unable to fetch the diagnostics",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Gets a specific diagnotic.
	 *
	 * @param id = the ID of the diagnostic
	 * @return the diagnotic
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get a diagnostic",
		description = "Returns a specific diagnostic, by ID."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public Diagnostic getDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id) throws WebApplicationException {
		log.debug("REST request, get diagnotic.");
		Session session = Database.getSession(true);
		try {
			Query<Diagnostic> query = session
				.createQuery("select d from Diagnostic d left join fetch d.targetGroup where d.id = :id", Diagnostic.class)
				.setParameter("id", id);
			Diagnostic diagnostic = query.uniqueResult();
			if (diagnostic == null) {
				log.warn("Unable to find the diagnostic object.");
				throw new NetshotBadRequestException("Unable to find the diagnostic",
					ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND);
			}
			return diagnostic;
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostic.", e);
			throw new NetshotBadRequestException("Unable to fetch the diagnostic",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	@POST
	@Path("/diagnostics")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a diagnostic.",
		description = "Creates a diagnostic."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public Diagnostic addDiagnostic(RsDiagnostic rsDiagnostic) throws WebApplicationException {
		log.debug("REST request, add diagnostic");
		String name = rsDiagnostic.getName().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty diagnostic name.");
			throw new NetshotBadRequestException("Invalid diagnostic name.",
				ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME);
		}
		if (name.contains("\"")) {
			log.warn("Double-quotes are not allowed in the diagnostic name.");
			throw new NetshotBadRequestException("Double-quotes are not allowed in the name.",
				ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME);
		}
		AttributeType resultType;
		try {
			resultType = AttributeType.valueOf(rsDiagnostic.getResultType());
		}
		catch (Exception e) {
			throw new NetshotBadRequestException("Invalid diagnostic result type.",
				ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
		}

		Diagnostic diagnostic;
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			DeviceGroup group = null;
			if (rsDiagnostic.getTargetGroup() != -1) {
				group = session.get(DeviceGroup.class, rsDiagnostic.getTargetGroup());
			}

			if ("JavaScriptDiagnostic".equals(rsDiagnostic.getType())) {
				if (rsDiagnostic.getScript() == null || "".equals(rsDiagnostic.getScript().trim())) {
					throw new NetshotBadRequestException(
						"Invalid diagnostic script",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				diagnostic = new JavaScriptDiagnostic(name, rsDiagnostic.isEnabled(), group,
					resultType, rsDiagnostic.getScript());
			}
			else if ("PythonDiagnostic".equals(rsDiagnostic.getType())) {
				if (rsDiagnostic.getScript() == null || "".equals(rsDiagnostic.getScript().trim())) {
					throw new NetshotBadRequestException(
						"Invalid diagnostic script",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				diagnostic = new PythonDiagnostic(name, rsDiagnostic.isEnabled(), group,
					resultType, rsDiagnostic.getScript());
			}
			else if ("SimpleDiagnostic".equals(rsDiagnostic.getType())) {
				if (rsDiagnostic.getCliMode() == null || "".equals(rsDiagnostic.getCliMode().trim())) {
					throw new NetshotBadRequestException("The CLI mode must be provided.",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				if (rsDiagnostic.getCommand() == null || "".equals(rsDiagnostic.getCommand().trim())) {
					throw new NetshotBadRequestException("The command cannot be empty.",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				diagnostic = new SimpleDiagnostic(name, rsDiagnostic.isEnabled(), group, resultType, rsDiagnostic.getDeviceDriver(),
					rsDiagnostic.getCliMode(), rsDiagnostic.getCommand(), rsDiagnostic.getModifierPattern(),
					rsDiagnostic.getModifierReplacement());
			}
			else {
				throw new NetshotBadRequestException(
					"Invalid diagnostic type.",
					ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_TYPE);
			}

			session.persist(diagnostic);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created", diagnostic);
			this.suggestReturnCode(Response.Status.CREATED);
			return diagnostic;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The posted group doesn't exist", e);
			throw new NetshotBadRequestException(
				"Invalid group",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new diagnostic.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A diagnostic with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC);
			}
			throw new NetshotBadRequestException(
				"Unable to add the policy to the database.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Updates the diagnostic.
	 * 
	 * @param id the diagnostic ID
	 * @param rsDiagnostic the passed diagnostic
	 * @return the updated diagnostic
	 * @throws WebApplicationException a web application exception
	 */
	@PUT
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a diagnostic",
		description = "Creates a new diagnostic."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public Diagnostic setDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id, RsDiagnostic rsDiagnostic)
		throws WebApplicationException {
		log.debug("REST request, edit diagnostic {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Diagnostic diagnostic = session.get(Diagnostic.class, id);
			if (diagnostic == null) {
				log.error("Unable to find the diagnostic {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this diagnostic.",
					ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
			}

			String name = rsDiagnostic.getName().trim();
			if (name.isEmpty()) {
				log.warn("User posted an empty diagnostic name.");
				throw new NetshotBadRequestException("Invalid diagnostic name.",
					ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME);
			}
			if (name.contains("\"")) {
				log.warn("Double-quotes are not allowed in the diagnostic name.");
				throw new NetshotBadRequestException("Double-quotes are not allowed in the name.",
					ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME);
			}
			AttributeType resultType;
			try {
				resultType = AttributeType.valueOf(rsDiagnostic.getResultType());
			}
			catch (Exception e) {
				throw new NetshotBadRequestException("Invalid diagnostic result type.",
					ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
			}
			diagnostic.setName(name);
			if (diagnostic.getTargetGroup() != null && diagnostic.getTargetGroup().getId() != rsDiagnostic.getTargetGroup()) {
				session.createMutationQuery("delete DiagnosticResult dr where dr.diagnostic.id = :id")
					.setParameter("id", diagnostic.getId())
					.executeUpdate();
			}
			DeviceGroup group = null;
			if (rsDiagnostic.getTargetGroup() != -1) {
				group = session.get(DeviceGroup.class, rsDiagnostic.getTargetGroup());
			}
			diagnostic.setTargetGroup(group);

			diagnostic.setResultType(resultType);
			diagnostic.setEnabled(rsDiagnostic.isEnabled());
			if (diagnostic instanceof JavaScriptDiagnostic javaScriptDiagnostic) {
				if (!"JavaScriptDiagnostic".equals(rsDiagnostic.getType())) {
					throw new NetshotBadRequestException("Incompatible posted diagnostic.",
						ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC);
				}
				if (rsDiagnostic.getScript() == null || "".equals(rsDiagnostic.getScript().trim())) {
					throw new NetshotBadRequestException(
						"Invalid diagnostic script",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				javaScriptDiagnostic.setScript(rsDiagnostic.getScript());
			}
			else if (diagnostic instanceof PythonDiagnostic pythonDiagnostic) {
				if (!"PythonDiagnostic".equals(rsDiagnostic.getType())) {
					throw new NetshotBadRequestException("Incompatible posted diagnostic.",
						ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC);
				}
				if (rsDiagnostic.getScript() == null || "".equals(rsDiagnostic.getScript().trim())) {
					throw new NetshotBadRequestException(
						"Invalid diagnostic script",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				pythonDiagnostic.setScript(rsDiagnostic.getScript());
			}
			else if (diagnostic instanceof SimpleDiagnostic simpleDiagnostic) {
				if (!"SimpleDiagnostic".equals(rsDiagnostic.getType())) {
					throw new NetshotBadRequestException("Incompatible posted diagnostic.",
						ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC);
				}
				if (rsDiagnostic.getCliMode() == null || "".equals(rsDiagnostic.getCliMode().trim())) {
					throw new NetshotBadRequestException("The CLI mode must be provided.",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				if (rsDiagnostic.getCommand() == null || "".equals(rsDiagnostic.getCommand().trim())) {
					throw new NetshotBadRequestException("The command cannot be empty.",
						ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
				}
				simpleDiagnostic.setDeviceDriver(rsDiagnostic.getDeviceDriver());
				simpleDiagnostic.setCliMode(rsDiagnostic.getCliMode());
				simpleDiagnostic.setCommand(rsDiagnostic.getCommand());
				simpleDiagnostic.setModifierPattern(rsDiagnostic.getModifierPattern());
				simpleDiagnostic.setModifierReplacement(rsDiagnostic.getModifierReplacement());
			}

			session.merge(diagnostic);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", diagnostic);
			return diagnostic;
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("Unable to find the group {} to be assigned to the diagnostic {}.",
				rsDiagnostic.getTargetGroup(), id, e);
			throw new NetshotBadRequestException(
				"Unable to find the group.",
				ErrorCode.NETSHOT_INVALID_GROUP);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to save the diagnostic {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException("A diagnostic with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC);
			}
			throw new NetshotBadRequestException("Unable to save the policy.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		catch (WebApplicationException e) {
			Database.rollbackSilently(session);
			throw e;
		}
		finally {
			session.close();
		}
	}


	/**
	 * Delete diagnostic.
	 *
	 * @param id the id of the diagnostic to delete
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/diagnostics/{id}")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a diagnostic",
		description = "Removes a given diagnostic, by ID."
	)
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public void deleteDiagnostic(@PathParam("id") @Parameter(description = "Diagnostic ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete diagnostic {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Diagnostic diagnostic = session.get(Diagnostic.class, id);
			if (diagnostic == null) {
				log.info("No such diagnostic of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			// Remove the related results
			session
				.createMutationQuery("delete from DiagnosticResult dr where dr.diagnostic = :diagnostic")
				.setParameter("diagnostic", diagnostic)
				.executeUpdate();
			// Remove the diagnostic
			session.remove(diagnostic);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been deleted", diagnostic);
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The diagnostic {} to be deleted doesn't exist.", id, e);
			throw new NetshotBadRequestException("The diagnostic doesn't exist.",
				ErrorCode.NETSHOT_INVALID_DIAGNOSTIC);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the diagnostic {}.", id, e);
			throw new NetshotBadRequestException("Unable to delete the diagnostic",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the device diagnostic results.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device compliance
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/diagnosticresults")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get diagnostic results",
		description = "Returns the results of a given diagnostic, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(
		name = "Diagnostics",
		description = "Diagnostic management (execute commands and retrieve custom data from devices)"
	)
	public List<DiagnosticResult> getDeviceDiagnosticResults(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Device ID") Long id) throws WebApplicationException {
		log.debug("REST request, get diagnostic results for device {}.", id);
		Session session = Database.getSession(true);
		try {
			Query<DiagnosticResult> query = session
				.createQuery(
					"from DiagnosticResult dr join fetch dr.diagnostic where dr.device.id = :id",
					DiagnosticResult.class)
				.setParameter("id", id);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the diagnostic results.", e);
			throw new NetshotBadRequestException("Unable to fetch the diagnostic results",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the hooks.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the current hooks
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/hooks")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the hooks",
		description = "Returns the current list of hooks."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<Hook> getHooks(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, hooks.");
		Session session = Database.getSession(true);
		try {
			Query<Hook> query = session.createQuery("select distinct h from Hook h left join fetch h.triggers", Hook.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the hooks.", e);
			throw new NetshotBadRequestException("Unable to fetch the hooks",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Adds the hook.
	 *
	 * @param hook the hook
	 * @return the new hook
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/hooks")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a hook",
		description = "Creates a hook. Based on given criteria, Netshot will run the given action when specific events occur."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public Hook addHook(Hook hook) throws WebApplicationException {
		log.debug("REST request, add hook.");
		if (hook.getName() == null || "".equals(hook.getName().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotBadRequestException("Invalid name for the hook",
				ErrorCode.NETSHOT_INVALID_HOOK_NAME);
		}
		if (hook.getTriggers() != null) {
			for (HookTrigger trigger : hook.getTriggers()) {
				trigger.setHook(hook);
			}
		}
		if (hook instanceof WebHook webHook) {
			try {
				webHook.getParsedUrl();
			}
			catch (MalformedURLException e) {
				throw new NetshotBadRequestException("Invalid Web hook target URL",
					ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL);
			}
			if (webHook.getAction() == null) {
				throw new NetshotBadRequestException("Invalid action",
					ErrorCode.NETSHOT_INVALID_HOOK_WEB);
			}
			RestService.validateWebHookTrustPolicy(webHook);
		}
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.persist(hook);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created.", hook);
			this.suggestReturnCode(Response.Status.CREATED);
			return hook;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			Throwable t = e.getCause();
			log.error("Can't add the hook.", e);
			if (t != null && t.getMessage().contains("uplicate")) {
				throw new NetshotBadRequestException(
					"A hook with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_HOOK);
			}
			throw new NetshotBadRequestException("Unable to save the hook",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete hook.
	 *
	 * @param id the id of the hook
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/hooks/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a hook",
		description = "Removes the hook, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteHook(@PathParam("id") @Parameter(description = "Hook ID") Long id) throws WebApplicationException {
		log.debug("REST request, delete hook {}", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			Hook hook = session.get(Hook.class, id);
			if (hook == null) {
				log.info("No such hook of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(hook);
			session.getTransaction().commit();
			AAA_LOG.info("Hook of ID {} has been deleted.", hook.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Unable to delete the hook {}", id, e);
			throw new NetshotBadRequestException(
				"Unable to delete the hook",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Sets the hook.
	 *
	 * @param id the id
	 * @param rsHook the hook
	 * @return the updated hook
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/hooks/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a hook",
		description = "Edits a hook, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public Hook setHook(@PathParam("id") @Parameter(description = "Hook ID") Long id, Hook rsHook) throws WebApplicationException {
		log.debug("REST request, edit hook {}", id);
		rsHook.setId(id);
		if (rsHook.getTriggers() == null) {
			// No triggers (null) means none, as on creation
			rsHook.setTriggers(new HashSet<>());
		}
		if (rsHook.getName() == null || "".equals(rsHook.getName().trim())) {
			log.error("Invalid hook name.");
			throw new NetshotBadRequestException("Invalid name for the hook",
				ErrorCode.NETSHOT_INVALID_HOOK_NAME);
		}
		if (rsHook instanceof WebHook rsWebHook) {
			try {
				rsWebHook.getParsedUrl();
			}
			catch (MalformedURLException e) {
				throw new NetshotBadRequestException("Invalid Web hook target URL",
					ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL);
			}
			if (rsWebHook.getAction() == null) {
				throw new NetshotBadRequestException("Invalid action",
					ErrorCode.NETSHOT_INVALID_HOOK_WEB);
			}
			RestService.validateWebHookTrustPolicy(rsWebHook);
		}
		{
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				Hook hook = session.get(rsHook.getClass(), id);
				if (hook == null) {
					log.error("Unable to find the hook {}.", id);
					throw new NetshotBadRequestException(
						"Unable to find the hook.",
						ErrorCode.NETSHOT_INVALID_HOOK);
				}
				if (!hook.getClass().equals(rsHook.getClass())) {
					log.error("Wrong posted type for hook {}.", id);
					throw new NetshotBadRequestException(
						"The posted hook type doesn't match the existing one.",
						ErrorCode.NETSHOT_INVALID_HOOK_TYPE);
				}
				hook.setEnabled(rsHook.isEnabled());
				hook.setName(rsHook.getName());
				for (HookTrigger trigger : rsHook.getTriggers()) {
					trigger.setHook(hook);
				}
				// Ensure we keep the same HookTrigger object instances
				hook.getTriggers().retainAll(rsHook.getTriggers());
				hook.getTriggers().addAll(rsHook.getTriggers());
				if (rsHook instanceof WebHook rsWebHook
						&& hook instanceof WebHook webHook) {
					webHook.setAction(rsWebHook.getAction());
					webHook.setUrl(rsWebHook.getUrl());
					webHook.setHttpsCaTrustMode(rsWebHook.getHttpsCaTrustMode());
					webHook.setHttpsCustomCaCertificate(rsWebHook.getHttpsCustomCaCertificate());
				}
				session.merge(hook);
				session.getTransaction().commit();
				AAA_LOG.info("{} has been edited", rsHook);
				return rsHook;
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				Throwable t = e.getCause();
				log.error("Unable to save the hook {}.", id, e);
				if (t != null && t.getMessage().contains("uplicate")) {
					throw new NetshotBadRequestException(
						"A hook with this name already exists.",
						ErrorCode.NETSHOT_DUPLICATE_HOOK);
				}
				throw new NetshotBadRequestException("Unable to save the hook",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			catch (NetshotBadRequestException e) {
				Database.rollbackSilently(session);
				throw e;
			}
			finally {
				session.close();
			}
		}
	}

}
