# Hospital Information System (HIS) – backend

REST API for a Hospital Information System. This first stage contains the project skeleton, JWT security,
Flyway-managed schema and one complete reference module (**Patient**) that future modules (Doctor,
Appointment, Hospitalization, ...) should copy.

## Stack

Java 21 · Spring Boot 3.5 · Maven (wrapper included) · Spring Web / Data JPA (Hibernate) / Security / Validation ·
PostgreSQL 16 · Flyway · JWT (`jjwt`) · Lombok · MapStruct · springdoc-openapi (Swagger UI) ·
JUnit 5 · Mockito · Spring Security Test · Testcontainers

## Run locally

Prerequisites: JDK 21, Docker.

```bash
docker compose up -d --wait                                  # PostgreSQL on localhost:5432
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run          # API on http://localhost:8080
```

Swagger UI (local profile only): <http://localhost:8080/swagger-ui.html> – use **Authorize** with the token from login.

### Configuration (environment variables)

| Variable | Purpose | Local default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | datasource | `jdbc:postgresql://localhost:5432/his`, `his`, `his` |
| `JWT_SECRET` | HS256 signing key, **≥ 32 bytes** | dev-only value in `application-local.yml` |
| `JWT_EXPIRATION` | token lifetime (e.g. `1h`, `30m`) | `1h` |
| `JWT_ISSUER` | token issuer | `his-backend` |
| `CORS_ALLOWED_ORIGINS` | comma separated origins | `http://localhost:3000,http://localhost:4200` |

Without the `local` profile `DB_*` and `JWT_SECRET` have **no defaults** and the app refuses to start – secrets never live in git.

## Default dev credentials (DEV ONLY)

Seeded by `V2__seed_admin_user.sql`:

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin123!` | ADMIN |

> ⚠️ Local development only. Because the seed is an ordinary migration it runs on every database; change the
> password or drop the seed before the first real deployment.

## Using the API

```bash
# 1. log in
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin123!"}' | jq -r .accessToken)

# 2. create a patient
curl -s -X POST localhost:8080/api/patients -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Maria","lastName":"Papadopoulou","dateOfBirth":"1990-05-17","gender":"FEMALE","nationalId":"17059012345"}'

# 3. search (filters: name, nationalId, phone, email, gender; paging: page, size; sorting: sort=lastName,desc)
curl -s "localhost:8080/api/patients?name=maria&sort=lastName,asc&page=0&size=20" -H "Authorization: Bearer $TOKEN"

