import React, { useState } from 'react';
import { deleteUrl } from '../api.js';
import Analytics from './Analytics.jsx';

export default function UrlRow({ url, accessKey, secretKey, onDeleted, onAuthError }) {
  const [showAnalytics, setShowAnalytics] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [copyLabel, setCopyLabel] = useState('Copy');

  const shortUrl = `${window.location.origin}/${url.shortCode}`;

  async function handleDelete() {
    if (!window.confirm(`Delete short URL "${url.shortCode}"? This cannot be undone.`)) {
      return;
    }
    setDeleting(true);
    try {
      const res = await deleteUrl(accessKey, secretKey, url.id);
      if (res.ok || res.status === 204) {
        onDeleted();
      } else if (res.status === 401 || res.status === 403) {
        onAuthError('Session expired. Please sign in again.');
      } else {
        alert(`Could not delete URL (HTTP ${res.status}).`);
      }
    } catch (err) {
      alert('Network error while deleting.');
    } finally {
      setDeleting(false);
    }
  }

  function handleCopy() {
    navigator.clipboard.writeText(shortUrl).then(() => {
      setCopyLabel('Copied!');
      setTimeout(() => setCopyLabel('Copy'), 1500);
    }).catch(() => {
      // Fallback for environments without clipboard API
      const el = document.createElement('textarea');
      el.value = shortUrl;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      setCopyLabel('Copied!');
      setTimeout(() => setCopyLabel('Copy'), 1500);
    });
  }

  const createdAt = url.createdAt
    ? new Date(url.createdAt).toLocaleDateString()
    : '—';

  return (
    <div style={{ borderBottom: '1px solid #e8e8e8', padding: '12px 0' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', gap: '8px', justifyContent: 'space-between' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '4px' }}>
            <a href={shortUrl} target="_blank" rel="noopener noreferrer" className="tag">
              /{url.shortCode}
            </a>
            <span style={{ color: '#888', fontSize: '0.8rem' }}>
              {url.clickCount} click{url.clickCount !== 1 ? 's' : ''}
            </span>
            <span style={{ color: '#bbb', fontSize: '0.8rem' }}>{createdAt}</span>
          </div>
          <div style={{ fontSize: '0.875rem', color: '#555', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            <a href={url.originalUrl} target="_blank" rel="noopener noreferrer" title={url.originalUrl}>
              {url.originalUrl}
            </a>
          </div>
        </div>
        <div className="url-row-actions">
          <button className="btn-secondary" onClick={handleCopy} title="Copy short URL">
            {copyLabel}
          </button>
          <button
            className="btn-secondary"
            onClick={() => setShowAnalytics((v) => !v)}
            title="Toggle analytics"
          >
            {showAnalytics ? 'Hide analytics' : 'Analytics'}
          </button>
          <button
            className="btn-danger"
            onClick={handleDelete}
            disabled={deleting}
            title="Delete this URL"
          >
            {deleting ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
      {showAnalytics && (
        <Analytics urlId={url.id} accessKey={accessKey} secretKey={secretKey} onAuthError={onAuthError} />
      )}
    </div>
  );
}
