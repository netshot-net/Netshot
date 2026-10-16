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
package net.netshot.netshot.hook;

import java.net.MalformedURLException;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.CaCertificates;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Validation of hooks: static, side-effect free (but for the default value of the CA trust
 * mode) functions which throw {@link NetshotException}.
 */
public final class HookValidation {

	private HookValidation() {
		// Static helpers only
	}

	/**
	 * Validates a Web hook: the target URL must be a HTTP(S) one, the action must be set, and the
	 * HTTPS trust policy must be consistent.
	 *
	 * @param webHook the Web hook to validate (its missing CA trust mode is defaulted)
	 * @throws NetshotException if the URL or the action is invalid, or if the CUSTOM_CA mode is
	 *         selected without a valid certificate
	 */
	public static void validateWebHook(WebHook webHook) {
		try {
			webHook.getParsedUrl();
		}
		catch (MalformedURLException e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, "Invalid Web hook target URL");
		}
		if (webHook.getAction() == null) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_WEB, "Invalid action");
		}
		validateWebHookTrustPolicy(webHook);
	}

	/**
	 * Validates a Web hook's HTTPS trust policy: defaults a missing CA trust
	 * mode to {@code SYSTEM_TRUSTSTORE}, and requires/validates a custom CA
	 * certificate when the mode is {@code CUSTOM_CA}.
	 *
	 * @param webHook the Web hook to validate
	 * @throws NetshotException if the CUSTOM_CA mode is selected without a valid certificate
	 */
	public static void validateWebHookTrustPolicy(WebHook webHook) {
		if (webHook.getHttpsCaTrustMode() == null) {
			webHook.setHttpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE);
		}
		if (webHook.getHttpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA) {
			if (webHook.getHttpsCustomCaCertificate() == null || webHook.getHttpsCustomCaCertificate().isBlank()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"The webhook requires a custom CA certificate when the HTTPS trust mode is CUSTOM_CA.");
			}
			CaCertificates.validateCustomCa(webHook.getHttpsCustomCaCertificate(), "the webhook");
		}
	}
}
