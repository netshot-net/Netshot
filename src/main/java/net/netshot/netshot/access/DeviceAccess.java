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
package net.netshot.netshot.access;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Per-(device, access) configuration: an optional address and/or TCP port
 * override for one of the driver's declared accesses (e.g. "ssh", "telnet",
 * "https", "alternateSsh"), keyed by access name, plus an optional credential
 * pin for that same access. The row's mere existence means the access is
 * used at all - an access with no {@code DeviceAccess} row is never tried
 * (see {@code AccessManager}).
 * <p>
 * This replaces the historical device-wide {@code sshPort}/{@code telnetPort}/
 * {@code connectAddress} fields, which were removed entirely from {@link Device}.
 * <p>
 * Credential pinning is derived purely from which of {@link #globalCredentialSet}
 * / {@link #specificCredentialSet} is set (never both - REST-side validation
 * rejects that): if neither is set, the access falls through to the device's
 * own default credential resolution (unchanged, see {@code AccessManager}).
 * <p>
 * (device, accessName) is naturally unique and is used directly as the
 * primary key - no separate surrogate id.
 */
@Entity
@Table(name = "device_access")
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public final class DeviceAccess {

	/**
	 * SSH server host key verification mode for this access (meaningful only
	 * when the access's protocol is SSH).
	 */
	public enum SshHostKeyVerification {
		/** No verification - any presented host key is accepted (historical behavior). */
		TRUST_ANY,
		/**
		 * Only host keys listed in {@link #sshTrustedHostKeys} are accepted - whether that
		 * list was learned automatically or entered manually is not tracked as a separate
		 * setting: as long as {@link #sshTrustedHostKeys} is empty, the first host key
		 * presented is learned and stored; once at least one key is on record, only a
		 * matching key is ever accepted (a key for a not-yet-seen algorithm is never
		 * silently learned in addition to an existing one).
		 */
		TRUST_KNOWN
	}

	/** The composite primary key: (device, accessName). */
	@Embeddable
	private static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		/** The device this access belongs to. */
		@Getter(onMethod = @__({
			@ManyToOne,
			@OnDelete(action = OnDeleteAction.CASCADE)
		}))
		@Setter
		private Device device;

		/** The access name (matches the driver's declared access, e.g. "ssh", "telnet", "https"). */
		@Getter(onMethod = @__({
			@Column(name = "access_name")
		}))
		@Setter
		private String accessName;

		protected Key() {
			// Reserved for Hibernate
		}

		Key(Device device, String accessName) {
			this.device = device;
			this.accessName = accessName;
		}

		@Override
		public int hashCode() {
			return Objects.hash(device, accessName);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Key)) {
				return false;
			}
			Key other = (Key) obj;
			return Objects.equals(device, other.device) && Objects.equals(accessName, other.accessName);
		}
	}

	/** The key. */
	@Getter(onMethod = @__({
		@EmbeddedId
	}))
	@Setter
	private Key key = new Key();

	/** Optional connection address override (IPv4/IPv6 literal or FQDN). Null = use the device's management address. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String address;

	/** Optional TCP port override. Null = use the driver's/protocol's default port for this access. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Integer port;

	/**
	 * A pinned, existing, shared credential set for this access only. A bare
	 * reference (no cascade) - deleting this {@link DeviceAccess} row (or the
	 * device) must never delete the referenced (shared) credential set.
	 */
	@Getter(onMethod = @__({
		@ManyToOne,
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private DeviceCredentialSet globalCredentialSet;

	/**
	 * A pinned, owned, device-and-access-specific credential set. Owned
	 * (cascade all + orphan removal), mirroring {@code Device.specificCredentialSet}'s
	 * lifecycle exactly.
	 */
	@Getter(onMethod = @__({
		@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private DeviceCredentialSet specificCredentialSet;

	/** SSH host key verification mode (only meaningful for a SSH-protocol access). */
	@Getter(onMethod = @__({
		@Enumerated(EnumType.STRING),
		@Column(name = "ssh_host_key_verification", nullable = false),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private SshHostKeyVerification sshHostKeyVerification = SshHostKeyVerification.TRUST_ANY;

	/**
	 * Trusted SSH host keys for this access, known_hosts-style: one
	 * "{@code <algorithm> <base64-key>}" entry per line (e.g.
	 * {@code ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI...}), no hostname prefix.
	 * When empty, the first host key presented is learned and appended
	 * automatically; once at least one line is present (whether learned or
	 * entered manually), only a matching key is ever accepted - a key for a
	 * not-yet-seen algorithm is rejected rather than silently added.
	 */
	@Getter(onMethod = @__({
		@Column(name = "ssh_trusted_host_keys", columnDefinition = "TEXT"),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String sshTrustedHostKeys;

	/** When {@link #sshTrustedHostKeys} was last learned or edited (display only). */
	@Getter(onMethod = @__({
		@Temporal(TemporalType.TIMESTAMP),
		@Column(name = "ssh_trusted_host_keys_since"),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Date sshTrustedHostKeysSince;

	/** HTTPS certificate CA trust mode (only meaningful for a HTTPS-protocol access). */
	@Getter(onMethod = @__({
		@Enumerated(EnumType.STRING),
		@Column(name = "https_ca_trust_mode", nullable = false),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private HttpsCaTrustMode httpsCaTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

	/**
	 * PEM-encoded trust anchor certificate (optionally a chain of several
	 * concatenated PEM certificates) used when {@link #httpsCaTrustMode} is
	 * {@link HttpsCaTrustMode#CUSTOM_CA}.
	 */
	@Getter(onMethod = @__({
		@Column(name = "https_custom_ca_certificate", columnDefinition = "TEXT"),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String httpsCustomCaCertificate;

	/**
	 * Instantiates a new device access.
	 */
	protected DeviceAccess() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new device access.
	 * @param device the device
	 * @param accessName the access name
	 */
	public DeviceAccess(Device device, String accessName) {
		this.key = new Key(device, accessName);
	}

	/**
	 * Gets the device this access belongs to.
	 * @return the device
	 */
	@Transient
	public Device getDevice() {
		return this.key.getDevice();
	}

	/**
	 * Gets the access name (matches the driver's declared access, e.g. "ssh", "telnet", "https").
	 * @return the access name
	 */
	@Transient
	@XmlElement
	@JsonView(DefaultView.class)
	public String getAccessName() {
		return this.key.getAccessName();
	}

	/**
	 * Sets the access name. Only meant for REST deserialization of an
	 * incoming (not-yet-attached) payload object - see the device mapper and
	 * {@code Device.replaceAccesses}, which reads it back off via
	 * {@link #getAccessName()} rather than persisting this instance as-is.
	 * @param accessName the access name
	 */
	public void setAccessName(String accessName) {
		this.key.setAccessName(accessName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DeviceAccess)) {
			return false;
		}
		DeviceAccess other = (DeviceAccess) obj;
		return Objects.equals(this.key, other.key);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.key);
	}

}
