import React, { useState, useCallback } from 'react';
import ApiKeyForm from './components/ApiKeyForm.jsx';
import UrlForm from './components/UrlForm.jsx';
import UrlList from './components/UrlList.jsx';

function readSavedCredentials() {
  try {
    const saved = sessionStorage.getItem('url-shortener-credentials');
    if (!saved) return null;
    const credentials = JSON.parse(saved);
    if (
      typeof credentials.accessKey === 'string' &&
      credentials.accessKey &&
      typeof credentials.secretKey === 'string' &&
      credentials.secretKey
    ) {
      return credentials;
    }
  } catch {
    // If storage is unavailable or malformed, show the sign-in page.
  }
  return null;
}

function clearSavedCredentials() {
  try {
    sessionStorage.removeItem('url-shortener-credentials');
  } catch {
    // Ignore storage errors; the in-memory sign-out still works.
  }
}

export default function App() {
  const [savedCredentials] = useState(readSavedCredentials);
  const [accessKey, setAccessKey] = useState(() => savedCredentials?.accessKey ?? '');
  const [secretKey, setSecretKey] = useState(() => savedCredentials?.secretKey ?? '');
  const [authenticated, setAuthenticated] = useState(() => Boolean(savedCredentials));
  const [error, setError] = useState(null);
  // Trigger UrlList refresh by bumping a counter
  const [refreshTick, setRefreshTick] = useState(0);

  const handleAuthenticated = useCallback((ak, sk) => {
    setAccessKey(ak);
    setSecretKey(sk);
    try {
      sessionStorage.setItem(
        'url-shortener-credentials',
        JSON.stringify({ accessKey: ak, secretKey: sk })
      );
    } catch {
      // The current page remains usable, but a refresh may require signing in again.
    }
    setAuthenticated(true);
    setError(null);
  }, []);

  const handleAuthError = useCallback((msg) => {
    clearSavedCredentials();
    setError(msg);
    setAuthenticated(false);
    setAccessKey('');
    setSecretKey('');
  }, []);

  const handleSignOut = useCallback(() => {
    clearSavedCredentials();
    setAuthenticated(false);
    setAccessKey('');
    setSecretKey('');
    setError(null);
  }, []);

  const handleUrlCreated = useCallback(() => {
    setRefreshTick((t) => t + 1);
  }, []);

  if (!authenticated) {
    return (
      <div>
        <div className="header">
          <h1>URL Shortener</h1>
        </div>
        <p className="subtitle">Enter your API credentials to get started.</p>
        <ApiKeyForm onAuthenticated={handleAuthenticated} />
        {error && <p className="error-text">{error}</p>}
      </div>
    );
  }

  return (
    <div>
      <div className="header">
        <h1>URL Shortener</h1>
        <div className="header-actions">
          <button className="btn-secondary" onClick={handleSignOut}>
            Sign out
          </button>
        </div>
      </div>
      <UrlForm
        accessKey={accessKey}
        secretKey={secretKey}
        onCreated={handleUrlCreated}
        onAuthError={handleAuthError}
      />
      <UrlList
        accessKey={accessKey}
        secretKey={secretKey}
        refreshTick={refreshTick}
        onAuthError={handleAuthError}
      />
    </div>
  );
}
