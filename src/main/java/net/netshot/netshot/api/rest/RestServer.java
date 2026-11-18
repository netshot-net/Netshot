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
package net.netshot.netshot.api.rest;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.servlet.ServletContainer;

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
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.api.rest.v0.V0Application;
import net.netshot.netshot.api.rest.v1.V1Application;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The web server of Netshot: a thread which starts Undertow, serving the Web UI (static files) and the
 * REST API (the Jersey applications of the legacy API, {@link V0Application}, on the API path, and of the v1 API,
 * {@link V1Application}, on {@code <API path>/v1}) with the HTTP(S) and session cookie
 * settings of the configuration.
 */
@Slf4j
public final class RestServer implements Service {

	/** Whether the HTTP server is enabled. */
	public static final Setting<Boolean> ENABLED = Setting.bool("netshot.http.enabled").defaultValue(true).restart();

	/** The path the static files (web UI) are served on. */
	static final Setting<String> STATIC_PATH = Setting.text("netshot.http.staticpath").defaultValue("/").restart();

	/** The path the REST API is served on. */
	static final Setting<String> API_PATH = Setting.text("netshot.http.apipath").defaultValue("/api").restart();

	/** Whether the legacy (v0) REST API is served (off by default: the web UI uses the v1 API). */
	static final Setting<Boolean> V0_ENABLED = Setting.bool("netshot.http.api.v0.enabled").defaultValue(false).restart();

	/** Name of the session cookie. */
	static final Setting<String> SESSION_COOKIE_NAME = Setting.text("netshot.http.sessioncookiename")
		.defaultValue("NetshotSessionID").restart();

	/** The keystore file (HTTPS). */
	static final Setting<String> KEYSTORE_FILE = Setting.text("netshot.http.ssl.keystore.file").defaultValue("netshot.jks").restart();

	/** The keystore password (HTTPS). */
	static final Setting<String> KEYSTORE_PASS = Setting.text("netshot.http.ssl.keystore.pass")
		.defaultValue("netshotpass").secret().restart();

	/** Whether the session cookie is marked as Secure (default: with HTTPS, or behind a reverse proxy). */
	static final Setting<Boolean> SECURE_COOKIE = Setting.bool("netshot.http.securecookie").restart();

	/**
	 * The settings of the server, read when it starts.
	 *
	 * @param address the address the server listens on, and whether it serves HTTPS
	 * @param keystore the keystore of the HTTPS certificate (null with plain HTTP)
	 * @param staticPath the path the web UI is served on
	 * @param api the path the REST API is served on, and the name of the session cookie
	 * @param v0Enabled whether the legacy (v0) REST API is served
	 * @param secureCookie whether the session cookie is marked as Secure, null for the default (with HTTPS, or behind a
	 *        reverse proxy)
	 */
	public record Settings(HttpServerAddress address, Keystore keystore, String staticPath, ApiEndpoint api, boolean v0Enabled,
			Boolean secureCookie) {

		/**
		 * Reads the settings from a configuration.
		 *
		 * @param config the configuration
		 * @return the settings
		 */
		public static Settings from(Config config) {
			HttpServerAddress address = HttpServerAddress.from(config);
			Keystore keystore = address.useSsl() ? new Keystore(config.get(KEYSTORE_FILE), config.get(KEYSTORE_PASS)) : null;
			return new Settings(address, keystore, config.get(STATIC_PATH),
				new ApiEndpoint(config.get(API_PATH), config.get(SESSION_COOKIE_NAME)), config.get(V0_ENABLED), config.get(SECURE_COOKIE));
		}
	}

	/**
	 * The keystore of the HTTPS certificate.
	 *
	 * @param file the keystore file (JKS if its name ends with {@code .jks}, PKCS12 otherwise)
	 * @param password the password of the keystore
	 */
	public record Keystore(String file, String password) {

		@Override
		public String toString() {
			return "Keystore[file=%s, password=***]".formatted(this.file);
		}
	}

	/** What the resources of the web applications may inject. */
	private final RestDependencies dependencies;
	private final ConfigStore config;

	/** The number of handled requests. */
	private final AtomicLong requests = new AtomicLong();

	/** The number of handled requests by status class (1xx to 5xx). */
	private final AtomicLongArray requestsByClass = new AtomicLongArray(5);

	/** The running Undertow server (null if not started). */
	private Undertow server;

	/** The servlet deployment of the REST API. */
	private volatile DeploymentManager manager;

	/** The description of the servlet deployment. */
	private DeploymentInfo servletBuilder;

	/**
	 * Creates the server.
	 *
	 * @param dependencies what the resources of the web applications may inject
	 *        (given by the composition root, see {@code net.netshot.netshot.boot.Wiring})
	 * @param config the configuration store
	 */
	public RestServer(RestDependencies dependencies, ConfigStore config) {
		this.dependencies = dependencies;
		this.config = config;
	}

	@Override
	public String id() {
		return "rest-server";
	}

	@Override
	public String name() {
		return "Web/REST server";
	}

