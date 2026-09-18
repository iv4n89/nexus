export type Project = {
  id: string
  name: string
  status: string
  runningCount: number
  totalCount: number
  deployable: boolean
}

export type ProjectService = {
  id: string
  name: string
  state: string
  status: string
  health: string | null
}

export type ProjectDetail = Project & {
  services: ProjectService[]
  recentErrors?: RecentError[]
}

export type ProjectHealth = {
  projectId: string
  projectStatus: string
  containersRunning: number
  containersTotal: number
  lastDeploymentStatus: string | null
  lastDeploymentAt: string | null
  openAlertsCount: number
  openSecurityFindingsCount: number | null
  backupsAvailable: boolean
  lastBackupSuccessAt: string | null
  domainsCount: number | null
}

export type RecentError = {
  serviceId: string
  sampleMessage: string
  count: number
  firstSeen: string
  lastSeen: string
}

export type LogSnapshot = {
  lines: string[]
}

export type AuthUser = {
  username: string
  role: string
}

export type Settings = {
  version: string
  retention: {
    activityDays: number
    deploymentEventsDays: number
    fingerprintDays: number
  }
}

export type Deployment = {
  id: string
  projectId: string
  status: string
  startedAt: string | null
  finishedAt: string | null
  triggeredBy: string
  commitSha: string | null
  exitCode: number | null
  outputSummary: string | null
  healthOk: boolean | null
  kind: string | null
}

export type DeployAccepted = {
  id: string
  status: string
}

export type PortMapping = { publicPort: number | null; privatePort: number }

export type Container = {
  id: string
  name: string
  image: string
  status: string
  state: string
  health: string | null
  created: string | null
  labels: Record<string, string>
  ports: PortMapping[]
  restartCount: number
  startedAt: string | null
}

export type SystemMetrics = {
  cpuPercent: number
  memoryUsedBytes: number
  memoryTotalBytes: number
  diskUsedBytes: number
  diskTotalBytes: number
  loadAverage: number
  uptimeSeconds: number
}

export type ProjectMetrics = {
  projectId: string
  cpuPercent: number
  memoryUsedBytes: number
  restartCount: number
}

export type ContainerMetrics = {
  containerId: string
  cpuPercent: number
  memoryUsedBytes: number
  memoryLimitBytes: number
  rxBytes: number
  txBytes: number
}

export type AlertStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'

export type AlertType =
  | 'CONTAINER_STOPPED'
  | 'RESTART_SPIKE'
  | 'HIGH_MEMORY'
  | 'DISK'
  | 'ERROR_RATE'
  | 'DOCKER_HEALTH'
  | 'HTTP_HEALTH'

export type Alert = {
  id: string
  ruleId: string | null
  projectId: string | null
  serviceId: string | null
  status: AlertStatus
  message: string
  openedAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
  type: AlertType
}

export type ActivityType =
  | 'DEPLOYMENT_STARTED'
  | 'DEPLOYMENT_SUCCESS'
  | 'DEPLOYMENT_FAILED'
  | 'CONTAINER_STARTED'
  | 'CONTAINER_STOPPED'
  | 'CONTAINER_RESTARTED'
  | 'ERROR_DETECTED'
  | 'ALERT_CREATED'
  | 'ALERT_RESOLVED'
  | 'HEALTH_CHECK_FAILED'
  | 'GITHUB_CONNECTED'
  | 'GITHUB_DISCONNECTED'
  | 'SECURITY_SCAN_COMPLETED'
  | 'DOMAIN_ADDED'
  | 'DOMAIN_REMOVED'
  | 'BACKUP_COMPLETED'
  | 'BACKUP_FAILED'

export type ActivityEvent = {
  id: string
  createdAt: string
  type: ActivityType
  projectId: string | null
  serviceId: string | null
  message: string
  metadata: Record<string, unknown>
}

export type IncidentTimelineItem = {
  at: string
  kind: string
  source: 'ACTIVITY' | 'ALERT' | string
  message: string
  serviceId: string | null
  refId: string
}

export type DatabaseInstance = {
  id: string
  service: string
  engine: 'POSTGRES' | 'MYSQL' | 'MONGO'
  status: 'READY' | 'UNREACHABLE'
  defaultDatabase: string
}

export type DatabaseColumn = {
  name: string
  dataType: string
  nullable: boolean
}

export type DatabaseTable = {
  name: string
  type: 'table' | 'view' | string
  primaryKey: string[]
  columns?: DatabaseColumn[]
}

export type DatabaseSchema = {
  name: string
  tables: DatabaseTable[]
}

export type MongoDatabase = {
  name: string
  collections: string[]
}

export type DatabaseMetadata = {
  engine: 'POSTGRES' | 'MYSQL' | 'MONGO'
  schemas?: DatabaseSchema[]
  databases?: MongoDatabase[]
}

export type QueryResult = {
  columns: string[]
  rows: unknown[][]
  truncated: boolean
  durationMs: number
  rowCount: number
}
