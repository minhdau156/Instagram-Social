import axios, { InternalAxiosRequestConfig } from 'axios';
import { navigationRef } from '../lib/navigationRef';

// ============================================================================
// 1. CREATE AXIOS INSTANCE
// ============================================================================
// We create a reusable 'api' object. It automatically prepends our backend URL
// to every request (e.g., http://localhost:8080/api/v1/posts).
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  timeout: 10000,
});

// ============================================================================
// 2. REQUEST INTERCEPTOR (Before the request is sent)
// ============================================================================
// Think of this as a checkpoint before the HTTP request leaves the browser.
// We grab the user's access token and attach it to the request header so the 
// backend knows who is logged in.
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('accessToken');

  if (token) {
    // If the headers object doesn't exist, create it
    if (!config.headers) {
      config.headers = {} as any;
    }
    // Attach the Token
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

// ============================================================================
// 3. REFRESH QUEUE STATE
// ============================================================================
// When a token expires, we only want to ask the server for a new token ONCE,
// even if 5 different requests fail at the exact same time. These variables
// keep track of whether we are currently refreshing, and store waiting requests.
let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string) => void; reject: (err: any) => void }> = [];

// This helper function goes through all the waiting requests and either sends 
// them on their way with the new token, or rejects them if the refresh failed.
const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = []; // Empty the queue after processing
};

// ============================================================================
// 4. RESPONSE INTERCEPTOR (After the response comes back)
// ============================================================================
api.interceptors.response.use(
  // If the request was successful, just return the response untouched.
  (response) => response,

  // If the request fails, evaluate what went wrong.
  async (error) => {
    const originalRequest = error.config;

    // SCENARIO A: The server said "401 Unauthorized" (Our token is expired/missing)
    // We also make sure we haven't already tried to retry this exact request (`!_retry`).
    if ((error.response?.status === 401 && !originalRequest._retry)) {

      // If the `/refresh` endpoint itself fails, just stop. Don't create an infinite loop.
      if (originalRequest.url?.includes('/api/v1/auth/refresh')) {
        return Promise.reject(error);
      }

      // If we are ALREADY currently asking the server for a new token,
      // just wait in line (push to the queue) until the new token arrives.
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            // Success! We got the new token from the queue, let's retry the request.
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      // We are the FIRST request to realize the token is expired!
      originalRequest._retry = true;  // Mark this request so we don't try it twice
      isRefreshing = true;            // Lock the system so others wait in the queue

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) throw new Error('No refresh token available');

        // Ask the backend for a new access token
        const { data } = await axios.post(`${import.meta.env.VITE_API_URL}/api/v1/auth/refresh`, {
          refreshToken,
        });

        const newAccessToken = data.data.accessToken;
        const newRefreshToken = data.data.refreshToken;

        // Save the new tokens to the browser's storage
        localStorage.setItem('accessToken', newAccessToken);
        if (newRefreshToken) localStorage.setItem('refreshToken', newRefreshToken);

        // Tell everyone waiting in the queue to continue with the new token
        processQueue(null, newAccessToken);

        // Retry our own original request with the new token
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);

      } catch (err) {
        // The refresh token is also invalid (user's session is completely over)
        processQueue(err, null);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');

        // Force the user back to the login screen
        if (navigationRef.navigate) {
          navigationRef.navigate('/login');
        }
        return Promise.reject(err);
      } finally {
        // Unlock the system, we are done trying to refresh
        isRefreshing = false;
      }
    }

    // SCENARIO B: A normal error happened (e.g. 404 Not Found, 500 Server Error)
    // We clean up the messy error object into a simple Error("Message") for the UI.
    const message =
      error.response?.data?.detail ??
      error.response?.data?.message ??
      error.message ??
      'An unexpected error occurred';

    return Promise.reject(new Error(message));
  }
);
