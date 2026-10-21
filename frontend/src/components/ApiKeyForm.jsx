import React, { useState } from 'react';
import { listUrls } from '../api.js';

export default function ApiKeyForm({ onAuthenticated }) {
  const [accessKey, setAccessKey] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    if (!accessKey.trim() || !secretKey.trim()) {
      setError('Both Access Key and Secret Key are required.');
      return;
    }
    setLoading(true);
    try {
      const res = await listUrls(accessKey.trim(), secretKey.trim());
      if (res.ok) {
        onAuthenticated(accessKey.trim(), secretKey.trim());
      } else if (res.status === 401 || res.status === 403) {
        setError('Invalid credentials. Check your Access Key and Secret Key.');
      } else {
        setError(`Unexpected error (HTTP ${res.status}). Try again.`);
      }
    } catch (err) {
      setError('Network error. Is the server running?');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card">
      <h2>Sign in</h2>
      <form onSubmit={handleSubmit} autoComplete="off">
        <div className="form-group">
          <label htmlFor="accessKey">Access Key</label>
          <input
            id="accessKey"
            type="password"
            value={accessKey}
            onChange={(e) => setAccessKey(e.target.value)}
            placeholder="Enter your access key"
            autoComplete="username"
            disabled={loading}
          />
        </div>
        <div className="form-group">
          <label htmlFor="secretKey">Secret Key</label>
          <input
            id="secretKey"
            type="password"
            value={secretKey}
            onChange={(e) => setSecretKey(e.target.value)}
            placeholder="Enter your secret key"
            autoComplete="current-password"
            disabled={loading}
          />
        </div>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
