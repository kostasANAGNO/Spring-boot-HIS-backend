package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.AdministrationRequest;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.DispenseRequest;
import com.kostasanagno.his.dto.MedicationOrderCloseRequest;
import com.kostasanagno.his.dto.MedicationOrderRequest;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationOrderSearchCriteria;
import com.kostasanagno.his.entity.AdministrationOutcome;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.entity.MedicationAdministration;
import com.kostasanagno.his.entity.MedicationDispensing;
import com.kostasanagno.his.entity.MedicationForm;
import com.kostasanagno.his.entity.MedicationOrder;
import com.kostasanagno.his.entity.MedicationOrderStatus;
import com.kostasanagno.his.entity.MedicationRoute;
import com.kostasanagno.his.entity.MedicationSchedule;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.Room;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.entity.Ward;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapperImpl;
import com.kostasanagno.his.mapper.MedicationOrderMapperImpl;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.MedicationAdministrationRepository;
import com.kostasanagno.his.repository.MedicationDispensingRepository;
import com.kostasanagno.his.repository.MedicationOrderRepository;
import com.kostasanagno.his.repository.MedicationRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.MedicationProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MedicationOrderServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant ADMITTED_AT = NOW.minus(Duration.ofDays(2));

    @Mock
    private MedicationOrderRepository orderRepository;
    @Mock
    private MedicationDispensingRepository dispensingRepository;
    @Mock
    private MedicationAdministrationRepository administrationRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private HospitalizationRepository hospitalizationRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BedAssignmentRepository bedAssignmentRepository;

    private MedicationOrderServiceImpl service;
    private Patient patient;
    private Doctor doctor;
    private Hospitalization hospitalization;
    private Medication paracetamol;
    private Medication morphine;
    private final Caller doctorCaller = new Caller("dr.house", Set.of(Role.DOCTOR));
    private final Caller pharmacist = new Caller("pharma", Set.of(Role.PHARMACIST));
    private final Caller admin = new Caller("admin", Set.of(Role.ADMIN));
    private final Caller nurse = new Caller("nurse", Set.of(Role.NURSE));

    @BeforeEach
    void setUp() {
        service = new MedicationOrderServiceImpl(orderRepository, dispensingRepository, administrationRepository,
                medicationRepository, hospitalizationRepository, doctorRepository, userRepository,
                bedAssignmentRepository, new MedicationOrderMapperImpl(), new BedMapperImpl(),
                new MedicationProperties(Duration.ofMinutes(30)), Clock.fixed(NOW, ZoneOffset.UTC));

        Specialty cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        doctor = Doctor.builder().firstName("Gregory").lastName("House").licenseNumber("MD-1").specialty(cardiology).build();
        doctor.setId(20L);
        hospitalization = Hospitalization.builder().patient(patient).attendingDoctor(doctor)
                .status(HospitalizationStatus.ADMITTED).admissionTime(ADMITTED_AT).build();
        hospitalization.setId(40L);
        paracetamol = medication(5L, "PARACETAMOL-500", false);
        morphine = medication(6L, "MORPHINE-10", true);

        lenient().when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(user(1L, "dr.house")));
        lenient().when(userRepository.findByUsernameIgnoreCase("pharma")).thenReturn(Optional.of(user(2L, "pharma")));
        lenient().when(userRepository.findByUsernameIgnoreCase("nurse")).thenReturn(Optional.of(user(3L, "nurse")));
        lenient().when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user(4L, "admin")));
        lenient().when(orderRepository.saveAndFlush(any(MedicationOrder.class))).thenAnswer(inv -> {
            MedicationOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(100L);
                o.setCreatedAt(NOW);
                o.setUpdatedAt(NOW);
            }
            return o;
        });
        lenient().when(dispensingRepository.saveAndFlush(any(MedicationDispensing.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(administrationRepository.saveAndFlush(any(MedicationAdministration.class))).thenAnswer(inv -> {
            MedicationAdministration a = inv.getArgument(0);
            a.setId(900L);
            a.setCreatedAt(NOW);
            return a;
        });
        lenient().when(bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(40L)).thenReturn(Optional.empty());
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static Medication medication(long id, String code, boolean controlled) {
        Medication m = Medication.builder().code(code).name(code).form(MedicationForm.TABLET).strength("500 mg")
                .controlled(controlled).build();
        m.setId(id);
        return m;
    }

    private static User user(long id, String username) {
        User u = User.builder().username(username).roles(Set.of(Role.NURSE)).build();
        u.setId(id);
        return u;
    }

    private MedicationOrder order(MedicationOrderStatus status) {
        MedicationOrder o = MedicationOrder.builder().hospitalization(hospitalization).medication(paracetamol)
                .prescribedBy(doctor).doseAmount(new BigDecimal("500.000")).doseUnit("mg").route(MedicationRoute.ORAL)
                .scheduleType(MedicationSchedule.SCHEDULED).intervalHours(8).startTime(NOW.minus(Duration.ofDays(1)))
                .status(status).build();
        o.setId(100L);
        o.setCreatedAt(NOW);
        o.setUpdatedAt(NOW);
        return o;
    }

    private MedicationOrder lockedOrder(MedicationOrderStatus status) {
        MedicationOrder o = order(status);
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(o));
        return o;
    }

    private MedicationOrderRequest request(MedicationSchedule schedule, Integer interval, Instant start, Instant end) {
        return new MedicationOrderRequest(40L, 5L, new BigDecimal("500"), " mg ", MedicationRoute.ORAL, schedule, interval,
                start, end, " With food ");
    }

    private void stubOrdering() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(hospitalization));
        when(medicationRepository.findById(5L)).thenReturn(Optional.of(paracetamol));
    }

    private MedicationAdministration given(MedicationOrder order, Instant at) {
        return MedicationAdministration.builder().order(order).outcome(AdministrationOutcome.GIVEN)
                .administeredAt(at).doseAmount(new BigDecimal("500.000")).administeredBy(user(3L, "nurse")).build();
    }

    private void stubLastGiven(MedicationOrder order, Instant at) {
        when(administrationRepository.findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(100L, AdministrationOutcome.GIVEN))
                .thenReturn(Optional.of(given(order, at)));
    }

    private static AdministrationRequest give() {
        return new AdministrationRequest(AdministrationOutcome.GIVEN, null, null, null);
    }

    // ---- ordering ------------------------------------------------------------------------------

    @Test
    void aDoctorOrdersForAnAdmittedPatientAndTheOrderStartsPending() {
        stubOrdering();

        MedicationOrderResponse response = service.create(request(MedicationSchedule.SCHEDULED, 8, null, null), doctorCaller);

        assertThat(response.status()).isEqualTo(MedicationOrderStatus.PENDING);
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
        assertThat(response.medication().code()).isEqualTo("PARACETAMOL-500");
        assertThat(response.prescribedBy().fullName()).isEqualTo("Gregory House");
        assertThat(response.doseAmount()).isEqualByComparingTo("500");
        assertThat(response.doseUnit()).isEqualTo("mg");
        assertThat(response.intervalHours()).isEqualTo(8);
        assertThat(response.startTime()).isEqualTo(NOW);
        assertThat(response.instructions()).isEqualTo("With food");
        assertThat(response.hospitalizationId()).isEqualTo(40L);
    }

    @Test
    void theResponseShowsWhereThePatientIsNow() {
        stubOrdering();
        Ward ward = Ward.builder().clinic(Clinic.builder().name("Cardiology").build()).name("Main Wing").build();
        Room room = Room.builder().ward(ward).roomNumber("101").build();
        Bed bed = Bed.builder().room(room).bedNumber("A").build();
        BedAssignment assignment = BedAssignment.builder().hospitalization(hospitalization).bed(bed).startTime(ADMITTED_AT).build();
        when(bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(40L)).thenReturn(Optional.of(assignment));

        MedicationOrderResponse response = service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller);

        assertThat(response.currentBed().bedNumber()).isEqualTo("A");
        assertThat(response.currentBed().wardName()).isEqualTo("Main Wing");
        assertThat(response.currentBed().clinicName()).isEqualTo("Cardiology");
    }

    @Test
    void onlyADoctorWithALinkedProfileCanOrder() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, null, null, null), nurse))
                .isInstanceOf(AccessDeniedException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void anOrderNeedsAnAdmittedPatientAnExistingHospitalizationAndAnActiveMedication() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Hospitalization");

        hospitalization.setStatus(HospitalizationStatus.DISCHARGED);
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(hospitalization));
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ADMITTED");

        hospitalization.setStatus(HospitalizationStatus.ADMITTED);
        when(medicationRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Medication");

        paracetamol.setActive(false);
        when(medicationRepository.findById(5L)).thenReturn(Optional.of(paracetamol));
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("catalog");
    }

    @Test
    void theIntervalGoesWithTheSchedule() {
        stubOrdering();

        assertThatThrownBy(() -> service.create(request(MedicationSchedule.ONCE, 6, null, null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.SCHEDULED, null, null, null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> service.create(request(MedicationSchedule.AS_NEEDED, null, null, null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("required");
        assertThat(service.create(request(MedicationSchedule.AS_NEEDED, 6, null, null), doctorCaller).intervalHours()).isEqualTo(6);
        assertThat(service.create(request(MedicationSchedule.ONCE, null, null, null), doctorCaller).intervalHours()).isNull();
    }

    @Test
    void thePeriodRules() {
        stubOrdering();

        assertThatThrownBy(() -> service.create(
                request(MedicationSchedule.ONCE, null, ADMITTED_AT.minusSeconds(1), null), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("admission");
        assertThatThrownBy(() -> service.create(
                request(MedicationSchedule.ONCE, null, NOW, NOW), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after");
        assertThatThrownBy(() -> service.create(
                request(MedicationSchedule.ONCE, null, NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(1))), doctorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
        // a start in the future and a start back to the admission are fine
        assertThat(service.create(request(MedicationSchedule.ONCE, null, NOW.plus(Duration.ofHours(6)), null), doctorCaller)
                .startTime()).isEqualTo(NOW.plus(Duration.ofHours(6)));
        assertThat(service.create(request(MedicationSchedule.ONCE, null, ADMITTED_AT, null), doctorCaller)
                .startTime()).isEqualTo(ADMITTED_AT);
    }

    // ---- pharmacy ------------------------------------------------------------------------------

    @Test
    void thePharmacyDispensesAPendingOrder() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.PENDING);

        MedicationOrderResponse response = service.dispense(100L, new DispenseRequest(30, " One pack "), pharmacist);

        assertThat(response.status()).isEqualTo(MedicationOrderStatus.DISPENSED);
        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.DISPENSED);
        ArgumentCaptor<MedicationDispensing> captor = ArgumentCaptor.forClass(MedicationDispensing.class);
        verify(dispensingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(30);
        assertThat(captor.getValue().getNotes()).isEqualTo("One pack");
        assertThat(captor.getValue().getDispensedBy().getUsername()).isEqualTo("pharma");
        assertThat(captor.getValue().getDispensedAt()).isEqualTo(NOW);
    }

    @Test
    void aDispensedOrderCanBeSuppliedAgainAndStaysDispensed() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);

        service.dispense(100L, new DispenseRequest(10, null), admin);

        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.DISPENSED);
        verify(dispensingRepository).saveAndFlush(any(MedicationDispensing.class));
    }

    @Test
    void controlledMedicationIsDispensedByPharmacistsOnly() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.PENDING);
        order.setMedication(morphine);

        assertThatThrownBy(() -> service.dispense(100L, new DispenseRequest(1, null), admin))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("pharmacist");
        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.PENDING);
        verify(dispensingRepository, never()).saveAndFlush(any());

        assertThat(service.dispense(100L, new DispenseRequest(1, null), pharmacist).status())
                .isEqualTo(MedicationOrderStatus.DISPENSED);
    }

    @ParameterizedTest
    @EnumSource(value = MedicationOrderStatus.class, names = {"REJECTED", "CANCELLED", "STOPPED", "COMPLETED"})
    void aClosedOrderCannotBeDispensed(MedicationOrderStatus status) {
        lockedOrder(status);

        assertThatThrownBy(() -> service.dispense(100L, new DispenseRequest(1, null), pharmacist))
                .isInstanceOf(BusinessRuleException.class);
        verify(dispensingRepository, never()).saveAndFlush(any());
    }

    @Test
    void nothingIsDispensedForADischargedPatient() {
        lockedOrder(MedicationOrderStatus.PENDING);
        hospitalization.setStatus(HospitalizationStatus.DISCHARGED);

        assertThatThrownBy(() -> service.dispense(100L, new DispenseRequest(1, null), pharmacist))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("discharged");
    }

    @Test
    void thePharmacyRejectsAPendingOrderWithAReason() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.PENDING);

        MedicationOrderResponse response = service.reject(100L, new MedicationOrderCloseRequest(" Interacts with heparin "), pharmacist);

        assertThat(response.status()).isEqualTo(MedicationOrderStatus.REJECTED);
        assertThat(response.closedReason()).isEqualTo("Interacts with heparin");
        assertThat(response.closedAt()).isEqualTo(NOW);
        assertThat(order.getClosedBy().getUsername()).isEqualTo("pharma");
    }

    @Test
    void cancelAndStopFollowTheStatusMachine() {
        MedicationOrder pending = lockedOrder(MedicationOrderStatus.PENDING);
        assertThat(service.cancel(100L, new MedicationOrderCloseRequest("Wrong drug"), doctorCaller).status())
                .isEqualTo(MedicationOrderStatus.CANCELLED);
        assertThat(pending.getClosedBy().getUsername()).isEqualTo("dr.house");

        lockedOrder(MedicationOrderStatus.DISPENSED);
        assertThat(service.stop(100L, new MedicationOrderCloseRequest("Course finished"), doctorCaller).status())
                .isEqualTo(MedicationOrderStatus.STOPPED);

        lockedOrder(MedicationOrderStatus.DISPENSED);
        assertThatThrownBy(() -> service.cancel(100L, new MedicationOrderCloseRequest("x"), doctorCaller))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reject(100L, new MedicationOrderCloseRequest("x"), pharmacist))
                .isInstanceOf(BusinessRuleException.class);

        lockedOrder(MedicationOrderStatus.PENDING);
        assertThatThrownBy(() -> service.stop(100L, new MedicationOrderCloseRequest("x"), doctorCaller))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = MedicationOrderStatus.class, names = {"REJECTED", "CANCELLED", "STOPPED", "COMPLETED"})
    void finalOrdersCannotBeClosedAgain(MedicationOrderStatus status) {
        lockedOrder(status);

        assertThatThrownBy(() -> service.stop(100L, new MedicationOrderCloseRequest("x"), doctorCaller))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.cancel(100L, new MedicationOrderCloseRequest("x"), doctorCaller))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reject(100L, new MedicationOrderCloseRequest("x"), pharmacist))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- bedside -------------------------------------------------------------------------------

    @Test
    void aGivenDoseDefaultsToTheOrderedDoseAndTheOrderStaysDispensed() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);
        when(administrationRepository.findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(100L, AdministrationOutcome.GIVEN))
                .thenReturn(Optional.empty());

        AdministrationResponse response = service.administer(100L, give(), nurse);

        assertThat(response.outcome()).isEqualTo(AdministrationOutcome.GIVEN);
        assertThat(response.doseAmount()).isEqualByComparingTo("500");
        assertThat(response.administeredAt()).isEqualTo(NOW);
        assertThat(response.administeredBy()).isEqualTo("nurse");
        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(response.hospitalizationId()).isEqualTo(40L);
        assertThat(response.patientId()).isEqualTo(10L);
        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.DISPENSED);
    }

    @Test
    void aOnceOrderIsCompletedByItsDose() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);
        order.setScheduleType(MedicationSchedule.ONCE);
        order.setIntervalHours(null);
        when(administrationRepository.findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(100L, AdministrationOutcome.GIVEN))
                .thenReturn(Optional.empty());

        service.administer(100L, give(), nurse);

        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.COMPLETED);
        assertThat(order.getClosedAt()).isEqualTo(NOW);
        assertThat(order.getClosedReason()).isNull();
    }

    @Test
    void aRefusedOrHeldDoseIsRecordedWithoutADoseAndWithoutClosingTheOrder() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);
        order.setScheduleType(MedicationSchedule.ONCE);
        order.setIntervalHours(null);

        AdministrationResponse refused = service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.REFUSED, null, new BigDecimal("500"), " Patient refused "), nurse);

        assertThat(refused.outcome()).isEqualTo(AdministrationOutcome.REFUSED);
        assertThat(refused.doseAmount()).isNull();
        assertThat(refused.notes()).isEqualTo("Patient refused");
        assertThat(order.getStatus()).isEqualTo(MedicationOrderStatus.DISPENSED);
        verify(administrationRepository, never()).findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = MedicationOrderStatus.class, names = {"PENDING", "REJECTED", "CANCELLED", "STOPPED", "COMPLETED"})
    void onlyADispensedOrderCanBeAdministered(MedicationOrderStatus status) {
        lockedOrder(status);

        assertThatThrownBy(() -> service.administer(100L, give(), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DISPENSED");
        verify(administrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void nothingIsAdministeredToADischargedPatient() {
        lockedOrder(MedicationOrderStatus.DISPENSED);
        hospitalization.setStatus(HospitalizationStatus.DISCHARGED);

        assertThatThrownBy(() -> service.administer(100L, give(), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("discharged");
    }

    @Test
    void theTimeOfAnAdministrationMustFitTheOrder() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);
        order.setEndTime(NOW.minus(Duration.ofHours(3)));

        assertThatThrownBy(() -> service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.HELD, NOW.plusSeconds(1), null, null), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.HELD, order.getStartTime().minusSeconds(1), null, null), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("before the order started");
        assertThatThrownBy(() -> service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.HELD, NOW.minus(Duration.ofHours(2)), null, null), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ended");
    }

    @Test
    void theDoseGivenCannotExceedTheOrderedDose() {
        lockedOrder(MedicationOrderStatus.DISPENSED);

        assertThatThrownBy(() -> service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.GIVEN, null, new BigDecimal("500.001"), null), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceeds");
        verify(administrationRepository, never()).saveAndFlush(any());

        when(administrationRepository.findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(100L, AdministrationOutcome.GIVEN))
                .thenReturn(Optional.empty());
        assertThat(service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.GIVEN, null, new BigDecimal("250"), null), nurse)
                .doseAmount()).isEqualByComparingTo("250");
    }

    @Test
    void aRepeatDoseMustKeepTheIntervalMinusTheTolerance() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);   // every 8 hours, tolerance 30 minutes

        stubLastGiven(order, NOW.minus(Duration.ofHours(2)));                    // next due at +6h, i.e. not now
        assertThatThrownBy(() -> service.administer(100L, give(), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not due");

        stubLastGiven(order, NOW.minus(Duration.ofHours(7)));                    // due at +1h minus 30 minutes: still early
        assertThatThrownBy(() -> service.administer(100L, give(), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not due");
        verify(administrationRepository, never()).saveAndFlush(any());

        stubLastGiven(order, NOW.minus(Duration.ofHours(7)).minus(Duration.ofMinutes(45)));   // 15 minutes early: allowed
        assertThat(service.administer(100L, give(), nurse).outcome()).isEqualTo(AdministrationOutcome.GIVEN);

        stubLastGiven(order, NOW.minus(Duration.ofHours(8)));                    // exactly on time
        assertThat(service.administer(100L, give(), nurse).outcome()).isEqualTo(AdministrationOutcome.GIVEN);
    }

    @Test
    void aDoseCannotBeRecordedAtOrBeforeTheLastDose() {
        MedicationOrder order = lockedOrder(MedicationOrderStatus.DISPENSED);
        stubLastGiven(order, NOW.minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> service.administer(100L,
                new AdministrationRequest(AdministrationOutcome.GIVEN, NOW.minus(Duration.ofHours(2)), null, null), nurse))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already recorded");
    }

    // ---- when the patient leaves ---------------------------------------------------------------

    @Test
    void openOrdersAreClosedWhenThePatientIsDischarged() {
        MedicationOrder pending = order(MedicationOrderStatus.PENDING);
        pending.setId(101L);
        MedicationOrder dispensed = order(MedicationOrderStatus.DISPENSED);
        dispensed.setId(102L);
        when(orderRepository.findIdsByHospitalizationAndStatusIn(any(), any(Collection.class))).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(pending));
        when(orderRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(dispensed));

        service.closeOpenOrders(40L, null);

        assertThat(pending.getStatus()).isEqualTo(MedicationOrderStatus.CANCELLED);
        assertThat(dispensed.getStatus()).isEqualTo(MedicationOrderStatus.STOPPED);
        for (MedicationOrder o : List.of(pending, dispensed)) {
            assertThat(o.getClosedReason()).isEqualTo("The patient was discharged");
            assertThat(o.getClosedAt()).isEqualTo(NOW);
            assertThat(o.getClosedBy()).isNull();
        }
    }

    @Test
    void anOrderThatChangedWhileWaitingForTheLockIsLeftAlone() {
        MedicationOrder alreadyDone = order(MedicationOrderStatus.COMPLETED);
        when(orderRepository.findIdsByHospitalizationAndStatusIn(any(), any(Collection.class))).thenReturn(List.of(100L));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(alreadyDone));

        service.closeOpenOrders(40L, null);

        assertThat(alreadyDone.getStatus()).isEqualTo(MedicationOrderStatus.COMPLETED);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void nothingHappensWhenThereAreNoOpenOrders() {
        when(orderRepository.findIdsByHospitalizationAndStatusIn(any(), any(Collection.class))).thenReturn(List.of());

        service.closeOpenOrders(40L, null);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    // ---- reads ---------------------------------------------------------------------------------

    @Test
    void unknownOrderIs404() {
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getAdministrations(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getDispensings(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.administer(9L, give(), nurse)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.dispense(9L, new DispenseRequest(1, null), pharmacist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchShowsWhereEachPatientIsWithOneExtraQuery() {
        MedicationOrder order = order(MedicationOrderStatus.PENDING);
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        Ward ward = Ward.builder().clinic(Clinic.builder().name("Cardiology").build()).name("Main Wing").build();
        Bed bed = Bed.builder().room(Room.builder().ward(ward).roomNumber("101").build()).bedNumber("A").build();
        BedAssignment assignment = BedAssignment.builder().hospitalization(hospitalization).bed(bed).startTime(ADMITTED_AT).build();
        when(bedAssignmentRepository.findByHospitalizationIdInAndEndTimeIsNull(List.of(40L))).thenReturn(List.of(assignment));

        Page<MedicationOrderResponse> page = service.search(
                new MedicationOrderSearchCriteria(null, null, null, null, MedicationOrderStatus.PENDING), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).currentBed().roomNumber()).isEqualTo("101");
    }

    @Test
    void myOrdersAreRestrictedToTheLinkedDoctor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MedicationOrderResponse> page = service.searchForDoctorUser(
                new MedicationOrderSearchCriteria(null, null, null, 999L, null), pageable, doctorCaller);

        assertThat(page.getTotalElements()).isZero();
        verify(bedAssignmentRepository, never()).findByHospitalizationIdInAndEndTimeIsNull(any());

        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.searchForDoctorUser(null, pageable, new Caller("nobody", Set.of(Role.DOCTOR))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
