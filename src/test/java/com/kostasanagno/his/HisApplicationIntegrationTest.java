package com.kostasanagno.his;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
