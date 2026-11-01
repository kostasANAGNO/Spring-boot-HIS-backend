# Project conventions – HIS backend

Spring Boot 3.5 / Java 21 / Maven / PostgreSQL. Base package `com.kostasanagno.his`. The **Patient** module is the
reference implementation: when adding Doctor, Appointment, ... copy its structure file by file.

## Layers (package `com.kostasanagno.his.`)

| Package | Content |
|---|---|
| `config` | Security, CORS, OpenAPI, JPA auditing, properties config |
| `controller` | Thin REST controllers; depend on service **interfaces** only; `@PreAuthorize` on every endpoint |
| `service` / `service.impl` | Interfaces / `@Service` impls with class-level `@Transactional`, `@Transactional(readOnly = true)` on reads |
| `repository` | Spring Data repos (+ `*Specifications` for search) |
| `entity` | JPA entities extending `BaseEntity` |
| `dto` | Request/response DTOs – never expose entities from controllers |
| `mapper` | MapStruct mappers |
| `exception` | Custom exceptions, `ApiError`, `GlobalExceptionHandler` |
| `security` | JWT service/filter, `UserDetailsService`, 401/403 handlers, `AccessRules` |

Rules: constructor injection only (`@RequiredArgsConstructor`, no field injection); controllers never touch repositories
or `*Impl` classes; use `@Slf4j` for logging.

## Lombok

* Entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` – **never `@Data`**. Extend `BaseEntity`
  (id, `createdAt`, `updatedAt`, proxy-safe id-based `equals`/`hashCode`). `toString` via
  `@ToString(onlyExplicitlyIncluded = true)` – never include lazy associations or passwords.
* DTOs: Java `record`s with Bean Validation annotations.

## DTOs & mappers

* One request record per write operation (create/update may share), one response record. List endpoints return
  `PageResponse<T>`; search filters go in a `*SearchCriteria` record and are applied through a `JpaSpecification`.
* Mappers: `@Mapper(config = CentralMapperConfig.class)` (Spring component model, **unmapped target = compile error**,
  builders disabled). Explicitly `@Mapping(ignore = true)` id/audit/server-managed fields.

## Lookup tables & links between modules

* Small lookup tables (e.g. `Specialty`) extend `IdentifiedEntity` (id only) instead of `BaseEntity`, are seeded in `V1`,
  and are exposed read-only (`GET` list).
* Reference other modules through `@ManyToOne(fetch = LAZY)` entities, but accept only the id in request DTOs
  (`specialtyId`, `userId`) and return a nested `{id, name}` response object (or just the id for optional links). The
  service resolves the link (404 if missing) and mapper `@Mapping(ignore = true)`s it.
* Avoid N+1 on list endpoints: put `@EntityGraph(attributePaths = ...)` on the repository `findAll(Specification, Pageable)`.
* Uniqueness checks (license number, user link, national ID) look at all rows incl. soft-deleted ones.
* A request that is well-formed but breaks a domain rule throws `BusinessRuleException` (400).
* Search criteria with an `active` flag default to active-only; `active=false` explicitly lists soft-deleted rows.

## Scheduling / overlap rules

* "Two things must not overlap" is enforced **in the database** with a GiST exclusion constraint using half-open
  `tstzrange(start, end)` ranges, partial (`WHERE status = 'SCHEDULED'`), plus `btree_gist` for the `=` on ids.
  See `appointments` in `V1`. The service checks first (through `*Specifications`, excluding the entity itself on
  update) only to give a friendly 409 message; the constraint is what guarantees correctness under concurrency.
* `GlobalExceptionHandler` maps `DataIntegrityViolationException` to 409 and recognises these constraints by name
  (`ex_appointments_*_no_overlap`). Name new constraints `ex_<table>_<what>` / `uq_` / `ck_` and add a branch there when
  the client needs a specific message.
* Status machines live on the enum (`AppointmentStatus.canTransitionTo`); the service has one private `transition(...)`
  used by every action endpoint (`POST /{id}/cancel|complete|no-show`). Terminal states reject changes with
  `BusinessRuleException` (400). Records that must keep history have no DELETE endpoint.
* Time based rules use the injectable `Clock` bean, never `Instant.now()`, so they can be unit tested. Timestamps are
  `Instant` / `TIMESTAMPTZ`, exposed as ISO-8601.
* Rules that depend on the caller (e.g. "only the assigned doctor") are checked in the service (it receives the
  username and an `admin` flag from the controller) and throw Spring's `AccessDeniedException`, which the handler turns
  into 403. Configurable limits go in a `@ConfigurationProperties` record (`AppointmentProperties`).
* Never log personal data (names, national IDs); log ids only.

## Movements, history and open-ended ranges

* Things that move (a patient between beds) are modelled as a **history table** (`bed_assignments`): one row per
  stay in a place with `start_time` and a nullable `end_time` (null = still there). Never update a row to "move" it:
  close the old row and open a new one at the same instant. History rows are never deleted.
* "At most one occupant" is an exclusion constraint where an open row runs to infinity:
  `tstzrange(start_time, COALESCE(end_time, 'infinity'::timestamptz))`. "At most one open row per owner" and
  "one active record per patient" are partial unique indexes (`WHERE end_time IS NULL` / `WHERE status = 'ADMITTED'`).
  Add the matching branch in `GlobalExceptionHandler` for each new constraint name.
* Soft structure (wards, rooms, beds) is deactivated, never deleted, and deactivation (or moving to another parent) is
  rejected while an open assignment exists below it. Creating something inside a parent requires an active parent.
* Fields that are only meaningful per page (like "is this bed occupied?") are computed with ONE extra query for the
  whole page (see `BedServiceImpl.search`, `HospitalizationServiceImpl.search`), never per row.

## Versioned (append-only) records

* Data that must never be lost (medical records) is a **header + versions** pair: the header holds identity and
  links (`clinical_notes`), every state of the content is an immutable row in the versions table with
  `version_number`, `change_reason` and `edited_by` (`clinical_note_versions`). The "current" content is the highest
  version; fetch it for a whole page with ONE query (`findLatestByNoteIds`), never per row.
* "Edit" means INSERT a new version. Enforce it in the database too: a `BEFORE UPDATE OR DELETE` trigger calling
  `forbid_row_change()` (see `V1`); entity columns of version rows are `updatable = false`. Version entities extend
  `IdentifiedEntity` (no `updated_at`) and carry their own `@CreatedDate`.
* Optimistic concurrency without a `@Version` column: the amend request carries `baseVersion`; the service compares it
  with the current version (409 with a clear message) and `UNIQUE (note_id, version_number)` is the safety net for a
  race, mapped to 409 in `GlobalExceptionHandler`.
* Records whose existence/content is clinical are blocked per role at the endpoint (`CLINICAL_STAFF`, `DOCTOR_ONLY`)
  instead of being redacted. Authorship comes from the doctor profile linked to the logged-in user, never from a
  request field. Never log or `toString` the content.

## Audit log

* Audit is declarative: put `@Audited(resource = ..., action = ..., idArg = "id")` on EVERY controller method that
  reads or changes patient-related data (new modules too). `AuditAspect` (package `audit`) does the rest; do not write
  audit code in controllers or services. Add the new module to `AuditResource` (and its `ck_audit_events_resource_type`
  CHECK in `V1`), and new actions to `AuditAction` (and `ck_audit_events_action`).
* Make the module's response records implement `Auditable` (`auditResourceId()`, `auditPatientId()`) so the aspect
  logs exactly the records a response disclosed (one event each, also for pages and `/me`). Sub-record or void
  endpoints (versions, history, delete) use `idArg`; add the owning-patient lookup for a new resource to
  `PatientIdResolver`.
* The audit aspect is the OUTERMOST advice (above `@PreAuthorize`), so refused requests are recorded as `DENIED`.
  The event is written in its own transaction (`REQUIRES_NEW`) before the response is returned: "no audit, no data".
* Store ids and metadata only. Never names, free text, query strings or search terms, and `audit_events` has no
  foreign keys so it outlives any data change. The table is append-only (trigger) and read only by ADMIN.

## Hiding clinical data by role

* The controller builds a `Caller` (username + roles) from the `Authentication` and passes it to services that return
  clinical data. `Caller.canSeeClinicalData()` is true for ADMIN/DOCTOR/NURSE; for anyone else the response record's
  `withoutClinicalData()` nulls the clinical fields. Apply it in the service on EVERY path that returns the record
  (create, read, search, history), and never log clinical fields (ids only).
* Services map to the API shape in one private `present(...)` method so redaction cannot be forgotten on a new path.
* State changes happen after all validation: check the transition first, mutate last.

## Errors

Throw `ResourceNotFoundException` (404) / `DuplicateResourceException` (409) from services. `GlobalExceptionHandler`
turns everything into `ApiError`; security 401/403 use the same shape. A new exception type = a new handler method there.

## Security

* Stateless JWT (`jjwt`), BCrypt, CSRF off, roles `ADMIN, DOCTOR, NURSE, RECEPTIONIST` (`entity.Role`).
* Everything under `/api/**` needs authentication except `POST /api/auth/login`. Use `@PreAuthorize` with the constants in
  `security/AccessRules` (add new ones there). Note: argument validation runs *before* `@PreAuthorize`; if a 403 must win
  over a 400, add a URL rule in `SecurityConfig` (done for `/api/auth/register`).
* JWT secret/expiry/issuer come from env/config (`app.jwt.*`). Never commit real secrets; the only default secrets
  live in `application-local.yml` and are marked dev-only. No `.env` files in git.
* Every new endpoint needs a case in the security matrix test (401/403/2xx).

## Database & Flyway (PRE-DEPLOYMENT POLICY)

* Flyway owns the schema; `spring.jpa.hibernate.ddl-auto=validate`. Migrations in `src/main/resources/db/migration`.
* **Not deployed yet:** keep a single baseline `V1__init_schema.sql` and **edit it in place** (add the new module's tables
  there). The local DB is reset with `docker compose down -v`. Do NOT add a migration per change.
  `V2__seed_admin_user.sql` is the dev-only admin seed.
* **After the first real deployment** (the user will say so): migrations become immutable and strictly incremental
  (`V3`, `V4`, ...). Never edit an applied migration then.
* The SQL must always match the entities exactly (types, nullability, enum columns as `VARCHAR` + CHECK).
* Soft-deleted rows: filter `active = true` in repository methods/specifications.

## Testing

`./mvnw clean verify` must pass (Docker needed for Testcontainers). Per module: service unit tests (Mockito), cases in
`ControllerSecurityTest` style (`@WebMvcTest` + imported `SecurityConfig`), and extend the Testcontainers integration test.

## Git

Small conventional commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`). Do not add `Co-Authored-By` trailers; commits
carry only the repository owner's identity. Never commit secrets, `.env` files or `target/`.
