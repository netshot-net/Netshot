export type ClusterMasterStatus = {
  clusterEnabled: boolean;
  master: boolean;
  currentMasterId: string;
};

export enum ClusterMemberStatus {
  Member = "MEMBER",
  Master = "MASTER",
  Expired = "EXPIRED",
  Negotiating = "NEGOTIATING",
}

export type ClusterMember = {
  local: boolean;
  instanceId: string;
  hostname: string;
  clusteringVersion: number;
  masterPriority: number;
  runnerPriority: number;
  runnerWeight: number;
  appVersion: string;
  jvmVersion: string;
  driverHash: string;
  status: ClusterMemberStatus;
  lastStatusChangeTime: number;
  lastSeenTime: number;
};
