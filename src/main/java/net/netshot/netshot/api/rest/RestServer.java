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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.MarkerFactory;

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
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.http.HttpServerAddress;

/**
 * The web server of Netshot: a thread which starts Undertow, serving the Web UI (static files) and the
 * REST API (the Jersey application, see {@link NetshotWebApplication}) with the HTTP(S) and session cookie
 * settings of the configuration.
 */
@Slf4j
public final class RestServer extends Thread {

	/** The static instance of the server. */
	private static RestServer instance;

	/** Whether the HTTP server is enabled. */
	static final Setting<Boolean> ENABLED = Setting.bool("netshot.http.enabled").defaultValue(true).restart();

	/** The path the static files (web UI) are served on. */
	static final Setting<String> STATIC_PATH = Setting.text("netshot.http.staticpath").defaultValue("/").restart();

	/** The path the REST API is served on. */
	static final Setting<String> API_PATH = Setting.text("netshot.http.apipath").defaultValue("/api").restart();

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

	/**
	 * Initializes the server, and starts it unless the HTTP server is disabled by the configuration.
	 *
	 * @param binder the dependency injection binder exposing the services to the REST resources
	 *        (given by the composition root, see {@code net.netshot.netshot.boot.Wiring})
	 */
	public static void init(AbstractBinder binder) {
		if (!Netshot.config().current().get(ENABLED)) {
			log.info("HTTP server is not enabled.");
			return;
		}
		instance = new RestServer(binder);
		instance.setUncaughtExceptionHandler(Netshot.exceptionHandler);
		instance.start();
	}

	/**
	 * Stops the server started by {@link #init(AbstractBinder)}, if any, and waits for it to be stopped
	 * (so the port is free again when this returns).
	 *
	 * @throws InterruptedException if interrupted while waiting for the server to stop
	 */
	public static void shutdown() throws InterruptedException {
		RestServer server = instance;
		if (server == null) {
			return;
		}
		instance = null;
		server.interrupt();
		server.join();
	}

	private URI httpBaseUri;
	private boolean httpUseSsl;
	private String httpSslKeystoreFile;
	private String httpSslKeystorePass;

	/** The dependency injection binder for the web application. */
	private AbstractBinder binder;

	/**
	 * Instantiates the web server thread.
	 *
	 * @param binder the dependency injection binder for the web application
	 */
	private RestServer(AbstractBinder binder) {
		this.binder = binder;
		this.setName("REST Service");
		HttpServerAddress address = HttpServerAddress.fromConfig();
		this.httpBaseUri = address.baseUri();
		httpUseSsl = address.useSsl();
		if (httpUseSsl) {
			httpSslKeystoreFile = Netshot.config().current().get(KEYSTORE_FILE);
			httpSslKeystorePass = Netshot.config().current().get(KEYSTORE_PASS);
		}
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
			DeploymentInfo servletBuilder = Servlets.deployment()
				.setClassLoader(Netshot.class.getClassLoader())
				.setContextPath(apiPath) // same as Grizzly context path
				.setDeploymentName("NetshotREST")
				.addServlets(
					Servlets.servlet("Jersey", ServletContainer.class,
						new ImmediateInstanceFactory<>(new ServletContainer(new NetshotWebApplication(this.binder))))
						.setLoadOnStartup(1)
						.addMapping("/*")
				)
				.setServletSessionConfig(new ServletSessionConfig()
					.setName(config.get(SESSION_COOKIE_NAME))
					.setHttpOnly(true)
					.setSecure(secureCookie)
					.setAttribute("SameSite", "Strict")
				)
				.setDefaultSessionTimeout(UiUser.settings().maxIdleTime());

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
				.addPrefixPath(staticPath, staticHandler)
				.addPrefixPath(apiPath, servletHandler);

			builder.setHandler(pathHandler);

			// === Start Undertow ===
			Undertow server = builder.build();
			server.start();

			try {
				synchronized (this) {
					while (true) {
						this.wait();
					}
				}
			}
			catch (InterruptedException e) {
				// Stop requested by shutdown()
				log.info("Stopping the Web/REST service.");
				server.stop();
				manager.stop();
				manager.undeploy();
				Servlets.defaultContainer().removeDeployment(servletBuilder);
			}
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"),
				"Fatal error with the REST service.", e);
			throw new RuntimeException(
				"Error with the REST service, see logs for more details.");
		}
	}
}
