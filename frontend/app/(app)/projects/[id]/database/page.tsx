import { DatabasePage } from '@/features/database/database-page'

export default async function ProjectDatabaseRoute({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return <DatabasePage projectId={id} />
}
