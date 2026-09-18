'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '@/lib/api'
import { formatClock } from '@/lib/format'
import type { ProjectEnvVar } from '@/types/api'

type Props = {
  projectId: string
  canEdit: boolean
}

export function ProjectEnvSection({ projectId, canEdit }: Props) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [value, setValue] = useState('')
  const [secret, setSecret] = useState(true)
  const [formError, setFormError] = useState<string | null>(null)

  const env = useQuery({
    queryKey: ['projects', projectId, 'env'],
    queryFn: () => api<ProjectEnvVar[]>(`/api/projects/${projectId}/env`),
  })

  const upsert = useMutation({
    mutationFn: (body: { name: string; value: string; secret: boolean }) =>
      api<ProjectEnvVar>(`/api/projects/${projectId}/env`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      setName('')
      setValue('')
      setSecret(true)
      setFormError(null)
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'env'] })
    },
    onError: (error) => {
      setFormError(error instanceof Error ? error.message : 'UPSERT_FAILED')
    },
  })

  const rotate = useMutation({
    mutationFn: ({ key, next }: { key: string; next: string }) =>
      api<ProjectEnvVar>(`/api/projects/${projectId}/env/${encodeURIComponent(key)}/rotate`, {
        method: 'POST',
        body: JSON.stringify({ value: next }),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'env'] })
    },
  })

  const remove = useMutation({
    mutationFn: (key: string) =>
      api<void>(`/api/projects/${projectId}/env/${encodeURIComponent(key)}`, { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'env'] })
    },
  })

  function onAdd(event: React.FormEvent) {
    event.preventDefault()
    if (!canEdit || upsert.isPending) {
      return
    }
    const trimmed = name.trim()
    if (!trimmed || !value) {
      setFormError('name and value are required')
      return
    }
    upsert.mutate({ name: trimmed, value, secret })
  }

  function onRotate(key: string) {
    if (!canEdit || rotate.isPending) {
      return
    }
    const next = window.prompt(`New value for ${key}`)
    if (next == null || next === '') {
      return
    }
    rotate.mutate({ key, next })
  }

  function onDelete(key: string) {
    if (!canEdit || remove.isPending) {
      return
    }
    if (!window.confirm(`Delete ${key}?`)) {
      return
    }
    remove.mutate(key)
  }

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-xs tracking-[0.25em] text-[#888]">Environment</h2>
          <p className="mt-1 text-xs text-[#666]">Synced from `.env`</p>
        </div>
        <button
          type="button"
          onClick={() => void env.refetch()}
          disabled={env.isFetching}
          className="border border-[#2a2a2a] px-3 py-1 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:text-[#888]"
        >
          RELOAD
        </button>
      </div>
      {env.isPending ? (
        <p className="text-sm text-[#888]">Loading…</p>
      ) : env.isError ? (
        <p className="text-sm text-[#ff4d4f]">{env.error.message}</p>
      ) : (env.data ?? []).length === 0 ? (
        <p className="text-sm text-[#888]">No environment variables</p>
      ) : (
        <ul>
          {(env.data ?? []).map((row) => (
            <li
              key={row.id}
              className="flex flex-col gap-2 border-b border-[#2a2a2a] py-3 last:border-b-0 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 font-mono text-sm">
                <p>{row.name}</p>
                <p className="mt-1 text-[#888]">
                  {row.secret ? '••••••••' : (row.value ?? '—')}
                  {row.secret ? ' · secret' : ''}
                  {' · '}
                  {formatClock(row.updatedAt)}
                </p>
              </div>
              {canEdit ? (
                <div className="flex shrink-0 gap-2">
                  <button
                    type="button"
                    disabled={rotate.isPending}
                    onClick={() => onRotate(row.name)}
                    className="border border-[#f5f5f5] px-3 py-1 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
                  >
                    ROTATE
                  </button>
                  <button
                    type="button"
                    disabled={remove.isPending}
                    onClick={() => onDelete(row.name)}
                    className="border border-[#ff4d4f] px-3 py-1 text-sm text-[#ff4d4f] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
                  >
                    DELETE
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {rotate.isError ? (
        <p className="mt-2 text-sm text-[#ff4d4f]">{rotate.error.message}</p>
      ) : null}
      {remove.isError ? (
        <p className="mt-2 text-sm text-[#ff4d4f]">{remove.error.message}</p>
      ) : null}

      {canEdit ? (
        <form onSubmit={onAdd} className="mt-4 flex flex-col gap-3">
          <div className="flex flex-wrap gap-3">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="NAME"
              className="border border-[#2a2a2a] bg-transparent px-3 py-2 font-mono text-sm text-[#f5f5f5] outline-none focus:border-[#f5f5f5]"
            />
            <input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="value"
              type={secret ? 'password' : 'text'}
              className="min-w-[12rem] flex-1 border border-[#2a2a2a] bg-transparent px-3 py-2 font-mono text-sm text-[#f5f5f5] outline-none focus:border-[#f5f5f5]"
            />
            <label className="flex items-center gap-2 font-mono text-sm text-[#888]">
              <input
                type="checkbox"
                checked={secret}
                onChange={(e) => setSecret(e.target.checked)}
              />
              secret
            </label>
            <button
              type="submit"
              disabled={upsert.isPending}
              className="border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
            >
              UPSERT
            </button>
          </div>
          {formError ? <p className="text-sm text-[#ff4d4f]">{formError}</p> : null}
          {upsert.isError ? (
            <p className="text-sm text-[#ff4d4f]">{upsert.error.message}</p>
          ) : null}
        </form>
      ) : null}
    </section>
  )
}
