# Compliance

The purpose of the _Compliance_ section is to keep your devices in a mastered state: any device not conforming to your own policies will be flagged so you can act and fix it.

* _Software rules_ keep track of the running software versions of your devices.
* _Hardware rules_ keep track of the hardware components in use in your network.
* _Configuration policies and rules_ check the other attributes of the devices.

Once the rules are defined within Netshot, the status of a device is refreshed by running a _Check compliance_ task. This can be done manually by scheduling such a task, and this is anyway automatic after snapshot and diagnostic tasks, to ensure that a device becoming non-compliant after a configuration change is immediately flagged.

The software compliance status can be seen in the Compliance tab of the device view, or at the global level in the software compliance report.

Creating, deleting or modifying policies or rules requires _read-write_ permissions.

## Software rules

The _software rules_ are simple rules which assign a level (Gold, Silver or Bronze) to a device, depending on its software version. The software version of a device, which can be seen in the General tab of the device view, is written by the device driver during the snapshots.

* A _Gold_ level means the device is fully compliant with the software strategy.
* A _Silver_ level means the running software version is fine, but not the best choice.
* A _Bronze_ level means the running software version should be rolled out soon.

When evaluating the software compliance status of a device, the rules are processed in order (from top to bottom as seen in the _Software version compliance_ table). The first rule whose criteria match the device's properties assigns its level to the device, and the process stops here.

If no rule is matched, then the device is non-compliant.

To create a software rule, go to the _Compliance_ main page, select the _Software_ section then click on the _+_.

* You can associate the rule with a specific device group, or with all devices.
* You can select a device type (i.e. driver) or apply to all types.
* In _Device family_, enter a string to be found in the device family. If you check the _RegExp_ box, the string will be interpreted as a regular expression, for more flexibility.
* If you want to define your software versions based on precise hardware part numbers, you can use the _Part number_ field to restrict the match to devices containing this piece of hardware. If you check the _RegExp_ box, the string will be interpreted as a regular expression.
* In _Version_, enter a string to be found in the software version field of the device. If you check the _RegExp_ box, the string will be interpreted as a regular expression, for more flexibility.
* Eventually, select the level that will be assigned to any device matching all the criteria defined above.

You can reorder the rules by dragging and dropping a line in the _Software version compliance_ table.

## Hardware rules

_Hardware rules_ aim at following up the support status of the hardware components of devices. A hardware rule defines the end-of-sale and end-of-life (end-of-support) dates for a specific part number. To evaluate the hardware support status of a device, Netshot selects the earliest date for end-of-sale, and for end-of-life among the contained cards (hardware modules).

To create a hardware rule, go to the _Compliance_ page, select the _Hardware_ section, and click on _+_ to add the rule.

* You can select a specific group the rule will apply to, or any group.
* You can select a specific device type (i.e. driver) for the rule, or any type.
* You can enter a _Device family_, a string to be looked for in the family assigned by the driver. If you check the _RegExp_ box, the string will be evaluated as a regular expression.
* You have to enter a _part number_, to find modules (as populated by the driver during snapshots).
* If all the previous criteria match for any module contained in the device, the end-of-sale and end-of-life dates will be assigned to the device, unless an earlier date is already assigned. You can leave one of the two dates empty.

## Configuration policies and rules

Apart from _software_ and _hardware_ rules, you can create advanced rules that will check the configuration or other attributes of the devices.

First you need to create a _policy_, which will contain the actual rules. A policy applies to a group of devices. If you want to assign a policy to all devices, just create a dynamic group without criteria, so it will contain all devices.

To create a policy, click on the _Create policy..._ button in the main toolbar. To edit or delete a policy, select it first, the buttons will appear.

Once you have the policy, select it and click on the _+_ button to add a rule. Note that a new rule is disabled by default, you have to edit it to enable it.

To edit a rule, select it and click on the _Edit_ (wrench icon) button. You can enable or disable it, and add exemptions. To add an exemption, enter the name of the device in the search field, select the device among the results, select the end-of-exemption date, and click on the arrow button.

A rule will check the device attributes and return one of the following:

