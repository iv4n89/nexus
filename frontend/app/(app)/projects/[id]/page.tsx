import { ProjectOverview } from '@/features/projects/project-overview'

export default async function ProjectPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return <ProjectOverview projectId={id} />
}
