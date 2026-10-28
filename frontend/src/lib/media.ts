// Base URL for media in the MinIO bucket. VITE_CDN_BASE_URL is baked in at build time:
// local dev → http://localhost:9000, prod → https://<domain> (Caddy proxies /instagram-media* to MinIO).
export const MEDIA_BASE_URL = `${import.meta.env.VITE_CDN_BASE_URL || 'http://localhost:9000'}/instagram-media`;

export const mediaUrl = (key: string | undefined): string => `${MEDIA_BASE_URL}/${key}`;