* `CONFORMING`: the device conforms to the policy.
* `NONCONFORMING`: the device doesn't conform to the policy.
* `NOTAPPLICABLE`: the rule doesn't apply to the device.
* `DISABLED`: the rule is currently disabled.
* `EXEMPTED`: there is an exemption for this device and this rule.
* `INVALIDRULE`: this could happen if a rule script has syntax errors.

There are two types of rules: text-based (or regular expression) rules and script-based (JavaScript or Python) rules.

### Text-based rules

Text-based rules just check that a specific attribute of the device (most of the time, the running configuration) matches some text, or a regular expression.

* Select the type of device (the driver). For devices of another type, the result of the rule checking will be `NOTAPPLICABLE`.
* Select the field to check. This depends on the selected type of device.
* The context is a hierarchy of regular expressions used to find a specific block within the text attribute.
* The identified blocks will have to match the text provided in the main text box.
* This text can be interpreted as a regular expression by checking the relevant box.

For example, in Cisco IOS if you want all interfaces to have a description, define a rule as follows:

* Device type: _Cisco IOS and IOS-XE_
* Field: _Running configuration_
* Context: `^interface .*`
* _All blocks_
* Text (regular expression): `^ *description .+`
* _The text must exist_

Another example, in IOS-XR, if you want all GigabitEthernet interfaces running OSPF to be configured as point-to-point:

* Device type: _Cisco IOS-XR_
* Field: _Configuration_
* Context:

    ```text
    ^router ospf .*
    ^ *area .*
    ^ *interface GigabitEthernet.*
    ```

* _All blocks_
* Text (regular expression): `(?m)^ *network point-to-point$`

    !!! note
        `(?m)` enables multiline mode in the regular expression.

* _The text must exist_

### Script-based rules

The script-based rules are JavaScript or Python code.

Click on _Edit the script..._ to open up the code editor. While you are writing your script, you can test it on a specific device by selecting a device at the bottom of the editor and clicking on _Test..._. Click on the underlined result to see the logs.

Here is a basic (and useless) JavaScript rule, which will set all devices compliant:

```javascript
function check(device) {
  return CONFORMING;
}
```

The function `check(device, [debug])` must exist in the code — this is the entry point, which will be called whenever the device must be validated by the rule.

* `device` is an object to access data of the device being checked, and some utility functions.
    * `device.get(attribute)` allows you to retrieve an attribute of the device, so you can perform further validation on it. The attribute can be one of:
        * `"type"`, the driver of the device.
        * `"name"`, the name of the device.
        * `"family"`, the family of the device.
        * `"managementDomain"`, the management domain of the device.
        * `"location"`, the location of the device.
        * `"contact"`, the contact for the device.
        * `"softwareVersion"`, the OS version of the device.
        * `"serialNumber"`, the main serial number of the device.
        * `"networkClass"`, the class of the device (e.g. `ROUTER`, `SWITCH`, `FIREWALL`, etc.).
        * `"virtualDevices"`, an array of names of the virtual devices embedded in this device (e.g. the list of VDC names for a Cisco Nexus device).
        * `"vrfs"`, an array of names of the VRFs configured on the device.
        * `"interfaces"`, the list of interfaces of the device.
        * Any other attribute name defined by the driver of the device.
    * `device.get(attribute, otherDevice)`, where `otherDevice` is either the ID or the name of another device, will retrieve for you the given attribute value from the specified device instead of the current device. By using this feature, you can compare configurations between devices.
    * `device.nslookup(host)` will return a DNS resolution (either direct or reverse) of the passed name.
    * `device.findSections(configuration, section)`, where `configuration` is a string and `section` is a JavaScript `RegExp` object, will find in `configuration` all lines matching `section`, and will return them along with their content, i.e. the following lines which are more indented.
* `debug(message)` is a function that you can call to log debug messages. These messages will appear in the logs of the _Check compliance_ task.

The `check` function must return one of the following special values:

* `CONFORMING`
* `NONCONFORMING`
* `NOTAPPLICABLE`

You can also return the same value along with a text message, that will appear in the device compliance status, mainly to indicate why the device was not compliant:

```javascript
return {
  result: NONCONFORMING,
  comment: "VTY ACL not applied"
};
```
