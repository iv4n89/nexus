'use client'

import { useEffect, useState } from 'react'
import { displayValue, rowKey, type CellDraft } from '@/features/database/cell-draft'
import type { QueryResult } from '@/types/api'

export function DataGrid({
  result,
  canEdit,
  primaryKey,
  idColumn,
  draft,
  resetToken,
  onDraftChange,
  insertRows = [],
  deletedKeys = [],
  showRowActions = false,
  onInsertChange,
  onToggleDelete,
  onRemoveInsert,
}: {
  result: QueryResult
  canEdit: boolean
  primaryKey: string[]
  idColumn: string | null
  draft: CellDraft
  resetToken: number
  onDraftChange: (row: Record<string, unknown>, column: string, input: string, original: unknown) => void
  insertRows?: { localId: string; values: Record<string, unknown> }[]
  deletedKeys?: string[]
  showRowActions?: boolean
  onInsertChange?: (localId: string, column: string, input: string) => void
  onToggleDelete?: (key: string) => void
  onRemoveInsert?: (localId: string) => void
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
            {showRowActions ? <th className="border-b border-[#2a2a2a] px-3 py-2 font-normal" /> : null}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => {
            const record: Record<string, unknown> = {}
            result.columns.forEach((name, index) => {
              record[name] = row[index]
            })
            const key = idColumn
              ? String(record[idColumn] ?? '')
              : rowKey(
                  Object.fromEntries(primaryKey.map((column) => [column, record[column]])),
                  primaryKey,
                )
            const deleted = deletedKeys.includes(key)
            return (
              <tr
                key={rowIndex}
                className={
                  deleted
                    ? 'border-b border-[#2a2a2a] line-through text-[#666]'
                    : 'border-b border-[#2a2a2a]'
                }
              >
                {result.columns.map((column, columnIndex) => {
                  const original = row[columnIndex]
                  const dirty = draft[key] != null && column in draft[key]
                  return (
                    <EditableCell
                      key={column}
                      value={displayValue(draft, key, column, original)}
                      dirty={dirty}
                      resetToken={resetToken}
                      struck={deleted}
                      editable={
                        canEdit &&
                        !deleted &&
                        (primaryKey.length > 0 || idColumn !== null) &&
                        column !== idColumn &&
                        !primaryKey.includes(column)
                      }
                      onCommit={(input) => onDraftChange(record, column, input, original)}
                    />
                  )
                })}
                {showRowActions ? (
                  <td className={`px-3 py-2 align-top${deleted ? ' line-through text-[#666]' : ''}`}>
                    <button
                      type="button"
                      className={deleted ? 'line-through text-[#666]' : 'text-[#888]'}
                      onClick={() => onToggleDelete?.(key)}
                    >
                      ×
                    </button>
                  </td>
                ) : null}
              </tr>
            )
          })}
          {insertRows.map((row) => (
            <tr key={row.localId} className="border-b border-[#2a2a2a]">
              {result.columns.map((column) => (
                <EditableCell
                  key={column}
                  value={column in row.values ? row.values[column] : ''}
                  dirty={column in row.values}
                  resetToken={resetToken}
                  editable={canEdit}
                  onCommit={(input) => onInsertChange?.(row.localId, column, input)}
                />
              ))}
              {showRowActions ? (
                <td className="px-3 py-2 align-top">
                  <button
                    type="button"
                    className="text-[#888]"
                    onClick={() => onRemoveInsert?.(row.localId)}
                  >
                    ×
                  </button>
                </td>
              ) : null}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function EditableCell({
  value,
  dirty,
  resetToken,
  editable,
  struck = false,
  onCommit,
}: {
  value: unknown
  dirty: boolean
  resetToken: number
  editable: boolean
  struck?: boolean
  onCommit: (input: string) => void
}) {
  const [localInput, setLocalInput] = useState(value == null ? '' : String(value))
  useEffect(() => {
    setLocalInput(value == null ? '' : String(value))
  }, [value, resetToken])
  const display = value == null ? 'null' : String(value)
  const dirtyClass = dirty ? 'outline outline-1 outline-[#f5f5f5]' : ''

  if (!editable) {
    const struckClass = struck ? 'line-through text-[#666]' : 'text-[#f5f5f5]'
    return <td className={`px-3 py-2 align-top ${struckClass} ${dirtyClass}`}>{display}</td>
  }

  return (
    <td className={`px-3 py-2 align-top ${dirtyClass}`}>
      <input
        value={localInput}
        onChange={(event) => setLocalInput(event.target.value)}
        onBlur={() => onCommit(localInput)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.currentTarget.blur()
          }
        }}
        className={`w-full bg-transparent text-[#f5f5f5] ${dirty ? 'outline outline-1 outline-[#f5f5f5]' : 'outline-none'}`}
      />
    </td>
  )
}
