# HTTPS / TLS

## What is it
HTTPS is HTTP running over TLS (Transport Layer Security) — an encrypted, authenticated channel between client and server. TLS sits between the application layer (HTTP) and the transport layer (TCP), providing three guarantees:

- **Encryption** — data in transit can't be read by a third party
- **Integrity** — data can't be tampered with without detection
- **Authentication** — the client can verify it's talking to the real server (via certificates)

### How the TLS handshake works (simplified, TLS 1.3)
1. Client sends `ClientHello` with supported cipher suites and a random value
2. Server responds with `ServerHello`, its certificate, and a random value
3. Client verifies the certificate against a trusted Certificate Authority (CA)
4. Both sides derive a shared symmetric session key using key exchange (e.g. ECDHE)
5. All further communication is encrypted with that symmetric key (asymmetric crypto is too slow for bulk data)

### Key building blocks
| Term | Meaning |
|---|---|
| Certificate | Digital document binding a public key to a domain, signed by a CA |
| CA (Certificate Authority) | Trusted entity that issues and signs certificates (e.g. Let's Encrypt, DigiCert) |
| Asymmetric encryption | Public/private key pair, used during handshake to establish trust |
| Symmetric encryption | Single shared key, used for actual data transfer (faster) |
| SNI (Server Name Indication) | Lets one IP serve certs for multiple domains |
| mTLS (mutual TLS) | Both client and server present certificates (common in service-to-service auth) |

## Why use it
- **Confidentiality**: prevents eavesdroppers (on public Wi-Fi, ISPs, routers) from reading passwords, tokens, or personal data
- **Integrity**: prevents man-in-the-middle attacks from injecting or modifying content (e.g. injecting ads/malware into unencrypted HTTP)
- **Authentication**: confirms users are talking to the real server, not a spoofed one
- **Trust signals**: browsers mark HTTP sites as "Not Secure"; HTTPS is required for the padlock icon
- **Required by modern web features**: HTTP/2, Service Workers, Geolocation API, and most browser APIs require a secure context
- **SEO and compliance**: search engines rank HTTPS sites higher; many compliance standards (PCI-DSS, GDPR) require it for sensitive data

## How to use it

### Getting a certificate (Let's Encrypt via Certbot)
```bash
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

### Nginx TLS termination
```nginx
server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate     /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://localhost:8080;
    }
}

server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$host$request_uri;  # force redirect HTTP -> HTTPS
}
```

### Spring Boot (enabling HTTPS directly, without a reverse proxy)
```properties
server.port=8443
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=tomcat
```

### Enforcing HTTPS in the app (paired with HSTS header)
```java
.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31536000))
```

### Verifying a cert from the command line
```bash
openssl s_client -connect yourdomain.com:443 -servername yourdomain.com
```

## When to use in real life
- **Always**, for any production system — there's no valid case for serving auth/personal data over plain HTTP today
- Login forms, checkout flows, and any place credentials or tokens travel
- REST APIs — especially ones issuing JWTs or session cookies (Instagram-like backend included)
- Internal microservice communication in sensitive environments — use **mTLS** so services authenticate each other, not just the client authenticating the server
- Mobile apps calling backend APIs — HTTPS prevents traffic interception on public networks
- Anywhere compliance is required: payment processing (PCI-DSS), healthcare (HIPAA), or GDPR-covered personal data
