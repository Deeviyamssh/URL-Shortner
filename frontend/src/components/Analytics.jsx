import React, { useState, useEffect } from 'react';
import { getUrlClicks } from '../api.js';

export default function Analytics({ urlId, accessKey, secretKey, onAuthError }) {
  const [clicks, setClicks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function fetchClicks() {
      setLoading(true);
      setError(null);
      try {
        const res = await getUrlClicks(accessKey, secretKey, urlId);
        if (cancelled) return;
        if (res.ok) {
          const data = await res.json();
          setClicks(data);
        } else if (res.status === 401 || res.status === 403) {
          onAuthError('Session expired. Please sign in again.');
        } else {
          setError(`Failed to load analytics (HTTP ${res.status}).`);
        }
      } catch (err) {
        if (!cancelled) setError('Network error while loading analytics.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchClicks();
    return () => { cancelled = true; };
  }, [urlId, accessKey, secretKey, onAuthError]);

  return (
    <div style={{ marginTop: '10px', padding: '10px', background: '#f8f8f8', borderRadius: '4px' }}>
      <h3 style={{ fontSize: '0.875rem', marginBottom: '8px' }}>Click Analytics</h3>
      {loading && <p className="loading">Loading analytics…</p>}
      {!loading && error && <p className="error-text">{error}</p>}
      {!loading && !error && clicks.length === 0 && (
        <p className="empty-state" style={{ fontSize: '0.875rem', padding: '8px 0' }}>
          No clicks recorded yet.
        </p>
      )}
      {!loading && !error && clicks.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Clicked At</th>
                <th>IP Address</th>
                <th>Referrer</th>
                <th>User Agent</th>
              </tr>
            </thead>
            <tbody>
              {clicks.map((click, idx) => (
                <tr key={click.eventId || idx}>
                  <td>{click.clickedAt ? new Date(click.clickedAt).toLocaleString() : '—'}</td>
                  <td>{click.ipAddress || '—'}</td>
                  <td>{click.referrer || '—'}</td>
                  <td style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={click.userAgent}>
                    {click.userAgent || '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
