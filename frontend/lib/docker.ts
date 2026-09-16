import type { Container } from '@/types/api'

export function projectLabel(container: Container): string | undefined {
  const labels = container.labels ?? {}
  return labels['nexus.project'] || labels['com.docker.compose.project'] || undefined
}

export function serviceLabel(container: Container): string | undefined {
  const labels = container.labels ?? {}
  return labels['nexus.service'] || labels['com.docker.compose.service'] || undefined
}

export function containersForProject(containers: Container[], projectId: string): Container[] {
  return containers.filter((container) => projectLabel(container) === projectId)
}

export function stripSlash(name: string): string {
  return name.startsWith('/') ? name.slice(1) : name
}

export function displayName(container: Container): string {
  return serviceLabel(container) ?? stripSlash(container.name)
}
