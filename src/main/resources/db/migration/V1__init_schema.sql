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

-- ---------------------------------------------------------------------------------------------
-- Hospital structure: wards -> rooms -> beds (deactivated, never deleted)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE wards (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_wards_name UNIQUE (name)
);

INSERT INTO wards (name, created_at, updated_at) VALUES
    ('Cardiology', now(), now()),
    ('Internal Medicine', now(), now()),
    ('Surgery', now(), now()),
    ('Orthopedics', now(), now()),
    ('Pediatrics', now(), now()),
    ('Intensive Care', now(), now()),
    ('Maternity', now(), now());

CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    ward_id     BIGINT      NOT NULL REFERENCES wards (id),
    room_number VARCHAR(20) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rooms_ward_room_number UNIQUE (ward_id, room_number)
);

CREATE TABLE beds (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms (id),
    bed_number VARCHAR(20) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_beds_room_bed_number UNIQUE (room_id, bed_number)
);

-- ---------------------------------------------------------------------------------------------
-- Hospitalizations and their bed history (never deleted)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE hospitalizations (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT        NOT NULL REFERENCES patients (id),
    attending_doctor_id BIGINT        NOT NULL REFERENCES doctors (id),
    appointment_id      BIGINT REFERENCES appointments (id),
    status              VARCHAR(20)   NOT NULL,
    admission_time      TIMESTAMPTZ   NOT NULL,
    discharge_time      TIMESTAMPTZ,
    admission_reason    VARCHAR(500)  NOT NULL,
    diagnosis           VARCHAR(2000),
    discharge_notes     VARCHAR(2000),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_hospitalizations_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_hospitalizations_status CHECK (status IN ('ADMITTED', 'DISCHARGED')),
    CONSTRAINT ck_hospitalizations_discharge_after_admission
        CHECK (discharge_time IS NULL OR discharge_time > admission_time),
    CONSTRAINT ck_hospitalizations_discharged_iff_discharge_time
        CHECK ((status = 'DISCHARGED') = (discharge_time IS NOT NULL))
);

-- A patient has at most one active stay.
CREATE UNIQUE INDEX uq_hospitalizations_patient_admitted ON hospitalizations (patient_id) WHERE status = 'ADMITTED';

CREATE INDEX idx_hospitalizations_patient ON hospitalizations (patient_id);
CREATE INDEX idx_hospitalizations_doctor ON hospitalizations (attending_doctor_id);
CREATE INDEX idx_hospitalizations_status ON hospitalizations (status);
CREATE INDEX idx_hospitalizations_admission_time ON hospitalizations (admission_time);

CREATE TABLE bed_assignments (
    id                 BIGSERIAL PRIMARY KEY,
    hospitalization_id BIGINT       NOT NULL REFERENCES hospitalizations (id),
    bed_id             BIGINT       NOT NULL REFERENCES beds (id),
    start_time         TIMESTAMPTZ  NOT NULL,
    end_time           TIMESTAMPTZ,
    reason             VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_bed_assignments_time_order CHECK (end_time IS NULL OR end_time > start_time),
    -- A bed is never occupied twice at the same time. An open assignment (end_time IS NULL) runs to infinity;
    -- ranges are half-open, so a transfer (old ends exactly when the new one starts) is allowed.
    CONSTRAINT ex_bed_assignments_bed_no_overlap EXCLUDE USING gist (
        bed_id WITH =, tstzrange(start_time, COALESCE(end_time, 'infinity'::timestamptz)) WITH &&)
);

-- A hospitalization occupies at most one bed at a time.
CREATE UNIQUE INDEX uq_bed_assignments_hospitalization_open ON bed_assignments (hospitalization_id) WHERE end_time IS NULL;

CREATE INDEX idx_bed_assignments_hospitalization ON bed_assignments (hospitalization_id, start_time);
CREATE INDEX idx_bed_assignments_bed ON bed_assignments (bed_id);

