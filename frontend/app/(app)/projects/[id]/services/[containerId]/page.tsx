import { ContainerView } from '@/features/containers/container-view'

export default async function ContainerPage({
  params,
}: {
  params: Promise<{ id: string; containerId: string }>
}) {
  const { id, containerId } = await params
  return <ContainerView projectId={id} containerId={containerId} />
}
