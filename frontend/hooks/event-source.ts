export function deploymentStreamUrl(deploymentId: string, status: string | undefined): string | null {
  if (status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELLED') {
    return null
  }
  return `/api/deployments/${encodeURIComponent(deploymentId)}/stream`
}

export function shouldAbandonEventSource(readyState: number, errorCount: number): boolean {
  return readyState === 2 || errorCount >= 3
}
