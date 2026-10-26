import React, { useState } from 'react';
import { listUrls, registerAccount } from '../api.js';

export default function ApiKeyForm({ onAuthenticated }) {
  const [mode, setMode] = useState('signin'); // 'signin' | 'signup'
  const [accessKey, setAccessKey] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [registeredKey, setRegisteredKey] = useState(null);
  const [registeredOwner, setRegisteredOwner] = useState(null);

  async function handleSignIn(e) {
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

  async function handleSignUp(e) {
    e.preventDefault();
    setError(null);
    if (!username.trim() || !password) {
      setError('Username and password are required.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (password.length < 8 || password.length > 72) {
      setError('Password must be 8–72 characters.');
      return;
    }
    setLoading(true);
    try {
      const res = await registerAccount(username.trim(), password);
      if (res.status === 201) {
        const data = await res.json();
        setRegisteredKey(data.accessKey);
        setRegisteredOwner(data.owner);
      } else if (res.status === 409) {
        setError('That username is already taken.');
      } else if (res.status === 400) {
        const body = await res.json().catch(() => ({}));
        setError(body.message || 'Invalid input. Check username and password length.');
      } else if (res.status === 429) {
        setError('Too many registrations from this IP. Try again later.');
      } else if (res.status === 503) {
        setError('Registration is temporarily unavailable. Try again later.');
      } else {
        setError(`Unexpected error (HTTP ${res.status}).`);
      }
    } catch (err) {
      setError('Network error. Is the server running?');
    } finally {
      setLoading(false);
    }
  }

  function handleKeySaved() {
    onAuthenticated(registeredKey, password);
  }

  function handleCopyKey() {
    navigator.clipboard.writeText(registeredKey).then(() => {}).catch(() => {});
  }

  // After registration: show the generated access key once
  if (registeredKey) {
    return (
      <div className="card">
        <h2>Registration successful</h2>
        <p className="subtitle">Welcome, <strong>{registeredOwner}</strong>!</p>
        <div className="form-group">
          <label>Your Access Key (save this — it will not be shown again)</label>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <code style={{ flex: 1, padding: '8px 12px', background: '#f0f0f0', borderRadius: '4px',
                           fontSize: '0.875rem', wordBreak: 'break-all', fontFamily: 'monospace' }}>
              {registeredKey}
            </code>
            <button type="button" className="btn-secondary" onClick={handleCopyKey}>Copy</button>
          </div>
        </div>
        <p className="error-text" style={{ color: '#e67e22' }}>
          You will need both this Access Key and your password to sign in. Save the Access Key now — it will not be shown again.
        </p>
        <button type="button" className="btn-primary" onClick={handleKeySaved}>
          I've saved my Access Key — continue
        </button>
      </div>
    );
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', gap: '0', marginBottom: '16px', borderBottom: '2px solid #e0e0e0' }}>
        <button
          type="button"
          className={mode === 'signin' ? 'btn-primary' : 'btn-secondary'}
          style={{ borderRadius: '4px 4px 0 0', borderBottom: 'none', flex: 1 }}
          onClick={() => { setMode('signin'); setError(null); }}
        >
          Sign in
        </button>
        <button
          type="button"
          className={mode === 'signup' ? 'btn-primary' : 'btn-secondary'}
          style={{ borderRadius: '4px 4px 0 0', borderBottom: 'none', flex: 1 }}
          onClick={() => { setMode('signup'); setError(null); }}
        >
          Sign up
        </button>
      </div>

      {mode === 'signin' ? (
        <form onSubmit={handleSignIn} autoComplete="off">
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
            <label htmlFor="secretKey">Secret Key (your password)</label>
            <input
              id="secretKey"
              type="password"
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value)}
              placeholder="Enter your password"
              autoComplete="current-password"
              disabled={loading}
            />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleSignUp} autoComplete="off">
          <div className="form-group">
            <label htmlFor="signupUsername">Username</label>
            <input
              id="signupUsername"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="3–30 chars: letters, numbers, _ -"
              autoComplete="username"
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <label htmlFor="signupPassword">Password</label>
            <input
              id="signupPassword"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8–72 characters"
              autoComplete="new-password"
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <label htmlFor="signupConfirm">Confirm password</label>
            <input
              id="signupConfirm"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Re-enter your password"
              autoComplete="new-password"
              disabled={loading}
            />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>
      )}
    </div>
  );
}
