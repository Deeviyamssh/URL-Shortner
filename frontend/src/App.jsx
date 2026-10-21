import React, { useState, useCallback } from 'react';
import ApiKeyForm from './components/ApiKeyForm.jsx';
import UrlForm from './components/UrlForm.jsx';
import UrlList from './components/UrlList.jsx';

export default function App() {
  const [accessKey, setAccessKey] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [authenticated, setAuthenticated] = useState(false);
  const [error, setError] = useState(null);
  // Trigger UrlList refresh by bumping a counter
  const [refreshTick, setRefreshTick] = useState(0);

  const handleAuthenticated = useCallback((ak, sk) => {
    setAccessKey(ak);
    setSecretKey(sk);
    setAuthenticated(true);
    setError(null);
  }, []);

  const handleAuthError = useCallback((msg) => {
    setError(msg);
    setAuthenticated(false);
    setAccessKey('');
    setSecretKey('');
  }, []);

  const handleSignOut = useCallback(() => {
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
