-- Baseline schema. PRE-DEPLOYMENT POLICY: edited in place while developing (reset DB with
-- `docker compose down -v`). After the first real deployment this file becomes immutable and
-- every change goes into a new V2, V3, ... migration. Must always match the JPA entities.

-- needed by the appointment exclusion constraints (equality on ids inside a GiST index)
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(100) NOT NULL,
    full_name  VARCHAR(150) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST'))
);

CREATE TABLE patients (
    id                      BIGSERIAL PRIMARY KEY,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    date_of_birth           DATE         NOT NULL,
    gender                  VARCHAR(20)  NOT NULL,
    national_id             VARCHAR(20)  NOT NULL,
    phone                   VARCHAR(30),
    email                   VARCHAR(255),
    address                 VARCHAR(255),
    emergency_contact_name  VARCHAR(150),
    emergency_contact_phone VARCHAR(30),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_patients_national_id UNIQUE (national_id),
    CONSTRAINT ck_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX idx_patients_last_name ON patients (lower(last_name));
CREATE INDEX idx_patients_first_name ON patients (lower(first_name));
CREATE INDEX idx_patients_phone ON patients (phone);
CREATE INDEX idx_patients_email ON patients (lower(email));
CREATE INDEX idx_patients_active ON patients (active);

CREATE TABLE specialties (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_specialties_name UNIQUE (name)
);

INSERT INTO specialties (name) VALUES
    ('Cardiology'),
    ('Neurology'),
    ('Orthopedics'),
    ('Pediatrics'),
    ('General Medicine'),
    ('Surgery'),
    ('Radiology'),
    ('Emergency Medicine'),
    ('Obstetrics and Gynecology'),
    ('Dermatology');

CREATE TABLE doctors (
    id             BIGSERIAL PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    license_number VARCHAR(30)  NOT NULL,
    specialty_id   BIGINT       NOT NULL REFERENCES specialties (id),
    phone          VARCHAR(30),
    email          VARCHAR(255),
    user_id        BIGINT REFERENCES users (id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_doctors_license_number UNIQUE (license_number),
    CONSTRAINT uq_doctors_user_id UNIQUE (user_id)
);

CREATE INDEX idx_doctors_last_name ON doctors (lower(last_name));
CREATE INDEX idx_doctors_specialty_id ON doctors (specialty_id);

CREATE TABLE appointments (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT       NOT NULL REFERENCES patients (id),
    doctor_id           BIGINT       NOT NULL REFERENCES doctors (id),
    start_time          TIMESTAMPTZ  NOT NULL,
    end_time            TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    reason              VARCHAR(255) NOT NULL,
    notes               VARCHAR(2000),
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_appointments_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_appointments_time_order CHECK (end_time > start_time),
    -- No double booking, enforced by the database so concurrent requests cannot both succeed.
    -- tstzrange is half-open [start, end): back-to-back appointments are allowed.
    -- Only SCHEDULED appointments occupy time; cancelled/completed/no-show ones free the slot.
    CONSTRAINT ex_appointments_doctor_no_overlap EXCLUDE USING gist (
        doctor_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (status = 'SCHEDULED'),
    CONSTRAINT ex_appointments_patient_no_overlap EXCLUDE USING gist (
        patient_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (status = 'SCHEDULED')
);

CREATE INDEX idx_appointments_doctor_start ON appointments (doctor_id, start_time);
CREATE INDEX idx_appointments_patient_start ON appointments (patient_id, start_time);
CREATE INDEX idx_appointments_status ON appointments (status);