-- ---------------------------------------------------------------------------------------------
-- Medical records: clinical notes. A note is a header plus an append-only list of versions;
-- an amendment adds a new version, nothing is ever updated or deleted.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE clinical_notes (
    id                 BIGSERIAL PRIMARY KEY,
    patient_id         BIGINT      NOT NULL REFERENCES patients (id),
    author_doctor_id   BIGINT      NOT NULL REFERENCES doctors (id),
    appointment_id     BIGINT REFERENCES appointments (id),
    hospitalization_id BIGINT REFERENCES hospitalizations (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_clinical_notes_single_link CHECK (appointment_id IS NULL OR hospitalization_id IS NULL)
);

CREATE INDEX idx_clinical_notes_patient ON clinical_notes (patient_id, created_at);
CREATE INDEX idx_clinical_notes_author ON clinical_notes (author_doctor_id);
CREATE INDEX idx_clinical_notes_appointment ON clinical_notes (appointment_id);
CREATE INDEX idx_clinical_notes_hospitalization ON clinical_notes (hospitalization_id);

CREATE TABLE clinical_note_versions (
    id                BIGSERIAL PRIMARY KEY,
    note_id           BIGINT      NOT NULL REFERENCES clinical_notes (id),
    version_number    INTEGER     NOT NULL,
    history           VARCHAR(4000),
    examination       VARCHAR(4000),
    assessment        VARCHAR(4000),
    plan              VARCHAR(4000),
    diagnosis_code    VARCHAR(10),
    change_reason     VARCHAR(500),
    edited_by_user_id BIGINT      NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    -- two concurrent amendments cannot both become version N+1
    CONSTRAINT uq_clinical_note_versions_note_version UNIQUE (note_id, version_number),
    CONSTRAINT ck_clinical_note_versions_version_positive CHECK (version_number >= 1),
    CONSTRAINT ck_clinical_note_versions_has_content
        CHECK (COALESCE(history, examination, assessment, plan, diagnosis_code) IS NOT NULL)
);

CREATE INDEX idx_clinical_note_versions_diagnosis ON clinical_note_versions (diagnosis_code);

-- Medical history is never rewritten or removed: the database itself refuses UPDATE/DELETE on versions
-- and DELETE on notes, whatever the application does.
CREATE FUNCTION forbid_row_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed (append-only record)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_clinical_note_versions_append_only
    BEFORE UPDATE OR DELETE ON clinical_note_versions
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

CREATE TRIGGER trg_clinical_notes_no_delete
    BEFORE DELETE ON clinical_notes
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- ---------------------------------------------------------------------------------------------
-- Audit log: who accessed or changed which patient-related record, when, and whether it was allowed.
-- Append-only. Holds ids and metadata only, never names, notes or any clinical text.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_events (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    roles         VARCHAR(100) NOT NULL,
    action        VARCHAR(20)  NOT NULL,
    resource_type VARCHAR(30)  NOT NULL,
    resource_id   BIGINT,
    patient_id    BIGINT,
    outcome       VARCHAR(10)  NOT NULL,
    http_method   VARCHAR(10)  NOT NULL,
    path          VARCHAR(255) NOT NULL,
    ip_address    VARCHAR(45),
    CONSTRAINT ck_audit_events_action CHECK (action IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'CANCEL', 'COMPLETE',
        'NO_SHOW', 'ADMIT', 'TRANSFER', 'DISCHARGE', 'AMEND', 'ISSUE', 'PAY')),
    CONSTRAINT ck_audit_events_resource_type CHECK (resource_type IN ('PATIENT', 'APPOINTMENT', 'HOSPITALIZATION',
        'CLINICAL_NOTE', 'INVOICE', 'AUDIT_LOG')),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED'))
);

-- "who looked at patient X" and "what did user Y do" are the two questions this table exists to answer
CREATE INDEX idx_audit_events_patient ON audit_events (patient_id, occurred_at);
CREATE INDEX idx_audit_events_username ON audit_events (username, occurred_at);
CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- ---------------------------------------------------------------------------------------------
-- Billing: price list (charge items), invoices with frozen line snapshots, and append-only payments.
-- Amounts are NUMERIC(12,2) in a single currency (EUR); VAT is a percentage per charge item.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE charge_items (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30)    NOT NULL,
    name       VARCHAR(150)   NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    vat_rate   NUMERIC(5, 2)  NOT NULL,
    active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_charge_items_code UNIQUE (code),
    CONSTRAINT ck_charge_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_charge_items_vat CHECK (vat_rate >= 0 AND vat_rate <= 100)
);

-- Starter price list with PLACEHOLDER prices: replace them through the API. BED-DAY is used to invoice stays.
INSERT INTO charge_items (code, name, unit_price, vat_rate, created_at, updated_at) VALUES
    ('CONSULT', 'General consultation', 50.00, 0.00, now(), now()),
    ('BED-DAY', 'Hospital bed day', 150.00, 0.00, now(), now()),
    ('ECG', 'Electrocardiogram', 30.00, 0.00, now(), now()),
    ('XRAY', 'X-ray', 40.00, 0.00, now(), now());

CREATE SEQUENCE invoice_number_seq;

