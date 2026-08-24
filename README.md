# EDCAP_BE
Backend for the Evidence Data Collection and Analysis Platform.

## Prerequisites

- Java 21
- Maven 4+
- PostgreSQL 16
- Optional: Docker / Docker Compose for local database

## Local Setup

1. Start PostgreSQL:

```bash
cd EDCAP_BE
docker compose up -d postgres
```

2. Configure environment variables:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sdd_platform`
- `SPRING_DATASOURCE_USERNAME=sdd`
- `SPRING_DATASOURCE_PASSWORD=change-me-in-production`
- `APP_JWT_SECRET=<random base64 secret>`

Optional connector/webhook variables:

- `GITHUB_API_TOKEN`
- `GITHUB_WEBHOOK_SECRET`
- `CIRCLECI_API_TOKEN`
- `CIRCLECI_WEBHOOK_SECRET`

3. Run the backend:

```bash
mvn spring-boot:run
```

Flyway migrations run on startup.

## Local Login

The repository seeds demo auth users in `src/main/resources/db/migration/V81__seed_demo_auth_users.sql`.

Seeded demo accounts use:

- Username: `nk_trung`
- Password: `Admin@123456`

Other seeded usernames in the same migration:

- `pd_khoa`
- `nvt_dung`
- `lx_loc`

Auth endpoints:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/me`

## Test

```bash
mvn test
```

For integration tests, keep PostgreSQL available and ensure Testcontainers can run in your environment.

## Build

```bash
mvn clean package
```

## Notes

- Do not use the default `APP_JWT_SECRET` placeholder in production.
- Keep secrets out of source control and out of README examples unless they are intentional demo seeds.
