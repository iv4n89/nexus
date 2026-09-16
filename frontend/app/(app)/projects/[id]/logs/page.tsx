import { Suspense } from 'react'
import { LogViewer } from '@/features/logs/log-viewer'

export default async function ProjectLogsPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return (
    <Suspense fallback={<p className="text-sm text-[#888]">Loading…</p>}>
      <LogViewer projectId={id} />
    </Suspense>
  )
}
