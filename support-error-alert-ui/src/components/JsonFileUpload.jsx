import './JsonFileUpload.css';

/**
 * @param {Object} props
 * @param {(file: File) => void | Promise<void>} props.onFileSelected
 * @param {string} [props.accept]
 * @param {string} [props.label]
 * @param {string} [props.className] - merged onto the label (e.g. variant class)
 * @param {boolean} [props.disabled]
 */
export function JsonFileUpload({
  onFileSelected,
  accept = 'application/json,.json',
  label = 'Upload JSON',
  className = '',
  disabled = false,
}) {
  const handleChange = async (e) => {
    if (disabled) return;
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    await onFileSelected(file);
  };

  const labelClass = ['json-upload', className].filter(Boolean).join(' ');
  return (
    <label className={labelClass} aria-disabled={disabled}>
      <input
        type="file"
        accept={accept}
        onChange={handleChange}
        className="json-upload__input"
        disabled={disabled}
      />
      {label}
    </label>
  );
}
