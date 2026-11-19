export default function FormInput({
  id,
  label,
  type = 'text',
  value,
  onChange,
  placeholder,
  error,
  hint,
  autoComplete,
  required = false,
  disabled = false,
}) {
  return (
    <div className="form-group">
      <label className="form-label" htmlFor={id}>
        {label}
        {required ? ' *' : ''}
      </label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        autoComplete={autoComplete}
        disabled={disabled}
        className={`form-input ${error ? 'input-error' : ''}`}
      />
      {error && <div className="field-error">{error}</div>}
      {!error && hint && <div className="field-hint">{hint}</div>}
    </div>
  );
}
