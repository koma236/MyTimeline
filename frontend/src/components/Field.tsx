/** mock/css/style.css の .field 相当。ラベル・入力欄・補足・エラーをひとまとめにする。 */
interface FieldProps {
  id: string
  name: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  help?: string
  /** バックエンドの fieldErrors から渡す。文言はサーバー側が持つ */
  error?: string
  autoComplete?: string
  placeholder?: string
}

export function Field({
  id,
  name,
  label,
  value,
  onChange,
  type = 'text',
  help,
  error,
  autoComplete,
  placeholder,
}: FieldProps) {
  return (
    <div className="mb-4">
      <label className="mb-1 block text-[13px] font-bold text-muted" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`w-full rounded-lg border bg-bg px-3 py-2.5 text-[15px] text-text outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 ${
          error ? 'border-danger' : 'border-border-strong'
        }`}
      />
      {help && <p className="mt-1 text-xs text-muted">{help}</p>}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-[13px] text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
