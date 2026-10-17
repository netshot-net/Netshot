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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Mapper to convert exception raised in the Web method to http error.
 */
@Slf4j
public class NetshotExceptionMapper implements ExceptionMapper<Throwable> {

	@Context
	private SecurityContext securityContext;

	public Response toResponse(Throwable t) {
		if (t instanceof JacksonException je && isUnreadableBody(je)) {
			// The request body (JSON, XML or YAML) is malformed or doesn't match the expected type
			log.info("Invalid request body: {}.", je.getMessage());
			return Response.status(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getHttpStatus())
				.entity(new RsErrorBean("Invalid request body: " + je.getOriginalMessage(), ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getCode()))
				.build();
		}
		if (t instanceof ForbiddenException fe) {
			// RolesAllowedDynamicFeature raises ForbiddenException (which leads to 403 Forbidden)
			// for both missing authentication and permission error
			if (securityContext.getUserPrincipal() == null) {
				log.info("Authentication required.", t);
				// If no user at all was recognized, force a 401
				// with OIDC info header if applicable
				Response.ResponseBuilder builder = Response.fromResponse(fe.getResponse());
				builder.status(Response.Status.UNAUTHORIZED);
				if (Oidc.isAvailable()) {
					builder.header("X-OIDC-AuthorizationEndpoint", Oidc.getAuthorizationEndpointURI().toString());
					builder.header("X-OIDC-ClientID", Oidc.getClientId());
				}
				return builder.build();
			}
			else {
				log.info("Missing required permission.", t);
			}
		}
		else if (t instanceof NetshotException ne) {
			if (ne.getErrorCode().getHttpStatus() >= 500) {
				log.error("Error reported by the service.", t);
			}
			else {
				log.info("Error reported by the service: {} ({}).", ne.getMessage(), ne.getErrorCode());
			}
		}
		else {
			log.error("Uncaught exception thrown by REST service", t);
		}

		if (t instanceof WebApplicationException wae) {
			return wae.getResponse();
		}
		else if (t instanceof NetshotException ne) {
			// Same rendering as the former NetshotBadRequestException
			return Response.status(ne.getErrorCode().getHttpStatus())
				.entity(new RsErrorBean(ne.getMessage(), ne.getErrorCode().getCode())).build();
		}
		else {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Tells whether the exception was raised while reading the request body: a parse error, or a
	 * value which doesn't match the target type. Errors while writing a response are not concerned.
	 *
	 * @param t the Jackson exception
	 * @return true if the request body could not be read
	 */
	private static boolean isUnreadableBody(JacksonException t) {
		return t instanceof StreamReadException
			|| t instanceof MismatchedInputException
			|| t instanceof ValueInstantiationException;
	}
}
