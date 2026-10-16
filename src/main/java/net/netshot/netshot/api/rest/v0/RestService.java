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
import java.net.URI;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
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
import net.netshot.netshot.api.rest.NetshotWebApplication;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;

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

}