CREATE TABLE invoices (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT         NOT NULL REFERENCES patients (id),
    appointment_id      BIGINT REFERENCES appointments (id),
    hospitalization_id  BIGINT REFERENCES hospitalizations (id),
    status              VARCHAR(20)    NOT NULL,
    invoice_number      VARCHAR(30),
    issued_at           TIMESTAMPTZ,
    total_net           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_vat           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_gross         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    paid_amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_invoices_single_link CHECK (appointment_id IS NULL OR hospitalization_id IS NULL),
    CONSTRAINT ck_invoices_totals CHECK (total_net >= 0 AND total_vat >= 0 AND total_gross = total_net + total_vat),
    -- an invoice can never be over-paid, even if two payments race
    CONSTRAINT ck_invoices_paid_range CHECK (paid_amount >= 0 AND paid_amount <= total_gross),
    CONSTRAINT ck_invoices_paid_iff_fully_paid CHECK ((status = 'PAID') = (total_gross > 0 AND paid_amount = total_gross)),
    CONSTRAINT ck_invoices_numbered_when_issued
        CHECK (status NOT IN ('ISSUED', 'PAID') OR (invoice_number IS NOT NULL AND issued_at IS NOT NULL AND total_gross > 0)),
    CONSTRAINT ck_invoices_cancelled CHECK (status <> 'CANCELLED' OR (paid_amount = 0 AND cancellation_reason IS NOT NULL))
);

-- A visit or a stay is invoiced at most once (a cancelled invoice frees it again).
CREATE UNIQUE INDEX uq_invoices_active_appointment ON invoices (appointment_id)
    WHERE appointment_id IS NOT NULL AND status <> 'CANCELLED';
CREATE UNIQUE INDEX uq_invoices_active_hospitalization ON invoices (hospitalization_id)
    WHERE hospitalization_id IS NOT NULL AND status <> 'CANCELLED';

CREATE INDEX idx_invoices_patient ON invoices (patient_id, created_at);
CREATE INDEX idx_invoices_status ON invoices (status);
CREATE INDEX idx_invoices_issued_at ON invoices (issued_at);

-- Lines are snapshots (code, description, price, VAT rate copied from the price list when added), so later price
-- changes never alter an invoice.
CREATE TABLE invoice_lines (
    id             BIGSERIAL PRIMARY KEY,
    invoice_id     BIGINT         NOT NULL REFERENCES invoices (id),
    charge_item_id BIGINT         NOT NULL REFERENCES charge_items (id),
    code           VARCHAR(30)    NOT NULL,
    description    VARCHAR(150)   NOT NULL,
    quantity       INTEGER        NOT NULL,
    unit_price     NUMERIC(12, 2) NOT NULL,
    vat_rate       NUMERIC(5, 2)  NOT NULL,
    net_amount     NUMERIC(12, 2) NOT NULL,
    vat_amount     NUMERIC(12, 2) NOT NULL,
    gross_amount   NUMERIC(12, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_invoice_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_lines_price CHECK (unit_price > 0),
    CONSTRAINT ck_invoice_lines_vat CHECK (vat_rate >= 0 AND vat_rate <= 100),
    CONSTRAINT ck_invoice_lines_net CHECK (net_amount = quantity * unit_price),
    CONSTRAINT ck_invoice_lines_gross CHECK (vat_amount >= 0 AND gross_amount = net_amount + vat_amount)
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines (invoice_id);

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_id          BIGINT         NOT NULL REFERENCES invoices (id),
    amount              NUMERIC(12, 2) NOT NULL,
    method              VARCHAR(20)    NOT NULL,
    paid_at             TIMESTAMPTZ    NOT NULL,
    reference           VARCHAR(100),
    recorded_by_user_id BIGINT         NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD', 'BANK_TRANSFER'))
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id, paid_at);

-- Financial records are never rewritten: payments and invoices cannot be changed/deleted behind the application's back.
CREATE TRIGGER trg_payments_append_only
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

CREATE TRIGGER trg_invoices_no_delete
    BEFORE DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

CREATE FUNCTION forbid_invoice_line_change_unless_draft() RETURNS trigger AS $$
DECLARE
    invoice_status VARCHAR(20);
BEGIN
    SELECT status INTO invoice_status FROM invoices WHERE id = COALESCE(NEW.invoice_id, OLD.invoice_id);
    IF invoice_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'invoice lines can only be changed while the invoice is a DRAFT';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoice_lines_draft_only
    BEFORE INSERT OR UPDATE OR DELETE ON invoice_lines
    FOR EACH ROW EXECUTE FUNCTION forbid_invoice_line_change_unless_draft();

CREATE FUNCTION guard_issued_invoice() RETURNS trigger AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND (NEW.patient_id <> OLD.patient_id
            OR NEW.total_net <> OLD.total_net OR NEW.total_vat <> OLD.total_vat OR NEW.total_gross <> OLD.total_gross
            OR NEW.invoice_number IS DISTINCT FROM OLD.invoice_number
            OR NEW.appointment_id IS DISTINCT FROM OLD.appointment_id
            OR NEW.hospitalization_id IS DISTINCT FROM OLD.hospitalization_id) THEN
        RAISE EXCEPTION 'an issued invoice cannot change its amounts, number, patient or links';
    END IF;
    IF OLD.status IN ('PAID', 'CANCELLED') AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'a % invoice is final', OLD.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoices_guard_issued
    BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION guard_issued_invoice();
