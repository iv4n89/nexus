'use client'

export function ConfirmDestructive({
  open,
  busy,
  error,
  onCancel,
  onConfirm,
}: {
  open: boolean
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
      <div role="dialog" aria-modal="true" className="w-full max-w-md border border-[#2a2a2a] bg-black p-6">
        <h2 className="text-sm tracking-[0.25em]">CONFIRM</h2>
        <p className="mt-4 text-sm">This statement is destructive. Run it?</p>
        {error ? <p className="mt-4 text-sm text-[#ff4d4f]">{error}</p> : null}
        <div className="mt-8 flex justify-end gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={onCancel}
            className="border border-[#2a2a2a] px-4 py-2 text-sm"
          >
            CANCEL
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onConfirm}
            className="border border-[#f5f5f5] px-4 py-2 text-sm"
          >
            RUN
          </button>
        </div>
      </div>
    </div>
  )
}
