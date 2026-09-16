'use client'

function shortened(id: string | undefined): string {
  if (!id) {
    return '—'
  }
  return id.slice(0, 8)
}

export function RollbackDialog({
  open,
  currentId,
  previousId,
  busy,
  error,
  onCancel,
  onConfirm,
}: {
  open: boolean
  currentId: string | undefined
  previousId: string | undefined
  busy: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}) {
  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="rollback-title"
        className="w-full max-w-md border border-[#2a2a2a] bg-black p-6"
      >
        <h2 id="rollback-title" className="text-sm tracking-[0.25em]">
          ROLLBACK
        </h2>
        <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 font-mono text-sm">
          <dt className="text-[#888]">Current deployment:</dt>
          <dd>{shortened(currentId)}</dd>
          <dt className="text-[#888]">Previous deployment:</dt>
          <dd>{shortened(previousId)}</dd>
        </dl>
        {error ? <p className="mt-4 text-sm text-[#ff4d4f]">{error}</p> : null}
        <div className="mt-8 flex justify-end gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={onCancel}
            className="border border-[#2a2a2a] px-4 py-2 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:text-[#888]"
          >
            CANCEL
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onConfirm}
            className="border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
          >
            ROLLBACK
          </button>
        </div>
      </div>
    </div>
  )
}
