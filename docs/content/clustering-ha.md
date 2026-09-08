# Clustering and high availability

Netshot supports running in **cluster mode**. In this mode, several Netshot instances (servers) interact to load balance the tasks (snapshots, compliance checks, etc.). In case of failure of a member, the pending and running tasks are automatically redistributed to the other instances.

!!! note
    Clustering requires PostgreSQL as the underlying database.

The other component to look at, in order to achieve full redundancy, is obviously the database. With PostgreSQL, [multiple options exist](https://www.postgresql.org/docs/12/high-availability.html) for that. Netshot also brings enhancements in the way it connects to the database, for better support of PostgreSQL primary/secondary setups.

## Application-level redundancy

### Roles

#### Master

Among the members of a cluster, one member (and only one) is elected as the **master** unit. The role of the master instance is to load balance new tasks to the possible runners (including itself if selected as task runner). It also monitors the other members and reassigns tasks in case of failure of one of them.

The master instance is chosen at startup among the available members, based on the highest _master priority_ (100 by default, this can be customized in the [configuration reference](configuration-reference.md)). The local instance ID is the tie breaker. There is no preemption on the master role.

#### Runner

The master distributes jobs only to the (available) instances configured with the highest _runner priority_ (100 by default). If multiple instances have the same high runner priority, jobs are load balanced based on the relative weights of these instances.

For instance, if the cluster has the following members:

| Member | Master priority | Runner priority | Runner weight |
|--------|-----------------|-----------------|---------------|
| server1 | 110 | 90  | 100 |
| server2 | 100 | 100 | 80 |
| server3 | 100 | 100 | 20 |

Assuming that all servers are started simultaneously:

* server1 will be the cluster master.
* About 4/5th of the tasks will be assigned to server2, 1/5th to server3.
* In case of failure of server2 or server3, all tasks will run on the remaining one.
* In case of failure of both server2 and server3, server1 will execute the tasks itself.
* In case of failure of server1, the new master will be either server2 or server3, based on their IDs.

#### Instance ID

Each instance joining the cluster requires a unique identifier. This should be set up in the [configuration reference](configuration-reference.md). If the configuration is missing, an ID will be automatically generated, but this is not recommended as the ID could change after a restart.

### Communication between members

Cluster members exchange HA messages using the PostgreSQL notification system as message bus. Thus, there is no need to open an additional protocol/port on firewalls, or to install anything else. Only access to the PostgreSQL server/cluster from each Netshot cluster member is required.

### User interface

The northbound interface (Web GUI, REST API) remains available on all cluster members, whatever their actual role.

Dedicated API endpoints are available to retrieve the clustering status (see the OpenAPI browser).

### Configuration files

Each server instance has its own configuration file. You must ensure that the various instance configurations are consistent, especially the clustering configuration (of course), and the encryption settings.

### Device drivers

The device drivers are NOT automatically synchronized between the cluster members. If you install custom drivers on one instance (additional JavaScript drivers), they need to be manually copied to the other instances. The synchronization status of the device drivers between cluster instances can be checked in the _Admin_ page, by looking at the driver hash of each instance member.

!!! note
    The _Refresh drivers_ button on the _Admin_ page automatically propagates the refresh to the other instances.

### Device configuration files

For device drivers (Checkpoint, for example) which store data as files (outside of the database), an additional synchronization mechanism must be set up to ensure that these files are copied to all instances exposing the northbound interface. The folder to synchronize is `/var/local/netshot` (or whatever is configured in the main configuration file under `netshot.snapshots.binary.path`).

Possible solutions:

* Shared partition mount point, for example using NFS.
* Frequent rsync's between servers.
* ...

### SNMP collector

The integrated SNMP collector reacts to SNMP traps indicating a configuration change on a device, to trigger an automatic snapshot. When working in cluster mode, any Netshot instance can receive the trap; it will notify the master to trigger a snapshot for the given device, assuming another automatic snapshot is not already pending.

## Database redundancy

PostgreSQL offers multiple options to achieve database redundancy for load balancing and high availability. See the [PostgreSQL documentation](https://www.postgresql.org/docs/12/high-availability.html) for details.

A common choice is to deploy a primary database, in read/write mode, with synchronous replication to a secondary database, which can be used for read-only requests, and can be promoted to primary in case of a major failure on the first node.

Netshot can take advantage of this setup, by configuring two database URIs:

* The main one, `netshot.db.url`, points to the read-write database instance.
* The second one, `netshot.db.readurl`, points to the read-only database instance.

If that second option is configured, Netshot will use the given server for all read-only requests, which effectively balances the load.

Assuming that the PostgreSQL database is set up using server1 as primary instance and server2 as secondary instance, one can configure Netshot as follows:

```properties
netshot.db.url = jdbc:postgresql://server1,server2/netshot01?sslmode=disable&targetServerType=primary&hostRecheckSeconds=5
netshot.db.readurl = jdbc:postgresql://server1,server2/netshot01?sslmode=disable&targetServerType=preferSecondary&hostRecheckSeconds=5
```

With this configuration, all read-only requests will be sent to server2, while server1 will receive read-write requests. In case of failure of one of the two servers, Netshot will use the remaining one. If server1 fails and server2 is not promoted to primary, all read operations will still be possible in Netshot.

## High availability configuration

Achieving high availability requires PostgreSQL as the database.

* The same version of Netshot must be deployed on the target servers, using the [standard installation procedure](installation/linux.md).
* On each server, the following minimal configuration is required in `/etc/netshot.conf`:

```properties
netshot.cluster.enabled = true
netshot.cluster.id = [20 figures or lowercase letters unique to each server]
```

* For database high availability, deploy two PostgreSQL instances (server1 and server2) in [streaming replication mode](https://www.postgresql.org/docs/12/warm-standby.html#STREAMING-REPLICATION), and configure the Netshot instances as follows:

```properties
netshot.db.url = jdbc:postgresql://server1,server2/netshot01?sslmode=disable&targetServerType=primary&hostRecheckSeconds=5
netshot.db.readurl = jdbc:postgresql://server1,server2/netshot01?sslmode=disable&targetServerType=preferSecondary&hostRecheckSeconds=5
```
