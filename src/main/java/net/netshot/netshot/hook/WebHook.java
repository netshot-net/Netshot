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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.Set;

import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * A Web hook: the data of the event is posted to a URL.
 *
 * @param id the ID (0 if not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param enabled whether the hook is enabled
 * @param triggers the triggers
 * @param action what is posted
 * @param url the target URL
 * @param httpsCaTrustMode how the certificate of an HTTPS target is checked
 * @param httpsCustomCaCertificate the CA (PEM) of {@link HttpsCaTrustMode#CUSTOM_CA}
 */
public record WebHook(long id, int version, Instant changeDate, String name, boolean enabled, Set<HookTrigger> triggers, Action action, String url,
	HttpsCaTrustMode httpsCaTrustMode, String httpsCustomCaCertificate) implements Hook {

	/**
	 * What a Web hook posts.
	 */
	public enum Action {
		POST_XML,
		POST_JSON,
		POST_YAML,
	}

	@Override
	public WebHook withId(long newId) {
		return new WebHook(newId, this.version, this.changeDate, this.name, this.enabled, this.triggers, this.action, this.url, this.httpsCaTrustMode,
			this.httpsCustomCaCertificate);
	}

	@Override
	public WebHook withTriggers(Set<HookTrigger> newTriggers) {
		return new WebHook(this.id, this.version, this.changeDate, this.name, this.enabled, newTriggers, this.action, this.url, this.httpsCaTrustMode,
			this.httpsCustomCaCertificate);
	}

	/**
	 * Gets the same hook with another HTTPS trust mode.
	 *
	 * @param newMode the trust mode
	 * @return the hook
	 */
	public WebHook withHttpsCaTrustMode(HttpsCaTrustMode newMode) {
		return new WebHook(this.id, this.version, this.changeDate, this.name, this.enabled, this.triggers, this.action, this.url, newMode,
			this.httpsCustomCaCertificate);
	}

	/**
	 * Parses the target URL.
	 *
	 * @return the URL
	 * @throws MalformedURLException if the URL is empty, invalid, relative, or not HTTP(S)
	 */
	public URL parsedUrl() throws MalformedURLException {
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
		catch (URISyntaxException | IllegalArgumentException e) {
			// IllegalArgumentException: a relative URI, which can't be turned into a URL
			throw new MalformedURLException("Invalid URL");
		}
	}

	/**
	 * Describes the hook in the logs (the audit log among others): the URL may hold credentials.
	 */
	@Override
	public String toString() {
		return "Hook [id=" + this.id + ", name=" + this.name + "]";
	}
}
