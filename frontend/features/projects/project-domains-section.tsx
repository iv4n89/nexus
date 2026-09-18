'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '@/lib/api'
import type { SiteDomain } from '@/types/api'

type Props = {
  projectId: string
  canEdit: boolean
}

export function ProjectDomainsSection({ projectId, canEdit }: Props) {
  const queryClient = useQueryClient()
  const [hostname, setHostname] = useState('')
  const [serviceName, setServiceName] = useState('')
  const [targetPort, setTargetPort] = useState('3000')
  const [formError, setFormError] = useState<string | null>(null)

  const domains = useQuery({
    queryKey: ['projects', projectId, 'domains'],
    queryFn: () => api<SiteDomain[]>(`/api/projects/${projectId}/domains`),
  })

  const add = useMutation({
    mutationFn: (body: { hostname: string; serviceName: string; targetPort: number }) =>
      api<SiteDomain>(`/api/projects/${projectId}/domains`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      setHostname('')
      setServiceName('')
      setTargetPort('3000')
      setFormError(null)
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'domains'] })
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'health'] })
    },
    onError: (error) => {
      setFormError(error instanceof Error ? error.message : 'ADD_FAILED')
    },
  })

  const remove = useMutation({
    mutationFn: (domainId: string) =>
      api<void>(`/api/projects/${projectId}/domains/${domainId}`, { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'domains'] })
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'health'] })
    },
  })

  const sync = useMutation({
    mutationFn: () =>
      api<SiteDomain[]>(`/api/projects/${projectId}/domains/sync`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'domains'] })
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'health'] })
    },
  })

  function onAdd(event: React.FormEvent) {
    event.preventDefault()
    if (!canEdit || add.isPending) {
      return
    }
    const port = Number.parseInt(targetPort, 10)
    if (!hostname.trim() || !serviceName.trim() || Number.isNaN(port)) {
      setFormError('hostname, service, and port are required')
      return
    }
    add.mutate({
      hostname: hostname.trim(),
      serviceName: serviceName.trim(),
      targetPort: port,
    })
  }

  function onRemove(domain: SiteDomain) {
    if (!canEdit || remove.isPending) {
      return
    }
    if (!window.confirm(`Remove ${domain.hostname}?`)) {
      return
    }
    remove.mutate(domain.id)
  }

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-xs tracking-[0.25em] text-[#888]">Domains</h2>
        {canEdit ? (
          <button
            type="button"
            disabled={sync.isPending}
            onClick={() => sync.mutate()}
            className="border border-[#2a2a2a] px-3 py-1 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:text-[#888]"
          >
            SYNC
          </button>
        ) : null}
      </div>
      {domains.isPending ? (
        <p className="text-sm text-[#888]">Loading…</p>
      ) : domains.isError ? (
        <p className="text-sm text-[#ff4d4f]">{domains.error.message}</p>
      ) : (domains.data ?? []).length === 0 ? (
        <p className="text-sm text-[#888]">No domains</p>
      ) : (
        <ul>
          {(domains.data ?? []).map((domain) => (
            <li
              key={domain.id}
              className="flex flex-col gap-2 border-b border-[#2a2a2a] py-3 last:border-b-0 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 font-mono text-sm">
                <p>{domain.hostname}</p>
                <p className="mt-1 text-[#888]">
                  {domain.serviceName}:{domain.targetPort}
                  {' · '}
                  {domain.certStatus}
                </p>
              </div>
              {canEdit ? (
                <button
                  type="button"
                  disabled={remove.isPending}
                  onClick={() => onRemove(domain)}
                  className="shrink-0 self-start border border-[#ff4d4f] px-3 py-1 text-sm text-[#ff4d4f] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
                >
                  REMOVE
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {remove.isError ? (
        <p className="mt-2 text-sm text-[#ff4d4f]">{remove.error.message}</p>
      ) : null}
      {sync.isError ? (
        <p className="mt-2 text-sm text-[#ff4d4f]">{sync.error.message}</p>
      ) : null}

      {canEdit ? (
        <form onSubmit={onAdd} className="mt-4 flex flex-col gap-3">
          <div className="flex flex-wrap gap-3">
            <input
              value={hostname}
              onChange={(e) => setHostname(e.target.value)}
              placeholder="hostname"
              className="min-w-[12rem] border border-[#2a2a2a] bg-transparent px-3 py-2 font-mono text-sm text-[#f5f5f5] outline-none focus:border-[#f5f5f5]"
            />
            <input
              value={serviceName}
              onChange={(e) => setServiceName(e.target.value)}
              placeholder="service"
              className="border border-[#2a2a2a] bg-transparent px-3 py-2 font-mono text-sm text-[#f5f5f5] outline-none focus:border-[#f5f5f5]"
            />
            <input
              value={targetPort}
              onChange={(e) => setTargetPort(e.target.value)}
              placeholder="port"
              inputMode="numeric"
              className="w-24 border border-[#2a2a2a] bg-transparent px-3 py-2 font-mono text-sm text-[#f5f5f5] outline-none focus:border-[#f5f5f5]"
            />
            <button
              type="submit"
              disabled={add.isPending}
              className="border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
            >
              ADD
            </button>
          </div>
          {formError ? <p className="text-sm text-[#ff4d4f]">{formError}</p> : null}
          {add.isError ? <p className="text-sm text-[#ff4d4f]">{add.error.message}</p> : null}
        </form>
      ) : null}
    </section>
  )
}
