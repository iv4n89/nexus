'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { me } from '@/features/auth/api'
import { boundTreeSelection } from '@/features/database/browse-selection'
import {
  applyCell,
  dirtyCount,
  emptyDraft,
  mongoPatches,
  rowKey,
  sqlPatches,
  toMongoRows,
  toSqlRows,
  type CellDraft,
} from '@/features/database/cell-draft'
import { ConfirmDestructive } from '@/features/database/confirm-destructive'
import { DataGrid } from '@/features/database/data-grid'
import { formatMongoStatements, formatSqlStatements } from '@/features/database/draft-sql'
import { InstanceSelect } from '@/features/database/instance-select'
import { QueryEditor } from '@/features/database/query-editor'
import { SchemaTree, type TreeSelection } from '@/features/database/schema-tree'
import { getMetadata, listInstances, postCells, postQuery, previewTable } from '@/features/database/api'
import { isDestructiveSql, isReadMongo, isReadSql } from '@/features/database/classify-sql'
import { ApiError } from '@/lib/api'
import type { AuthUser, DatabaseInstance, QueryResult } from '@/types/api'

export function DatabasePage({ projectId }: { projectId: string }) {
  const [instanceId, setInstanceId] = useState<string>('')
  const [tab, setTab] = useState<'browse' | 'query'>('browse')
  const [selection, setSelection] = useState<TreeSelection | null>(null)
  const [statement, setStatement] = useState('SELECT 1')
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null)
  const [queryError, setQueryError] = useState<string | null>(null)
  const [browseError, setBrowseError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [selectionInstanceId, setSelectionInstanceId] = useState('')
  const [draft, setDraft] = useState<CellDraft>(emptyDraft())
  const [resetToken, setResetToken] = useState(0)
  const [navBlocked, setNavBlocked] = useState(false)
  const queryClient = useQueryClient()

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
  const previewSelection = boundTreeSelection(selection, selectionInstanceId, selectedId)
  const changes = dirtyCount(draft)

  if (selectionInstanceId && selectedId && selectionInstanceId !== selectedId && changes === 0) {
    setSelectionInstanceId(selectedId)
    setSelection(null)
    setQueryResult(null)
    setStatement('SELECT 1')
    setQueryError(null)
    setBrowseError(null)
  }

  const metadata = useQuery({
    queryKey: ['projects', projectId, 'database', selectedId, 'metadata'],
    queryFn: () => getMetadata(projectId, selectedId),
    enabled: Boolean(selectedId) && selected?.status === 'READY',
  })

  const preview = useQuery({
    queryKey: ['projects', projectId, 'database', selectedId, 'preview', previewSelection],
    queryFn: () => {
      if (!previewSelection) {
        throw new Error('No selection')
      }
      if (previewSelection.kind === 'sql') {
        return previewTable(projectId, selectedId, {
          schema: previewSelection.schema,
          table: previewSelection.table,
        })
      }
      return previewTable(projectId, selectedId, {
        mongoDatabase: previewSelection.database,
        collection: previewSelection.collection,
      })
    },
    enabled:
      tab === 'browse' &&
      Boolean(selectedId) &&
      selected?.status === 'READY' &&
      previewSelection !== null,
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
      if (error instanceof ApiError && error.code === 'CONFIRMATION_REQUIRED') {
        setConfirmOpen(true)
        return
      }
      setQueryError(error.message)
    },
  })

  const save = useMutation({
    mutationFn: (payload: Parameters<typeof postCells>[2]) => postCells(projectId, selectedId, payload),
    onSuccess: () => {
      setDraft(emptyDraft())
      setResetToken((n) => n + 1)
      setBrowseError(null)
      void queryClient.invalidateQueries({
        queryKey: ['projects', projectId, 'database', selectedId, 'preview'],
      })
    },
    onError: (error: Error) => {
      setBrowseError(error.message)
    },
  })

  function onExecute() {
    setQueryError(null)
    if (isAdmin && engine !== 'MONGO' && isDestructiveSql(statement)) {
      setConfirmOpen(true)
      return
    }
    run.mutate(false)
  }

  function guardNav(): boolean {
    if (dirtyCount(draft) > 0) {
      setNavBlocked(true)
      return true
    }
    return false
  }

  function onCancel() {
    setDraft(emptyDraft())
    setResetToken((n) => n + 1)
    setBrowseError(null)
  }

  function onSave() {
    if (!previewSelection || !preview.data) {
      return
    }
    const result = preview.data
    const keys = previewRowKeys(result, previewSelection)
    if (previewSelection.kind === 'sql') {
      save.mutate({
        schema: previewSelection.schema,
        table: previewSelection.table,
        patches: sqlPatches(draft, keys, sqlPrimaryKeys(result, previewSelection.primaryKey), result.columns),
      })
      return
    }
    save.mutate({
      mongoDatabase: previewSelection.database,
      collection: previewSelection.collection,
      patches: mongoPatches(draft, keys, result.columns),
    })
  }

  function onDraftChange(row: Record<string, unknown>, column: string, input: string, original: unknown) {
    const idColumn = previewSelection?.kind === 'mongo' ? '_id' : null
    const primaryKey = previewSelection?.kind === 'sql' ? previewSelection.primaryKey : []
    const key = idColumn
      ? String(row[idColumn] ?? '')
      : rowKey(
          Object.fromEntries(primaryKey.map((name) => [name, row[name]])),
          primaryKey,
        )
    setDraft((current) => applyCell(current, key, column, input, original))
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
      <InstanceSelect
        instances={instances.data}
        value={selectedId}
        onChange={(id) => {
          if (guardNav()) {
            return
          }
          setInstanceId(id)
        }}
      />
      {selected?.status === 'UNREACHABLE' ? (
        <p className="text-sm">Cannot connect</p>
      ) : (
        <div className="grid gap-6 md:grid-cols-[14rem_1fr]">
          <aside className="border border-[#2a2a2a] p-4">
            {metadata.data ? (
              <SchemaTree
                metadata={metadata.data}
                selected={previewSelection}
                onSelect={(next) => {
                  if (guardNav()) {
                    return
                  }
                  setSelectionInstanceId(selectedId)
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
                onClick={() => {
                  if (guardNav()) {
                    return
                  }
                  setTab('query')
                }}
                className={tab === 'query' ? 'text-[#f5f5f5]' : 'text-[#888]'}
              >
                Query
              </button>
            </div>
            {tab === 'browse' ? (
              previewSelection == null ? (
                <p className="text-sm text-[#888]">Select a table or collection</p>
              ) : preview.isPending ? (
                <p className="text-sm text-[#888]">Loading…</p>
              ) : preview.isError ? (
                <p className="text-sm text-[#ff4d4f]">{preview.error.message}</p>
              ) : preview.data ? (
                <>
                  {browseError ? <p className="text-sm text-[#ff4d4f]">{browseError}</p> : null}
                  {preview.data.truncated ? (
                    <p className="text-sm text-[#888]">Result truncated at {preview.data.rowCount} rows</p>
                  ) : null}
                  {changes > 0 ? (
                    <>
                      <div className="flex items-center justify-between text-sm text-[#888]">
                        <span>
                          {changes} {changes === 1 ? 'cambio' : 'cambios'}
                        </span>
                        <span className="flex gap-3">
                          <button
                            type="button"
                            onClick={onCancel}
                            disabled={save.isPending}
                            className="text-[#888]"
                          >
                            Cancelar
                          </button>
                          <button
                            type="button"
                            onClick={onSave}
                            disabled={save.isPending}
                            className="text-[#f5f5f5]"
                          >
                            Guardar
                          </button>
                        </span>
                      </div>
                      <pre className="overflow-auto border border-[#2a2a2a] p-3 font-mono text-sm text-[#888] whitespace-pre-wrap">
                        {previewSelection.kind === 'sql'
                          ? formatSqlStatements(
                              engine === 'MYSQL' ? 'MYSQL' : 'POSTGRES',
                              previewSelection.schema,
                              previewSelection.table,
                              toSqlRows(
                                draft,
                                previewRowKeys(preview.data, previewSelection),
                                sqlPrimaryKeys(preview.data, previewSelection.primaryKey),
                                preview.data.columns,
                              ),
                            )
                          : formatMongoStatements(
                              previewSelection.collection,
                              toMongoRows(draft, previewRowKeys(preview.data, previewSelection), preview.data.columns),
                            )}
                      </pre>
                    </>
                  ) : null}
                  <DataGrid
                    result={preview.data}
                    canEdit={Boolean(isAdmin)}
                    primaryKey={previewSelection.kind === 'sql' ? previewSelection.primaryKey : []}
                    idColumn={previewSelection.kind === 'mongo' ? '_id' : null}
                    draft={draft}
                    resetToken={resetToken}
                    onDraftChange={onDraftChange}
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
                    draft={emptyDraft()}
                    resetToken={0}
                    onDraftChange={() => undefined}
                  />
                ) : null}
              </>
            )}
          </section>
        </div>
      )}
      {navBlocked ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80">
          <div className="border border-[#f5f5f5] bg-black p-3 text-sm">
            <p>Hay {changes} cambios sin guardar. Guarda o cancela antes de cambiar.</p>
            <div className="mt-2 text-right">
              <button type="button" onClick={() => setNavBlocked(false)}>
                Entendido
              </button>
            </div>
          </div>
        </div>
      ) : null}
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

function previewRowKeys(result: QueryResult, previewSelection: TreeSelection): string[] {
  return result.rows.map((row) => {
    const record = Object.fromEntries(result.columns.map((c, i) => [c, row[i]]))
    if (previewSelection.kind === 'mongo') {
      return String(record._id ?? '')
    }
    const pk: Record<string, unknown> = {}
    for (const column of previewSelection.primaryKey) {
      pk[column] = record[column]
    }
    return rowKey(pk, previewSelection.primaryKey)
  })
}

function sqlPrimaryKeys(
  result: QueryResult,
  primaryKey: string[],
): Record<string, Record<string, unknown>> {
  const map: Record<string, Record<string, unknown>> = {}
  for (const row of result.rows) {
    const record = Object.fromEntries(result.columns.map((c, i) => [c, row[i]]))
    const pk: Record<string, unknown> = {}
    for (const column of primaryKey) {
      pk[column] = record[column]
    }
    map[rowKey(pk, primaryKey)] = pk
  }
  return map
}
