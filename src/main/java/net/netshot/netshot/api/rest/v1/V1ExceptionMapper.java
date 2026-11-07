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
package net.netshot.netshot.api.rest.v1;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Renders the errors of the v1 API as problem details (RFC 9457), in the format the client accepts
 * ({@code application/problem+json} by default, {@code application/problem+xml}, or YAML).
 */
@Slf4j
public class V1ExceptionMapper implements ExceptionMapper<Throwable> {

	/** Plain HTTP error problem type. */
	private static final String ABOUT_BLANK = "about:blank";

	@Context
	private SecurityContext securityContext;

	@Context
	private HttpHeaders headers;

	@Context
	private UriInfo uriInfo;

	/** The OIDC authentication (for the headers telling the client where to authenticate). */
	private final Oidc oidc;

	/**
	 * Creates the mapper.
	 *
	 * @param oidc the OIDC authentication
	 */
	@Inject
	public V1ExceptionMapper(Oidc oidc) {
		this.oidc = oidc;
	}

	@Override
	public Response toResponse(Throwable thrown) {
		Throwable t = thrown instanceof MergePatch.InvalidResultException ire ? ire.getCause() : thrown;
		if (t instanceof JacksonException je && isUnreadableBody(je)) {
			log.info("Invalid request body: {}.", je.getMessage());
			return this.problem(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, describe(je), fieldErrors(je), null);
		}
		if (t instanceof NetshotException ne) {
			if (V1Status.of(ne.getErrorCode()) >= 500) {
				log.error("Error reported by the service.", t);
			}
			else {
				log.info("Error reported by the service: {} ({}).", ne.getMessage(), ne.getErrorCode());
			}
			List<ProblemDto.FieldErrorDto> errors = ne.getTarget() == null ? null
				: List.of(new ProblemDto.FieldErrorDto(ne.getTarget(), ne.getMessage()));
			return this.problem(ne.getErrorCode(), ne.getMessage(), errors, null);
		}
		if (t instanceof ForbiddenException fe) {
			if (this.securityContext.getUserPrincipal() == null) {
				log.info("Authentication required.");
				Response.ResponseBuilder builder = this.plainProblem(Response.Status.UNAUTHORIZED.getStatusCode(),
					"Authentication required", "Authentication is required to access this resource.");
				builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
				if (this.oidc.isAvailable()) {
					builder.header("X-OIDC-AuthorizationEndpoint", this.oidc.getAuthorizationEndpointURI().toString());
					builder.header("X-OIDC-ClientID", this.oidc.getClientId());
				}
				return builder.build();
			}
			log.info("Missing required permission.", fe);
			return this.plainProblem(Response.Status.FORBIDDEN.getStatusCode(), "Forbidden",
				"The permission level of the user is not sufficient for this operation.").build();
		}
		if (t instanceof WebApplicationException wae) {
			Response original = wae.getResponse();
			Response.StatusType status = original.getStatusInfo();
			if (status.getStatusCode() >= 500) {
				log.error("HTTP error while processing the request.", t);
			}
			Response.ResponseBuilder builder = this.plainProblem(status.getStatusCode(), status.getReasonPhrase(), wae.getMessage());
			original.getHeaders().forEach((name, values) -> {
				if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
					values.forEach(v -> builder.header(name, v));
				}
			});
			return builder.build();
		}
		log.error("Uncaught exception thrown by REST service", t);
		return this.plainProblem(500, "Internal server error", "Unexpected error while processing the request.").build();
	}

	/**
	 * Builds the problem of an error code.
	 *
	 * @param code the error code
	 * @param detail the description of the error
	 * @param errors the details by parameter or property, or null
	 * @param title the title (null for the title of the code)
	 * @return the response
	 */
	private Response problem(ErrorCode code, String detail, List<ProblemDto.FieldErrorDto> errors, String title) {
		int status = V1Status.of(code);
		ProblemDto problem = new ProblemDto(V1Status.type(code), title == null ? V1Status.title(code) : title, status,
			detail, this.instance(), code.name(), code.getCode(), errors);
		return Response.status(status).type(this.problemMediaType()).entity(problem).build();
	}

	/**
	 * Builds the problem of a plain HTTP error.
	 *
	 * @param status the HTTP status
	 * @param title the title
	 * @param detail the description of the error
	 * @return the response builder
	 */
	private Response.ResponseBuilder plainProblem(int status, String title, String detail) {
		ProblemDto problem = new ProblemDto(ABOUT_BLANK, title, status, detail, this.instance(), null, null, null);
		return Response.status(status).type(this.problemMediaType()).entity(problem);
	}

	private String instance() {
		try {
			return this.uriInfo.getRequestUri().getRawPath();
		}
		catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Chooses the media type of the problem from the accepted ones.
	 *
	 * @return the media type
	 */
	private MediaType problemMediaType() {
		try {
			for (MediaType accepted : this.headers.getAcceptableMediaTypes()) {
				if (V1Mappers.isXml(accepted)) {
					return MediaType.valueOf(V1Mappers.APPLICATION_PROBLEM_XML);
				}
				if (V1Mappers.isYaml(accepted)) {
					return MediaType.valueOf(V1Mappers.APPLICATION_YAML);
				}
				if (V1Mappers.isJson(accepted) || accepted.isWildcardType()) {
					break;
				}
			}
		}
		catch (RuntimeException e) {
			// Invalid Accept header: default format
		}
		return MediaType.valueOf(V1Mappers.APPLICATION_PROBLEM_JSON);
	}

	private static boolean isUnreadableBody(JacksonException t) {
		return t instanceof StreamReadException
			|| t instanceof MismatchedInputException
			|| t instanceof ValueInstantiationException;
	}

	private static String describe(JacksonException e) {
		if (e instanceof UnrecognizedPropertyException upe) {
			return "Unknown property '%s' in the request body".formatted(path(upe));
		}
		if (e instanceof MismatchedInputException mie && mie.getOriginalMessage() != null
			&& mie.getOriginalMessage().startsWith("Missing required creator property")) {
			return "Missing property '%s' in the request body".formatted(path(mie));
		}
		return "Invalid request body: " + e.getOriginalMessage();
	}

	private static List<ProblemDto.FieldErrorDto> fieldErrors(JacksonException e) {
		if (e instanceof DatabindException de && !path(de).isEmpty()) {
			return List.of(new ProblemDto.FieldErrorDto(path(de), describe(e)));
		}
		return null;
	}

	/**
	 * Gets the path of the property of the request body an error is about ({@code accesses[0].port}).
	 *
	 * @param e the exception
	 * @return the path (empty if none)
	 */
	private static String path(DatabindException e) {
		if (!(e instanceof JsonMappingException jme)) {
			return "";
		}
		return jme.getPath().stream()
			.map(r -> r.getFieldName() != null ? "." + r.getFieldName() : "[" + r.getIndex() + "]")
			.collect(Collectors.joining())
			.replaceFirst("^\\.", "");
	}
}
