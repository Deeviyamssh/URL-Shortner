/**
 * API client — all fetch calls use relative URLs.
 * No base URL or credentials are embedded in this file.
 */

function authHeaders(accessKey, secretKey) {
  return {
    'Content-Type': 'application/json',
    'X-Access-Key': accessKey,
    'X-Secret-Key': secretKey,
  };
}

export async function createUrl(accessKey, secretKey, originalUrl, customAlias) {
  const body = { originalUrl };
  if (customAlias && customAlias.trim()) {
    body.customAlias = customAlias.trim();
  }
  const res = await fetch('/api/urls', {
    method: 'POST',
    headers: authHeaders(accessKey, secretKey),
    body: JSON.stringify(body),
  });
  return res;
}

export async function listUrls(accessKey, secretKey) {
  const res = await fetch('/api/urls', {
    method: 'GET',
    headers: authHeaders(accessKey, secretKey),
  });
  return res;
}

export async function deleteUrl(accessKey, secretKey, id) {
  const res = await fetch(`/api/urls/${id}`, {
    method: 'DELETE',
    headers: authHeaders(accessKey, secretKey),
  });
  return res;
}

export async function getUrlClicks(accessKey, secretKey, id) {
  const res = await fetch(`/api/urls/${id}/clicks`, {
    method: 'GET',
    headers: authHeaders(accessKey, secretKey),
  });
  return res;
}

export async function getGlobalClicks(accessKey, secretKey) {
  const res = await fetch('/api/urls/clicks', {
    method: 'GET',
    headers: authHeaders(accessKey, secretKey),
  });
  return res;
}
