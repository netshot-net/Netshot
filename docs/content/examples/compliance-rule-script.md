# Example: enforcing an IS-IS NET address scheme (script rule)

Let's assume you want to enforce the configured IS-IS NET address on your IOS and IOS-XR routers to derive from the name of the router.

Example:

| Router | NET address |
|--------|-------------|
| XR1    | 49.0001.0000.0000.0001.00 |
| XR2    | 49.0002.0000.0000.0002.00 |
| XR3    | 49.0003.0000.0000.0003.00 |
| RTR11  | 49.000b.0000.0000.000b.00 |
| RTR12  | 49.000c.0000.0000.000c.00 |

This is not a simple pattern match, so a script-based rule is required to achieve this result.

Here is how you can write it:

```javascript
function check(device) {
    // Get the type of device and ensure it starts with IOS, otherwise the script doesn't apply
    var type = device.get('type');
    if (type.match(/Cisco IOS.*/)) {
        var name = device.get('name');
        // Get the final number in the device name
        var id = name.match(/.*?([0-9]+)/);
        if (!id) {
            return {
                result: NOTAPPLICABLE,
                comment: "Does not apply to this hostname"
            };
        }
        // Transform the number into a NET address (hex)
        id = parseInt(id[1]);
        id = id.toString(16).toLowerCase();
        var net = "";
        for (i = 0; i < 4 - id.length; i++) {
            net += "0";
        }
        net += id;
        net = "49." + net + ".0000.0000." + net + ".00";

        // Get the config (configuration for XR, runningConfig otherwise)
        var config = device.get('configuration');
        if (!config) config = device.get('runningConfig');
        // Find the IS-IS section
        var isis = device.findSections(config, /^router isis .*/);
        if (isis.length === 0) {
            return {
                result: NONCONFORMING,
                comment: "No IS-IS block"
            };
        }
        isis = isis[0].config;
        // Get the configured NET address
        var cNet = isis.match(/^ net ([0-9a-f\.]+)/);
        if (!cNet) {
            return {
                result: NONCONFORMING,
                comment: "No NET configured"
            };
        }
        cNet = cNet[1];
        // Compare the computed and the configured NET addresses
        if (cNet === net) {
            return CONFORMING;
        }
        else {
            return {
                result: NONCONFORMING,
                comment: "Configured NET " + cNet + ", should be " + net
            };
        }
    }
    return NOTAPPLICABLE;
}
```