# 4. register another user (ADMIN only)
curl -s -X POST localhost:8080/api/auth/register -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"dr.house","email":"house@his.local","password":"Sup3rSecret!","fullName":"Gregory House","roles":["DOCTOR"]}'
```

| Endpoint | Roles |
|---|---|
| `POST /api/auth/login` | public |
| `POST /api/auth/register` | ADMIN |
| `POST/GET/PUT /api/patients[/{id}]` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `DELETE /api/patients/{id}` (soft delete) | ADMIN |
| `GET /api/specialties` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `GET /api/doctors`, `GET /api/doctors/{id}` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `GET /api/doctors/me` (profile linked to the logged-in user) | DOCTOR |
| `POST /api/doctors`, `PUT /api/doctors/{id}`, `DELETE /api/doctors/{id}` (soft delete) | ADMIN |

| `POST/GET/PUT /api/appointments[/{id}]`, `POST /api/appointments/{id}/cancel` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `GET /api/appointments/me` (appointments of the logged-in doctor) | DOCTOR |
| `POST /api/appointments/{id}/complete` | ADMIN, or the assigned DOCTOR |
| `POST /api/appointments/{id}/no-show` | ADMIN, RECEPTIONIST, DOCTOR |

Appointments cannot be deleted; cancelling keeps the history.

| Hospitalization endpoint | Roles |
|---|---|
| `GET /api/wards[/{id}]`, `GET /api/rooms[/{id}]`, `GET /api/beds[/{id}]` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `POST/PUT /api/wards\|rooms\|beds`, `PATCH .../{id}/deactivate` | ADMIN |
| `POST /api/hospitalizations` (admit) | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `GET /api/hospitalizations`, `GET /api/hospitalizations/{id}`, `GET /api/hospitalizations/{id}/bed-history` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `GET /api/hospitalizations/me` (stays attended by the logged-in doctor) | DOCTOR |
| `PUT /api/hospitalizations/{id}` (reason, diagnosis, attending doctor) | ADMIN, DOCTOR |
| `POST /api/hospitalizations/{id}/transfer` | ADMIN, DOCTOR, NURSE |
| `POST /api/hospitalizations/{id}/discharge` | ADMIN, or the attending DOCTOR |

### Hospitalizations

* **Structure:** wards (seeded: Cardiology, Internal Medicine, Surgery, Orthopedics, Pediatrics, Intensive Care,
  Maternity) contain rooms, which contain beds. Rooms and beds are created through the API. Nothing is deleted; wards,
  rooms and beds are *deactivated*, which is refused while any bed below them is occupied. Bed search filters:
  `wardId`, `roomId`, `available` (true = nobody in it), `active` (default: active only).
* **Flow:** `POST /api/hospitalizations` (admit, creates the stay and its first bed assignment) ->
  `POST .../transfer` (optional, any number of times) -> `POST .../discharge`. Status: `ADMITTED -> DISCHARGED`
  (final). `admissionTime` / `dischargeTime` default to now and must not be in the future. Only `ADMITTED` stays can be
  updated or transferred. Hospitalizations and bed assignments are never deleted; `GET .../{id}/bed-history` lists
  every bed the patient occupied, oldest first.
* **Occupancy rules:** a bed holds at most one patient at a time and a patient has at most one `ADMITTED` stay.
  Violations return **409** ("The bed is already occupied" / "The patient already has an active hospitalization").
  Both are guaranteed by the database (exclusion constraint with open-ended ranges, partial unique index), so
  concurrent requests cannot both succeed. A transfer closes the old assignment and opens the new one at the same
  instant. The patient, doctor, bed, room and ward must exist (404) and be active (400).
* **Appointment link (optional):** `appointmentId` must belong to the same patient and can be linked to only one stay;
  the appointment's status is never changed automatically.
* **Privacy:** `diagnosis`, `dischargeNotes` and the transfer `reason` are returned as `null` to users whose roles are
  only RECEPTIONIST. Clinical text is never logged.
* Search filters: `patientId`, `doctorId`, `wardId`, `bedId` (the current bed, or the last bed once discharged),
  `status`, `admittedFrom`, `admittedTo`. Default sort is `admissionTime` descending.

> **After pulling this change** reset the local DB (`V1` gained the wards, rooms, beds, hospitalizations and
> bed_assignments tables): `docker compose down -v && docker compose up -d --wait`

### Appointments

* **Status flow:** `SCHEDULED -> COMPLETED | CANCELLED | NO_SHOW`. The other three statuses are final (changing them
  returns 400). Only `SCHEDULED` appointments can be updated/rescheduled.
* **Times:** ISO-8601 with offset (e.g. `2026-11-02T09:30:00+02:00`), free duration. `endTime` must be after `startTime`,
  the start must be in the future and the duration must be within `app.appointments.min-duration` /
  `max-duration` (defaults `5m` / `8h`, env `APPOINTMENT_MIN_DURATION` / `APPOINTMENT_MAX_DURATION`).
* **No double booking:** a doctor and a patient can each have only one `SCHEDULED` appointment at any moment.
  Back-to-back (one ends exactly when the next starts) is fine. Cancelled, completed and no-show appointments free the slot.
  A clash returns **409** saying whether the doctor or the patient is booked. This is also enforced by PostgreSQL
  exclusion constraints, so concurrent requests cannot both succeed (the loser gets the same 409).
* Patient and doctor must exist (404) and be active (400).
* Search filters: `doctorId`, `patientId`, `status`, `from`/`to` (appointments overlapping that window; encode `+` as `%2B`).
  Default sort is `startTime` ascending.

> **After pulling this change** reset the local DB (`V1` gained the `btree_gist` extension and the `appointments`
> table): `docker compose down -v && docker compose up -d --wait`

Doctor search filters: `name`, `licenseNumber`, `specialtyId`, `active` (omitted = active doctors only; `active=false`
lists soft-deleted ones). A doctor can optionally be linked to a login account via `userId` (must have the DOCTOR role
and not be linked to another doctor). Specialties are a seeded lookup table (see `V1`).

Errors always use one JSON shape: `timestamp`, `status`, `error`, `message`, `path` and (for 400s) `validationErrors[{field, message}]`.

Patient deletion is **soft** (`active=false`, `deleted_at` set); such patients disappear from every read/search.
Their national ID stays reserved (it is unique across all rows).

## Database & Flyway policy

* Flyway is the **only** owner of the schema; Hibernate runs with `ddl-auto=validate`.
* **Pre-deployment (now):** nothing is deployed yet, so `V1__init_schema.sql` is **edited in place** to match the entities –
  no new migration per small change. Apply changes by resetting the local DB:
  ```bash
  docker compose down -v && docker compose up -d --wait
  ```
* **After the first real deployment** migrations become strictly incremental and immutable: never edit `V1`/`V2` again,
  add `V3__...sql`, `V4__...sql`, ...
* The migration must always match the entities, otherwise startup fails validation.

> **After pulling schema changes** (e.g. the Doctor/Specialty tables added to `V1`) reset the local DB:
> `docker compose down -v && docker compose up -d --wait`

## Build & test

```bash
./mvnw clean verify
```

Includes unit tests (Patient service), a security matrix test (401/403/2xx per role) and a Testcontainers integration
test that boots the app on PostgreSQL – **Docker must be running**.

## Project layout

See [docs/CONVENTIONS.md](docs/CONVENTIONS.md) for the package structure and conventions to follow when adding modules.
