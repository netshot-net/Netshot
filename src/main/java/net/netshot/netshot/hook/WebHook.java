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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * A Web hook, called after specific event.
 */
@Entity
public class WebHook extends Hook {

	/**
	 * Types of web hook.
	 */
	public enum Action {
		POST_XML, POST_JSON, POST_YAML,
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
	public WebHook() {
	}

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
	public HttpsCaTrustMode getHttpsCaTrustMode() {
		return httpsCaTrustMode;
	}

	public void setHttpsCaTrustMode(HttpsCaTrustMode httpsCaTrustMode) {
		this.httpsCaTrustMode = httpsCaTrustMode;
	}

	@Column(columnDefinition = "TEXT")
	public String getHttpsCustomCaCertificate() {
		return httpsCustomCaCertificate;
	}

	public void setHttpsCustomCaCertificate(String httpsCustomCaCertificate) {
		this.httpsCustomCaCertificate = httpsCustomCaCertificate;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}
}
