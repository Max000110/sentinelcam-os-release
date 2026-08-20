# Nginx & Reverse Proxy Security Guidelines

## 1. Recommended Production Configuration
When terminating SSL with Nginx:
```nginx
server {
    listen 443 ssl http2;
    server_name sentinelcam.example.com;

    ssl_certificate /etc/letsencrypt/live/sentinelcam.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sentinelcam.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # WebSocket Proxying
    location /ws/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400s;
    }

    # API Routes
    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Dashboard UI
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
    }
}
```
