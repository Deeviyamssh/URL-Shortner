import React, { useState } from 'react';
import { createUrl } from '../api.js';

export default function UrlForm({ accessKey, secretKey, onCreated, onAuthError }) {
  const [originalUrl, setOriginalUrl] = useState('');
  const [customAlias, setCustomAlias] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    if (!originalUrl.trim()) {
      setError('URL is required.');
      return;
    }
    setLoading(true);
    try {
      const res = await createUrl(accessKey, secretKey, originalUrl.trim(), customAlias.trim());
      if (res.ok) {
        const data = await res.json();
        const shortRef = data.shortCode ? `${window.location.origin}/${data.shortCode}` : JSON.stringify(data);
        setSuccess(`Short URL created: ${shortRef}`);
        setOriginalUrl('');
        setCustomAlias('');
        onCreated();
      } else if (res.status === 401 || res.status === 403) {
        onAuthError('Session expired. Please sign in again.');
      } else if (res.status === 409) {
        setError('That alias is already in use. Try a different one.');
      } else if (res.status === 400) {
        let msg = 'Invalid request.';
        try {
          const body = await res.json();
          msg = body.message || body.error || JSON.stringify(body);
        } catch (_) {}
        setError(msg);
      } else {
        setError(`Unexpected error (HTTP ${res.status}).`);
      }
    } catch (err) {
      setError('Network error. Is the server running?');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card">
      <h2>Shorten a URL</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="originalUrl">Long URL</label>
          <input
            id="originalUrl"
            type="url"
            value={originalUrl}
            onChange={(e) => setOriginalUrl(e.target.value)}
            placeholder="https://example.com/very/long/url"
            disabled={loading}
            required
          />
        </div>
        <div className="form-group">
          <label htmlFor="customAlias">Custom alias (optional)</label>
          <input
            id="customAlias"
            type="text"
            value={customAlias}
            onChange={(e) => setCustomAlias(e.target.value)}
            placeholder="my-alias"
            disabled={loading}
          />
        </div>
        {error && <p className="error-text">{error}</p>}
        {success && <p className="success-text">{success}</p>}
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? 'Creating…' : 'Shorten'}
        </button>
      </form>
    </div>
  );
}
