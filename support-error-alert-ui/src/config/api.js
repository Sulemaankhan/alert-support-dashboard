/**
 * Empty string uses Vite dev proxy (/api → backend).
 * Override with VITE_API_BASE=https://api.example.com for production builds.
 */
export const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');
