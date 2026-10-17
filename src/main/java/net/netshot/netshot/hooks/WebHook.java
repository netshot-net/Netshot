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
package net.netshot.netshot.hooks;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.glassfish.jersey.client.ClientConfig;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import com.fasterxml.jackson.jakarta.rs.xml.JacksonXmlBindXMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonXmlBindYAMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.YAMLMediaTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.rest.RestViews.HookView;
import net.netshot.netshot.rest.RestViews.RestApiView;
import net.netshot.netshot.utils.HttpsCaTrustMode;
import net.netshot.netshot.utils.HttpsTrustPolicy;

/**
 * A Web hook, called after specific event.
 */
@Entity
@Slf4j
public class WebHook extends Hook {

	/**
	 * Types of web hook.
	 */
	public enum Action {
		POST_XML, POST_JSON, POST_YAML,
	}

	static {
		try {
			// Try to instanciate Trust Manager and change the trustStoreType to JKS
			// if we see an error about PKCS#12 loader from BC.
			// This happens on Debian where the ca-certificates-java package
			// generates the /etc/ssl/certs/java/cacerts file in JKS format
			// while the default keystore format in OpenJDK 11 is PKCS#12.
			// The default Java security code automatically tries both formats
			// but BouncyCastle is more strict on this.
			TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).init((KeyStore) null);
		}
		catch (Exception e) {
			if (e instanceof KeyStoreException && e.getCause() != null
				&& "stream does not represent a PKCS12 key store".equals(e.getCause().getMessage())) {
				log.info("Changing trustStoreType to JKS");
				System.setProperty("jakarta.net.ssl.trustStoreType", "JKS");
			}
		}
	}

	/** Action of the hook. */
	private Action action;

	/** The target http URL. */
	private String url;

	/** HTTPS certificate CA trust mode (only meaningful for a HTTPS target URL). */
	private HttpsCaTrustMode httpsCaTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

	/**
	 * PEM-encoded trust anchor certificate (optionally a chain of several
	 * concatenated PEM certificates) used when {@link #httpsCaTrustMode} is
	 * {@link HttpsCaTrustMode#CUSTOM_CA}.
	 */
	private String httpsCustomCaCertificate;

	/**
	 * Instantiates a new web hook.
	 */
	protected WebHook() {
	}

	@XmlElement
	@JsonView(DefaultView.class)
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Transient
	public URL getParsedUrl() throws MalformedURLException {
		if (this.url == null || "".equals(this.url.trim())) {
			throw new MalformedURLException("Empty URL");
		}
		try {
			URL pUrl = new URI(this.url.trim()).toURL();
			if (!"http".equals(pUrl.getProtocol()) && !"https".equals(pUrl.getProtocol())) {
				throw new MalformedURLException("Invalid protocol");
			}
			return pUrl;
		}
		catch (URISyntaxException e) {
			throw new MalformedURLException("Invalid URL");
		}
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@XmlElement
	@JsonView(DefaultView.class)
	public HttpsCaTrustMode getHttpsCaTrustMode() {
		return httpsCaTrustMode;
	}

	public void setHttpsCaTrustMode(HttpsCaTrustMode httpsCaTrustMode) {
		this.httpsCaTrustMode = httpsCaTrustMode;
	}

	@Column(columnDefinition = "TEXT")
	@XmlElement
	@JsonView(DefaultView.class)
	public String getHttpsCustomCaCertificate() {
		return httpsCustomCaCertificate;
	}

	public void setHttpsCustomCaCertificate(String httpsCustomCaCertificate) {
		this.httpsCustomCaCertificate = httpsCustomCaCertificate;
	}

	@XmlElement
	@JsonView(DefaultView.class)
	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	@Override
	public String execute(Object data) throws Exception {
		URL targetUrl = this.getParsedUrl();

		ClientConfig config = new ClientConfig();
		MediaType mediaType;
		switch (this.action) {
			case POST_JSON:
				mediaType = MediaType.APPLICATION_JSON_TYPE;
				JacksonXmlBindJsonProvider jsonProvider = new JacksonXmlBindJsonProvider();
				jsonProvider.setDefaultView(HookView.class);
				jsonProvider.setMapper(JsonMapper.builder()
					.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
					.build());
				config.register(jsonProvider);
				break;
			case POST_XML:
				mediaType = MediaType.APPLICATION_XML_TYPE;
				JacksonXmlBindXMLProvider xmlProvider = new JacksonXmlBindXMLProvider();
				xmlProvider.setDefaultView(HookView.class);
				xmlProvider.setMapper(XmlMapper.builder()
					.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
					.build());
				config.register(xmlProvider);
				break;
			case POST_YAML:
				mediaType = YAMLMediaTypes.APPLICATION_JACKSON_YAML_TYPE;
				JacksonXmlBindYAMLProvider yamlProvider = new JacksonXmlBindYAMLProvider();
				yamlProvider.setDefaultView(RestApiView.class);
				yamlProvider.setMapper(YAMLMapper.builder()
					.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
					.build());
				config.register(yamlProvider);
				break;
			default:
				throw new Exception("Invalid action");
		}

		ClientBuilder clientBuilder = ClientBuilder.newBuilder();
		if ("https".equals(targetUrl.getProtocol())) {
			try {
				SSLContext sslContext = HttpsTrustPolicy.buildSslContext(this.httpsCaTrustMode, this.httpsCustomCaCertificate);
				clientBuilder.sslContext(sslContext);
				HostnameVerifier hostnameVerifier = HttpsTrustPolicy.buildHostnameVerifier(this.httpsCaTrustMode);
				if (hostnameVerifier != null) {
					clientBuilder.hostnameVerifier(hostnameVerifier);
				}
			}
			catch (GeneralSecurityException | java.io.IOException e) {
				throw new Exception("Unable to initialize the HTTPS trust policy for the webhook: " + e.getMessage(), e);
			}
		}
		clientBuilder.withConfig(config);

		// The client (its connection pool) and the response are released after each call
		try (Client client = clientBuilder.build();
				Response response = client.target(targetUrl.toURI()).request().post(jakarta.ws.rs.client.Entity.entity(data, mediaType))) {
			return String.format("HTTP response code %d", response.getStatus());
		}
	}
}
