'use client'

import type { DatabaseInstance } from '@/types/api'

export function InstanceSelect({
  instances,
  value,
  onChange,
}: {
  instances: DatabaseInstance[]
  value: string
  onChange: (id: string) => void
}) {
  return (
    <label className="flex flex-col gap-2 text-sm">
      <span className="text-[#888]">Instance</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="border border-[#2a2a2a] bg-black px-3 py-2 text-[#f5f5f5]"
      >
        {instances.map((instance) => (
          <option key={instance.id} value={instance.id}>
            {instance.service} · {instance.engine}
          </option>
        ))}
      </select>
    </label>
  )
}
