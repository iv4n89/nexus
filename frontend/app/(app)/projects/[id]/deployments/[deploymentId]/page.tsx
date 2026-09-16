import { DeploymentStream } from '@/features/deployments/deployment-stream'

export default async function DeploymentPage({
  params,
}: {
  params: Promise<{ id: string; deploymentId: string }>
}) {
  const { id, deploymentId } = await params
  return <DeploymentStream projectId={id} deploymentId={deploymentId} />
}
