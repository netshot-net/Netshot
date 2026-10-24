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
 * Validation of hooks: static, side-effect free functions which throw {@link NetshotException}.
 */
public final class HookValidation {

	private HookValidation() {
		// Static helpers only
	}

	/**
	 * Validates a Web hook: the target URL must be a HTTP(S) one, the action must be set, and the
	 * HTTPS trust policy must be consistent.
	 *
	 * @param webHook the hook
	 * @return the hook, with the default CA trust mode if none is given
	 */
	public static WebHook validateWebHook(WebHook webHook) {
		try {
			webHook.parsedUrl();
		}
		catch (MalformedURLException e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, "Invalid Web hook target URL");
		}
		if (webHook.action() == null) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_HOOK_WEB, "Invalid action");
		}
		return validateWebHookTrustPolicy(webHook);
	}

	/**
	 * Validates the HTTPS trust policy of a Web hook: a custom CA must be given (and valid) in
	 * {@link HttpsCaTrustMode#CUSTOM_CA} mode.
	 *
	 * @param webHook the hook
	 * @return the hook, with the default CA trust mode ({@link HttpsCaTrustMode#SYSTEM_TRUSTSTORE}) if none is given
	 */
	public static WebHook validateWebHookTrustPolicy(WebHook webHook) {
		WebHook checked = webHook.httpsCaTrustMode() == null ? webHook.withHttpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE) : webHook;
		if (checked.httpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA) {
			if (checked.httpsCustomCaCertificate() == null || checked.httpsCustomCaCertificate().isBlank()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"The webhook requires a custom CA certificate when the HTTPS trust mode is CUSTOM_CA.");
			}
			CaCertificates.validateCustomCa(checked.httpsCustomCaCertificate(), "the webhook");
		}
		return checked;
	}
}
