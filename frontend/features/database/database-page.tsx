'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { me } from '@/features/auth/api'
import { ConfirmDestructive } from '@/features/database/confirm-destructive'
import { DataGrid } from '@/features/database/data-grid'
import { InstanceSelect } from '@/features/database/instance-select'
import { QueryEditor } from '@/features/database/query-editor'
import { SchemaTree, type TreeSelection } from '@/features/database/schema-tree'
import { getMetadata, listInstances, postCell, postQuery, previewTable } from '@/features/database/api'
import { isDestructiveSql, isReadMongo, isReadSql } from '@/features/database/classify-sql'
import type { AuthUser, DatabaseInstance, QueryResult } from '@/types/api'

export function DatabasePage({ projectId }: { projectId: string }) {
  const [instanceId, setInstanceId] = useState<string>('')
  const [tab, setTab] = useState<'browse' | 'query'>('browse')
  const [selection, setSelection] = useState<TreeSelection | null>(null)
  const [statement, setStatement] = useState('SELECT 1')
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null)
  const [queryError, setQueryError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const auth = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: (): Promise<AuthUser> => me(),
  })
  const instances = useQuery({
    queryKey: ['projects', projectId, 'database', 'instances'],
    queryFn: () => listInstances(projectId),
  })

  const selectedId = instanceId || instances.data?.[0]?.id || ''
  const selected: DatabaseInstance | undefined = instances.data?.find((item) => item.id === selectedId)

  const metadata = useQuery({
    queryKey: ['projects', projectId, 'database', selectedId, 'metadata'],
    queryFn: () => getMetadata(projectId, selectedId),
    enabled: Boolean(selectedId) && selected?.status === 'READY',
  })

  const preview = useQuery({
    queryKey: ['projects', projectId, 'database', selectedId, 'preview', selection],
    queryFn: () => {
      if (!selection) {
        throw new Error('No selection')
      }
      if (selection.kind === 'sql') {
        return previewTable(projectId, selectedId, { schema: selection.schema, table: selection.table })
      }
      return previewTable(projectId, selectedId, {
        mongoDatabase: selection.database,
        collection: selection.collection,
      })
    },
    enabled: tab === 'browse' && Boolean(selectedId) && selected?.status === 'READY' && selection !== null,
  })

  const isAdmin = auth.data?.role === 'ADMIN'
  const engine = selected?.engine ?? 'POSTGRES'
  const clientAllowsExecute = useMemo(() => {
    if (isAdmin) {
      return statement.trim().length > 0
    }
    return engine === 'MONGO' ? isReadMongo(statement) : isReadSql(statement)
  }, [engine, isAdmin, statement])

  const run = useMutation({
    mutationFn: (confirmDestructive: boolean) => postQuery(projectId, selectedId, statement, confirmDestructive),
    onSuccess: (result) => {
      setQueryResult(result)
      setQueryError(null)
      setConfirmOpen(false)
    },
    onError: (error: Error) => {
      if (error.message === 'CONFIRMATION_REQUIRED') {
        setConfirmOpen(true)
        return
      }
      setQueryError(error.message)
    },
  })

  const cell = useMutation({
    mutationFn: (payload: Parameters<typeof postCell>[2]) => postCell(projectId, selectedId, payload),
  })

  function onExecute() {
    setQueryError(null)
    if (isAdmin && engine !== 'MONGO' && isDestructiveSql(statement)) {
      setConfirmOpen(true)
      return
    }
    run.mutate(false)
  }

  if (instances.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (instances.isError) {
    return <p className="text-sm text-[#ff4d4f]">{instances.error.message}</p>
  }
  if (!instances.data || instances.data.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <Header projectId={projectId} />
        <p className="text-sm">No database containers in this project</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <Header projectId={projectId} />
      <InstanceSelect instances={instances.data} value={selectedId} onChange={setInstanceId} />
      {selected?.status === 'UNREACHABLE' ? (
        <p className="text-sm">Cannot connect</p>
      ) : (
        <div className="grid gap-6 md:grid-cols-[14rem_1fr]">
          <aside className="border border-[#2a2a2a] p-4">
            {metadata.data ? (
              <SchemaTree
                metadata={metadata.data}
                selected={selection}
                onSelect={(next) => {
                  setSelection(next)
                  setTab('browse')
                }}
              />
            ) : (
              <p className="text-sm text-[#888]">{metadata.error?.message ?? 'Loading…'}</p>
            )}
          </aside>
          <section className="flex flex-col gap-4">
            <div className="flex gap-4 text-sm">
              <button
                type="button"
                onClick={() => setTab('browse')}
                className={tab === 'browse' ? 'text-[#f5f5f5]' : 'text-[#888]'}
              >
                Browse
              </button>
              <button
                type="button"
                onClick={() => setTab('query')}
                className={tab === 'query' ? 'text-[#f5f5f5]' : 'text-[#888]'}
              >
                Query
              </button>
            </div>
            {tab === 'browse' ? (
              preview.data ? (
                <>
                  {preview.data.truncated ? (
                    <p className="text-sm text-[#888]">Result truncated at {preview.data.rowCount} rows</p>
                  ) : null}
                  <DataGrid
                    result={preview.data}
                    canEdit={Boolean(isAdmin)}
                    primaryKey={selection?.kind === 'sql' ? selection.primaryKey : []}
                    idColumn={selection?.kind === 'mongo' ? '_id' : null}
                    onEdit={(row, column, value) => {
                      if (selection?.kind === 'sql') {
                        const primaryKey: Record<string, unknown> = {}
                        for (const key of selection.primaryKey) {
                          primaryKey[key] = row[key]
                        }
                        cell.mutate({
                          schema: selection.schema,
                          table: selection.table,
                          primaryKey,
                          column,
                          value,
                        })
                        return
                      }
                      if (selection?.kind === 'mongo') {
                        cell.mutate({
                          mongoDatabase: selection.database,
                          collection: selection.collection,
                          id: String(row._id ?? ''),
                          field: column,
                          value,
                        })
                      }
                    }}
                  />
                </>
              ) : (
                <p className="text-sm text-[#888]">Select a table or collection</p>
              )
            ) : (
              <>
                <QueryEditor
                  value={statement}
                  onChange={setStatement}
                  onExecute={onExecute}
                  disabled={!clientAllowsExecute || run.isPending}
                />
                {queryError ? <p className="text-sm text-[#ff4d4f]">{queryError}</p> : null}
                {queryResult?.truncated ? (
                  <p className="text-sm text-[#888]">Result truncated at {queryResult.rowCount} rows</p>
                ) : null}
                {queryResult ? (
                  <DataGrid
                    result={queryResult}
                    canEdit={false}
                    primaryKey={[]}
                    idColumn={null}
                    onEdit={() => undefined}
                  />
                ) : null}
              </>
            )}
          </section>
        </div>
      )}
      <ConfirmDestructive
        open={confirmOpen}
        busy={run.isPending}
        error={queryError}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => run.mutate(true)}
      />
    </div>
  )
}

function Header({ projectId }: { projectId: string }) {
  return (
    <section className="flex items-baseline justify-between">
      <h1 className="text-xl uppercase tracking-wider">Database</h1>
      <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
        Overview
      </Link>
    </section>
  )
}
