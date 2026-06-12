# Instagram-Social-

![CI](https://github.com/minhdau156/Instagram-Social-/actions/workflows/ci.yml/badge.svg)

### First-time local setup

1. Copy the example environment file and fill in your values: `cp .env.example backend/.env`
2. Edit `backend/.env` — at minimum set `JWT_SECRET` to a random string of at least 32 characters.
3. Start the local Docker stack (PostgreSQL + MinIO): `docker compose up -d postgres minio`
4. Run the backend: `cd backend && mvn spring-boot:run`