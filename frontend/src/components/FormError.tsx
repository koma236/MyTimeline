/** mock/css/style.css の .form__error 相当。フォーム全体に対するエラーの帯。 */
export function FormError({ message }: { message?: string }) {
  if (!message) return null

  return (
    <p role="alert" className="mb-3 rounded-lg bg-danger/[0.08] px-3 py-2 text-[13px] text-danger">
      {message}
    </p>
  )
}
