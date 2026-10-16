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
package net.netshot.netshot.api.rest.v0.hook;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The request to add or edit a hook, according to its kind (the {@code type} property).
 *
 * <p>Kept as beans rather than records: several properties have a default value (enabled, the
 * triggers, the CA trust mode) which applies when the property is absent, whereas an explicit
 * {@code null} is not the same thing, which a bean with initialized fields does for free.</p>
 */
// Transitional: with @XmlRootElement, Jersey reads a XML body through its JAXB provider, which fails
// (there is no JAXB runtime), as it always did for this request (see the API contract goldens).
@XmlRootElement(name = "hook")
@XmlAccessorType(XmlAccessType.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = HookRequest.Web.class, name = "Web"),
})
public abstract class HookRequest {

	/** The id (ignored when editing a hook). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String name;

	/** Whether the hook is enabled. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean enabled = true;

	/**
	 * The events which trigger the hook. A list rather than a set, to keep the order of the request:
	 * the order the triggers are stored in follows from it.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private List<HookTriggerDto> triggers = new ArrayList<>();

	/**
	 * A Web hook, which calls a URL.
	 */
	public static class Web extends HookRequest {

		/** The way the payload is posted. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private Action action;

		/** The target URL. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String url;

		/** How the certificate of the target is trusted. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private HttpsCaTrustMode httpsCaTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

		/** The PEM trust anchors, for the custom CA mode. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String httpsCustomCaCertificate;
	}
}
