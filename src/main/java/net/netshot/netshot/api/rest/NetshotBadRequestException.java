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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import net.netshot.netshot.api.rest.v0.RestService.RsErrorBean;

/**
 * The NetshotBadRequestException class, a WebApplication exception
 * embedding an error message, and HTTP status code, to be sent to the REST client.
 */
public class NetshotBadRequestException extends WebApplicationException {

	public enum Reason {
		NETSHOT_INVALID_REQUEST_PARAMETER(10, Response.Status.BAD_REQUEST),
		NETSHOT_DATABASE_ACCESS_ERROR(20, Response.Status.INTERNAL_SERVER_ERROR),
		NETSHOT_INVALID_IP_ADDRESS(100, Response.Status.BAD_REQUEST),
		NETSHOT_MALFORMED_IP_ADDRESS(101, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_PORT(102, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_DOMAIN(110, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_DOMAIN(111, Response.Status.CONFLICT),
		NETSHOT_INVALID_DOMAIN_NAME(112, Response.Status.BAD_REQUEST),
		NETSHOT_DOMAIN_IN_USE(113, Response.Status.PRECONDITION_FAILED),
		NETSHOT_DOMAIN_NOT_FOUND(114, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_TASK(120, Response.Status.BAD_REQUEST),
		NETSHOT_TASK_NOT_CANCELLABLE(121, Response.Status.PRECONDITION_FAILED),
		NETSHOT_TASK_CANCEL_ERROR(122, Response.Status.INTERNAL_SERVER_ERROR),
		NETSHOT_TASK_NOT_FOUND(123, Response.Status.NOT_FOUND),
		NETSHOT_CREDENTIALS_IN_USE(130, Response.Status.PRECONDITION_FAILED),
		NETSHOT_DUPLICATE_CREDENTIALS(131, Response.Status.CONFLICT),
		NETSHOT_INVALID_CREDENTIALS_TYPE(132, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_CREDENTIALS(133, Response.Status.PRECONDITION_FAILED),
		NETSHOT_INVALID_CREDENTIALS_NAME(134, Response.Status.BAD_REQUEST),
		NETSHOT_CREDENTIALS_NOT_FOUND(135, Response.Status.NOT_FOUND),
		NETSHOT_SCHEDULE_ERROR(30, Response.Status.INTERNAL_SERVER_ERROR),
		NETSHOT_DUPLICATE_DEVICE(140, Response.Status.CONFLICT),
		NETSHOT_DEVICE_IN_USE(141, Response.Status.PRECONDITION_FAILED),
		NETSHOT_INVALID_DEVICE(142, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_CONFIG(143, Response.Status.BAD_REQUEST),
		NETSHOT_INCOMPATIBLE_CONFIGS(144, Response.Status.BAD_REQUEST),
		NETSHOT_DEVICE_NOT_FOUND(145, Response.Status.NOT_FOUND),
		NETSHOT_CONFIG_NOT_FOUND(146, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_DEVICE_DRIVER(150, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_SEARCH_STRING(151, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_GROUP_NAME(160, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_GROUP(161, Response.Status.CONFLICT),
		NETSHOT_INCOMPATIBLE_GROUP_TYPE(162, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_DEVICE_IN_STATICGROUP(163, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_GROUP(164, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_DYNAMICGROUP_QUERY(165, Response.Status.BAD_REQUEST),
		NETSHOT_GROUP_NOT_FOUND(166, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_SUBNET(170, Response.Status.BAD_REQUEST),
		NETSHOT_SCAN_SUBNET_TOO_BIG(171, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_POLICY_NAME(180, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_POLICY(181, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_POLICY(182, Response.Status.CONFLICT),
		NETSHOT_POLICY_NOT_FOUND(183, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_RULE_NAME(190, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_RULE(191, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_RULE(192, Response.Status.CONFLICT),
		NETSHOT_RULE_NOT_FOUND(193, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_USER(200, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_USER(201, Response.Status.CONFLICT),
		NETSHOT_INVALID_USER_NAME(202, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_PASSWORD(203, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_API_TOKEN_FORMAT(204, Response.Status.BAD_REQUEST),
		NETSHOT_EXPIRED_PASSWORD(205, Response.Status.PRECONDITION_FAILED),
		NETSHOT_FAILED_PASSWORD_POLICY(206, Response.Status.BAD_REQUEST),
		NETSHOT_LOCKED_USER(207, Response.Status.PRECONDITION_FAILED),
		NETSHOT_INVALID_SCRIPT(220, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_SCRIPT(222, Response.Status.CONFLICT),
		NETSHOT_SCRIPT_NOT_FOUND(223, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_DIAGNOSTIC_NAME(230, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_DIAGNOSTIC(231, Response.Status.CONFLICT),
		NETSHOT_INVALID_DIAGNOSTIC_TYPE(232, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_DIAGNOSTIC(233, Response.Status.BAD_REQUEST),
		NETSHOT_INCOMPATIBLE_DIAGNOSTIC(234, Response.Status.BAD_REQUEST),
		NETSHOT_DIAGNOSTIC_NOT_FOUND(235, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_HOOK_NAME(240, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_HOOK(241, Response.Status.CONFLICT),
		NETSHOT_INVALID_HOOK(242, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_HOOK_TYPE(243, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_HOOK_WEB(244, Response.Status.BAD_REQUEST),
		NETSHOT_INVALID_HOOK_WEB_URL(245, Response.Status.BAD_REQUEST),
		NETSHOT_HOOK_NOT_FOUND(246, Response.Status.NOT_FOUND),
		NETSHOT_INVALID_VAULT_INSTANCE_NAME(250, Response.Status.BAD_REQUEST),
		NETSHOT_DUPLICATE_VAULT_INSTANCE(251, Response.Status.CONFLICT),
		NETSHOT_INVALID_VAULT_INSTANCE(252, Response.Status.BAD_REQUEST),
		NETSHOT_VAULT_INSTANCE_IN_USE(253, Response.Status.PRECONDITION_FAILED),
		NETSHOT_VAULT_INSTANCE_NOT_FOUND(254, Response.Status.NOT_FOUND),
		NETSHOT_VAULT_CONNECTION_FAILED(255, Response.Status.BAD_REQUEST);

		@Getter
		int code;

		@Getter
		Response.Status status;

		Reason(int code, Response.Status status) {
			this.code = code;
			this.status = status;
		}
	}

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4538169756895835186L;

	/**
	 * Instantiates a new netshot bad request exception.
	 *
	 * @param message the message
	 * @param reason the reason
	 */
	public NetshotBadRequestException(String message, NetshotBadRequestException.Reason reason) {
		super(Response.status(reason.status)
			.entity(new RsErrorBean(message, reason.code)).build());
	}
}
