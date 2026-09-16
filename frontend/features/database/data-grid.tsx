'use client'

import { useState } from 'react'
import type { QueryResult } from '@/types/api'

export function DataGrid({
  result,
  canEdit,
  primaryKey,
  idColumn,
  onEdit,
}: {
  result: QueryResult
  canEdit: boolean
  primaryKey: string[]
  idColumn: string | null
  onEdit: (row: Record<string, unknown>, column: string, value: unknown) => void
}) {
  return (
    <div className="overflow-auto border border-[#2a2a2a]">
      <table className="w-full border-collapse font-mono text-sm">
        <thead>
          <tr className="bg-[#111] text-left text-[#888]">
            {result.columns.map((column) => (
              <th key={column} className="border-b border-[#2a2a2a] px-3 py-2 font-normal">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => (
            <tr key={rowIndex} className="border-b border-[#2a2a2a]">
              {result.columns.map((column, columnIndex) => (
                <EditableCell
                  key={column}
                  value={row[columnIndex]}
                  editable={
                    canEdit &&
                    (primaryKey.length > 0 || idColumn !== null) &&
                    column !== idColumn &&
                    !primaryKey.includes(column)
                  }
                  onCommit={(next) => {
                    const record: Record<string, unknown> = {}
                    result.columns.forEach((name, index) => {
                      record[name] = row[index]
                    })
                    onEdit(record, column, next)
                  }}
                />
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function EditableCell({
  value,
  editable,
  onCommit,
}: {
  value: unknown
  editable: boolean
  onCommit: (value: unknown) => void
}) {
  const [draft, setDraft] = useState(value == null ? '' : String(value))
  const display = value == null ? 'null' : String(value)

  if (!editable) {
    return <td className="px-3 py-2 align-top text-[#f5f5f5]">{display}</td>
  }

  return (
    <td className="px-3 py-2 align-top">
      <input
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={() => {
          if (draft !== display) {
            onCommit(draft)
          }
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.currentTarget.blur()
          }
        }}
        className="w-full bg-transparent text-[#f5f5f5] outline-none"
      />
    </td>
  )
}
