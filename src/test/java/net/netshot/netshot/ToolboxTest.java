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
package net.netshot.netshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.device.script.helper.ScriptToolbox;
import net.netshot.netshot.diagnostic.PythonDiagnostic;

/**
 * Tests of the script toolbox (toolbox.js and toolbox.py).
 * Both implementations run the same cases (through the JS_RUNNER and PYTHON_RUNNER
 * harnesses), and must return the same results - the expected ones when given.
 * The findSections cases (legacy helper, JavaScript only) only run on the JavaScript side.
 */
public class ToolboxTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Cisco IOS configuration (Cisco_IOS.js device simulator output, plus BGP and banners). */
	private static final String CISCO_IOS = """
		!
		! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin
		!
		version 15.5
		service timestamps debug datetime msec
		service timestamps log datetime msec
		!
		hostname router1
		!
		boot-start-marker
		boot-end-marker
		!
		no aaa new-model
		!
		no ip domain lookup
		!
		interface Loopback0
		 ip address 10.255.0.1 255.255.255.255
		!
		interface GigabitEthernet1
		 description Management
		 ip address 192.168.200.101 255.255.255.0
		 negotiation auto
		!
		interface GigabitEthernet2
		 ip address 10.0.0.1 255.255.255.254
		 ip ospf network point-to-point
		 negotiation auto
		!
		interface GigabitEthernet3
		 no ip address
		 shutdown
		 negotiation auto
		!
		interface GigabitEthernet4
		 no ip address
		 shutdown
		 negotiation auto
		!
		router ospf 1
		 network 10.0.0.0 0.0.0.1 area 0
		 network 10.255.0.1 0.0.0.0 area 0
		!
		ip forward-protocol nd
		!
		no ip http server
		no ip http secure-server
		ip ssh version 2
		!
		access-list 98 permit 192.168.200.0 0.0.0.255
		!
		snmp-server community cisco RO 98
		snmp-server location SNMPLOCATION
		snmp-server contact SNMPCONTACT
		snmp-server enable traps config
		!
		!
		control-plane
		!
		!
		line con 0
		 stopbits 1
		line vty 0 4
		 login local
		 transport input telnet ssh
		line vty 5 15
		 login local
		 transport input telnet ssh
		!
		ntp server pool.ntp.org
		!
		banner motd ^C
		Authorized access only!
		  Disconnect now ^C
		!
		router bgp 65001
		 bgp log-neighbor-changes
		 neighbor 10.0.0.2 remote-as 65002
		 !
		 address-family ipv4
		  network 10.255.0.1 mask 255.255.255.255
		  neighbor 10.0.0.2 activate
		 exit-address-family
		!
		banner login ^CSingle line banner^C
		!
		end
		""";

	/** Arista EOS configuration (Arista_MOS.js device simulator output). */
	private static final String ARISTA_EOS = """
		! command: show running-config
		! time: Tue 11 Oct 2022 09:02:39
		! device: switch1 (C48-A6, MOS-0.31.0)
		\s
		hostname switch1
		username admin secret sha512 $6$Iwn/TscxWEdXQVcu$yeqcWHWUt1qVmldsfPVM/O9z2hiYs/iL35WNP6zOcM.PwkGRVgTO8r3kWp3k4DpRGHYnohK/xx3gw//rxqlPo1
		tacacs-server host 10.18.18.1 key 7 095C4F1A0A1218000F
		tacacs-server host 10.18.19.1 key 7 12090404011C03162E
		\s
		clock timezone GB
		ntp server 10.18.18.12 prefer
		ntp server 10.18.19.12
		\s
		logging host 10.1.18.10
		\s
		interface et1
		    source et3
		\s
		interface et2
		    source et1
		\s
		interface et3
		    source et1
		\s
		interface et4
		    source et3
		\s
		interface et5
		    source et7
		\s
		interface et6
		    source et5
		\s
		interface et7
		    source et5
		\s
		interface et8
		    source et7
		\s
		interface et9
		    source et11
		\s
		interface et10
		    source et9
		\s
		interface et11
		    source et9
		\s
		interface et12
		    source et11
		\s
		interface et13
		    description to switch2
		    source et15
		\s
		interface et14
		    shutdown
		\s
		interface et15
		    description to switch2
		    source et13
		\s
		interface et16
		    shutdown
		\s
		interface et17
		    shutdown
		\s
		interface et18
		    shutdown
		\s
		interface et19
		    shutdown
		\s
		interface et20
		    shutdown
		\s
		interface et21
		    description to switch3
		    source et23
		\s
		interface et22
		    shutdown
		\s
		interface et23
		    description to switch4
		    negotiation
		    source et21
		\s
		interface et24
		    shutdown
		\s
		interface et25
		    shutdown
		\s
		interface et26
		    shutdown
		\s
		interface et27
		    shutdown
		\s
		interface et28
		    shutdown
		\s
		interface et29
		    shutdown
		\s
		interface et30
		    shutdown
		\s
		interface et31
		    shutdown
		\s
		interface et32
		    shutdown
		\s
		interface et33
		    shutdown
		\s
		interface et34
		    shutdown
		\s
		interface et35
		    shutdown
		\s
		interface et36
		    shutdown
		\s
		interface et37
		    shutdown
		\s
		interface et38
		    shutdown
		\s
		interface et39
		    shutdown
		\s
		interface et40
		    shutdown
		\s
		interface et41
		    shutdown
		\s
		interface et42
		    shutdown
		\s
		interface et43
		    shutdown
		\s
		interface et44
		    shutdown
		\s
		interface et45
		    shutdown
		\s
		interface et46
		    shutdown
		\s
		interface et47
		    shutdown
		\s
		interface et48
		    shutdown
		\s
		interface ma1
		    ip address 10.18.25.40 255.255.255.0
		    ip default-gateway 10.18.25.254
		\s
		management snmp
		    snmp-server community comm1 ro
		    snmp-server community comm2 ro
		    snmp-server community comm3 ro
		    snmp-server host 10.1.18.135 version 2c comm1
		    snmp-server host 10.1.20.135 version 2c comm1
		    snmp-server host 10.2.18.135 version 2c comm2
		    snmp-server host 10.2.18.135 version 2c comm2
		\s
		end
		""";

	/** Juniper Junos configuration (Juniper_Junos.js device simulator output, plus comments and inactive block). */
	private static final String JUNIPER_JUNOS = """
		## Last commit: 2024-01-01 08:00:00 UTC by admin
		version 21.4R3.15;
		system {
		    host-name mx1;
		    root-authentication {
		        encrypted-password "$6$netshot$fakehashfakehashfakehash";
		    }
		    services {
		        ssh;
		        netconf {
		            ssh;
		        }
		    }
		}
		interfaces {
		    ge-0/0/0 {
		        description "Uplink to core";
		        unit 0 {
		            family inet {
		                address 10.0.0.1/30;
		            }
		        }
		    }
		    ge-0/0/1 {
		        disable;
		    }
		}
		/* Monitoring
		   configuration */
		inactive: protocols {
		    lldp {
		        interface all;
		    }
		}
		snmp {
		    location "Room 42";
		    contact "netshot@example.com";
		}
		""";

	/** Palo Alto PAN-OS configuration (Palo-Alto_PAN-OS.js device simulator output). */
	private static final String PALOALTO_PANOS = """
		config {
		  mgt-config {
		    users {
		      admin {
		        permissions {
		          role-based {
		            superuser yes;
		          }
		        }
		        phash $5$netshot$fakehashfakehashfakehashfakehashfakehash;
		      }
		    }
		  }
		  shared {
		    application;
		    application-group;
		    service;
		    service-group;
		  }
		  devices {
		    localhost.localdomain {
		      network {
		        interface {
		          ethernet {
		            ethernet1/1 {
		              layer3 {
		                ip {
		                  198.51.100.2/29;
		                }
		              }
		              comment UNTRUST;
		            }
		            ethernet1/2 {
		              layer3 {
		                units {
		                  ethernet1/2.810 {
		                    ip {
		                      172.16.10.1/26;
		                    }
		                    tag 810;
		                    comment "DMZ servers";
		                  }
		                }
		              }
		            }
		            ethernet1/7 {
		              ha;
		            }
		            ethernet1/8 {
		              ha;
		            }
		          }
		          loopback {
		            units;
		          }
		          vlan {
		            units;
		          }
		          tunnel {
		            units;
		          }
		        }
		      }
		      deviceconfig {
		        system {
		          hostname fw1;
		          ip-address 192.0.2.10;
		          netmask 255.255.255.248;
		          default-gateway 192.0.2.9;
		        }
		      }
		    }
		  }
		}
		""";

	/** Fortinet FortiOS configuration. */
	private static final String FORTINET_FORTIOS = """
		#config-version=FGT60F-7.2.8-FW-build1639-240313:opmode=0:vdom=0:user=admin
		#conf_file_ver=123456789
		#buildno=1639
		#global_vdom=1
		config system global
		    set alias "FGT60F"
		    set hostname "fw-branch1"
		    set timezone 28
		end
		config system interface
		    edit "wan1"
		        set vdom "root"
		        set ip 198.51.100.2 255.255.255.248
		        set allowaccess ping https ssh
		        set type physical
		        set role wan
		        config secondaryip
		            edit 1
		                set ip 198.51.100.3 255.255.255.248
		                set allowaccess ping
		            next
		        end
		    next
		    edit "internal"
		        set vdom "root"
		        set ip 192.168.1.99 255.255.255.0
		        set allowaccess ping https ssh http fgfm
		        set type hard-switch
		        set role lan
		    next
		    edit "dmz"
		        set vdom "root"
		        set status down
		        set type physical
		    next
		end
		config system snmp sysinfo
		    set status enable
		    set description "Branch firewall"
		    set location "Room 42"
		end
		config vpn certificate local
		    edit "branch-cert"
		        set password ENC fakefakefake
		        set comments "Multi-line certificate"
		        set certificate "-----BEGIN CERTIFICATE-----
		MIIBszCCAVmgAwIBAgIUFAKEFAKEFAKEFAKEFAKEFAKEFAKEwCgYIKoZIzj0EAwIw
		FAKEFAKEFAKE
		-----END CERTIFICATE-----"
		    next
		end
		config firewall policy
		    edit 1
		        set name "LAN to WAN"
		        set srcintf "internal"
		        set dstintf "wan1"
		        set action accept
		        set srcaddr "all"
		        set dstaddr "all"
		        set schedule "always"
		        set service "ALL"
		        set nat enable
		    next
		    edit 2
		        set name "DMZ to WAN"
		        set srcintf "dmz"
		        set dstintf "wan1"
		        set srcaddr "all"
		        set dstaddr "all"
		        set schedule "always"
		        set service "HTTP" "HTTPS"
		    next
		end
		""";

	/** JavaScript harness: runs one case and returns the result as JSON. */
	private static final String JS_RUNNER = """
		function runToolboxCase(text, caseJson) {
		    const c = JSON.parse(caseJson);
		    const options = { recursive: !!c.recursive };
		    const groups = (match) => Array.from(match, (g) => (g === undefined ? null : g));
		    const nodes = (list) => list.map((n) => [n.line, n.text, n.match ? groups(n.match) : null]);
		    const outline = (n, depth, out) => {
		        for (const child of n.children) {
		            out.push("  ".repeat(depth) + `${child.line}:${child.indent} ${child.text.replace(/\\n/g, "\\\\n")}`
		                + (child.closing === null ? "" : ` [${child.closing.trim()}]`) + (child.isComment ? " (comment)" : ""));
		            outline(child, depth + 1, out);
		        }
		        return out;
		    };
		    if (c.op === "findSections") {
		        return JSON.stringify(__netshotToolbox.findSections(text, new RegExp(c.regex)).map((s) => [groups(s.match), s.lines, s.config]));
		    }
		    let node = Toolbox.ConfigTree.parse(text, { syntax: c.syntax, indented: c.indented });
		    for (const r of c.on) {
		        const found = node.find(r, { recursive: true });
		        if (found.length === 0) {
		            throw `Can't find ${r}`;
		        }
		        node = found[0];
		    }
		    let result;
		    switch (c.op) {
		    case "tree": result = outline(node, 0, []).map((l) => l + "\\n").join(""); break;
		    case "find": result = nodes(node.find(c.regex, options)); break;
		    case "findWithChild": result = nodes(node.findWithChild(c.regex, c.child, options)); break;
		    case "findWithoutChild": result = nodes(node.findWithoutChild(c.regex, c.child, options)); break;
		    case "hasChild": result = node.hasChild(c.regex, options); break;
		    case "findValue": result = node.findValue(c.regex, options); break;
		    case "findValues": result = node.findValues(c.regex, options); break;
		    case "config": result = [node.config, node.lines]; break;
		    case "path": result = node.path; break;
		    case "parents": result = node.filter(() => true, { recursive: true })
		        .map((n) => [n.line, n.parent === n ? "self" : n.parent.line]); break;
		    case "settings": result = node.settings(); break;
		    case "setting": result = [node.setting(c.regex), node.settingValues(c.regex)]; break;
		    default: throw `Unknown op ${c.op}`;
		    }
		    return JSON.stringify(result);
		}
		""";

	/** Python harness: runs one case and returns the result as JSON. */
	private static final String PYTHON_RUNNER = """
		import json
		from netshot.toolbox import ConfigTree

		def run_toolbox_case(text, case_json):
		    c = json.loads(case_json)
		    recursive = c["recursive"]
		    def groups(match):
		        return [match.group(0)] + list(match.groups())
		    def nodes(lst):
		        return [[n.line, n.text, groups(n.match) if n.match else None] for n in lst]
		    def outline(n, depth, out):
		        for child in n.children:
		            out.append("  " * depth + "%d:%d %s" % (child.line, child.indent, child.text.replace("\\n", "\\\\n"))
		                       + ("" if child.closing is None else " [%s]" % child.closing.strip())
		                       + (" (comment)" if child.is_comment else ""))
		            outline(child, depth + 1, out)
		        return out
		    node = ConfigTree.parse(text, syntax=c["syntax"], indented=c["indented"])
		    for r in c["on"]:
		        found = node.find(r, recursive=True)
		        if len(found) == 0:
		            raise ValueError("Can't find " + r)
		        node = found[0]
		    op = c["op"]
		    if op == "tree":
		        result = "".join(line + "\\n" for line in outline(node, 0, []))
		    elif op == "find":
		        result = nodes(node.find(c["regex"], recursive))
		    elif op == "findWithChild":
		        result = nodes(node.find_with_child(c["regex"], c["child"], recursive))
		    elif op == "findWithoutChild":
		        result = nodes(node.find_without_child(c["regex"], c["child"], recursive))
		    elif op == "hasChild":
		        result = node.has_child(c["regex"], recursive)
		    elif op == "findValue":
		        result = node.find_value(c["regex"], recursive)
		    elif op == "findValues":
		        result = node.find_values(c["regex"], recursive)
		    elif op == "config":
		        result = [node.config, node.lines]
		    elif op == "path":
		        result = node.path
		    elif op == "parents":
		        result = [[n.line, "self" if n.parent is n else n.parent.line]
		                  for n in node.filter(lambda n: True, recursive=True)]
		    elif op == "settings":
		        result = node.settings()
		    elif op == "setting":
		        result = [node.setting(c["regex"]), node.setting_values(c["regex"])]
		    else:
		        raise ValueError("Unknown op " + op)
		    return json.dumps(result)
		""";

	/**
	 * A test case, run against both implementations: a query (op) on the parsed text.
	 * The "tree" op returns an outline of the tree, one "line:indent text [closing]" line per node.
	 */
	private static final class ToolboxCase {
		private final String name;
		private final String text;
		private final String syntax;
		private final String op;
		private final List<String> on = new ArrayList<>();
		private String regex;
		private String child;
		private boolean recursive;
		private boolean indented;
		private JsonNode expected;

		ToolboxCase(String name, String text, String syntax, String op) {
			this.name = name;
			this.text = text;
			this.syntax = syntax;
			this.op = op;
		}

		/** Run the query on the first node (searched recursively) matching the regex. */
		ToolboxCase on(String nodeRegex) {
			this.on.add(nodeRegex);
			return this;
		}

		ToolboxCase regex(String queryRegex) {
			this.regex = queryRegex;
			return this;
		}

		ToolboxCase child(String childRegex) {
			this.child = childRegex;
			return this;
		}

		ToolboxCase recursive() {
			this.recursive = true;
			return this;
		}

		/** Parse with the indented option. */
		ToolboxCase indented() {
			this.indented = true;
			return this;
		}

		ToolboxCase expect(String json) {
			try {
				this.expected = MAPPER.readTree(json);
			}
			catch (IOException e) {
				throw new IllegalArgumentException("Invalid expected JSON for case " + this.name, e);
			}
			return this;
		}

		ToolboxCase expectOutline(String outline) {
			this.expected = MAPPER.getNodeFactory().textNode(outline);
			return this;
		}

		/** Whether the case only applies to the JavaScript implementation (legacy findSections helper). */
		boolean isJsOnly() {
			return "findSections".equals(this.op);
		}

		String toJson() {
			ObjectNode json = MAPPER.createObjectNode();
			json.put("op", this.op);
			json.put("syntax", this.syntax);
			json.put("regex", this.regex);
			json.put("child", this.child);
			json.put("recursive", this.recursive);
			json.put("indented", this.indented);
			ArrayNode onArray = json.putArray("on");
			this.on.forEach(onArray::add);
			return json.toString();
		}
	}

	private static final List<ToolboxCase> CASES = List.of(
			new ToolboxCase("ios-tree", CISCO_IOS, "indent", "tree"),
			new ToolboxCase("ios-find-interfaces", CISCO_IOS, "indent", "find").regex("^interface (\\S+)")
				.expect("[[17,\"interface Loopback0\",[\"interface Loopback0\",\"Loopback0\"]],[20,\"interface GigabitEthe"
					+ "rnet1\",[\"interface GigabitEthernet1\",\"GigabitEthernet1\"]],[25,\"interface GigabitEthernet2\""
					+ ",[\"interface GigabitEthernet2\",\"GigabitEthernet2\"]],[30,\"interface GigabitEthernet3\",[\"int"
					+ "erface GigabitEthernet3\",\"GigabitEthernet3\"]],[35,\"interface GigabitEthernet4\",[\"interface"
					+ " GigabitEthernet4\",\"GigabitEthernet4\"]]]"),
			new ToolboxCase("ios-find-recursive", CISCO_IOS, "indent", "find").regex("^neighbor (\\S+) (\\S+)").recursive()
				.expect("[[78,\"neighbor 10.0.0.2 remote-as 65002\",[\"neighbor 10.0.0.2 remote-as\",\"10.0.0.2\",\"remote"
					+ "-as\"]],[82,\"neighbor 10.0.0.2 activate\",[\"neighbor 10.0.0.2 activate\",\"10.0.0.2\",\"activate"
					+ "\"]]]"),
			new ToolboxCase("ios-find-not-recursive", CISCO_IOS, "indent", "find").regex("^neighbor")
				.expect("[]"),
			new ToolboxCase("ios-find-with-child", CISCO_IOS, "indent", "findWithChild").regex("^interface").child("^shutdown$")
				.expect("[[30,\"interface GigabitEthernet3\",[\"interface\"]],[35,\"interface GigabitEthernet4\",[\"interf"
					+ "ace\"]]]"),
			new ToolboxCase("ios-find-without-child", CISCO_IOS, "indent", "findWithoutChild").regex("^interface").child("^shutdown$")
				.expect("[[17,\"interface Loopback0\",[\"interface\"]],[20,\"interface GigabitEthernet1\",[\"interface\"]],"
					+ "[25,\"interface GigabitEthernet2\",[\"interface\"]]]"),
			new ToolboxCase("ios-find-with-child-recursive", CISCO_IOS, "indent", "findWithChild").regex("^address-family").child("activate").recursive()
				.expect("[[80,\"address-family ipv4\",[\"address-family\"]]]"),
			new ToolboxCase("ios-has-child", CISCO_IOS, "indent", "hasChild").on("^interface GigabitEthernet2$").regex("^ip ospf network point-to-point$")
				.expect("true"),
			new ToolboxCase("ios-has-no-child", CISCO_IOS, "indent", "hasChild").on("^interface GigabitEthernet1$").regex("^shutdown")
				.expect("false"),
			new ToolboxCase("ios-find-value", CISCO_IOS, "indent", "findValue").regex("^hostname (\\S+)")
				.expect("\"router1\""),
			new ToolboxCase("ios-find-value-no-group", CISCO_IOS, "indent", "findValue").regex("^ntp server \\S+")
				.expect("\"ntp server pool.ntp.org\""),
			new ToolboxCase("ios-find-value-none", CISCO_IOS, "indent", "findValue").regex("^ip domain name (\\S+)")
				.expect("null"),
			new ToolboxCase("ios-find-values", CISCO_IOS, "indent", "findValues").regex("^ip address (\\S+) (\\S+)").recursive()
				.expect("[\"10.255.0.1\",\"192.168.200.101\",\"10.0.0.1\"]"),
			new ToolboxCase("ios-find-values-optional-group", CISCO_IOS, "indent", "findValues").regex("^snmp-server (community|location)?")
				.expect("[\"community\",\"location\",null,null]"),
			new ToolboxCase("ios-config", CISCO_IOS, "indent", "config").on("^router bgp")
				.expect("[\" bgp log-neighbor-changes\\n neighbor 10.0.0.2 remote-as 65002\\n !\\n address-family ipv4\\n  network"
					+ " 10.255.0.1 mask 255.255.255.255\\n  neighbor 10.0.0.2 activate\\n exit-address-family\",[\" bgp log-nei"
					+ "ghbor-changes\",\" neighbor 10.0.0.2 remote-as 65002\",\" !\",\" address-family ipv4\",\"  network 10.255.0."
					+ "1 mask 255.255.255.255\",\"  neighbor 10.0.0.2 activate\",\" exit-address-family\"]]"),
			new ToolboxCase("ios-path", CISCO_IOS, "indent", "path").on("^address-family").on("activate$")
				.expect("[\"router bgp 65001\",\"address-family ipv4\",\"neighbor 10.0.0.2 activate\"]"),
			new ToolboxCase("ios-banner", CISCO_IOS, "indent", "find").regex("^banner")
				.expect("[[72,\"banner motd ^C\\nAuthorized access only!\\n  Disconnect now ^C\",[\"banner\"]],[85,\"banne"
					+ "r login ^CSingle line banner^C\",[\"banner\"]]]"),
			new ToolboxCase("eos-tree", ARISTA_EOS, "indent", "tree"),
			new ToolboxCase("eos-find-with-child", ARISTA_EOS, "indent", "findWithChild").regex("^interface (\\S+)").child("^description")
				.expect("[[52,\"interface et13\",[\"interface et13\",\"et13\"]],[59,\"interface et15\",[\"interface et15\",\"e"
					+ "t15\"]],[78,\"interface et21\",[\"interface et21\",\"et21\"]],[85,\"interface et23\",[\"interface et"
					+ "23\",\"et23\"]]]"),
			new ToolboxCase("junos-tree", JUNIPER_JUNOS, "braces", "tree")
				.expectOutline("""
					1:0 ## Last commit: 2024-01-01 08:00:00 UTC by admin (comment)
					2:0 version 21.4R3.15
					3:0 system [}]
					  4:4 host-name mx1
					  5:4 root-authentication [}]
					    6:8 encrypted-password "$6$netshot$fakehashfakehashfakehash"
					  8:4 services [}]
					    9:8 ssh
					    10:8 netconf [}]
					      11:12 ssh
					15:0 interfaces [}]
					  16:4 ge-0/0/0 [}]
					    17:8 description "Uplink to core"
					    18:8 unit 0 [}]
					      19:12 family inet [}]
					        20:16 address 10.0.0.1/30
					  24:4 ge-0/0/1 [}]
					    25:8 disable
					28:0 /* Monitoring\\n   configuration */ (comment)
					30:0 inactive: protocols [}]
					  31:4 lldp [}]
					    32:8 interface all
					35:0 snmp [}]
					  36:4 location "Room 42"
					  37:4 contact "netshot@example.com"
					"""),
			new ToolboxCase("junos-find-value", JUNIPER_JUNOS, "braces", "findValue").regex("^host-name (\\S+)").recursive()
				.expect("\"mx1\""),
			new ToolboxCase("junos-find-with-child", JUNIPER_JUNOS, "braces", "findWithChild").regex("^ge-").child("^disable$").recursive()
				.expect("[[24,\"ge-0/0/1\",[\"ge-\"]]]"),
			new ToolboxCase("junos-find-values", JUNIPER_JUNOS, "braces", "findValues").regex("^address (\\S+)").recursive()
				.expect("[\"10.0.0.1/30\"]"),
			new ToolboxCase("junos-config", JUNIPER_JUNOS, "braces", "config").on("^interfaces$")
				.expect("[\"    ge-0/0/0 {\\n        description \\\"Uplink to core\\\";\\n        unit 0 {\\n            f"
					+ "amily inet {\\n                address 10.0.0.1/30;\\n            }\\n        }\\n    }\\n    g"
					+ "e-0/0/1 {\\n        disable;\\n    }\",[\"    ge-0/0/0 {\",\"        description \\\"Uplink to cor"
					+ "e\\\";\",\"        unit 0 {\",\"            family inet {\",\"                address 10.0.0.1/30;"
					+ "\",\"            }\",\"        }\",\"    }\",\"    ge-0/0/1 {\",\"        disable;\",\"    }\"]]"),
			new ToolboxCase("junos-path", JUNIPER_JUNOS, "braces", "path").on("^address ")
				.expect("[\"interfaces\",\"ge-0/0/0\",\"unit 0\",\"family inet\",\"address 10.0.0.1/30\"]"),
			new ToolboxCase("panos-tree", PALOALTO_PANOS, "braces", "tree"),
			new ToolboxCase("panos-find-values", PALOALTO_PANOS, "braces", "findValues").on("^ethernet$").regex("^(ethernet\\S+)$")
				.expect("[\"ethernet1/1\",\"ethernet1/2\",\"ethernet1/7\",\"ethernet1/8\"]"),
			new ToolboxCase("panos-find-value", PALOALTO_PANOS, "braces", "findValue").on("^deviceconfig$").regex("^hostname (\\S+)").recursive()
				.expect("\"fw1\""),
			new ToolboxCase("fortios-tree", FORTINET_FORTIOS, "fortios", "tree")
				.expectOutline("""
					1:0 #config-version=FGT60F-7.2.8-FW-build1639-240313:opmode=0:vdom=0:user=admin (comment)
					2:0 #conf_file_ver=123456789 (comment)
					3:0 #buildno=1639 (comment)
					4:0 #global_vdom=1 (comment)
					5:0 config system global [end]
					  6:4 set alias "FGT60F"
					  7:4 set hostname "fw-branch1"
					  8:4 set timezone 28
					10:0 config system interface [end]
					  11:4 edit "wan1" [next]
					    12:8 set vdom "root"
					    13:8 set ip 198.51.100.2 255.255.255.248
					    14:8 set allowaccess ping https ssh
					    15:8 set type physical
					    16:8 set role wan
					    17:8 config secondaryip [end]
					      18:12 edit 1 [next]
					        19:16 set ip 198.51.100.3 255.255.255.248
					        20:16 set allowaccess ping
					  24:4 edit "internal" [next]
					    25:8 set vdom "root"
					    26:8 set ip 192.168.1.99 255.255.255.0
					    27:8 set allowaccess ping https ssh http fgfm
					    28:8 set type hard-switch
					    29:8 set role lan
					  31:4 edit "dmz" [next]
					    32:8 set vdom "root"
					    33:8 set status down
					    34:8 set type physical
					37:0 config system snmp sysinfo [end]
					  38:4 set status enable
					  39:4 set description "Branch firewall"
					  40:4 set location "Room 42"
					42:0 config vpn certificate local [end]
					  43:4 edit "branch-cert" [next]
					    44:8 set password ENC fakefakefake
					    45:8 set comments "Multi-line certificate"
					    46:8 set certificate "-----BEGIN CERTIFICATE-----\\nMIIBszCCAVmgAwIBAgIUFAKEFAKEFAKEFAKEFAKEFAKEFAKEwCgYIKoZIzj0EAwIw\\nFAKEFAKEFAKE\\n-----END CERTIFICATE-----"
					52:0 config firewall policy [end]
					  53:4 edit 1 [next]
					    54:8 set name "LAN to WAN"
					    55:8 set srcintf "internal"
					    56:8 set dstintf "wan1"
					    57:8 set action accept
					    58:8 set srcaddr "all"
					    59:8 set dstaddr "all"
					    60:8 set schedule "always"
					    61:8 set service "ALL"
					    62:8 set nat enable
					  64:4 edit 2 [next]
					    65:8 set name "DMZ to WAN"
					    66:8 set srcintf "dmz"
					    67:8 set dstintf "wan1"
					    68:8 set srcaddr "all"
					    69:8 set dstaddr "all"
					    70:8 set schedule "always"
					    71:8 set service "HTTP" "HTTPS"
					"""),
			new ToolboxCase("fortios-find-edit", FORTINET_FORTIOS, "fortios", "find").on("^config system interface$").regex("^edit \"(.+)\"")
				.expect("[[11,\"edit \\\"wan1\\\"\",[\"edit \\\"wan1\\\"\",\"wan1\"]],[24,\"edit \\\"internal\\\"\",[\"edit \\\"internal\\\""
					+ "\",\"internal\"]],[31,\"edit \\\"dmz\\\"\",[\"edit \\\"dmz\\\"\",\"dmz\"]]]"),
			new ToolboxCase("fortios-find-without-child", FORTINET_FORTIOS, "fortios", "findWithoutChild").on("^config firewall policy$").regex("^edit").child("^set nat enable$")
				.expect("[[64,\"edit 2\",[\"edit\"]]]"),
			new ToolboxCase("fortios-find-value", FORTINET_FORTIOS, "fortios", "findValue").on("^config system global$").regex("^set hostname \"(.*)\"")
				.expect("\"fw-branch1\""),
			new ToolboxCase("fortios-config", FORTINET_FORTIOS, "fortios", "config").on("^edit \"wan1\"$")
				.expect("[\"        set vdom \\\"root\\\"\\n        set ip 198.51.100.2 255.255.255.248\\n        set allo"
					+ "waccess ping https ssh\\n        set type physical\\n        set role wan\\n        config se"
					+ "condaryip\\n            edit 1\\n                set ip 198.51.100.3 255.255.255.248\\n      "
					+ "          set allowaccess ping\\n            next\\n        end\",[\"        set vdom \\\"root\\\""
					+ "\",\"        set ip 198.51.100.2 255.255.255.248\",\"        set allowaccess ping https ssh\",\""
					+ "        set type physical\",\"        set role wan\",\"        config secondaryip\",\"          "
					+ "  edit 1\",\"                set ip 198.51.100.3 255.255.255.248\",\"                set allow"
					+ "access ping\",\"            next\",\"        end\"]]"),
			new ToolboxCase("fortios-certificate", FORTINET_FORTIOS, "fortios", "find").regex("^set certificate").recursive()
				.expect("[[46,\"set certificate \\\"-----BEGIN CERTIFICATE-----\\nMIIBszCCAVmgAwIBAgIUFAKEFAKEFAKEFAKEF"
					+ "AKEFAKEFAKEwCgYIKoZIzj0EAwIw\\nFAKEFAKEFAKE\\n-----END CERTIFICATE-----\\\"\",[\"set certificate"
					+ "\"]]]"),
			new ToolboxCase("sections-ios-interfaces", CISCO_IOS, null, "findSections").regex("^interface (\\S+)")
				.expect("[[[\"interface Loopback0\",\"Loopback0\"],[\" ip address 10.255.0.1 255.255.255.255\"],\" ip addr"
					+ "ess 10.255.0.1 255.255.255.255\"],[[\"interface GigabitEthernet1\",\"GigabitEthernet1\"],[\" des"
					+ "cription Management\",\" ip address 192.168.200.101 255.255.255.0\",\" negotiation auto\"],\" de"
					+ "scription Management\\n ip address 192.168.200.101 255.255.255.0\\n negotiation auto\"],[[\"in"
					+ "terface GigabitEthernet2\",\"GigabitEthernet2\"],[\" ip address 10.0.0.1 255.255.255.254\",\" ip"
					+ " ospf network point-to-point\",\" negotiation auto\"],\" ip address 10.0.0.1 255.255.255.254\\n"
					+ " ip ospf network point-to-point\\n negotiation auto\"],[[\"interface GigabitEthernet3\",\"Gigab"
					+ "itEthernet3\"],[\" no ip address\",\" shutdown\",\" negotiation auto\"],\" no ip address\\n shutdow"
					+ "n\\n negotiation auto\"],[[\"interface GigabitEthernet4\",\"GigabitEthernet4\"],[\" no ip address"
					+ "\",\" shutdown\",\" negotiation auto\"],\" no ip address\\n shutdown\\n negotiation auto\"]]"),
			new ToolboxCase("sections-ios-nested", CISCO_IOS, null, "findSections").regex("^ *(network|neighbor) (\\S+)")
				.expect("[[[\" network 10.0.0.0\",\"network\",\"10.0.0.0\"],[],\"\"],[[\" network 10.255.0.1\",\"network\",\"10."
					+ "255.0.1\"],[],\"\"],[[\" neighbor 10.0.0.2\",\"neighbor\",\"10.0.0.2\"],[],\"\"],[[\"  network 10.255."
					+ "0.1\",\"network\",\"10.255.0.1\"],[],\"\"],[[\"  neighbor 10.0.0.2\",\"neighbor\",\"10.0.0.2\"],[],\"\"]]"),
			new ToolboxCase("sections-fortios-edit", FORTINET_FORTIOS, null, "findSections").regex("^ *edit \"(.*)\"")
				.expect("[[[\"    edit \\\"wan1\\\"\",\"wan1\"],[\"        set vdom \\\"root\\\"\",\"        set ip 198.51.100.2 2"
					+ "55.255.255.248\",\"        set allowaccess ping https ssh\",\"        set type physical\",\"    "
					+ "    set role wan\",\"        config secondaryip\",\"            edit 1\",\"                set i"
					+ "p 198.51.100.3 255.255.255.248\",\"                set allowaccess ping\",\"            next\","
					+ "\"        end\"],\"        set vdom \\\"root\\\"\\n        set ip 198.51.100.2 255.255.255.248\\n  "
					+ "      set allowaccess ping https ssh\\n        set type physical\\n        set role wan\\n   "
					+ "     config secondaryip\\n            edit 1\\n                set ip 198.51.100.3 255.255.2"
					+ "55.248\\n                set allowaccess ping\\n            next\\n        end\"],[[\"    edit "
					+ "\\\"internal\\\"\",\"internal\"],[\"        set vdom \\\"root\\\"\",\"        set ip 192.168.1.99 255.25"
					+ "5.255.0\",\"        set allowaccess ping https ssh http fgfm\",\"        set type hard-switch\""
					+ ",\"        set role lan\"],\"        set vdom \\\"root\\\"\\n        set ip 192.168.1.99 255.255.2"
					+ "55.0\\n        set allowaccess ping https ssh http fgfm\\n        set type hard-switch\\n    "
					+ "    set role lan\"],[[\"    edit \\\"dmz\\\"\",\"dmz\"],[\"        set vdom \\\"root\\\"\",\"        set s"
					+ "tatus down\",\"        set type physical\"],\"        set vdom \\\"root\\\"\\n        set status do"
					+ "wn\\n        set type physical\"],[[\"    edit \\\"branch-cert\\\"\",\"branch-cert\"],[\"        set "
					+ "password ENC fakefakefake\",\"        set comments \\\"Multi-line certificate\\\"\",\"        set "
					+ "certificate \\\"-----BEGIN CERTIFICATE-----\"],\"        set password ENC fakefakefake\\n      "
					+ "  set comments \\\"Multi-line certificate\\\"\\n        set certificate \\\"-----BEGIN CERTIFICAT"
					+ "E-----\"]]"),
			new ToolboxCase("sections-junos", JUNIPER_JUNOS, null, "findSections").regex("^ +(ge-\\S+) \\{")
				.expect("[[[\"    ge-0/0/0 {\",\"ge-0/0/0\"],[\"        description \\\"Uplink to core\\\";\",\"        unit 0"
					+ " {\",\"            family inet {\",\"                address 10.0.0.1/30;\",\"            }\",\"  "
					+ "      }\"],\"        description \\\"Uplink to core\\\";\\n        unit 0 {\\n            family i"
					+ "net {\\n                address 10.0.0.1/30;\\n            }\\n        }\"],[[\"    ge-0/0/1 {\""
					+ ",\"ge-0/0/1\"],[\"        disable;\"],\"        disable;\"]]"),
			new ToolboxCase("ios-comments-tree", "!\ninterface a\n description x\n !\n shutdown\n!\nend\n", "indent", "tree")
				.expectOutline("""
					1:0 ! (comment)
					2:0 interface a
					  3:1 description x
					  4:1 ! (comment)
					  5:1 shutdown
					6:0 ! (comment)
					7:0 end
					"""),
			new ToolboxCase("ios-indented-find", CISCO_IOS, "indent", "find").indented().regex("^\\s+shutdown$").recursive()
				.expect("[[32,\" shutdown\",[\" shutdown\"]],[37,\" shutdown\",[\" shutdown\"]]]"),
			new ToolboxCase("ios-indented-find-with-child", CISCO_IOS, "indent", "findWithChild").indented()
				.regex("^interface (\\S+)").child("^\\s+shutdown$")
				.expect("[[30,\"interface GigabitEthernet3\",[\"interface GigabitEthernet3\",\"GigabitEthernet3\"]],[35,\"interface "
					+ "GigabitEthernet4\",[\"interface GigabitEthernet4\",\"GigabitEthernet4\"]]]"),
			new ToolboxCase("ios-indented-path", CISCO_IOS, "indent", "path").indented().on("activate$")
				.expect("[\"router bgp 65001\",\" address-family ipv4\",\"  neighbor 10.0.0.2 activate\"]"),
			new ToolboxCase("ios-path-top-level", CISCO_IOS, "indent", "path").on("^interface Loopback0$")
				.expect("[\"interface Loopback0\"]"),
			new ToolboxCase("junos-indented-tree", JUNIPER_JUNOS, "braces", "tree").indented()
				.expectOutline("""
					1:0 ## Last commit: 2024-01-01 08:00:00 UTC by admin (comment)
					2:0 version 21.4R3.15
					3:0 system [}]
					  4:4     host-name mx1
					  5:4     root-authentication [}]
					    6:8         encrypted-password "$6$netshot$fakehashfakehashfakehash"
					  8:4     services [}]
					    9:8         ssh
					    10:8         netconf [}]
					      11:12             ssh
					15:0 interfaces [}]
					  16:4     ge-0/0/0 [}]
					    17:8         description "Uplink to core"
					    18:8         unit 0 [}]
					      19:12             family inet [}]
					        20:16                 address 10.0.0.1/30
					  24:4     ge-0/0/1 [}]
					    25:8         disable
					28:0 /* Monitoring\\n   configuration */ (comment)
					30:0 inactive: protocols [}]
					  31:4     lldp [}]
					    32:8         interface all
					35:0 snmp [}]
					  36:4     location "Room 42"
					  37:4     contact "netshot@example.com"
					"""),
			new ToolboxCase("fortios-indented-find", FORTINET_FORTIOS, "fortios", "find").indented().regex("^ {8}set ip (\\S+)").recursive()
				.expect("[[13,\"        set ip 198.51.100.2 255.255.255.248\",[\"        set ip 198.51.100.2\",\"198.51.100.2\"]],["
					+ "26,\"        set ip 192.168.1.99 255.255.255.0\",[\"        set ip 192.168.1.99\",\"192.168.1.99\"]]]"),
			new ToolboxCase("parents", "a\n b\n  c\n !\nd\n", "indent", "parents")
				.expect("[[1,\"self\"],[2,1],[3,2],[4,1],[5,\"self\"]]"),
			new ToolboxCase("parents-of-query-result", "a\n b\n  c\nd\n", "indent", "parents").on("^a$")
				.expect("[[2,1],[3,2]]"),
			new ToolboxCase("fortios-settings", FORTINET_FORTIOS, "fortios", "settings").on("^edit \"wan1\"$")
				.expect("{\"vdom\":[\"root\"],\"ip\":[\"198.51.100.2\",\"255.255.255.248\"],\"allowaccess\":[\"ping\",\"https\",\"ssh\"],"
					+ "\"type\":[\"physical\"],\"role\":[\"wan\"]}"),
			new ToolboxCase("fortios-settings-indented", FORTINET_FORTIOS, "fortios", "settings").indented().on("^ *edit 1$")
				.expect("{\"ip\":[\"198.51.100.3\",\"255.255.255.248\"],\"allowaccess\":[\"ping\"]}"),
			new ToolboxCase("fortios-settings-certificate", FORTINET_FORTIOS, "fortios", "settings").on("^edit \"branch-cert\"$")
				.expect("{\"password\":[\"ENC\",\"fakefakefake\"],\"comments\":[\"Multi-line certificate\"],\"certificate\":[\"-----BE"
					+ "GIN CERTIFICATE-----\\nMIIBszCCAVmgAwIBAgIUFAKEFAKEFAKEFAKEFAKEFAKEFAKEwCgYIKoZIzj0EAwIw\\nFAKEFAKEFAKE"
					+ "\\n-----END CERTIFICATE-----\"]}"),
			new ToolboxCase("fortios-setting-multiple", FORTINET_FORTIOS, "fortios", "setting").on("^config firewall policy$").on("^edit 2$")
				.regex("service")
				.expect("[\"HTTP HTTPS\",[\"HTTP\",\"HTTPS\"]]"),
			new ToolboxCase("fortios-setting-missing", FORTINET_FORTIOS, "fortios", "setting").on("^edit \"dmz\"$").regex("role")
				.expect("[null,[]]"),
			new ToolboxCase("settings-quoting",
				"config a\n"
					+ "    set comments \"say \\\"hi\\\" to C:\\\\temp\"\n"
					+ "    set empty \"\"\n"
					+ "    set novalue\n"
					+ "    set mixed one \"two three\"   four\n"
					+ "    set joined ab\"c d\"e\n"
					+ "    set raw back\\slash\n"
					+ "    # set commented yes\n"
					+ "    set status enable\n"
					+ "    set status disable\n"
					+ "    set removed yes\n"
					+ "    unset removed\n"
					+ "    setting nope\n"
					+ "end\n", "fortios", "settings").on("^config a$")
				.expect("{\"comments\":[\"say \\\"hi\\\" to C:\\\\temp\"],\"empty\":[\"\"],\"novalue\":[],\"mixed\":[\"one\",\"two three\","
					+ "\"four\"],\"joined\":[\"abc de\"],\"raw\":[\"back\\\\slash\"],\"status\":[\"disable\"]}"),
			new ToolboxCase("empty-text", "", "indent", "tree")
				.expectOutline(""),
			new ToolboxCase("unbalanced-braces", "a {\n  b;\n}\n}\nc {\n  d {\n    e;\n", "braces", "tree")
				.expectOutline("""
					1:0 a [}]
					  2:2 b
					5:0 c
					  6:2 d
					    7:4 e
					"""),
			new ToolboxCase("unbalanced-fortios", "config a\n    edit 1\n        set x 1\n    end\nnext\nset y\n", "fortios", "tree")
				.expectOutline("""
					1:0 config a [end]
					  2:4 edit 1
					    3:8 set x 1
					6:0 set y
					"""),
			new ToolboxCase("tabs-and-crlf", "a\r\n\tb\r\n\t\tc\r\n\td\r\ne", "indent", "tree")
				.expectOutline("""
					1:0 a
					  2:1 b
					    3:2 c
					  4:1 d
					5:0 e
					"""),
			new ToolboxCase("eos-banner-eof", "hostname x\nbanner login\nWelcome\n  to this switch\nEOF\nntp server 1.2.3.4\n", "indent", "tree")
				.expectOutline("""
					1:0 hostname x
					2:0 banner login\\nWelcome\\n  to this switch\\nEOF
					6:0 ntp server 1.2.3.4
					"""),
			new ToolboxCase("sections-whitespace-lines", "a\n \n  b\n \nc\n  d", null, "findSections").regex("^\\s*$")
				.expect("[[[\" \"],[],\"\"],[[\" \"],[],\"\"]]")
	);

	private static Context jsContext;

	private static Context pyContext;

	@BeforeAll
	static void createContexts() {
		jsContext = Context.newBuilder("js").build();
		ScriptToolbox.loadJs(jsContext);
		jsContext.eval("js", JS_RUNNER);
		pyContext = Context.newBuilder("python").build();
		ScriptToolbox.loadPython(pyContext);
		pyContext.eval("python", PYTHON_RUNNER);
	}

	@AfterAll
	static void closeContexts() {
		jsContext.close();
		pyContext.close();
	}

	@TestFactory
	@DisplayName("Same results with the JavaScript and Python implementations")
	Stream<DynamicTest> cases() {
		return CASES.stream().map((testCase) -> DynamicTest.dynamicTest(testCase.name, () -> {
			Value jsResult = jsContext.getBindings("js").getMember("runToolboxCase")
				.execute(testCase.text, testCase.toJson());
			JsonNode js = MAPPER.readTree(jsResult.asString());
			if (testCase.isJsOnly()) {
				Assertions.assertNotNull(testCase.expected, "No expected result for JavaScript-only case " + testCase.name);
			}
			else {
				Value pyResult = pyContext.getBindings("python").getMember("run_toolbox_case")
					.execute(testCase.text, testCase.toJson());
				JsonNode py = MAPPER.readTree(pyResult.asString());
				Assertions.assertEquals(js, py, "Different JavaScript and Python results for case " + testCase.name);
			}
			if (testCase.expected != null) {
				Assertions.assertEquals(testCase.expected, js, "Unexpected result for case " + testCase.name);
			}
		}));
	}

	@Nested
	@DisplayName("JavaScript specifics")
	class JsSpecifics {

		@Test
		@DisplayName("Global Toolbox can be shadowed by user code, internal alias can't be overwritten")
		void shadowing() {
			try (Context context = Context.newBuilder("js").build()) {
				ScriptToolbox.loadJs(context);
				context.eval("js", "const Toolbox = 42; __netshotToolbox = null;");
				Assertions.assertEquals(42, context.eval("js", "Toolbox").asInt());
				Assertions.assertTrue(context.eval("js", "typeof __netshotToolbox.findSections === 'function'").asBoolean());
				Assertions.assertTrue(context.eval("js", "globalThis.Toolbox.findSections === undefined").asBoolean());
				Assertions.assertTrue(context.eval("js", "Object.isFrozen(globalThis.Toolbox.ConfigTree)").asBoolean());
			}
		}

		@Test
		@DisplayName("Query results don't alter the tree")
		void immutableQueries() {
			try (Context context = Context.newBuilder("js").build()) {
				ScriptToolbox.loadJs(context);
				Value ok = context.eval("js", ""
					+ "const t = Toolbox.ConfigTree.parse('interface a\\n shutdown\\n');"
					+ "const r = t.find(/^interface (\\S+)/);"
					+ "r[0].match[1] === 'a' && t.children[0].match === null && r[0].children === t.children[0].children "
					+ "&& r[0].hasChild('shutdown') && r[0].filter((n) => n.text === 'shutdown').length === 1");
				Assertions.assertTrue(ok.asBoolean());
			}
		}

		@Test
		@DisplayName("Invalid parameters")
		void invalidParameters() {
			try (Context context = Context.newBuilder("js").build()) {
				ScriptToolbox.loadJs(context);
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("js", "Toolbox.ConfigTree.parse('a', { syntax: 'xml' })"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("js", "Toolbox.ConfigTree.parse(42)"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("js", "Toolbox.ConfigTree.parse('a').find(42)"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("js", "__netshotToolbox.findSections('a', 'a')"));
			}
		}
	}

	@Nested
	@DisplayName("Python specifics")
	class PythonSpecifics {

		@Test
		@DisplayName("Module registration and namespace isolation")
		void module() {
			try (Context context = Context.newBuilder("python").build()) {
				ScriptToolbox.loadPython(context);
				ScriptToolbox.loadPython(context);
				Value ok = context.eval("python", ""
					+ "import netshot.toolbox\n"
					+ "from netshot.toolbox import ConfigTree, CiscoConfParse\n"
					+ "'_netshot_register_toolbox' not in globals() and 're' not in globals() "
					+ "and netshot.toolbox.ConfigTree is ConfigTree and not hasattr(netshot.toolbox, 'find_sections')\n");
				Assertions.assertTrue(ok.asBoolean());
			}
		}

		@Test
		@DisplayName("ciscoconfparse compatibility")
		void ciscoConfParse() throws IOException {
			try (Context context = Context.newBuilder("python").build()) {
				ScriptToolbox.loadPython(context);
				context.getBindings("python").putMember("config", CISCO_IOS);
				Value result = context.eval("python", ""
					+ "from netshot.toolbox import CiscoConfParse\n"
					+ "parse = CiscoConfParse(config.splitlines())\n"
					+ "shut = parse.find_objects_w_child(r'^interface', r'^\\s+shutdown')\n"
					+ "ospf = parse.find_objects(r'^interface')[2].re_match_iter_typed(r'ip ospf network (\\S+)')\n"
					+ "comments = len([o for o in parse.objs if o.is_comment])\n"
					+ "[[o.text for o in shut], ospf, parse.find_objects(r'^\\s+network')[0].geneology_text, "
					+ "parse.re_match_iter_typed(r'^hostname (\\S+)'), comments, parse.objs[1].linenum]\n");
				Assertions.assertEquals("[['interface GigabitEthernet3', 'interface GigabitEthernet4'], "
					+ "'point-to-point', ['router ospf 1', ' network 10.0.0.0 0.0.0.1 area 0'], 'router1', 27, 1]",
					result.toString());
			}
		}

		@Test
		@DisplayName("ciscoconfparse aliases on ConfigTree nodes")
		void ciscoConfParseAliases() {
			try (Context context = Context.newBuilder("python").build()) {
				ScriptToolbox.loadPython(context);
				context.getBindings("python").putMember("config", CISCO_IOS);
				Value result = context.eval("python", ""
					+ "from netshot.toolbox import ConfigTree, ConfigNode, CiscoConfParse\n"
					+ "tree = ConfigTree.parse(config, indented=True)\n"
					+ "parse = CiscoConfParse(config)\n"
					+ "same = [[n.text for n in tree.find_objects_w_child(r'^interface', r'^\\s+shutdown')]"
					+ " == [n.text for n in parse.find_objects_w_child(r'^interface', r'^\\s+shutdown')],"
					+ " tree.ioscfg == parse.ioscfg, [n.linenum for n in tree.objs] == [n.linenum for n in parse.objs]]\n"
					+ "intf = tree.find(r'^interface GigabitEthernet2$')[0]\n"
					+ "top = tree.children[1]\n"
					+ "[isinstance(parse, ConfigNode), same, top.parent is top, top.is_child, intf.parent is intf,"
					+ " intf.children[0].is_child, intf.children[0].parent.text,"
					+ " intf.re_search_children(r'^\\s+ip ospf'), intf.has_child_with(r'negotiation'),"
					+ " intf.re_match_iter_typed(r'ip address (\\S+)'), intf.children[1].geneology_text,"
					+ " ConfigTree.parse(config).find(r'^interface Loopback0$')[0].re_search_children(r'^ip address')]\n");
				Assertions.assertEquals("[True, [True, True, True], True, False, True, True, 'interface GigabitEthernet2', "
					+ "[<ConfigNode #27 ' ip ospf network point-to-point'>], True, '10.0.0.1', "
					+ "['interface GigabitEthernet2', ' ip ospf network point-to-point'], "
					+ "[<ConfigNode #18 'ip address 10.255.0.1 255.255.255.255'>]]",
					result.toString());
			}
		}

		@Test
		@DisplayName("Invalid parameters")
		void invalidParameters() {
			try (Context context = Context.newBuilder("python").build()) {
				ScriptToolbox.loadPython(context);
				context.eval("python", "from netshot.toolbox import ConfigTree, CiscoConfParse");
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("python", "ConfigTree.parse('a', syntax='xml')"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("python", "ConfigTree.parse(42)"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("python", "ConfigTree.parse('a').find(42)"));
				Assertions.assertThrows(PolyglotException.class,
					() -> context.eval("python", "CiscoConfParse('a', syntax='xml')"));
			}
		}
	}

	@Nested
	@DisplayName("Driver context")
	class DriverContext {

		DriverContext() throws Exception {
			Netshot.initConfig(new Properties());
			DeviceDriver.refreshDrivers();
		}

		@Test
		@DisplayName("Toolbox available in driver context")
		void driverToolbox() throws Exception {
			DeviceDriver driver = DeviceDriver.getDriverByName("CiscoIOS12");
			try (Context context = driver.getContext()) {
				driver.loadCode(context);
				Value result = context.eval("js", "Toolbox.ConfigTree.parse('a\\n b').children[0].findValue(/^(b)$/)");
				Assertions.assertEquals("b", result.asString());
			}
		}

		@Test
		@DisplayName("Toolbox module available to Python diagnostics")
		void pythonDiagnosticToolbox() throws Exception {
			DeviceDriver driver = DeviceDriver.getDriverByName("CiscoIOS12");
			PythonDiagnostic diagnostic = new PythonDiagnostic("Toolbox diagnostic", true, null, AttributeType.TEXT, ""
				+ "from netshot.toolbox import ConfigTree\n"
				+ "def diagnose(cli, device, diagnostic):\n"
				+ "  return ConfigTree.parse('config system global\\n  set hostname \"fw\"\\nend', syntax='fortios')"
				+ ".find_value(r'^set hostname \"(.*)\"', recursive=True)\n");
			try (Context context = driver.getContext()) {
				driver.loadCode(context);
				Value diagnose = diagnostic.getJsObject(null, context);
				Assertions.assertEquals("fw", diagnose.execute(null, null, null).asString());
			}
		}
	}
}
