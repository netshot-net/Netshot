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

import org.glassfish.hk2.utilities.binding.AbstractBinder;
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
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.aaa.user.UiUser;
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

	/** Whether the legacy (v0) REST API is served (the web UI uses it). */
	static final Setting<Boolean> V0_ENABLED = Setting.bool("netshot.http.api.v0.enabled").defaultValue(true).restart();

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
	 * Gets the path the REST API is served on.
	 *
	 * @return the path, e.g. {@code /api}
	 */
	public static String apiPath() {
		return Netshot.config().current().get(API_PATH);
	}

	/**
	 * Gets the name of the session cookie.
	 *
	 * @return the name of the cookie
	 */
	public static String sessionCookieName() {
		return Netshot.config().current().get(SESSION_COOKIE_NAME);
	}

	private URI httpBaseUri;
	private boolean httpUseSsl;
	private String httpSslKeystoreFile;
	private String httpSslKeystorePass;

	/** The dependency injection binder for the web application. */
	private final AbstractBinder binder;

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
	 * @param binder the dependency injection binder exposing the services to the REST resources
	 *        (given by the composition root, see {@code net.netshot.netshot.boot.Wiring})
	 */
	public RestServer(AbstractBinder binder) {
		this.binder = binder;
	}

	@Override
	public String name() {
		return "Web/REST server";
	}

	@Override
	public synchronized void start() throws Exception {
		HttpServerAddress address = HttpServerAddress.fromConfig();
		this.httpBaseUri = address.baseUri();
		httpUseSsl = address.useSsl();
		if (httpUseSsl) {
			httpSslKeystoreFile = Netshot.config().current().get(KEYSTORE_FILE);
			httpSslKeystorePass = Netshot.config().current().get(KEYSTORE_PASS);
		}
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
		Config config = Netshot.config().current();
		boolean behindProxy = config.get(LoggerFilter.TRUST_X_FORWARDED_FOR);
		Boolean configuredSecureCookie = config.get(SECURE_COOKIE);
		boolean secureCookie = configuredSecureCookie == null ? httpUseSsl || behindProxy : configuredSecureCookie;
		String apiPath = config.get(API_PATH);
		String staticPath = config.get(STATIC_PATH);
		if (!secureCookie) {
			log.warn("The session cookie is not marked as Secure. If Netshot is accessed over HTTPS "
				+ "via a reverse proxy, set netshot.http.securecookie to true.");
		}
		else if (!httpUseSsl) {
			log.info("The session cookie is marked as Secure while Netshot serves plain HTTP (expecting a HTTPS reverse proxy). "
				+ "If users access Netshot over plain HTTP, set netshot.http.securecookie to false.");
		}

		// === Servlet (Jersey REST API) Deployment ===
		DeploymentInfo deployment = Servlets.deployment()
			.setClassLoader(Netshot.class.getClassLoader())
			.setContextPath(apiPath) // same as Grizzly context path
			.setDeploymentName("NetshotREST")
			.addServlet(Servlets.servlet("JerseyV1", ServletContainer.class,
					new ImmediateInstanceFactory<>(new ServletContainer(new V1Application(this.binder))))
				.setLoadOnStartup(1)
				.addMapping(V1Links.VERSION_PATH + "/*"));
		if (config.get(V0_ENABLED)) {
			ServletInfo v0 = Servlets.servlet("Jersey", ServletContainer.class,
					new ImmediateInstanceFactory<>(new ServletContainer(new V0Application(this.binder))))
				.setLoadOnStartup(1)
				.addMapping("/*");
			deployment.addServlet(v0);
		}
		else {
			log.warn("The legacy REST API (v0) is disabled (netshot.http.api.v0.enabled), the web UI can't work without it.");
		}
		deployment
			.setServletSessionConfig(new ServletSessionConfig()
				.setName(config.get(SESSION_COOKIE_NAME))
				.setHttpOnly(true)
				.setSecure(secureCookie)
				.setAttribute("SameSite", "Strict")
			)
			.setDefaultSessionTimeout(UiUser.settings().maxIdleTime());

		DeploymentManager deploymentManager = Servlets
			.defaultContainer()
			.addDeployment(deployment);
		deploymentManager.deploy();
		HttpHandler servletHandler = deploymentManager.start();

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
			.addPrefixPath(staticPath, staticHandler)
			.addPrefixPath(apiPath, servletHandler);

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
		log.info("The Web/REST server listens on {}.", this.httpBaseUri);
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
