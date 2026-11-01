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

| Billing endpoint | Roles |
|---|---|
| `GET /api/charge-items[/{id}]` (price list) | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `POST/PUT /api/charge-items`, `PATCH /api/charge-items/{id}/deactivate` | ADMIN |
| `GET /api/invoices`, `GET /api/invoices/{id}`, `GET /api/invoices/{id}/payments` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `POST /api/invoices`, `POST /api/invoices/from-hospitalization/{id}`, `POST .../{id}/lines`, `DELETE .../{id}/lines/{lineId}`, `POST .../{id}/issue`, `POST .../{id}/payments` | ADMIN, RECEPTIONIST |
| `POST /api/invoices/{id}/cancel` | ADMIN |

### Billing

* **Price list:** `charge_items` have a `code`, `name`, `unitPrice` (EUR, without VAT) and a `vatRate` percentage. A
  starter list is seeded (`CONSULT`, `BED-DAY`, `ECG`, `XRAY`) with **placeholder prices**: change them through the
  API. Entries are deactivated, never deleted. A price change never affects existing invoices.
* **Invoice flow:** `DRAFT` (add or remove lines) -> `ISSUED` (gets its number `INV-<year>-<6 digits>`, frozen) ->
  `PAID` (when the payments add up to the total). A `DRAFT` or an unpaid `ISSUED` invoice can be `CANCELLED` (ADMIN, with a
  reason); an invoice with payments cannot (refunds and credit notes are not implemented). Invoices and payments are
  never deleted.
* **Lines are snapshots:** the code, description, price and VAT rate are copied from the price list when the line is
  added. Per line: `net = quantity x price`, `VAT = net x rate / 100` rounded half-up to cents, `gross = net + VAT`; the
  invoice totals are the sums of its lines. Amounts use exactly two decimals.
* **Payments** (`CASH`, `CARD`, `BANK_TRANSFER`, optional reference) are accepted only on `ISSUED` invoices, can be
  partial, and can never exceed what is still due. The invoice row is locked while it changes, so concurrent payments
  cannot over-pay it (a database constraint is the safety net). Payments are append-only.
* **Invoicing a stay:** `POST /api/invoices/from-hospitalization/{id}` creates a `DRAFT` for a *discharged*
  hospitalization with one `BED-DAY` unit per started 24 hours of the stay (minimum one). A visit or a stay can have only
  one active invoice (a cancelled one frees it); an invoice can also be linked to one appointment.
* **Database protection:** lines of a non-draft invoice, the amounts/number/patient of an issued invoice, payments and
  invoice rows cannot be changed or deleted, even with raw SQL.
* This is an internal billing record, **not** a fiscal/tax document: there is no gap-free legal numbering or
  integration with a tax authority. Invoice activity is part of the audit log.

> **After pulling this change** reset the local DB (`V1` gained the billing tables, triggers and the invoice number
> sequence): `docker compose down -v && docker compose up -d --wait`

| Audit log endpoint | Roles |
|---|---|
| `GET /api/audit-events` | ADMIN (read-only; nothing can change or delete events) |

### Audit log

Every access to or change of patient-related data is recorded: **who** (username, roles), **what** (action and record),
**which patient**, **when**, from which IP, and the **outcome** (`SUCCESS`, `DENIED` when the role was not allowed,
`FAILED` for e.g. a missing record). It covers patients, appointments, hospitalizations, clinical notes and invoices (reads,
searches, creates, updates and their actions such as cancel, discharge or amend) and the reading of the audit log itself.

* A response that discloses several records (a search page, `/me`) writes **one event per record**, so
  `GET /api/audit-events?patientId=5` answers "who looked at patient 5". An empty search leaves a single summary event.
* Filters: `username`, `ipAddress`, `patientId`, `resourceType` (`PATIENT`, `APPOINTMENT`, `HOSPITALIZATION`, `CLINICAL_NOTE`,
  `INVOICE`, `USER`, `AUDIT_LOG`), `resourceId`, `action`, `outcome`, `from`, `to`. Newest first.
* **Privacy:** only ids and request metadata are stored (path without the query string): no names, notes, diagnoses or
  search terms. The table is append-only: the database refuses `UPDATE` and `DELETE`.
* **No audit, no data:** the event is written before the response is sent. If it cannot be stored, the request fails
  (for writes the change itself is already committed). Refused requests are recorded even though they return 403.
* **Logins and user creation:** every login attempt is recorded (`action=LOGIN`, `resourceType=USER`): `SUCCESS` with the
  user's roles, or `DENIED` with a short `detail` code (`BAD_CREDENTIALS`, `ACCOUNT_DISABLED`, `ACCOUNT_LOCKED`, ...).
  A failed attempt for a name that is **not an account** is stored as username `unknown`, so a password typed into the
  username field never lands in the log; passwords are never stored. A successful login is written before the token is
  returned. Creating a user (`POST /api/auth/register`) is recorded too, including refusals for non-admins. Useful
  queries: `?action=LOGIN&outcome=DENIED` (failed logins), add `&ipAddress=...` to follow one address.
  Requests that only carry a token are not logins and are not recorded.
* Not audited (yet): doctors, wards/rooms/beds, the price list. There is no lock-out after repeated failed logins. The client IP is the direct connection address; behind a
  proxy configure `server.forward-headers-strategy` so it reflects the real client.

> **After pulling this change** reset the local DB (`V1` gained the `audit_events` table):
> `docker compose down -v && docker compose up -d --wait`

| Clinical notes endpoint | Roles |
|---|---|
| `POST /api/clinical-notes` | DOCTOR (the author is the doctor profile linked to the login) |
| `GET /api/clinical-notes`, `GET /api/clinical-notes/{id}`, `GET /api/clinical-notes/{id}/versions` | ADMIN, DOCTOR, NURSE |
| `GET /api/clinical-notes/me` (notes written by the logged-in doctor) | DOCTOR |
| `PUT /api/clinical-notes/{id}` (amend = add a new version) | the author DOCTOR, or ADMIN |

Receptionists get **403** on every clinical-notes endpoint.

### Medical records (clinical notes)

* **What a note is:** free text sections (`history`, `examination`, `assessment`, `plan`, at least one required) plus an
  optional ICD-10 `diagnosisCode` such as `I20.9` (validated and stored upper-case). It belongs to a patient and can be
  linked to **one** appointment *or* one hospitalization of that same patient. Linking never changes the appointment or
  hospitalization.
* **Nothing is lost:** notes are never deleted or overwritten. An amendment (`PUT`) adds a new **version** holding the
  full new content plus a required `changeReason`; `GET .../versions` returns every version, oldest first, with who
  made it and when. The database enforces this: it refuses `UPDATE`/`DELETE` on versions and `DELETE` on notes.
* **Concurrent edits:** an amendment must state the `baseVersion` it was based on. If someone amended in the
  meantime the answer is **409** and nothing is saved (also guaranteed by a unique `(note, version)` constraint).
* Only the author or an ADMIN can amend. Search filters: `patientId`, `authorDoctorId`, `appointmentId`,
  `hospitalizationId`, `diagnosisCode` (prefix of the *current* version's code), `createdFrom`, `createdTo`.
  Default sort is newest first. Note text is never logged.

> **After pulling this change** reset the local DB (`V1` gained the clinical notes tables and the append-only
> triggers): `docker compose down -v && docker compose up -d --wait`

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
