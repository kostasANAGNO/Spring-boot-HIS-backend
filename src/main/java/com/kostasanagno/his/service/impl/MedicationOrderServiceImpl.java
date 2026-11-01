package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.AdministrationRequest;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.AdministrationSearchCriteria;
import com.kostasanagno.his.dto.BedSummary;
import com.kostasanagno.his.dto.DispenseRequest;
import com.kostasanagno.his.dto.DispensingResponse;
import com.kostasanagno.his.dto.MedicationOrderCloseRequest;
import com.kostasanagno.his.dto.MedicationOrderRequest;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationOrderSearchCriteria;
import com.kostasanagno.his.entity.AdministrationOutcome;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.entity.MedicationAdministration;
import com.kostasanagno.his.entity.MedicationDispensing;
import com.kostasanagno.his.entity.MedicationOrder;
import com.kostasanagno.his.entity.MedicationOrderStatus;
import com.kostasanagno.his.entity.MedicationSchedule;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapper;
import com.kostasanagno.his.mapper.MedicationOrderMapper;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.MedicationAdministrationRepository;
import com.kostasanagno.his.repository.MedicationDispensingRepository;
import com.kostasanagno.his.repository.MedicationOrderRepository;
import com.kostasanagno.his.repository.MedicationRepository;
import com.kostasanagno.his.repository.MedicationSpecifications;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.MedicationOrderService;
import com.kostasanagno.his.service.MedicationProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety rules live here (small private methods); the database repeats the important ones (status moves forward only,
 * nothing administered unless dispensed, nothing edited or deleted). Operations that change an order lock its row
 * first. Only ids are logged: never names, doses or notes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MedicationOrderServiceImpl implements MedicationOrderService {

    private static final String RESOURCE = "Medication order";
    private static final String DISCHARGE_REASON = "The patient was discharged";

    private final MedicationOrderRepository orderRepository;
    private final MedicationDispensingRepository dispensingRepository;
    private final MedicationAdministrationRepository administrationRepository;
    private final MedicationRepository medicationRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final MedicationOrderMapper orderMapper;
    private final BedMapper bedMapper;
    private final MedicationProperties properties;
    private final Clock clock;

    // ---- ordering ------------------------------------------------------------------------------

    @Override
    public MedicationOrderResponse create(MedicationOrderRequest request, Caller caller) {
        Doctor prescriber = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .orElseThrow(() -> new AccessDeniedException("Only a doctor with a linked profile can order medication"));
        Hospitalization hospitalization = hospitalizationRepository.findById(request.hospitalizationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Hospitalization", request.hospitalizationId()));
        requireAdmitted(hospitalization, "Medication can only be ordered for an ADMITTED hospitalization");
        Medication medication = medicationRepository.findById(request.medicationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Medication", request.medicationId()));
        if (!medication.isActive()) {
            throw new BusinessRuleException("Medication %d is not in the catalog any more".formatted(medication.getId()));
        }
        validateSchedule(request);
        Instant now = clock.instant();
        Instant start = request.startTime() == null ? now : request.startTime();
        if (start.isBefore(hospitalization.getAdmissionTime())) {
            throw new BusinessRuleException("startTime cannot be before the admission");
        }
        if (request.endTime() != null && !request.endTime().isAfter(start)) {
            throw new BusinessRuleException("endTime must be after startTime");
        }
        if (request.endTime() != null && !request.endTime().isAfter(now)) {
            throw new BusinessRuleException("endTime is already in the past");
        }

        MedicationOrder order = orderMapper.toEntity(request);
        order.setHospitalization(hospitalization);
        order.setMedication(medication);
        order.setPrescribedBy(prescriber);
        order.setStartTime(start);
        order.setDoseUnit(request.doseUnit().trim());
        order.setInstructions(blankToNull(request.instructions()));
        order.setStatus(MedicationOrderStatus.PENDING);
        MedicationOrder saved = orderRepository.saveAndFlush(order);
        log.info("Ordered medication order {} (hospitalization {}, doctor {})", saved.getId(),
                hospitalization.getId(), prescriber.getId());
        return present(saved, currentBed(hospitalization.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicationOrderResponse getById(Long id) {
        MedicationOrder order = find(id);
        return present(order, currentBed(order.getHospitalization().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MedicationOrderResponse> search(MedicationOrderSearchCriteria criteria, Pageable pageable) {
        Page<MedicationOrder> page = orderRepository.findAll(MedicationSpecifications.orders(criteria), pageable);
        if (page.isEmpty()) {
            return page.map(order -> present(order, null));
        }
        // where each patient is now: one query for the whole page
        List<Long> hospitalizationIds = page.getContent().stream().map(o -> o.getHospitalization().getId()).distinct().toList();
        Map<Long, BedAssignment> beds = bedAssignmentRepository
                .findByHospitalizationIdInAndEndTimeIsNull(hospitalizationIds).stream()
                .collect(Collectors.toMap(a -> a.getHospitalization().getId(), Function.identity()));
        return page.map(order -> present(order, bedOf(beds.get(order.getHospitalization().getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MedicationOrderResponse> searchForDoctorUser(MedicationOrderSearchCriteria criteria, Pageable pageable,
            Caller caller) {
        Doctor doctor = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile is linked to the current user"));
        MedicationOrderSearchCriteria own = (criteria == null
                ? new MedicationOrderSearchCriteria(null, null, null, null, null) : criteria)
                .withPrescribedByDoctorId(doctor.getId());
        return search(own, pageable);
    }

    // ---- pharmacy ------------------------------------------------------------------------------

    @Override
    public MedicationOrderResponse dispense(Long id, DispenseRequest request, Caller caller) {
        MedicationOrder order = lock(id);
        if (order.getStatus() != MedicationOrderStatus.PENDING && order.getStatus() != MedicationOrderStatus.DISPENSED) {
            throw new BusinessRuleException("Only a PENDING or DISPENSED order can be dispensed");
        }
        requireAdmitted(order.getHospitalization(), "The patient has been discharged");
        if (order.getMedication().isControlled() && !caller.roles().contains(Role.PHARMACIST)) {
            throw new AccessDeniedException("Controlled medication can only be dispensed by a pharmacist");
        }
        Instant now = clock.instant();
        dispensingRepository.saveAndFlush(MedicationDispensing.builder()
                .order(order)
                .quantity(request.quantity())
                .dispensedBy(userOf(caller))
                .dispensedAt(now)
                .notes(blankToNull(request.notes()))
                .build());
        if (order.getStatus() == MedicationOrderStatus.PENDING) {
            order.setStatus(MedicationOrderStatus.DISPENSED);
        }
        MedicationOrder saved = orderRepository.saveAndFlush(order);
        log.info("Dispensed medication order {}", id);
        return present(saved, currentBed(saved.getHospitalization().getId()));
    }

    @Override
    public MedicationOrderResponse reject(Long id, MedicationOrderCloseRequest request, Caller caller) {
        return close(id, MedicationOrderStatus.REJECTED, request.reason(), caller);
    }

    @Override
    public MedicationOrderResponse cancel(Long id, MedicationOrderCloseRequest request, Caller caller) {
        return close(id, MedicationOrderStatus.CANCELLED, request.reason(), caller);
    }

    @Override
    public MedicationOrderResponse stop(Long id, MedicationOrderCloseRequest request, Caller caller) {
        return close(id, MedicationOrderStatus.STOPPED, request.reason(), caller);
    }

    // ---- bedside -------------------------------------------------------------------------------

    @Override
    public AdministrationResponse administer(Long id, AdministrationRequest request, Caller caller) {
        MedicationOrder order = lock(id);
        if (order.getStatus() != MedicationOrderStatus.DISPENSED) {
            throw new BusinessRuleException("Only a DISPENSED order can be administered");
        }
        requireAdmitted(order.getHospitalization(), "The patient has been discharged");
        Instant now = clock.instant();
        Instant at = request.administeredAt() == null ? now : request.administeredAt();
        validateAdministrationTime(order, at, now);

        BigDecimal dose = null;
        if (request.outcome() == AdministrationOutcome.GIVEN) {
            dose = doseGiven(order, request.doseAmount());
            assertDoseSpacing(order, at);
        }
        MedicationAdministration saved = administrationRepository.saveAndFlush(MedicationAdministration.builder()
                .order(order)
                .outcome(request.outcome())
                .administeredAt(at)
                .doseAmount(dose)
                .notes(blankToNull(request.notes()))
                .administeredBy(userOf(caller))
                .build());
        if (request.outcome() == AdministrationOutcome.GIVEN && order.getScheduleType() == MedicationSchedule.ONCE) {
            order.setStatus(MedicationOrderStatus.COMPLETED);
            order.setClosedAt(now);
            orderRepository.saveAndFlush(order);
        }
        log.info("Recorded an administration ({}) for medication order {}", saved.getOutcome(), id);
        return orderMapper.toAdministrationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispensingResponse> getDispensings(Long id) {
        find(id);
        return dispensingRepository.findByOrderIdOrderByDispensedAtAscIdAsc(id).stream()
                .map(orderMapper::toDispensingResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdministrationResponse> getAdministrations(Long id) {
        find(id);
        return administrationRepository.findByOrderIdOrderByAdministeredAtAscIdAsc(id).stream()
                .map(orderMapper::toAdministrationResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdministrationResponse> searchAdministrations(AdministrationSearchCriteria criteria, Pageable pageable) {
        return administrationRepository.findAll(MedicationSpecifications.administrations(criteria), pageable)
                .map(orderMapper::toAdministrationResponse);
    }

    // ---- closing -------------------------------------------------------------------------------

    @Override
    public void closeOpenOrders(Long hospitalizationId, String reason) {
        List<Long> openIds = orderRepository.findIdsByHospitalizationAndStatusIn(hospitalizationId,
                EnumSet.of(MedicationOrderStatus.PENDING, MedicationOrderStatus.DISPENSED));
        Instant now = clock.instant();
        for (Long id : openIds) {
            MedicationOrder order = lock(id);   // waits for a dispensing/administration in flight, then sees its result
            MedicationOrderStatus target = order.getStatus() == MedicationOrderStatus.PENDING
                    ? MedicationOrderStatus.CANCELLED : MedicationOrderStatus.STOPPED;
            if (order.getStatus().canTransitionTo(target)) {
                order.setStatus(target);
                order.setClosedReason(reason == null ? DISCHARGE_REASON : reason);
                order.setClosedAt(now);
                orderRepository.saveAndFlush(order);
            }
        }
        if (!openIds.isEmpty()) {
            log.info("Closed {} open medication order(s) of hospitalization {}", openIds.size(), hospitalizationId);
        }
    }

    private MedicationOrderResponse close(Long id, MedicationOrderStatus target, String reason, Caller caller) {
        MedicationOrder order = lock(id);
        if (!order.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("Cannot change a medication order from %s to %s"
                    .formatted(order.getStatus(), target));
        }
        order.setStatus(target);
        order.setClosedReason(reason.trim());
        order.setClosedAt(clock.instant());
        order.setClosedBy(userOf(caller));
        MedicationOrder saved = orderRepository.saveAndFlush(order);
        log.info("Medication order {} is now {}", id, target);
        return present(saved, currentBed(saved.getHospitalization().getId()));
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** ONCE has no interval; SCHEDULED and AS_NEEDED need one (hours). */
    private static void validateSchedule(MedicationOrderRequest request) {
        boolean once = request.scheduleType() == MedicationSchedule.ONCE;
        if (once && request.intervalHours() != null) {
            throw new BusinessRuleException("intervalHours must be empty for a ONCE order");
        }
        if (!once && request.intervalHours() == null) {
            throw new BusinessRuleException("intervalHours is required for a %s order".formatted(request.scheduleType()));
        }
    }

    private static void requireAdmitted(Hospitalization hospitalization, String message) {
        if (hospitalization.getStatus() != HospitalizationStatus.ADMITTED) {
            throw new BusinessRuleException(message);
        }
    }

    /** Not in the future, not before the order started, not after it ended. */
    private static void validateAdministrationTime(MedicationOrder order, Instant at, Instant now) {
        if (at.isAfter(now)) {
            throw new BusinessRuleException("administeredAt must not be in the future");
        }
        if (at.isBefore(order.getStartTime())) {
            throw new BusinessRuleException("administeredAt is before the order started");
        }
        if (order.getEndTime() != null && at.isAfter(order.getEndTime())) {
            throw new BusinessRuleException("The order had already ended at that time");
        }
    }

    /** The dose given defaults to the ordered dose and can never exceed it. */
    private static BigDecimal doseGiven(MedicationOrder order, BigDecimal requested) {
        BigDecimal dose = requested == null ? order.getDoseAmount() : requested;
        if (dose.compareTo(order.getDoseAmount()) > 0) {
            throw new BusinessRuleException("The dose given exceeds the ordered dose");
        }
        return dose;
    }

    /** Guards against a double dose: a repeat dose must keep the order's interval (minus a small tolerance). */
    private void assertDoseSpacing(MedicationOrder order, Instant at) {
        administrationRepository.findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(order.getId(), AdministrationOutcome.GIVEN)
                .ifPresent(last -> {
                    if (!at.isAfter(last.getAdministeredAt())) {
                        throw new BusinessRuleException("A dose was already recorded at or after that time");
                    }
                    if (order.getIntervalHours() != null) {
                        Instant earliest = last.getAdministeredAt().plus(Duration.ofHours(order.getIntervalHours()))
                                .minus(properties.earlyTolerance());
                        if (at.isBefore(earliest)) {
                            throw new BusinessRuleException("The next dose is not due before %s".formatted(earliest));
                        }
                    }
                });
    }

    // ---- helpers -------------------------------------------------------------------------------

    private MedicationOrder find(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private MedicationOrder lock(Long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private User userOf(Caller caller) {
        return userRepository.findByUsernameIgnoreCase(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private BedSummary currentBed(Long hospitalizationId) {
        return bedOf(bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(hospitalizationId).orElse(null));
    }

    private BedSummary bedOf(BedAssignment assignment) {
        return assignment == null ? null : bedMapper.toSummary(assignment.getBed());
    }

    private MedicationOrderResponse present(MedicationOrder order, BedSummary bed) {
        return MedicationOrderResponse.withBed(orderMapper.toResponse(order), bed);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
