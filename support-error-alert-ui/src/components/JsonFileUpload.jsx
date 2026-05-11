import './JsonFileUpload.css';

export function JsonFileUpload({ onFileSelected, accept = 'application/json,.json', label = 'Upload JSON' }) {
  const handleChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    await onFileSelected(file);
  };

  return (
    <label className="json-upload">
      <input type="file" accept={accept} onChange={handleChange} className="json-upload__input" />
      {label}
    </label>
  );
}
