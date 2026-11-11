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
package net.netshot.netshot.testing.api.v0;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.testing.contract.Golden;
import net.netshot.netshot.testing.contract.Golden.Format;
import net.netshot.netshot.testing.contract.Golden.Transform;

/**
 * One API contract case: a request and the name of the golden file its
 * response is compared to.
 *
 * <p>The request path and body are suppliers, evaluated when the case is run,
 * because they may depend on IDs which only exist once the dataset has been
 * seeded (or once a previous case created something). Building the case list
 * itself does not need a database, which is what the coverage guard relies on.</p>
 */
public final class V0ContractCase {

	public static final String JSON = "application/json";
	public static final String XML = "application/xml";
	public static final String YAML = "application/yaml";
	public static final String TEXT = "text/plain";
	public static final String OCTET = "application/octet-stream";
	public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	/**
	 * A raw HTTP response.
	 * @param status the HTTP status code
	 * @param contentType the Content-Type header, if any
	 * @param contentDisposition the Content-Disposition header, if any
	 * @param body the raw body
	 */
	public record Reply(int status, String contentType, String contentDisposition, byte[] body) {
		long xmlLong(String element) {
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<" + element + ">(\\d+)</" + element + ">")
				.matcher(new String(this.body, StandardCharsets.UTF_8));
			if (!matcher.find()) {
				throw new IllegalStateException("No <" + element + "> in " + new String(this.body, StandardCharsets.UTF_8));
			}
			return Long.parseLong(matcher.group(1));
		}

		JsonNode json() {
			return Golden.parseJson(new String(this.body, StandardCharsets.UTF_8));
		}
	}

	public final String method;
	/** The path template as declared in the JAX-RS annotations, e.g. /devices/{id}. */
	public final String template;
	/** Optional name of the variant (query parameters, payload kind...). */
	public final String variant;
	public final String accept;
	public final Supplier<String> path;
	public final Supplier<String> body;
	public final String requestContentType;
	/** API token to use (null: the administrator token). */
	public final String token;
	/** Called with the reply, to capture IDs for the next cases. */
	public final Consumer<Reply> afterReply;
	/** Projection applied to the response. */
	public final Transform transform;
	/** Whether the items of the root list have no defined order. */
	public final boolean sortRoot;

	private V0ContractCase(Builder builder) {
		this.method = builder.method;
		this.template = builder.template;
		this.variant = builder.variant;
		this.accept = builder.accept;
		this.path = builder.path;
		this.body = builder.body;
		this.requestContentType = builder.requestContentType;
		this.token = builder.token;
		this.afterReply = builder.afterReply;
		this.transform = builder.transform;
		this.sortRoot = builder.sortRoot;
	}

	public static Builder builder(String method, String template) {
		return new Builder(method, template);
	}

	public Format format() {
		switch (this.accept) {
			case XML:
				return Format.XML;
			case YAML:
				return Format.YAML;
			case TEXT:
			case OCTET:
				return Format.TEXT;
			case XLSX:
				return Format.XLSX;
			case JSON:
			default:
				return Format.JSON;
		}
	}

	/**
	 * Name of the golden file, such as {@code GET_devices_id.json}
	 * or {@code GET_devices__details.xml}.
	 * @return the file name
	 */
	public String fileName() {
		String base = this.template.replaceAll("^/+", "")
			.replaceAll("\\{([A-Za-z0-9]+)(:[^}]*)?\\}", "$1")
			.replace('/', '_');
		return this.method + "_" + base + (this.variant == null ? "" : "__" + this.variant)
			+ "." + this.format().extension;
	}

	/**
	 * Normalized path template: path parameter names removed, to be matched
	 * against the JAX-RS declarations whatever the names they use.
	 * @param template the path template
	 * @return the normalized template
	 */
	public static String normalizeTemplate(String template) {
		String normalized = ("/" + template).replaceAll("/+", "/")
			.replaceAll("\\{[^}]*\\}", "{}");
		return normalized.length() > 1 && normalized.endsWith("/")
			? normalized.substring(0, normalized.length() - 1) : normalized;
	}

	public String key() {
		return this.method + " " + normalizeTemplate(this.template);
	}

	/**
	 * Sends the request.
	 * @param client the HTTP client
	 * @param apiUrl the API base URL
	 * @param adminToken the administrator token
	 * @return the reply
	 * @throws IOException on I/O error
	 * @throws InterruptedException if interrupted
	 */
	public Reply send(HttpClient client, String apiUrl, String adminToken) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.uri(URI.create(apiUrl + this.path.get()))
			.header("Accept", this.accept);
		String useToken = this.token == null ? adminToken : this.token;
		if (!"".equals(useToken)) {
			request.header("X-Netshot-API-Token", useToken);
		}
		String payload = this.body == null ? null : this.body.get();
		if (payload != null) {
			request.header("Content-Type", this.requestContentType);
			request.method(this.method, HttpRequest.BodyPublishers.ofString(payload));
		}
		else {
			request.method(this.method, HttpRequest.BodyPublishers.noBody());
		}
		HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
		Reply reply = new Reply(response.statusCode(),
			response.headers().firstValue("Content-Type").orElse(""),
			response.headers().firstValue("Content-Disposition").orElse(null), response.body());
		if (this.afterReply != null) {
			this.afterReply.accept(reply);
		}
		return reply;
	}

	/** Case builder. */
	public static final class Builder {
		private final String method;
		private final String template;
		private String variant;
		private String accept = JSON;
		private Supplier<String> path;
		private Supplier<String> body;
		private String requestContentType = JSON;
		private String token;
		private Consumer<Reply> afterReply;
		private Transform transform = Transform.NONE;
		private boolean sortRoot;

		private Builder(String method, String template) {
			this.method = method;
			this.template = template;
			this.path = () -> template;
		}

		Builder variant(String newVariant) {
			this.variant = newVariant;
			return this;
		}

		Builder accept(String newAccept) {
			this.accept = newAccept;
			return this;
		}

		Builder path(Supplier<String> newPath) {
			this.path = newPath;
			return this;
		}

		Builder path(String newPath) {
			this.path = () -> newPath;
			return this;
		}

		Builder body(Supplier<String> newBody) {
			this.body = newBody;
			return this;
		}

		Builder body(String newBody) {
			this.body = () -> newBody;
			return this;
		}

		Builder requestContentType(String newType) {
			this.requestContentType = newType;
			return this;
		}

		Builder token(String newToken) {
			this.token = newToken;
			return this;
		}

		Builder sortRoot() {
			this.sortRoot = true;
			return this;
		}

		Builder transform(Transform newTransform) {
			this.transform = newTransform;
			return this;
		}

		Builder afterReply(Consumer<Reply> newAfterReply) {
			this.afterReply = newAfterReply;
			return this;
		}

		V0ContractCase build() {
			return new V0ContractCase(this);
		}
	}
}
