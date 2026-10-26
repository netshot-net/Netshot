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
package net.netshot.netshot.driver.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * SSH-specific configuration of a driver access.
 */
public final class SshConfig {

	/** Key exchange algorithms allowed for the session. */
	@Getter
	private List<String> kexAlgorithms = null;

	/** Host key algorithms allowed for the session. */
	@Getter
	private List<String> hostKeyAlgorithms = null;

	/** Ciphers allowed for the session. */
	@Getter
	private List<String> ciphers = null;

	/** MACs allowed for the session. */
	@Getter
	private List<String> macs = null;

	/** Compression algorithms allowed for the session. */
	@Getter
	private List<String> compressionAlgorithms = null;

	/** Rekey time limit (ms), i.e. maximum session duration before a rekey is triggered. */
	@Setter
	@Getter
	private Long rekeyTimeLimit = null;

	/** Rekey data limit (bytes), i.e. maximum amount of data exchanged before a rekey is triggered. */
	@Setter
	@Getter
	private Long rekeyDataLimit = null;

	/** Rekey blocks limit (cipher blocks), i.e. maximum amount of cipher blocks processed before a rekey
	 * is triggered. */
	@Setter
	@Getter
	private Long rekeyBlocksLimit = null;

	/** Rekey packets limit, i.e. maximum number of packets exchanged before a rekey is triggered. */
	@Setter
	@Getter
	private Long rekeyPacketsLimit = null;

	/** User interaction instructions. */
	@Setter
	@Getter
	private List<SshInteractionInstruction> interactionInstructions = null;

	/** Whether to allocate a PTY or not. */
	@Setter
	@Getter
	private Boolean usePty = null;

	/** Type of terminal. */
	@Setter
	@Getter
	private String terminalType = null;

	/** Number of columns in the terminal. */
	@Setter
	@Getter
	private Integer terminalCols = null;

	/** Number of rows in the terminal. */
	@Setter
	@Getter
	private Integer terminalRows = null;

	/** Height of the terminal. */
	@Setter
	@Getter
	private Integer terminalHeight = null;

	/** Width of the terminal. */
	@Setter
	@Getter
	private Integer terminalWidth = null;

	/**
	 * Default algorithms and rekey limits, from the SSH settings of the server.
	 */
	public interface Defaults {
		List<String> kexAlgorithms();

		List<String> hostKeyAlgorithms();

		List<String> ciphers();

		List<String> macs();

		List<String> compressionAlgorithms();

		long rekeyTimeLimit();

		long rekeyDataLimit();

		long rekeyBlocksLimit();

		long rekeyPacketsLimit();
	}

	/**
	 * Builds a configuration with every field {@code null} (a "delta", only ever merged onto
	 * another config, e.g. the one of a driver access).
	 */
	public SshConfig() {
	}

	/**
	 * Builds a complete configuration: the given algorithms and rekey limits, and a vt100 terminal.
	 * @param defaults the default algorithms and rekey limits
	 */
	public SshConfig(Defaults defaults) {
		this.kexAlgorithms = new ArrayList<>(defaults.kexAlgorithms());
		this.hostKeyAlgorithms = new ArrayList<>(defaults.hostKeyAlgorithms());
		this.ciphers = new ArrayList<>(defaults.ciphers());
		this.macs = new ArrayList<>(defaults.macs());
		this.compressionAlgorithms = new ArrayList<>(defaults.compressionAlgorithms());
		this.rekeyTimeLimit = defaults.rekeyTimeLimit();
		this.rekeyDataLimit = defaults.rekeyDataLimit();
		this.rekeyBlocksLimit = defaults.rekeyBlocksLimit();
		this.rekeyPacketsLimit = defaults.rekeyPacketsLimit();
		this.usePty = true;
		this.terminalType = "vt100";
		this.terminalCols = 80;
		this.terminalRows = 24;
		this.terminalHeight = 480;
		this.terminalWidth = 640;
	}

	public void setKexAlgorithms(String[] kexAlgorithms) {
		this.kexAlgorithms = Arrays.asList(kexAlgorithms);
	}

	public void setHostKeyAlgorithms(String[] hostKeyAlgorithms) {
		this.hostKeyAlgorithms = Arrays.asList(hostKeyAlgorithms);
	}

	public void setCiphers(String[] ciphers) {
		this.ciphers = Arrays.asList(ciphers);
	}

	public void setMacs(String[] macs) {
		this.macs = Arrays.asList(macs);
	}

	public void setCompressionAlgorithms(String[] compressionAlgorithms) {
		this.compressionAlgorithms = Arrays.asList(compressionAlgorithms);
	}

	/**
	 * Applies the set (non-null) fields of another configuration onto this one.
	 * @param other the configuration to apply
	 */
	public void apply(SshConfig other) {
		if (other.kexAlgorithms != null) {
			this.kexAlgorithms = other.kexAlgorithms;
		}
		if (other.hostKeyAlgorithms != null) {
			this.hostKeyAlgorithms = other.hostKeyAlgorithms;
		}
		if (other.ciphers != null) {
			this.ciphers = other.ciphers;
		}
		if (other.macs != null) {
			this.macs = other.macs;
		}
		if (other.compressionAlgorithms != null) {
			this.compressionAlgorithms = other.compressionAlgorithms;
		}
		if (other.rekeyTimeLimit != null) {
			this.rekeyTimeLimit = other.rekeyTimeLimit;
		}
		if (other.rekeyDataLimit != null) {
			this.rekeyDataLimit = other.rekeyDataLimit;
		}
		if (other.rekeyBlocksLimit != null) {
			this.rekeyBlocksLimit = other.rekeyBlocksLimit;
		}
		if (other.rekeyPacketsLimit != null) {
			this.rekeyPacketsLimit = other.rekeyPacketsLimit;
		}
		if (other.usePty != null) {
			this.usePty = other.usePty;
		}
		if (other.terminalType != null) {
			this.terminalType = other.terminalType;
		}
		if (other.terminalCols != null) {
			this.terminalCols = other.terminalCols;
		}
		if (other.terminalRows != null) {
			this.terminalRows = other.terminalRows;
		}
		if (other.terminalHeight != null) {
			this.terminalHeight = other.terminalHeight;
		}
		if (other.terminalWidth != null) {
			this.terminalWidth = other.terminalWidth;
		}
		if (other.interactionInstructions != null) {
			this.interactionInstructions = other.interactionInstructions;
		}
	}
}
