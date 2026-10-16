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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;

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
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.NetshotBadRequestException;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.NetshotWebApplication;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.credentials.CredentialSets;
import net.netshot.netshot.platform.tls.CaCertificates;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
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
