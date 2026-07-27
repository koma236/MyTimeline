import type { ReactNode } from 'react'

/** mock/css/style.css の .btn--primary.btn--block 相当（角丸は 999px のピル型）。 */
export function SubmitButton({ pending, children }: { pending: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={pending}
      className="w-full rounded-full bg-accent px-[18px] py-2 font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </button>
  )
}
