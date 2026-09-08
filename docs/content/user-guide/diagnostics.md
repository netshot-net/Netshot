# Diagnostics

Thanks to the _Diagnostics_ section, you can collect custom data from your equipment, by making Netshot run specific commands on the devices (and possibly process the result).

Once the diagnostics are defined within Netshot, the commands are effectively executed, and the output is collected by running a _Run diagnostics_ task. This can be done manually by scheduling this type of task, and it is, by default, automatic after a snapshot task.

After the data is saved in the database, it is possible to build search and dynamic group queries based on it. You may also create compliance rules to check the diagnostic result.

Creating, deleting or modifying diagnostics requires _read-write & device commands_ permissions.

## Defining diagnostics

### Simple diagnostics

_Simple diagnostics_ consist of one command to execute in a specific mode of a certain type of device. The output of the command may be further processed by applying a search-and-replace regular expression.

To create a _simple diagnostic_, click the _Create diagnostics..._ button in the main toolbar, in the _Diagnostics_ tab. Then select _Simple diagnostic_ and fill in the required information. The _RegEx pattern_ and _Replace with_ fields are optional.

For example, assume you want to collect the reload reason of Cisco IOS devices. A way to collect this information is to look at the output of the `show version` command:

```text
router1#show version
Cisco IOS XE Software (...)
router1 uptime is 2 days, 23 hours, 21 minutes
(...)
Last reload reason: Reload Command
(...)
Configuration register is 0x2102
```

A diagnostic to execute this command and extract just the interesting part could be defined as follows:

* Command: `show version`
* Execution mode: `exec`
* RegEx pattern: `(?s).*Last reload reason: (.*?)[\r\n].*`
* Replace with: `$1`

!!! note
    The `(?s)` modifier in the regular expression makes `.*` also match line breaks, so it matches all the lines before and after the interesting line, allowing them to be discarded, leaving only the captured group as the diagnostic result.

### JavaScript-based diagnostics

When simple diagnostics are not smart enough to properly collect some diagnostic information, _JavaScript-based diagnostics_ bring all the needed flexibility.

A _JavaScript-based diagnostic_ is a piece of JavaScript code which must define a `diagnose` function that will be called by Netshot to run the diagnostic. This entry point will be called with the following parameters:

* `cli`: an object to interact with the device through CLI.
    * `cli.macro(macro)`: use a macro to change mode as defined in the device driver.
    * `cli.command(command)`: sends a command to the device and returns the result.
* `device`: an object to retrieve known information about the device.
    * `device.get(attributeName)`: get the current value of a known attribute.
* `diagnostic`: an object to set the diagnostic result value.
    * `diagnostic.set(value)`: set the diagnostic result for the current device.
    * `diagnostic.set(diagnosticName, value)`: set the result of the diagnostic identified by its name.

For example, the following code retrieves the current OSPF router-id from IOS-XR devices:

```javascript
function diagnose(cli, device, diagnostic) {
  if (device.get("type") !== "Cisco IOS-XR") {
    return;
  }
  cli.macro("exec");
  var showOspf = cli.command("show ospf");
  debug("showOspf = " + showOspf);
  var routerId = showOspf.match(/Routing Process .* with ID ([0-9\.]+)/m);
  routerId = routerId ? routerId[1] : "Unknown";
  diagnostic.set(routerId);
}
```

To create a _JavaScript-based diagnostic_, click the _Create diagnostics..._ button in the main toolbar, in the _Diagnostics_ tab. Then select _JavaScript-based diagnostic_ and fill in the required information. The _Result type_ must be consistent with the value the script will be setting.

## Viewing diagnostic results

The _Diagnostics_ tab of the device view displays the current diagnostic results, as they were collected during the last _Run diagnostics_ task on this equipment. The diagnostics themselves must be defined as described above.

* The _Name_ column displays the name of the diagnostic.
* The _Value_ column displays the last collected value of the given diagnostic.
* The _First seen_ column gives the date when this value was obtained for the first time.
* The _Last seen_ column gives the date when the value was obtained for the last time (i.e. the last time the diagnostics were run on the device).

A _Run diagnostics_ task is by default automatically scheduled after snapshot tasks, which should collect up-to-date diagnostic results. You can force a diagnostic task to run and refresh the diagnostic values on the given device by clicking the _Run diagnostics on this device_ button.
