import axios from 'axios';
import Cookies from 'js-cookie';

// Get API URL dynamically based on current host
// If accessing from mobile, use the same hostname but port 8000
const getApiUrl = () => {
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  
  // Use current hostname (works for both localhost and IP access)
  const hostname = window.location.hostname;
  // If localhost, keep it. Otherwise use the same hostname
  return `http://${hostname}:8000`;
};

const API_URL = getApiUrl();

const api = axios.create({
    baseURL: API_URL,
});

// Interceptor to add Token if available
api.interceptors.request.use((config) => {
    const token = Cookies.get('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export const register = async (username, password) => {
    const response = await api.post('/register', { username, password });
    return response.data;
};

export const login = async (username, password) => {
    const response = await api.post('/login', { username, password });
    return response.data;
};

// Unified heartbeat: sync count (can be 0) and get stats
// Try /heartbeat first, fallback to /sync + /stats if not available
export const heartbeat = async (count, guestUuid) => {
    try {
        const response = await api.post('/heartbeat', { count, guest_uuid: guestUuid });
        return response.data; // Returns Stats object
    } catch (err) {
        // Fallback: if /heartbeat doesn't exist, use /sync + /stats
        if (err.response?.status === 404) {
            if (count > 0) {
                await api.post('/sync', { count, guest_uuid: guestUuid });
            }
            const statsResponse = await api.get('/stats', { params: { guest_uuid: guestUuid } });
            return statsResponse.data;
        }
        throw err;
    }
};

// Keep for backward compatibility
export const getStats = async (guestUuid) => {
    const response = await api.get('/stats', { params: { guest_uuid: guestUuid } });
    return response.data;
};