	@Override
	public synchronized void start() throws Exception {
		Settings settings = Settings.from(this.config.current());
		URI baseUri = settings.address().baseUri();
		boolean useSsl = settings.address().useSsl();
		Undertow.Builder builder = Undertow.builder()
			.setServerOption(UndertowOptions.ENABLE_HTTP2, true);

		// === SSL Setup ===
		if (useSsl) {
			Keystore keystore = settings.keystore();
			KeyStore keyStore = KeyStore.getInstance(
				keystore.file().endsWith(".jks") ? "JKS" : "PKCS12"
			);
			try (InputStream is = new FileInputStream(keystore.file())) {
				keyStore.load(is, keystore.password().toCharArray());
			}
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(
				KeyManagerFactory.getDefaultAlgorithm()
			);
			kmf.init(keyStore, keystore.password().toCharArray());

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), null, null);

			builder.addHttpsListener(baseUri.getPort(), baseUri.getHost(), sslContext);
		}
		else {
			builder.addHttpListener(baseUri.getPort(), baseUri.getHost());
		}

		// === Session cookie ===
		// Secure by default when serving HTTPS, or when behind a (HTTPS) reverse proxy
		boolean behindProxy = this.dependencies.clientAddress().trustsForwardedFor();
		boolean secureCookie = settings.secureCookie() == null ? useSsl || behindProxy : settings.secureCookie();
		ApiEndpoint api = settings.api();
		if (!secureCookie) {
			log.warn("The session cookie is not marked as Secure. If Netshot is accessed over HTTPS "
				+ "via a reverse proxy, set netshot.http.securecookie to true.");
		}
		else if (!useSsl) {
			log.info("The session cookie is marked as Secure while Netshot serves plain HTTP (expecting a HTTPS reverse proxy). "
				+ "If users access Netshot over plain HTTP, set netshot.http.securecookie to false.");
		}

		// === Servlet (Jersey REST API) Deployment ===
		DeploymentInfo deployment = Servlets.deployment()
			.setClassLoader(RestServer.class.getClassLoader())
			.setContextPath(api.path()) // same as Grizzly context path
			.setDeploymentName("NetshotREST")
			.addServlet(Servlets.servlet("JerseyV1", ServletContainer.class,
					new ImmediateInstanceFactory<>(new ServletContainer(new V1Application(this.dependencies.binder(), api))))
				.setLoadOnStartup(1)
				.addMapping(V1Links.VERSION_PATH + "/*"));
		if (settings.v0Enabled()) {
			ServletInfo v0 = Servlets.servlet("Jersey", ServletContainer.class,
					new ImmediateInstanceFactory<>(new ServletContainer(new V0Application(this.dependencies.binder(), api))))
				.setLoadOnStartup(1)
				.addMapping("/*");
			deployment.addServlet(v0);
		}
		else {
			log.info("The legacy REST API (v0) is disabled (netshot.http.api.v0.enabled).");
		}
		deployment
			.setServletSessionConfig(new ServletSessionConfig()
				.setName(api.sessionCookieName())
				.setHttpOnly(true)
				.setSecure(secureCookie)
				.setAttribute("SameSite", "Strict")
			)
			.setDefaultSessionTimeout(this.dependencies.loginService().sessionMaxIdleTime());

		DeploymentManager deploymentManager = Servlets
			.defaultContainer()
			.addDeployment(deployment);
		deploymentManager.deploy();
		HttpHandler servletHandler = deploymentManager.start();

		// === Static files from /www/ (classpath) ===
		ClassPathResourceManager staticResourceManager =
			new ClassPathResourceManager(RestServer.class.getClassLoader(), "www");
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
			.addPrefixPath(settings.staticPath(), staticHandler)
			.addPrefixPath(api.path(), servletHandler);

		// Counts the requests by status class (statistics of the service)
		builder.setHandler(exchange -> {
			exchange.addExchangeCompleteListener((completed, next) -> {
				this.countRequest(completed.getStatusCode());
				next.proceed();
			});
			pathHandler.handleRequest(exchange);
		});

		// === Start Undertow ===
		this.server = builder.build();
		this.server.start();
		this.manager = deploymentManager;
		this.servletBuilder = deployment;
		log.info("The Web/REST server listens on {}.", baseUri);
	}

	@Override
	public synchronized void stop() throws Exception {
		if (this.server == null) {
			return;
		}
		this.server.stop();
		this.server = null;
		this.manager.stop();
		this.manager.undeploy();
		Servlets.defaultContainer().removeDeployment(this.servletBuilder);
	}

	@Override
	public synchronized boolean isHealthy() {
		return this.server != null;
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("requests", this.requests.get());
		for (int statusClass = 0; statusClass < this.requestsByClass.length(); statusClass++) {
			stats.put("%dxx".formatted(statusClass + 1), this.requestsByClass.get(statusClass));
		}
		DeploymentManager currentManager = this.manager;
		if (currentManager != null) {
			stats.put("sessions", currentManager.getDeployment().getSessionManager().getActiveSessions().size());
		}
		return stats;
	}

	/**
	 * Counts a request.
	 * @param statusCode the HTTP status code of the response
	 */
	private void countRequest(int statusCode) {
		this.requests.incrementAndGet();
		int statusClass = statusCode / 100 - 1;
		if (statusClass >= 0 && statusClass < this.requestsByClass.length()) {
			this.requestsByClass.incrementAndGet(statusClass);
		}
	}
}
