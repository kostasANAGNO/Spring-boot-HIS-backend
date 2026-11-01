package com.kostasanagno.his;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole application against a real PostgreSQL. Proves that the Flyway migrations apply and
 * that Hibernate's {@code ddl-auto=validate} accepts the resulting schema, then walks the main flow:
 * seeded admin login -> patient CRUD -> soft delete.
 */
@Testcontainers
@SpringBootTest(properties = "app.jwt.secret=integration-test-secret-integration-test-secret")
@AutoConfigureMockMvc
class HisApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigrationsAreApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void seededAdminCanRunPatientCrudFlow() throws Exception {
        String token = login("admin", "Admin123!");

        // create
        String body = """
                {"firstName":"Maria","lastName":"Papadopoulou","dateOfBirth":"1990-05-17","gender":"FEMALE",
                 "nationalId":"17059012345","phone":"+30 210 1234567","email":"maria@example.com"}""";
        String created = mockMvc.perform(post("/api/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // duplicate national ID -> 409
        mockMvc.perform(post("/api/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // read + search + sort
        mockMvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Papadopoulou"));
        mockMvc.perform(get("/api/patients").param("name", "maria papad").param("sort", "lastName,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/patients").param("sort", "nope").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // update
        mockMvc.perform(put("/api/patients/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("Maria", "Marianna")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Marianna"))
                .andExpect(jsonPath("$.updatedAt").exists());

        // soft delete -> hidden from reads and searches, but the row stays
        mockMvc.perform(delete("/api/patients/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/patients").param("nationalId", "17059012345")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(0));
        Boolean active = jdbc.queryForObject("SELECT active FROM patients WHERE id = ?", Boolean.class, id);
        assertThat(active).isFalse();
    }

    @Test
    void registeredReceptionistCanWorkWithPatientsButNotDelete() throws Exception {
        String adminToken = login("admin", "Admin123!");
        mockMvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reception1","email":"r1@his.local","password":"Sup3rSecret!",
                                 "fullName":"Rita Reception","roles":["RECEPTIONIST"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        String token = login("reception1", "Sup3rSecret!");
        mockMvc.perform(get("/api/patients").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCanBeCreatedLinkedSearchedAndSoftDeleted() throws Exception {
        String admin = login("admin", "Admin123!");
        String auth = "Bearer " + admin;

        // seeded specialties
        String specialties = mockMvc.perform(get("/api/specialties").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        long cardiologyId = objectMapper.readTree(specialties).get(0).get("id").asLong();
        assertThat(objectMapper.readTree(specialties).get(0).get("name").asText()).isEqualTo("Cardiology");

        // a login account with the DOCTOR role to link
        String userJson = mockMvc.perform(post("/api/auth/register").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dr.house","email":"house@his.local","password":"Sup3rSecret!",
                                 "fullName":"Gregory House","roles":["DOCTOR"]}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(userJson).get("id").asLong();

        String body = """
                {"firstName":"Gregory","lastName":"House","licenseNumber":"MD-1001","specialtyId":%d,"userId":%d}"""
                .formatted(cardiologyId, userId);
        String created = mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.specialty.name").value("Cardiology"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // duplicate license, unknown specialty, user already linked
        mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"userId\":" + userId, "\"userId\":null")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("MD-1001", "MD-1002").replace("\"specialtyId\":" + cardiologyId, "\"specialtyId\":9999")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("MD-1001", "MD-1003")))
                .andExpect(status().isConflict());
        // the admin account has no DOCTOR role
        mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("MD-1001", "MD-1004").replace("\"userId\":" + userId, "\"userId\":1")))
                .andExpect(status().isBadRequest());

        // search by name / specialty with sorting; /me for the linked doctor
        mockMvc.perform(get("/api/doctors").param("name", "hou").param("specialtyId", String.valueOf(cardiologyId))
                        .param("sort", "specialty.name,asc").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].licenseNumber").value("MD-1001"));
        String doctorToken = login("dr.house", "Sup3rSecret!");
        mockMvc.perform(get("/api/doctors/me").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(put("/api/doctors/1").header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        // update
        mockMvc.perform(put("/api/doctors/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("Gregory", "Greg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Greg"));

        // soft delete: hidden by default, visible with active=false, license stays reserved
        mockMvc.perform(delete("/api/doctors/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/doctors/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/doctors/me").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/doctors").param("licenseNumber", "MD-1001").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/doctors").param("active", "false").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"userId\":" + userId, "\"userId\":null")))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejectedAndAnonymousAccessBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void appointmentFlowWithOverlapRulesRescheduleCancelAndComplete() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long patientA = createPatient(auth, "AP000000001");
        long patientB = createPatient(auth, "AP000000002");
        long doctor = createDoctor(auth, "AP-DOC-1");
        Instant start = futureHour(10);

        // book, then every flavour of overlap on the same doctor / same patient -> 409 with a clear message
        long first = bookOk(auth, patientA, doctor, start, start.plus(30, ChronoUnit.MINUTES));
        book(auth, patientB, doctor, start.plus(10, ChronoUnit.MINUTES), start.plus(40, ChronoUnit.MINUTES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("doctor")));
        long otherDoctor = createDoctor(auth, "AP-DOC-2");
        book(auth, patientA, otherDoctor, start.plus(10, ChronoUnit.MINUTES), start.plus(20, ChronoUnit.MINUTES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("patient")));

        // back-to-back is fine
        long second = bookOk(auth, patientB, doctor, start.plus(30, ChronoUnit.MINUTES), start.plus(60, ChronoUnit.MINUTES));

        // reschedule the first one (overlapping its own old slot is fine, overlapping the second is not)
        putAppointment(auth, first, doctor, start.plus(5, ChronoUnit.MINUTES), start.plus(25, ChronoUnit.MINUTES), "Moved")
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Moved"));
        putAppointment(auth, first, doctor, start.plus(20, ChronoUnit.MINUTES), start.plus(40, ChronoUnit.MINUTES), "Clash")
                .andExpect(status().isConflict());

        // cancel frees the slot, which can then be booked again; terminal states reject further changes
        mockMvc.perform(post("/api/appointments/" + first + "/cancel").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancellationReason\":\"Patient called\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        long rebooked = bookOk(auth, patientA, doctor, start, start.plus(30, ChronoUnit.MINUTES));
        mockMvc.perform(post("/api/appointments/" + first + "/no-show").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        putAppointment(auth, first, doctor, start.plus(120, ChronoUnit.MINUTES), start.plus(150, ChronoUnit.MINUTES), "Late")
                .andExpect(status().isBadRequest());

        // complete (admin), then search with filters and a window
        mockMvc.perform(post("/api/appointments/" + second + "/complete").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/appointments").param("doctorId", String.valueOf(doctor))
                        .param("status", "SCHEDULED").param("from", start.toString())
                        .param("to", start.plus(2, ChronoUnit.HOURS).toString()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(rebooked))
                .andExpect(jsonPath("$.content[0].doctor.specialty").value("Cardiology"));
        mockMvc.perform(get("/api/appointments/" + rebooked).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patient.id").value(patientA));
        mockMvc.perform(delete("/api/appointments/" + rebooked).header("Authorization", auth))
                .andExpect(status().is4xxClientError());

        // validation rules
        book(auth, patientA, doctor, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES)).andExpect(status().isBadRequest());
        book(auth, 999999L, doctor, futureHour(14), futureHour(14).plus(30, ChronoUnit.MINUTES))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAssignedDoctorCanCompleteAndSeesOwnAppointments() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "AP000000003");
        for (String name : List.of("dr.assigned", "dr.other")) {
            mockMvc.perform(post("/api/auth/register").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","email":"%s@his.local","password":"Sup3rSecret!",
                                     "fullName":"Dr X","roles":["DOCTOR"]}""".formatted(name, name)))
                    .andExpect(status().isCreated());
        }
        long assignedUser = userId("dr.assigned");
        long otherUser = userId("dr.other");
        long assigned = createDoctorLinked(admin, "AP-DOC-3", assignedUser);
        createDoctorLinked(admin, "AP-DOC-4", otherUser);
        Instant start = futureHour(16);
        long appointment = bookOk(admin, patient, assigned, start, start.plus(30, ChronoUnit.MINUTES));

        String otherToken = "Bearer " + login("dr.other", "Sup3rSecret!");
        mockMvc.perform(post("/api/appointments/" + appointment + "/complete").header("Authorization", otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/appointments/me").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        String assignedToken = "Bearer " + login("dr.assigned", "Sup3rSecret!");
        mockMvc.perform(get("/api/appointments/me").header("Authorization", assignedToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(post("/api/appointments/" + appointment + "/complete").header("Authorization", assignedToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/appointments/me").header("Authorization", admin)).andExpect(status().isForbidden());
    }

    @Test
    void databaseExclusionConstraintsRejectOverlapsButNotBackToBackOrCancelled() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long patientA = createPatient(auth, "AP000000004");
        long patientB = createPatient(auth, "AP000000005");
        long doctor = createDoctor(auth, "AP-DOC-5");
        Instant start = futureHour(18);
        Instant mid = start.plus(30, ChronoUnit.MINUTES);
        Instant end = start.plus(60, ChronoUnit.MINUTES);

        insertAppointment(patientA, doctor, start, mid, "SCHEDULED");
        insertAppointment(patientB, doctor, mid, end, "SCHEDULED");              // back-to-back: allowed
        insertAppointment(patientB, doctor, start, end, "CANCELLED");            // not SCHEDULED: ignored

        assertThatThrownBy(() -> insertAppointment(patientB, doctor, start.plus(10, ChronoUnit.MINUTES),
                start.plus(20, ChronoUnit.MINUTES), "SCHEDULED"))                // same doctor
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_appointments_doctor_no_overlap");
        long otherDoctor = createDoctor(auth, "AP-DOC-6");
        assertThatThrownBy(() -> insertAppointment(patientA, otherDoctor, start.plus(10, ChronoUnit.MINUTES),
                start.plus(20, ChronoUnit.MINUTES), "SCHEDULED"))                // same patient
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_appointments_patient_no_overlap");
        assertThatThrownBy(() -> insertAppointment(patientA, otherDoctor, end.plus(1, ChronoUnit.HOURS), end,
                "SCHEDULED"))                                                    // end before start
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_appointments_time_order");
    }

    @Test
    void concurrentBookingsOfTheSameDoctorAndTimeYieldExactlyOneSuccess() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long doctor = createDoctor(auth, "AP-DOC-7");
        int requests = 6;
        List<Long> patients = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            patients.add(createPatient(auth, "AP00000010" + i));
        }
        Instant start = futureHour(20);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long patient : patients) {
                Callable<Integer> booking = () -> {
                    ready.countDown();
                    go.await();
                    return book(auth, patient, doctor, start, end).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(booking));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }
        Integer scheduled = jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE doctor_id = ? AND status = 'SCHEDULED'", Integer.class, doctor);
        assertThat(scheduled).isEqualTo(1);
    }

    // ---- helpers for the appointment tests -----------------------------------------------------

    private static Instant futureHour(int hourOfDay) {
        return Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(hourOfDay, ChronoUnit.HOURS);
    }

    private long createPatient(String auth, String nationalId) throws Exception {
        String body = """
                {"firstName":"Test","lastName":"Patient","dateOfBirth":"1990-01-01","gender":"OTHER","nationalId":"%s"}"""
                .formatted(nationalId);
        return idOf(mockMvc.perform(post("/api/patients").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
    }

    private long createDoctor(String auth, String license) throws Exception {
        return createDoctorLinked(auth, license, null);
    }

    private long createDoctorLinked(String auth, String license, Long userId) throws Exception {
        String body = """
                {"firstName":"Test","lastName":"Doctor","licenseNumber":"%s","specialtyId":1,"userId":%s}"""
                .formatted(license, userId);
        return idOf(mockMvc.perform(post("/api/doctors").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private ResultActions book(String auth, long patientId, long doctorId, Instant start, Instant end) throws Exception {
        String body = """
                {"patientId":%d,"doctorId":%d,"startTime":"%s","endTime":"%s","reason":"Checkup"}"""
                .formatted(patientId, doctorId, start, end);
        return mockMvc.perform(post("/api/appointments").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long bookOk(String auth, long patientId, long doctorId, Instant start, Instant end) throws Exception {
        return idOf(book(auth, patientId, doctorId, start, end).andExpect(status().isCreated()));
    }

    private ResultActions putAppointment(String auth, long id, long doctorId, Instant start, Instant end, String reason)
            throws Exception {
        String body = """
                {"doctorId":%d,"startTime":"%s","endTime":"%s","reason":"%s"}""".formatted(doctorId, start, end, reason);
        return mockMvc.perform(put("/api/appointments/" + id).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void insertAppointment(long patientId, long doctorId, Instant start, Instant end, String status) {
        jdbc.update("""
                INSERT INTO appointments (patient_id, doctor_id, start_time, end_time, status, reason, created_at, updated_at)
                VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?, 'Checkup', now(), now())""",
                patientId, doctorId, start.toString(), end.toString(), status);
    }

    // ---- hospitalizations ----------------------------------------------------------------------

    @Test
    void wardsAreSeededAndStructureCanBeManaged() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");

        mockMvc.perform(get("/api/wards").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
        long ward = wardId("Surgery");
        long room = createRoom(auth, ward, "S-100");
        createBed(auth, room, "A");
        mockMvc.perform(post("/api/beds").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + room + ",\"bedNumber\":\"a\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/rooms").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wardId\":" + ward + ",\"roomNumber\":\"S-100\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/beds").param("roomId", String.valueOf(room)).header("Authorization", auth))
                .andExpect(jsonPath("$.content[0].wardName").value("Main Wing"))
                .andExpect(jsonPath("$.content[0].clinicName").value("Surgery"))
                .andExpect(jsonPath("$.content[0].roomNumber").value("S-100"))
                .andExpect(jsonPath("$.content[0].occupied").value(false));
    }

    @Test
    void hospitalizationFlowAdmitTransferDischargeWithOccupancyRules() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long ward = wardId("Cardiology");
        long room = createRoom(auth, ward, "C-100");
        long bed1 = createBed(auth, room, "1");
        long bed2 = createBed(auth, room, "2");
        long patientA = createPatient(auth, "HZ000000001");
        long patientB = createPatient(auth, "HZ000000002");
        long doctor = createDoctor(auth, "HZ-DOC-1");

        long stayA = idOf(admit(auth, patientA, doctor, bed1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentBed.id").value(bed1))
                .andExpect(jsonPath("$.currentBed.wardName").value("Main Wing"))
                .andExpect(jsonPath("$.currentBed.clinicName").value("Cardiology"))
                .andExpect(jsonPath("$.admission.initialDiagnosis").value("Suspected angina"))
                .andExpect(jsonPath("$.admission.admissionType").value("EMERGENCY")));

        // bed occupied, patient already admitted
        admit(auth, patientB, doctor, bed1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bed")));
        admit(auth, patientA, doctor, bed2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("patient")));

        // availability filter and occupied flag
        mockMvc.perform(get("/api/beds").param("roomId", String.valueOf(room)).param("available", "true")
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(bed2));
        mockMvc.perform(get("/api/beds/" + bed1).header("Authorization", auth))
                .andExpect(jsonPath("$.occupied").value(true));

        // cannot deactivate occupied structure
        mockMvc.perform(patch("/api/beds/" + bed1 + "/deactivate").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/rooms/" + room + "/deactivate").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/wards/" + ward + "/deactivate").header("Authorization", auth))
                .andExpect(status().isBadRequest());

        // transfer A to bed 2 (and the same bed is rejected), then B takes the freed bed 1
        transfer(auth, stayA, bed1).andExpect(status().isBadRequest());
        transfer(auth, stayA, bed2).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBed.id").value(bed2));
        long stayB = idOf(admit(auth, patientB, doctor, bed1).andExpect(status().isCreated()));
        transfer(auth, stayA, bed1).andExpect(status().isConflict());

        // search: by ward + status, by bed
        mockMvc.perform(get("/api/hospitalizations").param("wardId", String.valueOf(ward))
                        .param("status", "ADMITTED").param("patientId", String.valueOf(patientA))
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currentBed.id").value(bed2));
        mockMvc.perform(get("/api/hospitalizations").param("bedId", String.valueOf(bed1))
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(stayB));

        // update while admitted
        mockMvc.perform(put("/api/hospitalizations/" + stayA).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendingDoctorId\":" + doctor + ",\"admissionReason\":\"Chest pain\","
                                + "\"initialDiagnosis\":\"Confirmed angina\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admission.initialDiagnosis").value("Confirmed angina"))
                .andExpect(jsonPath("$.admission.admissionType").value("EMERGENCY"));

        // receptionists never see clinical text
        long receptionistUser = registerUser(auth, "recep.hz", "RECEPTIONIST");
        String receptionist = "Bearer " + login("recep.hz", "Sup3rSecret!");
        mockMvc.perform(get("/api/hospitalizations/" + stayA).header("Authorization", receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admission.initialDiagnosis").doesNotExist())
                .andExpect(jsonPath("$.admission.reason").value("Chest pain"))
                .andExpect(jsonPath("$.admission.admissionType").value("EMERGENCY"));
        mockMvc.perform(get("/api/hospitalizations").param("patientId", String.valueOf(patientA))
                        .header("Authorization", receptionist))
                .andExpect(jsonPath("$.content[0].admission.initialDiagnosis").doesNotExist());
        mockMvc.perform(post("/api/hospitalizations/" + stayA + "/discharge").header("Authorization", receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(receptionistUser).isPositive();

        // discharge A: assignment closed, status final, no more changes, bed history has both beds
        mockMvc.perform(post("/api/hospitalizations/" + stayA + "/discharge").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"Recovered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCHARGED"))
                .andExpect(jsonPath("$.currentBed").doesNotExist())
                .andExpect(jsonPath("$.discharge.summary").value("Recovered"))
                .andExpect(jsonPath("$.discharge.dischargeType").value("HOME"));
        mockMvc.perform(post("/api/hospitalizations/" + stayA + "/discharge").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"again\"}"))
                .andExpect(status().isBadRequest());
        transfer(auth, stayA, bed2).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hospitalizations/" + stayA + "/bed-history").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bed.id").value(bed1))
                .andExpect(jsonPath("$[0].endTime").exists())
                .andExpect(jsonPath("$[1].bed.id").value(bed2))
                .andExpect(jsonPath("$[1].endTime").exists());
        mockMvc.perform(get("/api/hospitalizations/" + stayA + "/bed-history").header("Authorization", receptionist))
                .andExpect(jsonPath("$[1].reason").doesNotExist());

        // the discharged stay is still searchable by its last bed; the freed bed can be used again
        mockMvc.perform(get("/api/hospitalizations").param("bedId", String.valueOf(bed2))
                        .param("status", "DISCHARGED").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1));
        long patientC = createPatient(auth, "HZ000000003");
        admit(auth, patientC, doctor, bed2).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/hospitalizations/" + stayA).header("Authorization", auth))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void admissionAndDischargeRecordsAreSeparateDocumentsWithTheirOwnRules() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        registerUser(admin, "recep.docs", "RECEPTIONIST");
        String receptionist = "Bearer " + login("recep.docs", "Sup3rSecret!");
        long room = createRoom(admin, wardId("Pediatrics"), "P-DOC1");
        long bed = createBed(admin, room, "1");
        long patient = createPatient(admin, "DC000000001");
        long doctor = createDoctor(admin, "DC-DOC-1");

        // the admission record has its own rules
        String base = "{\"patientId\":%d,\"attendingDoctorId\":%d,\"bedId\":%d,\"admissionReason\":\"Fracture\","
                .formatted(patient, doctor, bed);
        postAdmission(admin, base + "\"admissionType\":\"TRANSFER_IN\",\"admissionSource\":\"REFERRAL\"}")
                .andExpect(status().isBadRequest());                                  // transfer-in needs the facility
        postAdmission(admin, base + "\"admissionType\":\"ELECTIVE\",\"admissionSource\":\"APPOINTMENT\"}")
                .andExpect(status().isBadRequest());                                  // appointment source needs a link
        postAdmission(admin, base + "\"admissionType\":\"ELECTIVE\"}").andExpect(status().isBadRequest());
        long stay = idOf(postAdmission(admin, base + "\"admissionType\":\"TRANSFER_IN\","
                + "\"admissionSource\":\"REFERRAL\",\"referringFacility\":\"General Hospital of Patras\","
                + "\"initialDiagnosisCode\":\"s72.0\",\"initialDiagnosis\":\"Femur fracture\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.admission.admissionType").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.admission.referringFacility").value("General Hospital of Patras"))
                .andExpect(jsonPath("$.admission.initialDiagnosisCode").value("S72.0"))
                .andExpect(jsonPath("$.discharge").doesNotExist()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_records WHERE hospitalization_id = ?",
                Integer.class, stay)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discharge_records WHERE hospitalization_id = ?",
                Integer.class, stay)).isZero();

        // the discharge record has its own rules too, and a refused discharge leaves the stay admitted
        discharge(admin, stay, "{\"dischargeType\":\"TRANSFER_OUT\",\"summary\":\"x\"}").andExpect(status().isBadRequest());
        discharge(admin, stay, "{\"dischargeType\":\"HOME\",\"destinationFacility\":\"X\",\"summary\":\"x\"}")
                .andExpect(status().isBadRequest());
        discharge(admin, stay, "{\"dischargeType\":\"DECEASED\",\"followUpDate\":\"2999-01-01\",\"summary\":\"x\"}")
                .andExpect(status().isBadRequest());
        discharge(admin, stay, "{\"dischargeType\":\"HOME\",\"followUpDate\":\"2000-01-01\",\"summary\":\"x\"}")
                .andExpect(status().isBadRequest());
        discharge(admin, stay, "{\"dischargeType\":\"HOME\"}").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hospitalizations/" + stay).header("Authorization", admin))
                .andExpect(jsonPath("$.status").value("ADMITTED")).andExpect(jsonPath("$.discharge").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discharge_records WHERE hospitalization_id = ?",
                Integer.class, stay)).isZero();

        discharge(admin, stay, "{\"dischargeType\":\"TRANSFER_OUT\",\"destinationFacility\":\"University Hospital\","
                + "\"finalDiagnosisCode\":\"s72.0\",\"finalDiagnosis\":\"Femur fracture, operated\","
                + "\"summary\":\"Operated successfully\",\"instructions\":\"No weight bearing\","
                + "\"followUpDate\":\"2999-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCHARGED"))
                .andExpect(jsonPath("$.discharge.dischargeType").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.discharge.destinationFacility").value("University Hospital"))
                .andExpect(jsonPath("$.discharge.finalDiagnosisCode").value("S72.0"))
                .andExpect(jsonPath("$.discharge.followUpDate").value("2999-01-01"))
                .andExpect(jsonPath("$.admission.reason").value("Fracture"));

        // the clinical parts of both documents are hidden from receptionists, the administrative parts are not
        mockMvc.perform(get("/api/hospitalizations/" + stay).header("Authorization", receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admission.reason").value("Fracture"))
                .andExpect(jsonPath("$.admission.admissionType").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.admission.initialDiagnosis").doesNotExist())
                .andExpect(jsonPath("$.admission.initialDiagnosisCode").doesNotExist())
                .andExpect(jsonPath("$.discharge.dischargeType").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.discharge.followUpDate").value("2999-01-01"))
                .andExpect(jsonPath("$.discharge.summary").doesNotExist())
                .andExpect(jsonPath("$.discharge.finalDiagnosis").doesNotExist())
                .andExpect(jsonPath("$.discharge.instructions").doesNotExist());

        // lists carry both documents (loaded for the whole page at once)
        mockMvc.perform(get("/api/hospitalizations").param("patientId", String.valueOf(patient))
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.content[0].admission.referringFacility").value("General Hospital of Patras"))
                .andExpect(jsonPath("$.content[0].discharge.summary").value("Operated successfully"));

        // the documents are protected by the database as well
        assertThatThrownBy(() -> jdbc.update("UPDATE discharge_records SET summary = 'tampered' WHERE hospitalization_id = ?", stay))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM discharge_records WHERE hospitalization_id = ?", stay))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE admission_records SET reason = 'tampered' WHERE hospitalization_id = ?", stay))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM admission_records WHERE hospitalization_id = ?", stay))
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO discharge_records (hospitalization_id, discharge_type, summary, "
                + "created_at, updated_at) VALUES (?, 'HOME', 'second', now(), now())", stay))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_discharge_records_hospitalization");

        // every stay needs its admission record, a discharged stay its discharge record (checked at commit)
        long other = createPatient(admin, "DC000000002");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO hospitalizations (patient_id, attending_doctor_id, status, "
                + "admission_time, created_at, updated_at) VALUES (?, ?, 'ADMITTED', now(), now(), now())", other, doctor))
                .hasMessageContaining("has no admission record");
        long stay2 = insertHospitalization(other, doctor, "ADMITTED", "now() - interval '1 hour'", null);
        assertThatThrownBy(() -> jdbc.update("UPDATE hospitalizations SET status = 'DISCHARGED', discharge_time = now() "
                + "WHERE id = ?", stay2)).hasMessageContaining("has no discharge record");
        assertThat(jdbc.queryForObject("SELECT status FROM hospitalizations WHERE id = ?", String.class, stay2))
                .isEqualTo("ADMITTED");
        // a record-keeping problem on one table cannot hide the typed rules of another
        assertThatThrownBy(() -> jdbc.update("INSERT INTO discharge_records (hospitalization_id, discharge_type, summary, "
                + "created_at, updated_at) VALUES (?, 'TRANSFER_OUT', 'x', now(), now())", stay2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_discharge_records_destination");
    }

    private ResultActions postAdmission(String auth, String json) throws Exception {
        return mockMvc.perform(post("/api/hospitalizations").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions discharge(String auth, long hospitalizationId, String json) throws Exception {
        return mockMvc.perform(post("/api/hospitalizations/" + hospitalizationId + "/discharge")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void onlyTheAttendingDoctorCanDischargeAndSeesOwnHospitalizations() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(admin, wardId("Orthopedics"), "O-100");
        long bed = createBed(admin, room, "1");
        long patient = createPatient(admin, "HZ000000010");
        long attendingUser = registerUser(admin, "dr.attending", "DOCTOR");
        long otherUser = registerUser(admin, "dr.hz.other", "DOCTOR");
        long attending = createDoctorLinked(admin, "HZ-DOC-2", attendingUser);
        createDoctorLinked(admin, "HZ-DOC-3", otherUser);
        long stay = idOf(admit(admin, patient, attending, bed).andExpect(status().isCreated()));

        String other = "Bearer " + login("dr.hz.other", "Sup3rSecret!");
        mockMvc.perform(post("/api/hospitalizations/" + stay + "/discharge").header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/hospitalizations/me").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        String mine = "Bearer " + login("dr.attending", "Sup3rSecret!");
        mockMvc.perform(get("/api/hospitalizations/me").header("Authorization", mine))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(stay));
        mockMvc.perform(post("/api/hospitalizations/" + stay + "/discharge").header("Authorization", mine)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"Recovered\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hospitalizations/me").header("Authorization", admin)).andExpect(status().isForbidden());
    }

    @Test
    void appointmentLinkRules() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(auth, wardId("Pediatrics"), "P-100");
        long bed1 = createBed(auth, room, "1");
        long bed2 = createBed(auth, room, "2");
        long patientA = createPatient(auth, "HZ000000020");
        long patientB = createPatient(auth, "HZ000000021");
        long doctor = createDoctor(auth, "HZ-DOC-4");
        Instant start = futureHour(22);
        long appointmentOfA = bookOk(auth, patientA, doctor, start, start.plus(30, ChronoUnit.MINUTES));

        admitWithAppointment(auth, patientB, doctor, bed1, appointmentOfA).andExpect(status().isBadRequest());
        admitWithAppointment(auth, patientA, doctor, bed1, appointmentOfA).andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentOfA));
        // the appointment itself is untouched, and it cannot be linked twice
        mockMvc.perform(get("/api/appointments/" + appointmentOfA).header("Authorization", auth))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        admitWithAppointment(auth, patientB, doctor, bed2, appointmentOfA).andExpect(status().isBadRequest());
    }

    @Test
    void databaseConstraintsForBedOccupancyAndSingleActiveStay() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(auth, wardId("Maternity"), "M-100");
        long bed = createBed(auth, room, "1");
        long patientA = createPatient(auth, "HZ000000030");
        long patientB = createPatient(auth, "HZ000000031");
        long doctor = createDoctor(auth, "HZ-DOC-5");
        long stayA = idOf(admit(auth, patientA, doctor, bed).andExpect(status().isCreated()));
        long stayB = insertHospitalization(patientB, doctor, "ADMITTED", "now() - interval '1 hour'", null);

        // second open assignment for the same bed / same stay
        assertThatThrownBy(() -> insertAssignment(stayB, bed, "now()", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_bed_assignments_bed_no_overlap");
        // a closed range inside A's open-ended (runs to infinity) occupancy is rejected too ...
        assertThatThrownBy(() -> insertAssignment(stayB, bed, "now() + interval '1 hour'",
                "now() + interval '2 hours'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_bed_assignments_bed_no_overlap");
        // ... while a range that ended before A arrived is history and does not collide
        insertAssignment(stayB, bed, "now() - interval '30 minutes'", "now() - interval '10 minutes'");
        // a second active stay for the same patient
        assertThatThrownBy(() -> insertHospitalization(patientA, doctor, "ADMITTED", "now()", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_hospitalizations_patient_admitted");
        // a discharged stay needs a discharge time and vice versa
        assertThatThrownBy(() -> insertHospitalization(patientA, doctor, "DISCHARGED", "now() - interval '2 hours'", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_hospitalizations_discharged_iff_discharge_time");

        // after A is discharged the bed is free again for B's stay
        mockMvc.perform(post("/api/hospitalizations/" + stayA + "/discharge").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"Recovered\"}"))
                .andExpect(status().isOk());
        insertAssignment(stayB, bed, "now() + interval '1 second'", null);
        // and A can be admitted again later (history rows do not block a new stay)
        Integer open = jdbc.queryForObject(
                "SELECT count(*) FROM bed_assignments WHERE bed_id = ? AND end_time IS NULL", Integer.class, bed);
        assertThat(open).isEqualTo(1);
    }

    @Test
    void concurrentAdmissionsToTheSameBedYieldExactlyOneSuccess() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(auth, wardId("Internal Medicine"), "IM-100");
        long bed = createBed(auth, room, "1");
        long doctor = createDoctor(auth, "HZ-DOC-6");
        int requests = 6;
        List<Long> patients = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            patients.add(createPatient(auth, "HZ00000004" + i));
        }

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long patient : patients) {
                Callable<Integer> admission = () -> {
                    ready.countDown();
                    go.await();
                    return admit(auth, patient, doctor, bed).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(admission));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }
        Integer open = jdbc.queryForObject(
                "SELECT count(*) FROM bed_assignments WHERE bed_id = ? AND end_time IS NULL", Integer.class, bed);
        assertThat(open).isEqualTo(1);
        // the losing admissions were rolled back completely: one hospitalization in total for these patients
        Integer stays = jdbc.queryForObject("SELECT count(*) FROM hospitalizations WHERE patient_id = ANY (?)",
                Integer.class, (Object) patients.toArray(Long[]::new));
        assertThat(stays).isEqualTo(1);
    }

    @Test
    void concurrentAdmissionsOfTheSamePatientYieldExactlyOneSuccess() throws Exception {
        String auth = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(auth, wardId("Intensive Care"), "ICU-100");
        long doctor = createDoctor(auth, "HZ-DOC-7");
        long patient = createPatient(auth, "HZ000000050");
        int requests = 4;
        List<Long> beds = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            beds.add(createBed(auth, room, "B" + i));
        }

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long bed : beds) {
                Callable<Integer> admission = () -> {
                    ready.countDown();
                    go.await();
                    return admit(auth, patient, doctor, bed).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(admission));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- clinics -------------------------------------------------------------------------------

    @Test
    void clinicsAreSeededAndTheHierarchyClinicWardRoomBedWorks() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        registerUser(admin, "nurse.clinic", "NURSE");
        String nurse = "Bearer " + login("nurse.clinic", "Sup3rSecret!");

        // seeded clinics, linked to a specialty where one matches; read by everyone, written by ADMIN
        mockMvc.perform(get("/api/clinics").header("Authorization", nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$[?(@.name=='Cardiology')].specialty.name").value("Cardiology"));
        mockMvc.perform(post("/api/clinics").header("Authorization", nurse).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\"}")).andExpect(status().isForbidden());

        // a new clinic with its own wing, room and bed; names only need to be unique inside the parent
        long specialty = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Dermatology'", Long.class);
        long clinic = createClinic(admin, "Dermatology Clinic", specialty);
        mockMvc.perform(post("/api/clinics").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"dermatology clinic\"}")).andExpect(status().isConflict());
        long wing = createWard(admin, clinic, "North Wing");
        createWard(admin, clinicId("Cardiology"), "North Wing");                 // same name, other clinic: fine
        mockMvc.perform(post("/api/wards").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clinicId\":%d,\"name\":\"north wing\"}".formatted(clinic)))
                .andExpect(status().isConflict());
        long room = createRoom(admin, wing, "D-101");
        long bed = createBed(admin, room, "1");
        mockMvc.perform(get("/api/beds/" + bed).header("Authorization", nurse))
                .andExpect(jsonPath("$.clinicName").value("Dermatology Clinic"))
                .andExpect(jsonPath("$.wardName").value("North Wing"))
                .andExpect(jsonPath("$.roomNumber").value("D-101"));

        // filters at every level
        mockMvc.perform(get("/api/wards").param("clinicId", String.valueOf(clinic)).header("Authorization", nurse))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].clinicName").value("Dermatology Clinic"));
        mockMvc.perform(get("/api/rooms").param("clinicId", String.valueOf(clinic)).header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].clinicName").value("Dermatology Clinic"));
        mockMvc.perform(get("/api/beds").param("clinicId", String.valueOf(clinic)).param("available", "true")
                        .header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(bed));
        mockMvc.perform(get("/api/beds").param("clinicId", String.valueOf(clinicId("Cardiology")))
                        .param("roomId", String.valueOf(room)).header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(0));

        // a doctor can belong to a clinic and be found by it
        long doctor = idOf(mockMvc.perform(post("/api/doctors").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anna\",\"lastName\":\"Skin\",\"licenseNumber\":\"CL-DOC-1\","
                                + "\"specialtyId\":%d,\"clinicId\":%d}".formatted(specialty, clinic)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clinic.name").value("Dermatology Clinic")));
        mockMvc.perform(get("/api/doctors").param("clinicId", String.valueOf(clinic)).header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(doctor));
        mockMvc.perform(post("/api/doctors").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"B\",\"lastName\":\"C\",\"licenseNumber\":\"CL-DOC-2\","
                                + "\"specialtyId\":%d,\"clinicId\":999999}".formatted(specialty)))
                .andExpect(status().isNotFound());

        // stays are found by clinic (current bed, or last bed once discharged); occupied structure cannot be closed
        long patient = createPatient(admin, "CL000000001");
        long stay = idOf(admit(admin, patient, doctor, bed).andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentBed.clinicName").value("Dermatology Clinic")));
        mockMvc.perform(get("/api/hospitalizations").param("clinicId", String.valueOf(clinic)).param("status", "ADMITTED")
                        .header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(stay));
        mockMvc.perform(patch("/api/clinics/" + clinic + "/deactivate").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wards/" + wing).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clinicId\":%d,\"name\":\"Moved Wing\"}".formatted(clinicId("Cardiology"))))
                .andExpect(status().isBadRequest());                                    // occupied: cannot move
        mockMvc.perform(post("/api/hospitalizations/" + stay + "/discharge").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"Recovered\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hospitalizations").param("clinicId", String.valueOf(clinic)).param("status", "DISCHARGED")
                        .header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1));

        // once empty the clinic can be deactivated; new wards cannot be added to it any more
        mockMvc.perform(patch("/api/clinics/" + clinic + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/wards").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clinicId\":%d,\"name\":\"South Wing\"}".formatted(clinic))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/clinics").header("Authorization", nurse))
                .andExpect(jsonPath("$[?(@.name=='Dermatology Clinic')]").isEmpty());
        mockMvc.perform(get("/api/clinics").param("active", "false").header("Authorization", nurse))
                .andExpect(jsonPath("$[?(@.name=='Dermatology Clinic')]").isNotEmpty());
        mockMvc.perform(delete("/api/clinics/" + clinic).header("Authorization", admin)).andExpect(status().is4xxClientError());
    }

    // ---- billing -------------------------------------------------------------------------------

    @Test
    void billingFlowPriceListInvoiceLinesIssuePaymentsAndRoles() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        registerUser(admin, "recep.bill", "RECEPTIONIST");
        registerUser(admin, "nurse.bill", "NURSE");
        String receptionist = "Bearer " + login("recep.bill", "Sup3rSecret!");
        String nurse = "Bearer " + login("nurse.bill", "Sup3rSecret!");
        long patient = createPatient(admin, "BL000000001");

        // the seeded price list is readable by every role, maintained by ADMIN only
        mockMvc.perform(get("/api/charge-items").header("Authorization", nurse))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
        String item = "{\"code\":\"lab-bl1\",\"name\":\"Blood panel\",\"unitPrice\":33.33,\"vatRate\":13}";
        mockMvc.perform(post("/api/charge-items").header("Authorization", receptionist)
                .contentType(MediaType.APPLICATION_JSON).content(item)).andExpect(status().isForbidden());
        long panel = idOf(mockMvc.perform(post("/api/charge-items").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(item))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("LAB-BL1")));
        mockMvc.perform(post("/api/charge-items").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(item)).andExpect(status().isConflict());
        long consult = chargeItemId("CONSULT");

        // nurses cannot bill; the receptionist builds a draft
        mockMvc.perform(post("/api/invoices").header("Authorization", nurse).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + patient + "}")).andExpect(status().isForbidden());
        long invoice = idOf(mockMvc.perform(post("/api/invoices").header("Authorization", receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":" + patient + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.invoiceNumber").doesNotExist()));
        postLine(receptionist, invoice, consult, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGross").value(100.00));
        String withPanel = postLine(receptionist, invoice, panel, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.totalNet").value(199.99))     // 100.00 + 3 x 33.33
                .andExpect(jsonPath("$.totalVat").value(13.00))      // 13% of 99.99 = 12.9987 -> 13.00
                .andExpect(jsonPath("$.totalGross").value(212.99))
                .andReturn().getResponse().getContentAsString();
        long panelLine = objectMapper.readTree(withPanel).get("lines").get(1).get("id").asLong();
        mockMvc.perform(delete("/api/invoices/" + invoice + "/lines/" + panelLine).header("Authorization", receptionist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalGross").value(100.00));
        postLine(receptionist, invoice, panel, 3).andExpect(status().isOk());   // 212.99 again

        // a later price change does not touch the invoice
        mockMvc.perform(put("/api/charge-items/" + panel).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LAB-BL1\",\"name\":\"Blood panel\",\"unitPrice\":99.00,\"vatRate\":13}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/invoices/" + invoice).header("Authorization", nurse))
                .andExpect(jsonPath("$.totalGross").value(212.99));

        // issue: numbered and frozen
        mockMvc.perform(post("/api/invoices/" + invoice + "/issue").header("Authorization", receptionist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoiceNumber").value(org.hamcrest.Matchers.matchesPattern("INV-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.outstandingAmount").value(212.99));
        postLine(receptionist, invoice, consult, 1).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/invoices/" + invoice + "/issue").header("Authorization", receptionist))
                .andExpect(status().isBadRequest());

        // payments: partial, over-payment refused, the rest makes it PAID, nothing more is accepted
        pay(receptionist, invoice, "100.00", "CASH").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.paidAmount").value(100.00))
                .andExpect(jsonPath("$.outstandingAmount").value(112.99));
        pay(receptionist, invoice, "113.00", "CARD").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/invoices").param("patientId", String.valueOf(patient)).param("outstanding", "true")
                        .header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(112.99));
        pay(admin, invoice, "112.99", "BANK_TRANSFER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.outstandingAmount").value(0.0));
        pay(receptionist, invoice, "0.01", "CASH").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/invoices/" + invoice + "/payments").header("Authorization", nurse))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].recordedBy").value("recep.bill"))
                .andExpect(jsonPath("$[1].recordedBy").value("admin"));

        // only an admin cancels, and not a paid invoice
        mockMvc.perform(post("/api/invoices/" + invoice + "/cancel").header("Authorization", receptionist)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/" + invoice + "/cancel").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/invoices/" + invoice).header("Authorization", admin)).andExpect(status().is4xxClientError());

        // billing activity is in the audit trail, tied to the patient
        assertThat(auditCount("username = 'recep.bill' AND resource_type = 'INVOICE' AND action = 'PAY' "
                + "AND patient_id = ? AND resource_id = ? AND outcome = 'SUCCESS'", patient, invoice)).isEqualTo(1);
        assertThat(auditCount("username = 'recep.bill' AND resource_type = 'INVOICE' AND action = 'ISSUE' "
                + "AND patient_id = ? AND outcome = 'SUCCESS'", patient)).isEqualTo(1);
        assertThat(auditCount("username = 'nurse.bill' AND resource_type = 'INVOICE' AND action = 'CREATE' "
                + "AND outcome = 'DENIED'")).isEqualTo(1);
    }

    @Test
    void aDischargedStayIsInvoicedOncePerBedDay() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long room = createRoom(admin, wardId("Surgery"), "S-BL1");
        long bed = createBed(admin, room, "1");
        long patient = createPatient(admin, "BL000000010");
        long doctor = createDoctor(admin, "BL-DOC-1");
        Instant admittedAt = Instant.now().minus(50, ChronoUnit.HOURS);
        long stay = idOf(mockMvc.perform(post("/api/hospitalizations").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"patientId\":%d,\"attendingDoctorId\":%d,\"bedId\":%d,\"admissionType\":\"ELECTIVE\","
                                + "\"admissionSource\":\"OTHER\",\"admissionReason\":\"Surgery\","
                                + "\"admissionTime\":\"%s\"}").formatted(patient, doctor, bed, admittedAt)))
                .andExpect(status().isCreated()));

        mockMvc.perform(post("/api/invoices/from-hospitalization/" + stay).header("Authorization", admin))
                .andExpect(status().isBadRequest());   // still admitted
        mockMvc.perform(post("/api/hospitalizations/" + stay + "/discharge").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dischargeType\":\"HOME\",\"summary\":\"Recovered\"}"))
                .andExpect(status().isOk());

        // 50 hours = 3 started days x 150.00
        long invoice = idOf(mockMvc.perform(post("/api/invoices/from-hospitalization/" + stay)
                        .header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hospitalizationId").value(stay))
                .andExpect(jsonPath("$.lines[0].code").value("BED-DAY"))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.totalGross").value(450.00)));
        mockMvc.perform(post("/api/invoices/from-hospitalization/" + stay).header("Authorization", admin))
                .andExpect(status().isConflict());

        // cancelling frees the stay for a new invoice
        mockMvc.perform(post("/api/invoices/" + invoice + "/cancel").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong period\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/invoices/from-hospitalization/" + stay).header("Authorization", admin))
                .andExpect(status().isCreated());
    }

    @Test
    void theDatabaseProtectsIssuedInvoicesAndPayments() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "BL000000020");
        long invoice = idOf(mockMvc.perform(post("/api/invoices").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":" + patient + "}"))
                .andExpect(status().isCreated()));
        postLine(admin, invoice, chargeItemId("CONSULT"), 2).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/" + invoice + "/issue").header("Authorization", admin)).andExpect(status().isOk());
        pay(admin, invoice, "40.00", "CASH").andExpect(status().isOk());

        // lines of a non-draft invoice are frozen
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_lines WHERE invoice_id = ?", invoice))
                .hasMessageContaining("DRAFT");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_lines SET quantity = 1, net_amount = 50, gross_amount = 50 "
                + "WHERE invoice_id = ?", invoice)).hasMessageContaining("DRAFT");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO invoice_lines (invoice_id, charge_item_id, code, description, "
                + "quantity, unit_price, vat_rate, net_amount, vat_amount, gross_amount, created_at, updated_at) "
                + "VALUES (?, ?, 'X', 'x', 1, 1.00, 0, 1.00, 0, 1.00, now(), now())", invoice, chargeItemId("CONSULT")))
                .hasMessageContaining("DRAFT");
        // an issued invoice cannot change its amounts or number
        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET total_net = 1, total_gross = 1 WHERE id = ?", invoice))
                .hasMessageContaining("cannot change");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET invoice_number = 'INV-HACKED' WHERE id = ?", invoice))
                .hasMessageContaining("cannot change");
        // invoices are never deleted, payments never rewritten, and nothing can be over-paid
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoices WHERE id = ?", invoice)).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET amount = 1 WHERE invoice_id = ?", invoice))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payments WHERE invoice_id = ?", invoice))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET paid_amount = 100.01 WHERE id = ?", invoice))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_invoices_paid_range");
        // a paid invoice must be flagged PAID and vice versa
        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET paid_amount = 100.00 WHERE id = ?", invoice))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_invoices_paid_iff_fully_paid");
    }

    @Test
    void concurrentPaymentsNeverOverPayAnInvoice() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "BL000000030");
        long invoice = idOf(mockMvc.perform(post("/api/invoices").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":" + patient + "}"))
                .andExpect(status().isCreated()));
        postLine(admin, invoice, chargeItemId("CONSULT"), 2).andExpect(status().isOk());   // 100.00
        mockMvc.perform(post("/api/invoices/" + invoice + "/issue").header("Authorization", admin)).andExpect(status().isOk());

        int requests = 8;   // 8 x 20.00 against 100.00 due: exactly 5 can succeed
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<Integer> payment = () -> {
                    ready.countDown();
                    go.await();
                    return pay(admin, invoice, "20.00", "CARD").andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(payment));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(5);
            assertThat(statuses).filteredOn(code -> code == 400).hasSize(requests - 5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM invoices WHERE id = ?", java.math.BigDecimal.class, invoice))
                .isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?", String.class, invoice)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE invoice_id = ?", Integer.class, invoice))
                .isEqualTo(5);
    }

    private long chargeItemId(String code) {
        return jdbc.queryForObject("SELECT id FROM charge_items WHERE code = ?", Long.class, code);
    }

    private ResultActions postLine(String auth, long invoiceId, long chargeItemId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoiceId + "/lines").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeItemId\":%d,\"quantity\":%d}".formatted(chargeItemId, quantity)));
    }

    private ResultActions pay(String auth, long invoiceId, String amount, String method) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoiceId + "/payments").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%s,\"method\":\"%s\"}".formatted(amount, method)));
    }

    // ---- audit log -----------------------------------------------------------------------------

    @Test
    void auditTrailRecordsWhoAccessedWhichPatientAndWhetherItWasAllowed() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "AU000000001");
        long doctorUser = registerUser(admin, "dr.audit", "DOCTOR");
        registerUser(admin, "nurse.audit", "NURSE");
        registerUser(admin, "recep.audit", "RECEPTIONIST");
        createDoctorLinked(admin, "AU-DOC-1", doctorUser);
        String doctor = "Bearer " + login("dr.audit", "Sup3rSecret!");
        String nurse = "Bearer " + login("nurse.audit", "Sup3rSecret!");
        String receptionist = "Bearer " + login("recep.audit", "Sup3rSecret!");

        // the doctor writes a note about the patient: CREATE of the note, tied to the patient
        long note = idOf(mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":%d,\"assessment\":\"Highly confidential finding\"}".formatted(patient)))
                .andExpect(status().isCreated()));
        assertThat(auditCount("username = 'dr.audit' AND action = 'CREATE' AND resource_type = 'CLINICAL_NOTE' "
                + "AND resource_id = ? AND patient_id = ? AND outcome = 'SUCCESS'", note, patient)).isEqualTo(1);

        // a nurse reads the note, its versions and searches: each disclosed record is logged against the patient
        mockMvc.perform(get("/api/clinical-notes/" + note).header("Authorization", nurse)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clinical-notes/" + note + "/versions").header("Authorization", nurse))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clinical-notes").param("patientId", String.valueOf(patient))
                        .header("Authorization", nurse)).andExpect(status().isOk());
        assertThat(auditCount("username = 'nurse.audit' AND action = 'VIEW' AND resource_type = 'CLINICAL_NOTE' "
                + "AND resource_id = ? AND patient_id = ? AND outcome = 'SUCCESS'", note, patient)).isEqualTo(3);

        // a receptionist is refused access to clinical data - and the attempt is on record, tied to the patient
        mockMvc.perform(get("/api/clinical-notes/" + note).header("Authorization", receptionist))
                .andExpect(status().isForbidden());
        assertThat(auditCount("username = 'recep.audit' AND resource_type = 'CLINICAL_NOTE' AND resource_id = ? "
                + "AND patient_id = ? AND outcome = 'DENIED' AND roles = 'RECEPTIONIST'", note, patient)).isEqualTo(1);
        // ... and may not delete a patient (denied), but may look at the patient record itself (allowed)
        mockMvc.perform(delete("/api/patients/" + patient).header("Authorization", receptionist))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/" + patient).header("Authorization", receptionist))
                .andExpect(status().isOk());
        assertThat(auditCount("username = 'recep.audit' AND action = 'DELETE' AND resource_type = 'PATIENT' "
                + "AND patient_id = ? AND outcome = 'DENIED'", patient)).isEqualTo(1);
        assertThat(auditCount("username = 'recep.audit' AND action = 'VIEW' AND resource_type = 'PATIENT' "
                + "AND patient_id = ? AND outcome = 'SUCCESS'", patient)).isEqualTo(1);

        // looking for something that does not exist is recorded too
        mockMvc.perform(get("/api/patients/987654").header("Authorization", nurse)).andExpect(status().isNotFound());
        assertThat(auditCount("username = 'nurse.audit' AND resource_type = 'PATIENT' AND resource_id = 987654 "
                + "AND outcome = 'FAILED'")).isEqualTo(1);

        // only an admin reads the log; "who accessed this patient" is one query
        mockMvc.perform(get("/api/audit-events").header("Authorization", nurse)).andExpect(status().isForbidden());
        assertThat(auditCount("username = 'nurse.audit' AND resource_type = 'AUDIT_LOG' AND outcome = 'DENIED'"))
                .isEqualTo(1);
        mockMvc.perform(get("/api/audit-events").param("patientId", String.valueOf(patient))
                        .param("outcome", "DENIED").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.username=='recep.audit')]").isNotEmpty());
        mockMvc.perform(get("/api/audit-events").param("patientId", String.valueOf(patient))
                        .param("username", "NURSE.audit").param("action", "VIEW").param("resourceType", "CLINICAL_NOTE")
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].roles").value("NURSE"))
                .andExpect(jsonPath("$.content[0].path").exists());
        assertThat(auditCount("username = 'admin' AND resource_type = 'AUDIT_LOG' AND outcome = 'SUCCESS'"))
                .isGreaterThanOrEqualTo(2);

        // no clinical text, names or query strings ever reach the audit table
        mockMvc.perform(get("/api/patients").param("name", "UniqueSearchSecret").header("Authorization", nurse))
                .andExpect(status().isOk());
        assertThat(auditCount("path ILIKE '%UniqueSearchSecret%' OR path ILIKE '%confidential%'")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE username = 'nurse.audit' "
                + "AND resource_type = 'PATIENT' AND action = 'VIEW' AND resource_id IS NULL", Integer.class))
                .isEqualTo(1);   // the empty search left a single summary event

        // the log cannot be rewritten or pruned, not even with raw SQL
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET username = 'someone.else'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE username = 'dr.audit'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_events (occurred_at, username, roles, action, "
                + "resource_type, outcome, http_method, path) VALUES (now(), 'x', 'ADMIN', 'EXPORT', 'PATIENT', "
                + "'SUCCESS', 'GET', '/x')")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_audit_events_action");
    }

    @Test
    void loginsAndUserCreationAreAudited() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        assertThat(auditCount("username = 'admin' AND action = 'LOGIN' AND resource_type = 'USER' "
                + "AND outcome = 'SUCCESS' AND roles = 'ADMIN' AND path = '/api/auth/login'")).isGreaterThanOrEqualTo(1);

        // register a user (ADMIN): CREATE of a USER, tied to the new account; not about a patient
        long created = registerUser(admin, "audit.login", "NURSE");
        assertThat(auditCount("username = 'admin' AND action = 'CREATE' AND resource_type = 'USER' AND resource_id = ? "
                + "AND patient_id IS NULL AND outcome = 'SUCCESS'", created)).isEqualTo(1);
        // a nurse trying to register somebody is refused and recorded
        String nurse = "Bearer " + login("audit.login", "Sup3rSecret!");
        mockMvc.perform(post("/api/auth/register").header("Authorization", nurse)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(auditCount("username = 'audit.login' AND action = 'CREATE' AND resource_type = 'USER' "
                + "AND outcome = 'DENIED'")).isEqualTo(1);
        assertThat(auditCount("username = 'audit.login' AND action = 'LOGIN' AND outcome = 'SUCCESS' "
                + "AND roles = 'NURSE' AND resource_id = ?", created)).isEqualTo(1);

        // a wrong password for a real account: recorded against that account, with a reason code
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audit.login\",\"password\":\"Wr0ngPassw0rd!\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(auditCount("username = 'audit.login' AND action = 'LOGIN' AND outcome = 'DENIED' "
                + "AND detail = 'BAD_CREDENTIALS' AND roles = '' AND resource_id = ?", created)).isEqualTo(1);

        // a disabled account is told apart from a wrong password
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", created);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audit.login\",\"password\":\"Sup3rSecret!\"}"))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET enabled = TRUE WHERE id = ?", created);
        assertThat(auditCount("username = 'audit.login' AND action = 'LOGIN' AND detail = 'ACCOUNT_DISABLED' "
                + "AND outcome = 'DENIED'")).isEqualTo(1);

        // a name that is not an account (maybe a password typed into the wrong box) is never stored
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"TypedMyPassword-9\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(auditCount("username = 'unknown' AND action = 'LOGIN' AND outcome = 'DENIED' "
                + "AND detail = 'BAD_CREDENTIALS' AND resource_id IS NULL")).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE username ILIKE '%TypedMyPassword%' "
                + "OR path ILIKE '%TypedMyPassword%' OR detail ILIKE '%TypedMyPassword%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE username ILIKE '%Wr0ngPassw0rd%' "
                + "OR path ILIKE '%Wr0ngPassw0rd%' OR detail ILIKE '%Wr0ngPassw0rd%'", Integer.class)).isZero();

        // requests that merely carry a token are not logins
        int loginsBefore = auditCount("action = 'LOGIN'");
        mockMvc.perform(get("/api/patients").header("Authorization", nurse)).andExpect(status().isOk());
        assertThat(auditCount("action = 'LOGIN'")).isEqualTo(loginsBefore);

        // an admin can follow failed logins: by outcome, by address
        mockMvc.perform(get("/api/audit-events").param("action", "LOGIN").param("outcome", "DENIED")
                        .param("username", "audit.login").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.detail=='ACCOUNT_DISABLED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.detail=='BAD_CREDENTIALS')]").isNotEmpty());
        mockMvc.perform(get("/api/audit-events").param("action", "LOGIN").param("outcome", "DENIED")
                        .param("ipAddress", "127.0.0.1").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        mockMvc.perform(get("/api/audit-events").param("ipAddress", "203.0.113.99").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private int auditCount(String where, Object... args) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE " + where, Integer.class, args);
    }

    // ---- helpers for the clinical note tests (and the audit test above) -------------------------

    // ---- clinical notes ------------------------------------------------------------------------

    @Test
    void clinicalNotesAreVersionedAccessControlledAndImmutable() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "CN000000001");
        long otherPatient = createPatient(admin, "CN000000002");
        long authorUser = registerUser(admin, "dr.notes", "DOCTOR");
        long otherUser = registerUser(admin, "dr.notes.other", "DOCTOR");
        registerUser(admin, "nurse.notes", "NURSE");
        registerUser(admin, "recep.notes", "RECEPTIONIST");
        long authorDoctor = createDoctorLinked(admin, "CN-DOC-1", authorUser);
        createDoctorLinked(admin, "CN-DOC-2", otherUser);
        String doctor = "Bearer " + login("dr.notes", "Sup3rSecret!");
        String otherDoctor = "Bearer " + login("dr.notes.other", "Sup3rSecret!");
        String nurse = "Bearer " + login("nurse.notes", "Sup3rSecret!");
        String receptionist = "Bearer " + login("recep.notes", "Sup3rSecret!");

        // only a doctor writes; the author is the doctor profile of the caller
        String note = "{\"patientId\":%d,\"history\":\"Chest pain\",\"assessment\":\"Suspected angina\",\"diagnosisCode\":\"i20.9\"}"
                .formatted(patient);
        long id = idOf(mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(note))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.diagnosisCode").value("I20.9"))
                .andExpect(jsonPath("$.authorDoctor.id").value(authorDoctor))
                .andExpect(jsonPath("$.editedBy").value("dr.notes")));
        for (String token : List.of(admin, nurse, receptionist)) {
            mockMvc.perform(post("/api/clinical-notes").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(note)).andExpect(status().isForbidden());
        }

        // nurses read, receptionists never see clinical records
        mockMvc.perform(get("/api/clinical-notes/" + id).header("Authorization", nurse))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessment").value("Suspected angina"));
        mockMvc.perform(get("/api/clinical-notes/" + id).header("Authorization", receptionist))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-notes").header("Authorization", receptionist))
                .andExpect(status().isForbidden());

        // amendments: the author adds version 2, a stale base version is a conflict, others are refused
        amendNote(doctor, id, 1, "Corrected diagnosis", "Confirmed angina", "I20.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.assessment").value("Confirmed angina"))
                .andExpect(jsonPath("$.lastChangeReason").value("Corrected diagnosis"));
        amendNote(doctor, id, 1, "Stale", "Whatever", "I20.0").andExpect(status().isConflict());
        amendNote(otherDoctor, id, 2, "Not mine", "Hijack", "I20.0").andExpect(status().isForbidden());
        amendNote(nurse, id, 2, "Nurse", "Nope", "I20.0").andExpect(status().isForbidden());
        amendNote(admin, id, 2, "Admin review", "Confirmed angina, stable", "I20.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.editedBy").value("admin"))
                .andExpect(jsonPath("$.authorDoctor.id").value(authorDoctor));

        // the whole history is kept, oldest first
        mockMvc.perform(get("/api/clinical-notes/" + id + "/versions").header("Authorization", nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].assessment").value("Suspected angina"))
                .andExpect(jsonPath("$[0].diagnosisCode").value("I20.9"))
                .andExpect(jsonPath("$[0].changeReason").doesNotExist())
                .andExpect(jsonPath("$[1].assessment").value("Confirmed angina"))
                .andExpect(jsonPath("$[2].changeReason").value("Admin review"))
                .andExpect(jsonPath("$[2].editedBy").value("admin"));

        // search sees the CURRENT version: diagnosis prefix, patient filter, /me
        mockMvc.perform(get("/api/clinical-notes").param("patientId", String.valueOf(patient))
                        .param("diagnosisCode", "i20.0").header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].version").value(3));
        mockMvc.perform(get("/api/clinical-notes").param("diagnosisCode", "I20.9").header("Authorization", nurse))
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")]").isEmpty());
        mockMvc.perform(get("/api/clinical-notes").param("patientId", String.valueOf(otherPatient))
                        .header("Authorization", nurse))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/clinical-notes/me").header("Authorization", doctor))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/clinical-notes/me").header("Authorization", otherDoctor))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(delete("/api/clinical-notes/" + id).header("Authorization", admin))
                .andExpect(status().is4xxClientError());

        // validation: content required, bad ICD-10 code, patient must exist
        mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":" + patient + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":999999,\"assessment\":\"x\"}"))
                .andExpect(status().isNotFound());

        // the database itself refuses to rewrite or remove medical history
        assertThatThrownBy(() -> jdbc.update("UPDATE clinical_note_versions SET assessment = 'tampered' WHERE note_id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM clinical_note_versions WHERE note_id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM clinical_notes WHERE id = ?", id))
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_note_versions WHERE note_id = ?",
                Integer.class, id)).isEqualTo(3);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO clinical_note_versions (note_id, version_number, "
                + "edited_by_user_id, created_at) VALUES (?, 4, ?, now())", id, userId("admin")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_clinical_note_versions_has_content");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO clinical_note_versions (note_id, version_number, assessment, "
                + "edited_by_user_id, created_at) VALUES (?, 3, 'dup', ?, now())", id, userId("admin")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_clinical_note_versions_note_version");
    }

    @Test
    void clinicalNoteLinksMustBelongToTheSamePatient() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patientA = createPatient(admin, "CN000000010");
        long patientB = createPatient(admin, "CN000000011");
        long doctorUser = registerUser(admin, "dr.links", "DOCTOR");
        long doctorId = createDoctorLinked(admin, "CN-DOC-3", doctorUser);
        String doctor = "Bearer " + login("dr.links", "Sup3rSecret!");
        Instant start = futureHour(23);
        long appointmentOfA = bookOk(admin, patientA, doctorId, start, start.plus(30, ChronoUnit.MINUTES));

        String forB = "{\"patientId\":%d,\"appointmentId\":%d,\"assessment\":\"x\"}".formatted(patientB, appointmentOfA);
        mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(forB))
                .andExpect(status().isBadRequest());
        String forA = "{\"patientId\":%d,\"appointmentId\":%d,\"assessment\":\"Routine checkup\"}"
                .formatted(patientA, appointmentOfA);
        mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(forA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentOfA));
        mockMvc.perform(get("/api/clinical-notes").param("appointmentId", String.valueOf(appointmentOfA))
                        .header("Authorization", doctor))
                .andExpect(jsonPath("$.totalElements").value(1));
        // writing a note does not touch the appointment
        mockMvc.perform(get("/api/appointments/" + appointmentOfA).header("Authorization", admin))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        // both links at once are refused
        mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":%d,\"appointmentId\":%d,\"hospitalizationId\":1,\"assessment\":\"x\"}"
                                .formatted(patientA, appointmentOfA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentAmendmentsOfTheSameVersionYieldExactlyOneSuccess() throws Exception {
        String admin = "Bearer " + login("admin", "Admin123!");
        long patient = createPatient(admin, "CN000000020");
        long doctorUser = registerUser(admin, "dr.race", "DOCTOR");
        createDoctorLinked(admin, "CN-DOC-4", doctorUser);
        String doctor = "Bearer " + login("dr.race", "Sup3rSecret!");
        long id = idOf(mockMvc.perform(post("/api/clinical-notes").header("Authorization", doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":%d,\"assessment\":\"Initial\"}".formatted(patient)))
                .andExpect(status().isCreated()));

        int requests = 6;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int n = i;
                Callable<Integer> amendment = () -> {
                    ready.countDown();
                    go.await();
                    return amendNote(doctor, id, 1, "Edit " + n, "Assessment " + n, "I10")
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(amendment));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_note_versions WHERE note_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    private ResultActions amendNote(String auth, long id, int baseVersion, String reason, String assessment,
            String code) throws Exception {
        String body = """
                {"baseVersion":%d,"changeReason":"%s","assessment":"%s","diagnosisCode":"%s"}"""
                .formatted(baseVersion, reason, assessment, code);
        return mockMvc.perform(put("/api/clinical-notes/" + id).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- helpers for the hospitalization tests -------------------------------------------------

    /** The starter ward ("Main Wing") of the seeded clinic with this name. */
    private long wardId(String clinicName) {
        return jdbc.queryForObject("SELECT w.id FROM wards w JOIN clinics c ON c.id = w.clinic_id "
                + "WHERE c.name = ? AND w.name = 'Main Wing'", Long.class, clinicName);
    }

    private long clinicId(String name) {
        return jdbc.queryForObject("SELECT id FROM clinics WHERE name = ?", Long.class, name);
    }

    private long createClinic(String auth, String name, Long specialtyId) throws Exception {
        return idOf(mockMvc.perform(post("/api/clinics").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"specialtyId\":%s}".formatted(name, specialtyId)))
                .andExpect(status().isCreated()));
    }

    private long createWard(String auth, long clinicId, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/wards").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clinicId\":%d,\"name\":\"%s\"}".formatted(clinicId, name)))
                .andExpect(status().isCreated()));
    }

    private long createRoom(String auth, long wardId, String number) throws Exception {
        return idOf(mockMvc.perform(post("/api/rooms").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wardId\":%d,\"roomNumber\":\"%s\"}".formatted(wardId, number)))
                .andExpect(status().isCreated()));
    }

    private long createBed(String auth, long roomId, String number) throws Exception {
        return idOf(mockMvc.perform(post("/api/beds").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":%d,\"bedNumber\":\"%s\"}".formatted(roomId, number)))
                .andExpect(status().isCreated()));
    }

    private long registerUser(String auth, String username, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@his.local","password":"Sup3rSecret!",
                                 "fullName":"Test User","roles":["%s"]}""".formatted(username, username, role)))
                .andExpect(status().isCreated());
        return userId(username);
    }

    private ResultActions admit(String auth, long patientId, long doctorId, long bedId) throws Exception {
        String body = """
                {"patientId":%d,"attendingDoctorId":%d,"bedId":%d,"admissionType":"EMERGENCY",
                 "admissionSource":"EMERGENCY_DEPARTMENT","admissionReason":"Chest pain",
                 "initialDiagnosisCode":"i20.9","initialDiagnosis":"Suspected angina"}""".formatted(patientId, doctorId, bedId);
        return mockMvc.perform(post("/api/hospitalizations").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions admitWithAppointment(String auth, long patientId, long doctorId, long bedId,
            long appointmentId) throws Exception {
        String body = """
                {"patientId":%d,"attendingDoctorId":%d,"bedId":%d,"admissionType":"ELECTIVE",
                 "admissionSource":"APPOINTMENT","admissionReason":"Follow-up",
                 "appointmentId":%d}""".formatted(patientId, doctorId, bedId, appointmentId);
        return mockMvc.perform(post("/api/hospitalizations").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions transfer(String auth, long hospitalizationId, long bedId) throws Exception {
        return mockMvc.perform(post("/api/hospitalizations/" + hospitalizationId + "/transfer")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bedId\":%d,\"reason\":\"Needs monitoring\"}".formatted(bedId)));
    }

    /** Inserts a stay together with its admission record in ONE statement (every stay must have one at commit). */
    private long insertHospitalization(long patientId, long doctorId, String status, String admissionSql,
            String dischargeSql) {
        return jdbc.queryForObject("WITH h AS (INSERT INTO hospitalizations (patient_id, attending_doctor_id, status, "
                + "admission_time, discharge_time, created_at, updated_at) VALUES (?, ?, ?, " + admissionSql + ", "
                + (dischargeSql == null ? "NULL" : dischargeSql) + ", now(), now()) RETURNING id), "
                + "a AS (INSERT INTO admission_records (hospitalization_id, admission_type, admission_source, reason, "
                + "created_at, updated_at) SELECT id, 'ELECTIVE', 'OTHER', 'Direct insert', now(), now() FROM h) "
                + "SELECT id FROM h", Long.class, patientId, doctorId, status);
    }

    private void insertAssignment(long hospitalizationId, long bedId, String startSql, String endSql) {
        jdbc.update("INSERT INTO bed_assignments (hospitalization_id, bed_id, start_time, end_time, created_at, "
                + "updated_at) VALUES (?, ?, " + startSql + ", " + (endSql == null ? "NULL" : endSql)
                + ", now(), now())", hospitalizationId, bedId);
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
