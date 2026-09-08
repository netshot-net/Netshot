# Example: NTP stratum diagnostic

This example defines a simple diagnostic that extracts the current NTP stratum from Cisco IOS devices, using the output of the `show ntp status` command:

```text
router1#show ntp status
Clock is synchronized, stratum 2, reference is 192.168.1.1
nominal freq is 250.0000 Hz, actual freq is 250.0000 Hz, precision is 2**18
reference time is E5B4A5C1.00000000 (10:00:00.000 UTC Mon Aug 3 2026)
```

The diagnostic can be defined as a _Simple diagnostic_ (see [Diagnostics](../user-guide/diagnostics.md)) with the following fields:

* Command: `show ntp status`
* Execution mode: `exec`
* RegEx pattern: `(?s).*stratum (\d+).*`
* Replace with: `$1`
* Result type: `Text`

With this definition, Netshot runs `show ntp status` on the device, then keeps only the stratum number from the output (e.g. `2`) as the diagnostic result. Once collected, the value appears in the diagnostic tab of the device, and can be used to build search or dynamic group criteria — for example, to find every device not synchronized at stratum 1 or 2.
