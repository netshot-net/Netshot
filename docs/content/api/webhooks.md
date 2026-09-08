# Web hooks

Web hooks are HTTP POST calls that can be automatically executed by Netshot when some events occur.

## Supported options

* POST is the only supported method.
* Possible triggers are:
    * After a device snapshot
    * After a JS script is executed on a device
    * After diagnostics are performed on a device
* The content of the task can be posted in either XML (`application/xml`) or JSON (`application/json`).

## Creating, editing or deleting hooks

Go to the _Admin_ tab, _Web Hooks_ section, and click the _Add..._ button. The dialog lets you name the hook, set the target URL, select the triggering event(s), and choose the payload format (JSON or XML).

## Monitoring

The execution of the hooks can be monitored by looking at the main log file, `/var/log/netshot/netshot.log` by default.

Messages such as the following should appear:

```text
2021-01-23 14:47:13,601 INFO  [QuartzScheduler_Worker-1] TaskJob: Result of post-task hook 'Test Hook' after task 118 is: HTTP response code 200
```

## Content

For a post-task hook, the content of the task will be posted.

Here is an example in JSON format:

```json
{
    "type": ".TakeSnapshotTask",
    "author": "admin",
    "changeDate": 1611412533000,
    "comments": "",
    "creationDate": 1611412516384,
    "debugEnabled": false,
    "executionDate": 1611412517443,
    "id": 122,
    "log": "[INFO] Snapshot task for device router1 (192.168.1.101).\n[DEBUG] Authentication failed 192.168.1.101/0:0 using SSH credential set DEVICESPECIFIC-ee93955a-03d9-4b4e-99b0-90462c33e4a5.\n[TRACE] Will try SSH credentials SSH admin/netshot.\n[DEBUG] Authentication failed using SSH credential set SSH admin/netshot.\n[TRACE] Will try SSH credentials SSH admin/admin.\n[INFO] Connected using SSH to 192.168.1.101:0 using credentials SSH admin/admin.\n[INFO] The configuration hasn't changed. Not storing a new one in the DB.\n",
    "scheduleReference": 1611412516224,
    "scheduleType": "ASAP",
    "status": "SUCCESS",
    "target": "router1",
    "device": {
        "autoTryCredentials": true,
        "changeDate": 1611411940000,
        "comments": "",
        "contact": "",
        "createdDate": 1609456546507,
        "creator": "admin",
        "specificCredentialSet": {
            "type": "SSH",
            "id": 9,
            "name": "DEVICESPECIFIC-ee93955a-03d9-4b4e-99b0-90462c33e4a5",
            "mgmtDomain": null,
            "deviceSpecific": true
        },
        "driver": "CiscoIOS12",
        "eolDate": null,
        "eolModule": null,
        "eosDate": null,
        "eosModule": null,
        "family": "Cisco CSR1000V",
        "location": "Virtual DC",
        "mgmtAddress": {
            "prefixLength": 0,
            "addressUsage": "PRIMARY",
            "ip": "192.168.1.101"
        },
        "name": "router1",
        "networkClass": "ROUTER",
        "serialNumber": "***",
        "softwareLevel": "UNKNOWN",
        "softwareVersion": "15.5(3)S7b",
        "status": "INPRODUCTION",
        "sshPort": 0,
        "telnetPort": 0,
        "connectAddress": null,
        "realDeviceType": "Cisco IOS and IOS-XE",
        "endOfLife": false,
        "endOfSale": false,
        "credentialSetIds": [
            5,
            3
        ],
        "compliant": true
    },
    "automatic": false,
    "dontRunDiagnostics": true,
    "dontCheckCompliance": true,
    "taskDescription": "Device snapshot",
    "deviceId": 4,
    "nextExecutionDate": null,
    "repeating": false
}
```

## SSL certificate validation

By default, when a hook is triggered, if the URL is an `https` one, the server certificate is validated using the Java system TrustStore.

On Debian systems using OpenJDK, this is the file `/etc/ssl/certs/java/cacerts`, which is automatically generated based on the installed CAs by the _ca-certificates-java_ package.

!!! warning
    You can also disable SSL certificate verification on the hook, but this is not advised.
