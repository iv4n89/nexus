import { api } from '@/lib/api'
import type { DatabaseInstance, DatabaseMetadata, QueryResult } from '@/types/api'

export function listInstances(projectId: string) {
  return api<DatabaseInstance[]>(`/api/projects/${projectId}/database/instances`)
}

export function getMetadata(projectId: string, databaseId: string) {
  return api<DatabaseMetadata>(
    `/api/projects/${projectId}/database/instances/${encodeURIComponent(databaseId)}/metadata`,
  )
}

export function previewTable(
  projectId: string,
  databaseId: string,
  params: { schema?: string; table?: string; mongoDatabase?: string; collection?: string },
) {
  const search = new URLSearchParams()
  if (params.schema) search.set('schema', params.schema)
  if (params.table) search.set('table', params.table)
  if (params.mongoDatabase) search.set('mongoDatabase', params.mongoDatabase)
  if (params.collection) search.set('collection', params.collection)
  const query = search.toString()
  return api<QueryResult>(
    `/api/projects/${projectId}/database/instances/${encodeURIComponent(databaseId)}/preview${query ? `?${query}` : ''}`,
  )
}

export function postQuery(
  projectId: string,
  databaseId: string,
  statement: string,
  confirmDestructive = false,
) {
  return api<QueryResult>(`/api/projects/${projectId}/database/instances/${encodeURIComponent(databaseId)}/query`, {
    method: 'POST',
    body: JSON.stringify({ statement, confirmDestructive }),
  })
}

export function postCells(
  projectId: string,
  databaseId: string,
  body: {
    schema?: string
    table?: string
    mongoDatabase?: string
    collection?: string
    patches: Array<{
      primaryKey?: Record<string, unknown>
      column?: string
      value?: unknown
      id?: string
      field?: string
    }>
    inserts?: Array<{ values: Record<string, unknown> }>
    deletes?: Array<Record<string, unknown>>
  },
) {
  return api<QueryResult>(
    `/api/projects/${projectId}/database/instances/${encodeURIComponent(databaseId)}/cells`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}
