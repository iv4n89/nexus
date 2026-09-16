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
}

export type AuthUser = {
  username: string
  role: string
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
