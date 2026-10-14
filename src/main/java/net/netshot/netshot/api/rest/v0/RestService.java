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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

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
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.auth.Radius;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.NetshotAuthenticationRequiredException;
import net.netshot.netshot.api.rest.NetshotBadRequestException;
import net.netshot.netshot.api.rest.NetshotNotAuthorizedException;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.NetshotWebApplication;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.api.rest.SecurityFilter;
import net.netshot.netshot.credentials.CredentialSets;
import net.netshot.netshot.platform.tls.CaCertificates;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterMember.MastershipStatus;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.HideSecretDeserializer;
import net.netshot.netshot.credentials.HideSecretSerializer;
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
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskContext;
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

	/**
	 * The Class RsLogin.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsLogin {

		/** The username. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String username;

		/** The password. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String password;

		/** The new password. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String newPassword;

		/** The OIDC authorization code. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String authorizationCode;

		/** The redirect URI. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String redirectUri;
	}

	/**
	 * Logout.
	 *
	 * @param id the ID of the user (ignored)
	 * @return the boolean
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/user/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "User log out",
		description = "Terminates the current user session (useless when using API tokens)."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public Response logout(@PathParam("id") @Parameter(description = "User ID - not used") Long id)
		throws WebApplicationException {
		log.debug("REST logout request.");
		HttpSession httpSession = request.getSession(false);
		User user = (User) securityContext.getUserPrincipal();
		String username = user == null ? "<none>" : user.getUsername();
		if (httpSession != null) {
			httpSession.invalidate();
		}
		AAA_LOG.warn("User {} has logged out.", username);
		Response.ResponseBuilder builder = Response.noContent();
		builder.status(Response.Status.NO_CONTENT);
		if (Oidc.isAvailable()) {
			builder.header("X-OIDC-EndSessionEndpoint", Oidc.getEndSessionEndpointURI());
		}
		return builder.build();
	}

	/**
	 * Sets the password.
	 *
	 * @param rsLogin the rs login
	 * @param id the user ID (ignored)
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/user/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update self user",
		description = "Edits current user, especially password."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public UiUser setSelfUser(
		RsLogin rsLogin,
		@PathParam("id") @Parameter(description = "User ID (ignored)") Long id)
		throws WebApplicationException {
		log.debug("REST password change request, username {}.", rsLogin.getUsername());
		User currentUser = (User) securityContext.getUserPrincipal();
		AAA_LOG.warn("Password change request via REST by user {} for user {}.", currentUser.getUsername(), rsLogin.getUsername());

		UiUser user;
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			user = session.bySimpleNaturalId(UiUser.class).load(currentUser.getUsername());
			if (user == null || !user.getUsername().equals(rsLogin.getUsername()) || !user.isLocal()) {
				throw new NetshotBadRequestException("Invalid user.",
					ErrorCode.NETSHOT_INVALID_USER);
			}

			try {
				user.checkPassword(rsLogin.getPassword(), PasswordPolicy.getMainPolicy());
			}
			catch (WrongPasswordException e) {
				throw new NetshotBadRequestException("Invalid current password.",
					ErrorCode.NETSHOT_INVALID_PASSWORD);
			}
			catch (PasswordPolicyException e) {
				// Ignore to let user update the password
			}

			String newPassword = rsLogin.getNewPassword();
			if (newPassword == null || "".equals(newPassword)) {
				throw new NetshotBadRequestException("The password cannot be empty.",
					ErrorCode.NETSHOT_INVALID_PASSWORD);
			}

			try {
				user.setPassword(newPassword, PasswordPolicy.getMainPolicy());
			}
			catch (PasswordPolicyException e) {
				log.error("New user password doesn't comply with password policy.", e);
				throw new NetshotBadRequestException(
					e.getMessage(),
					ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY);
			}
			session.persist(user);
			session.getTransaction().commit();
			AAA_LOG.warn("Password successfully changed by user {} for user {}.",
				currentUser.getUsername(), rsLogin.getUsername());
			return user;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to retrieve the user {}.", rsLogin.getUsername(), e);
			throw new NetshotBadRequestException("Unable to retrieve the user.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Login.
	 *
	 * @param rsLogin the rs login
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@PermitAll
	@Path("/user")
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Log in",
		description = "Logs in (create session) by username and password or OIDC authorization code (useless when using API tokens)."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public UiUser login(RsLogin rsLogin) throws WebApplicationException {
		log.debug("REST authentication request, username {}.", rsLogin.getUsername());
		AAA_LOG.info("REST authentication request, username {}.", rsLogin.getUsername());

		String remoteAddress = LoggerFilter.getClientAddress(request);
		UiUser user = null;
		UiUser remoteUser = null;
		String username = rsLogin.getUsername();

		if (username == null && rsLogin.getAuthorizationCode() != null && rsLogin.getRedirectUri() != null) {
			try {
				remoteUser = Oidc.authenticateWithCode(
					rsLogin.getAuthorizationCode(),
					URI.create(rsLogin.getRedirectUri()).toURL());
				if (remoteUser != null) {
					username = remoteUser.getUsername();
				}
			}
			catch (MalformedURLException e) {
				log.error("Error while extracting client request URL", e);
			}
		}

		if (username != null) {
			Session session = Database.getSession(true);
			try {
				user = session.bySimpleNaturalId(UiUser.class).load(username);
			}
			catch (HibernateException e) {
				log.error("Unable to retrieve the user {}.", username, e);
				throw new NetshotBadRequestException("Unable to retrieve the user.",
					ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
			}
			finally {
				session.close();
			}
		}

		if (user != null && user.isLocal()) {
			try {
				if (remoteUser != null) {
					// Remote login (via OIDC) while a corresponding user configured as local exists
					throw new WrongPasswordException("Remote login attempt with username which is configured as Netshot local");
				}
				try {
					user.checkPassword(rsLogin.getPassword(), PasswordPolicy.getMainPolicy());
					AAA_LOG.info(
						"Local authentication success for user {} from {}.", username, remoteAddress);
				}
				catch (PasswordPolicyException e) {
					if (rsLogin.getNewPassword() == null) {
						throw e;
					}
					AAA_LOG.info(
						"User {} authenticated from {}, password has to be changed.", username, remoteAddress);
					// If new password, proceed with password change
				}
				if (rsLogin.getNewPassword() != null) {
					Session session = Database.getSession();
					try {
						session.beginTransaction();
						UiUser user1 = session.get(UiUser.class, user.getId());
						user1.setPassword(rsLogin.getNewPassword(), PasswordPolicy.getMainPolicy());
						session.merge(user1);
						session.getTransaction().commit();
						AAA_LOG.info(
							"User {} changed its password.", username, remoteAddress);
					}
					catch (HibernateException e) {
						Database.rollbackSilently(session);
						log.error("Error while updating a user.", e);
						throw new NetshotBadRequestException(
							"Unable to update the user in the database",
							ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
					}
					catch (PasswordPolicyException e) {
						Database.rollbackSilently(session);
						log.error("New user password doesn't comply with password policy.", e);
						user = null;
						throw new NetshotBadRequestException(
							e.getMessage(),
							ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY);
					}
					finally {
						session.close();
					}
				}
			}
			catch (WrongPasswordException e) {
				AAA_LOG.warn(
					"Local authentication failure for user {} from {}.",
					username, remoteAddress);
				user = null;
			}
			catch (PasswordPolicyException e) {
				AAA_LOG.warn(
					"Password of user {} is expired, it must be changed.", username);
				user = null;
				throw new NetshotBadRequestException(
					"Password has expired, it must be changed.",
					ErrorCode.NETSHOT_EXPIRED_PASSWORD);
			}
		}
		else if (username != null) {
			if (remoteUser == null && Radius.isAvailable()) {
				remoteUser = Radius.authenticate(username, rsLogin.getPassword(), remoteAddress);
			}
			if (remoteUser == null && Tacacs.isAvailable()) {
				remoteUser = Tacacs.authenticate(username, rsLogin.getPassword(), remoteAddress);
			}
			if (remoteUser == null) {
				AAA_LOG.warn("Remote authentication failure for user {} from {}.",
					username, remoteAddress);
			}
			else {
				AAA_LOG.info("Remote authentication success for user {} from {}.",
					username, remoteAddress);
				if (user != null) {
					remoteUser.setLevel(user.getLevel());
					AAA_LOG.info("Level permission for user {} is locally overriden: {}.",
						username, user.getLevel());
				}
			}
			user = remoteUser;
		}

		// Always drop the existing session (if any): a new session (with new ID) is created
		// on successful authentication, to prevent session fixation
		HttpSession previousSession = request.getSession(false);
		if (previousSession != null) {
			previousSession.invalidate();
		}
		if (user != null && user.getLevel() <= 0) {
			AAA_LOG.info(
				"User {} was successfully authenticated but has unsufficient permission (level {})",
				user.getUsername(), user.getLevel());
		}
		else if (user != null) {
			HttpSession httpSession = request.getSession(true);
			httpSession.setAttribute(SecurityFilter.USER_ATTRIBUTE, user);
			return user;
		}
		throw new NetshotAuthenticationRequiredException();
	}

	/**
	 * Gets the user.
	 *
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Path("/user")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the current user",
		description = "Returns the current logged in user."
	)
	@Tag(name = "Login", description = "Login and password management for standard user")
	public User getUser() throws WebApplicationException {
		User user = (User) securityContext.getUserPrincipal();
		return user;
	}

	/**
	 * Gets the users.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the users
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/users")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the users",
		description = "Returns the list of Netshot users."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<UiUser> getUsers(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get user list.");
		Session session = Database.getSession(true);
		try {
			Query<UiUser> query = session.createQuery("from net.netshot.netshot.aaa.user.UiUser", UiUser.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the users.", e);
			throw new NetshotBadRequestException("Unable to retrieve the users.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * The Class RsUser.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsUser {

		/** The id. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private long id;

		/** The username. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String username;

		/** The password. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class),
			@JsonSerialize(contentUsing = HideSecretSerializer.class),
			@JsonDeserialize(contentUsing = HideSecretDeserializer.class),
		}))
		@Setter
		private String password;

		/** The level. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private int level;

		/** The local. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean local;
	}

	/**
	 * Adds the user.
	 *
	 * @param rsUser the rs user
	 * @return the user
	 */
	@POST
	@Path("/users")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a user to Netshot",
		description = "Create a Netshot user."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public UiUser addUser(RsUser rsUser) {
		log.debug("REST request, add user");

		String username = rsUser.getUsername();
		if (username == null || username.trim().isEmpty()) {
			log.warn("User posted an empty user name.");
			throw new NetshotBadRequestException("Invalid user name.",
				ErrorCode.NETSHOT_INVALID_USER_NAME);
		}
		username = username.trim();

		String password = rsUser.getPassword();
		if (rsUser.isLocal()) {
			if (password == null || "".equals(password)) {
				log.warn("User tries to create a local account without password.");
				throw new NetshotBadRequestException("Please set a password.",
					ErrorCode.NETSHOT_INVALID_PASSWORD);
			}
		}
		else {
			password = "";
		}

		UiUser user = new UiUser(username, rsUser.isLocal(), password);
		user.setLevel(rsUser.level);

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.persist(user);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created", user);
			this.suggestReturnCode(Response.Status.CREATED);
			return user;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new user.", e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A user with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_USER);
			}
			throw new NetshotBadRequestException(
				"Unable to add the user to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * Sets the user.
	 *
	 * @param id the id
	 * @param rsUser the rs user
	 * @return the user
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/users/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Update a Netshot user",
		description = "Edits a Netshot user, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public UiUser setUser(
		@PathParam("id") @Parameter(description = "User ID") Long id,
		RsUser rsUser) throws WebApplicationException {
		log.debug("REST request, edit user {}.", id);
		User currentUser = (User) securityContext.getUserPrincipal();
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			UiUser user = session.get(UiUser.class, id);
			if (user == null) {
				log.error("Unable to find the user {} to be edited.", id);
				throw new NetshotBadRequestException("Unable to find this user.",
					ErrorCode.NETSHOT_INVALID_USER);
			}
			if (user.getUsername().equals(currentUser.getUsername())) {
				throw new NetshotBadRequestException(
					"Cannot edit the user who is making the request.",
					ErrorCode.NETSHOT_LOCKED_USER);
			}

			String username = rsUser.getUsername();
			if (username == null || username.trim().isEmpty()) {
				log.warn("User posted an empty user name.");
				throw new NetshotBadRequestException("Invalid user name.",
					ErrorCode.NETSHOT_INVALID_USER_NAME);
			}
			username = username.trim();
			user.setUsername(username);

			user.setLevel(rsUser.getLevel());
			if (rsUser.isLocal()) {
				if (rsUser.getPassword() == null) {
					if (!user.isLocal()) {
						log.error("The password cannot be empty for user {}.", id);
						throw new NetshotBadRequestException("You must set a password.",
							ErrorCode.NETSHOT_INVALID_PASSWORD);
					}
				}
				else {
					try {
						user.setPassword(rsUser.getPassword(), PasswordPolicy.getMainPolicy());
					}
					catch (PasswordPolicyException e) {
						log.error("New user password doesn't comply with password policy.", e);
						throw new NetshotBadRequestException(
							e.getMessage(),
							ErrorCode.NETSHOT_INVALID_PASSWORD);
					}
				}
			}
			else {
				user.setHashedPassword("");
			}
			user.setLocal(rsUser.isLocal());
			session.merge(user);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been edited", user);
			return user;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Unable to save the user {}.", id, e);
			if (Database.isDuplicateException(e)) {
				throw new NetshotBadRequestException(
					"A user with this name already exists.",
					ErrorCode.NETSHOT_DUPLICATE_USER);
			}
			throw new NetshotBadRequestException("Unable to save the user.",
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
	 * Delete user.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/users/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove a Netshot user.",
		description = "Removes a user from the Netshot database."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteUser(
		@PathParam("id") @Parameter(description = "User ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete user {}.", id);
		User currentUser = (User) securityContext.getUserPrincipal();
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			UiUser user = session.get(UiUser.class, id);
			if (user == null) {
				log.info("No such user of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			if (user.getUsername().equals(currentUser.getUsername())) {
				throw new NetshotBadRequestException(
					"Cannot delete the user who is making the request.",
					ErrorCode.NETSHOT_LOCKED_USER);
			}
			session.remove(user);
			session.getTransaction().commit();
			AAA_LOG.info("User {} of ID {} has been deleted", user.getUsername(), user.getId());
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			throw new NetshotBadRequestException("Unable to delete the user.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}


	/**
	 * The Class RsApiToken.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsApiToken {

		/** The id. */
		private long id;

		/** The description. */
		private String description;

		/** The token. */
		private String token;

		/** The level. */
		private int level;

		/**
		 * Gets the id.
		 *
		 * @return the id
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public long getId() {
			return id;
		}

		/**
		 * Sets the id.
		 *
		 * @param id the new id
		 */
		public void setId(long id) {
			this.id = id;
		}

		/**
		 * Gets the description.
		 *
		 * @return the description
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public String getDescription() {
			return description;
		}

		/**
		 * Sets the description.
		 *
		 * @param description the new description
		 */
		public void setDescription(String description) {
			this.description = description;
		}

		/**
		 * Gets the token.
		 *
		 * @return the token
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public String getToken() {
			return token;
		}

		/**
		 * Sets the token.
		 *
		 * @param token the new token
		 */
		public void setToken(String token) {
			this.token = token;
		}

		/**
		 * Gets the level.
		 *
		 * @return the level
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public int getLevel() {
			return level;
		}

		/**
		 * Sets the level.
		 *
		 * @param level the new level
		 */
		public void setLevel(int level) {
			this.level = level;
		}
	}

	/**
	 * Adds the API token.
	 *
	 * @param rsApiToken the posted API token
	 * @return the API token
	 */
	@POST
	@Path("/apitokens")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Add a new API token",
		description = "Creates a new API token."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public ApiToken addApiToken(RsApiToken rsApiToken) {
		log.debug("REST request, add API token");

		String description = rsApiToken.getDescription();
		if (description == null || description.trim().isEmpty()) {
			description = "";
		}
		description = description.trim();

		String token = rsApiToken.getToken();
		if (!ApiToken.isValidToken(token)) {
			log.warn("The passed token is not valid");
			throw new NetshotBadRequestException("Invalid token format.",
				ErrorCode.NETSHOT_INVALID_API_TOKEN_FORMAT);
		}

		ApiToken apiToken = new ApiToken(description, token, rsApiToken.getLevel());

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			session.persist(apiToken);
			session.getTransaction().commit();
			AAA_LOG.info("{} has been created", apiToken);
			this.suggestReturnCode(Response.Status.CREATED);
			return apiToken;
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			log.error("Error while saving the new API token.", e);
			throw new NetshotBadRequestException(
				"Unable to add the API token to the database",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Gets the API tokens.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the API tokens
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/apitokens")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get the API tokens",
		description = "Returns the list of API tokens."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<ApiToken> getApiTokens(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get API token list.");
		Session session = Database.getSession(true);
		try {
			Query<ApiToken> query = session.createQuery("from ApiToken", ApiToken.class);
			paginationParams.apply(query);
			return query.list();
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the API tokens.", e);
			throw new NetshotBadRequestException("Unable to retrieve the API tokens.",
				ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR);
		}
		finally {
			session.close();
		}
	}

	/**
	 * Delete API token.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/apitokens/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Remove an API token",
		description = "Removes an API token, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteApiToken(@PathParam("id") @Parameter(description = "Token ID") Long id) throws WebApplicationException {
		log.debug("REST request, delete API token {}.", id);
		Session session = Database.getSession();
		try {
			session.beginTransaction();
			ApiToken apiToken = session.get(ApiToken.class, id);
			if (apiToken == null) {
				log.info("No such API token of ID {}", id);
				this.suggestReturnCode(Response.Status.NOT_FOUND);
				return;
			}
			session.remove(apiToken);
			session.getTransaction().commit();
			AAA_LOG.info("API token of ID {} has been deleted", apiToken);
			this.suggestReturnCode(Response.Status.NO_CONTENT);
		}
		catch (ObjectNotFoundException e) {
			Database.rollbackSilently(session);
			log.error("The API token doesn't exist.");
			throw new NetshotBadRequestException("The API token doesn't exist.",
				ErrorCode.NETSHOT_INVALID_USER);
		}
		catch (HibernateException e) {
			Database.rollbackSilently(session);
			throw new NetshotBadRequestException("Unable to delete the API token.",
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

	/**
	 * Gets the cluster members.
	 *
	 * @return the cluster members
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/cluster/members")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get cluster members",
		description = "Returns the members of the Netshot high availability cluster "
			+ "(empty list if clustering is not enabled)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<ClusterMember> getClusterMembers() throws WebApplicationException {
		log.debug("REST request, get cluster members");
		return ClusterManager.getClusterMembers();
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsClusterMasterCheck {

		/** Is clustering enabled. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean clusterEnabled;

		/** Is local server master. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private boolean master = true;

		/** Current master instance ID. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String currentMasterId;
	}

	/**
	 * Gets the cluster mastership status of the current node.
	 *
	 * @return the mastership status
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@PermitAll
	@Path("/cluster/masterstatus")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Check if local server is cluster master",
		description = "Returns cluster mastership info of the current node. "
			+ "Might be used by local-balancer to redirect http to the proper server "
			+ "(return code 205 means the local server is not master)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public RsClusterMasterCheck getClusterMasterStatus() throws WebApplicationException {
		RsClusterMasterCheck check = new RsClusterMasterCheck();
		List<ClusterMember> members = ClusterManager.getClusterMembers();
		if (members.size() > 0) {
			check.setClusterEnabled(true);
			check.setMaster(false);
			for (ClusterMember member : members) {
				if (MastershipStatus.MASTER.equals(member.getStatus())) {
					check.setCurrentMasterId(member.getInstanceId());
					if (member.isLocal()) {
						check.setMaster(true);
					}
				}
			}
			if (!check.isMaster()) {
				this.suggestReturnCode(Response.Status.RESET_CONTENT);
			}
		}
		return check;
	}

	/**
	 * Utility class to send server information.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class RsServerInfo {

		/**
		 * Gets the server version.
		 *
		 * @return the server version
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public String getServerVersion() {
			return Netshot.VERSION;
		}

		/**
		 * Gets the user max idle time (in seconds).
		 * 
		 * @return the max idle time
		 */
		@XmlElement
		@JsonView(DefaultView.class)
		public int getMaxIdleTimeout() {
			return UiUser.SETTINGS.getMaxIdleTime();
		}
	}

	@GET
	@Path("/serverinfo")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@JsonView(RestApiView.class)
	@Operation(
		summary = "Get Netshot server info.",
		description = "Retrieves some general info about Netshot server."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public RsServerInfo getServerInfo() {
		log.debug("REST request, get server info");
		return new RsServerInfo();
	}

}
