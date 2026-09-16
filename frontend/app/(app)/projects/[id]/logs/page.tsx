import { LogViewer } from '@/features/logs/log-viewer'

export default async function ProjectLogsPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return <LogViewer projectId={id} />
}
