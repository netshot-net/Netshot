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
package net.netshot.netshot.api.hook;

import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.glassfish.jersey.client.ClientConfig;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import com.fasterxml.jackson.jakarta.rs.xml.JacksonXmlBindXMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonXmlBindYAMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.YAMLMediaTypes;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.tls.HttpsTrustPolicy;

/**
 * Posts a payload to the target URL of a web hook, in the format (JSON, XML or YAML) of the hook.
 */
@Slf4j
public class WebHookSender {

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

	/**
	 * Posts a payload to a web hook.
	 *
	 * @param hook the web hook
	 * @param data the payload
	 * @return the result of the call
	 * @throws Exception if the hook is invalid or the call fails
	 */
	public String send(WebHook hook, Object data) throws Exception {
		URL targetUrl = hook.getParsedUrl();

		ClientConfig config = new ClientConfig();
		MediaType mediaType;
		switch (hook.getAction()) {
			case POST_JSON:
				mediaType = MediaType.APPLICATION_JSON_TYPE;
				JacksonXmlBindJsonProvider jsonProvider = new JacksonXmlBindJsonProvider();
				jsonProvider.setMapper(JsonMapper.builder()
					.build());
				config.register(jsonProvider);
				break;
			case POST_XML:
				mediaType = MediaType.APPLICATION_XML_TYPE;
				JacksonXmlBindXMLProvider xmlProvider = new JacksonXmlBindXMLProvider();
				xmlProvider.setMapper(XmlMapper.builder()
					.build());
				config.register(xmlProvider);
				break;
			case POST_YAML:
				mediaType = YAMLMediaTypes.APPLICATION_JACKSON_YAML_TYPE;
				JacksonXmlBindYAMLProvider yamlProvider = new JacksonXmlBindYAMLProvider();
				yamlProvider.setMapper(yamlMapper());
				config.register(yamlProvider);
				break;
			default:
				throw new Exception("Invalid action");
		}

		ClientBuilder clientBuilder = ClientBuilder.newBuilder();
		if ("https".equals(targetUrl.getProtocol())) {
			try {
				SSLContext sslContext = HttpsTrustPolicy.buildSslContext(hook.getHttpsCaTrustMode(), hook.getHttpsCustomCaCertificate());
				clientBuilder.sslContext(sslContext);
				HostnameVerifier hostnameVerifier = HttpsTrustPolicy.buildHostnameVerifier(hook.getHttpsCaTrustMode());
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

	/**
	 * Builds the mapper of the YAML payloads. The type id of a payload is written as a {@code type}
	 * property, as in JSON, rather than as a YAML tag: some type ids ("SNMP v1") are not valid tags.
	 *
	 * @return the mapper
	 */
	public static YAMLMapper yamlMapper() {
		return YAMLMapper.builder()
			.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
			.build();
	}
}
