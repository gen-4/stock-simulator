const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

async function request(endpoint, options = {}) {
  const url = `${API_URL}${endpoint}`;
  const token = localStorage.getItem('accessToken');

  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  };

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, config);

  // Handle 401 - try refresh token
  if (response.status === 401 && !options._retry) {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      try {
        const refreshResponse = await fetch(`${API_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });

        if (refreshResponse.ok) {
          const data = await refreshResponse.json();
          localStorage.setItem('accessToken', data.accessToken);
          localStorage.setItem('refreshToken', data.refreshToken);

          // Retry original request with new token
          config.headers['Authorization'] = `Bearer ${data.accessToken}`;
          const retryResponse = await fetch(url, { ...config, _retry: true });
          return retryResponse;
        }
      } catch {
        // Refresh failed - clear tokens and redirect
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        throw new Error('Session expired. Please login again.');
      }
    }
  }

  return response;
}

export const api = {
  get: (endpoint) => request(endpoint, { method: 'GET' }),

  post: (endpoint, body) =>
    request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  put: (endpoint, body) =>
    request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  delete: (endpoint) => request(endpoint, { method: 'DELETE' }),
};

export default api;
