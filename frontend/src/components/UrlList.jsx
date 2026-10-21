import React, { useState, useEffect, useCallback } from 'react';
import { listUrls } from '../api.js';
import UrlRow from './UrlRow.jsx';

export default function UrlList({ accessKey, secretKey, refreshTick, onAuthError }) {
  const [urls, setUrls] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchUrls = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listUrls(accessKey, secretKey);
      if (res.ok) {
        const data = await res.json();
        setUrls(data);
      } else if (res.status === 401 || res.status === 403) {
        onAuthError('Session expired. Please sign in again.');
      } else {
        setError(`Failed to load URLs (HTTP ${res.status}).`);
      }
    } catch (err) {
      setError('Network error. Is the server running?');
    } finally {
      setLoading(false);
    }
  }, [accessKey, secretKey, onAuthError]);

  useEffect(() => {
    fetchUrls();
  }, [fetchUrls, refreshTick]);

  return (
    <div className="card">
      <h2>Your URLs</h2>
      {loading && <p className="loading">Loading…</p>}
      {!loading && error && <p className="error-text">{error}</p>}
      {!loading && !error && urls.length === 0 && (
        <p className="empty-state">No URLs yet. Shorten one above!</p>
      )}
      {!loading && !error && urls.length > 0 && (
        <div>
          {urls.map((url) => (
            <UrlRow
              key={url.id}
              url={url}
              accessKey={accessKey}
              secretKey={secretKey}
              onDeleted={fetchUrls}
              onAuthError={onAuthError}
            />
          ))}
        </div>
      )}
    </div>
  );
}
