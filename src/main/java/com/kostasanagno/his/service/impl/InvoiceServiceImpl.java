package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.CancelInvoiceRequest;
import com.kostasanagno.his.dto.InvoiceCreateRequest;
import com.kostasanagno.his.dto.InvoiceLineRequest;
import com.kostasanagno.his.dto.InvoiceResponse;
import com.kostasanagno.his.dto.InvoiceSearchCriteria;
import com.kostasanagno.his.dto.InvoiceSummaryResponse;
import com.kostasanagno.his.dto.PaymentRequest;
import com.kostasanagno.his.dto.PaymentResponse;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.ChargeItem;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Invoice;
import com.kostasanagno.his.entity.InvoiceLine;
import com.kostasanagno.his.entity.InvoiceStatus;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Payment;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.InvoiceMapper;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.ChargeItemRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.InvoiceLineRepository;
import com.kostasanagno.his.repository.InvoiceRepository;
import com.kostasanagno.his.repository.InvoiceSpecifications;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.repository.PaymentRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.BillingProperties;
import com.kostasanagno.his.service.InvoiceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Amounts are BigDecimal with 2 decimals, computed here and nowhere else: line net = quantity x unit price,
 * VAT = net x rate / 100 rounded HALF_UP per line, gross = net + VAT; invoice totals are the sums of the lines.
 * Operations that change an invoice lock its row first, so concurrent payments cannot over-pay it (the CHECK
 * constraints in V1 are the safety net). Only ids are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private static final String RESOURCE = "Invoice";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final long SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeItemRepository chargeItemRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final UserRepository userRepository;
    private final InvoiceMapper invoiceMapper;
    private final BillingProperties billingProperties;
    private final Clock clock;

    @Override
    public InvoiceResponse create(InvoiceCreateRequest request) {
        Patient patient = findActivePatient(request.patientId());
        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Appointment", request.appointmentId()));
            if (!appointment.getPatient().getId().equals(patient.getId())) {
                throw new BusinessRuleException("Appointment %d belongs to a different patient"
                        .formatted(request.appointmentId()));
            }
            if (invoiceRepository.existsByAppointmentIdAndStatusNot(appointment.getId(), InvoiceStatus.CANCELLED)) {
                throw new DuplicateResourceException(
                        "Appointment %d already has an invoice".formatted(appointment.getId()));
            }
        }
        Invoice invoice = invoiceRepository.saveAndFlush(Invoice.builder()
                .patient(patient).appointment(appointment).build());
        log.info("Created draft invoice {} (patient {})", invoice.getId(), patient.getId());
        return present(invoice);
    }

    @Override
    public InvoiceResponse createFromHospitalization(Long hospitalizationId) {
        Hospitalization hospitalization = hospitalizationRepository.findById(hospitalizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Hospitalization", hospitalizationId));
        if (hospitalization.getStatus() != HospitalizationStatus.DISCHARGED) {
            throw new BusinessRuleException("Only a discharged hospitalization can be invoiced");
        }
        if (invoiceRepository.existsByHospitalizationIdAndStatusNot(hospitalizationId, InvoiceStatus.CANCELLED)) {
            throw new DuplicateResourceException(
                    "Hospitalization %d already has an invoice".formatted(hospitalizationId));
        }
        ChargeItem bedDay = chargeItemRepository.findByCodeIgnoreCase(billingProperties.bedDayCode())
                .filter(ChargeItem::isActive)
                .orElseThrow(() -> new BusinessRuleException("The bed-day charge item '%s' is missing or inactive"
                        .formatted(billingProperties.bedDayCode())));
        Patient patient = findActivePatient(hospitalization.getPatient().getId());

        Invoice invoice = invoiceRepository.saveAndFlush(Invoice.builder()
                .patient(patient).hospitalization(hospitalization).build());
        int days = bedDays(hospitalization.getAdmissionTime(), hospitalization.getDischargeTime());
        InvoiceLine line = invoiceLineRepository.saveAndFlush(buildLine(invoice, bedDay, days,
                "%s (%d day%s)".formatted(bedDay.getName(), days, days == 1 ? "" : "s")));
        return saveTotals(invoice, List.of(line));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        return present(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> search(InvoiceSearchCriteria criteria, Pageable pageable) {
        return invoiceRepository.findAll(InvoiceSpecifications.from(criteria), pageable)
                .map(invoiceMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(Long id) {
        find(id);
        return paymentRepository.findByInvoiceIdOrderByPaidAtAscIdAsc(id).stream()
                .map(invoiceMapper::toPaymentResponse).toList();
    }

    @Override
    public InvoiceResponse addLine(Long id, InvoiceLineRequest request) {
        Invoice invoice = lockDraft(id, "Lines can only be added to a DRAFT invoice");
        ChargeItem item = chargeItemRepository.findById(request.chargeItemId())
                .orElseThrow(() -> ResourceNotFoundException.of("Charge item", request.chargeItemId()));
        if (!item.isActive()) {
            throw new BusinessRuleException("Charge item %d is inactive".formatted(item.getId()));
        }
        invoiceLineRepository.saveAndFlush(buildLine(invoice, item, request.quantity(), item.getName()));
        log.info("Added a line to invoice {}", id);
        return saveTotals(invoice, invoiceLineRepository.findByInvoiceIdOrderByIdAsc(id));
    }

    @Override
    public InvoiceResponse removeLine(Long id, Long lineId) {
        Invoice invoice = lockDraft(id, "Lines can only be removed from a DRAFT invoice");
        InvoiceLine line = invoiceLineRepository.findById(lineId)
                .filter(candidate -> candidate.getInvoice().getId().equals(id))
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice line", lineId));
        invoiceLineRepository.delete(line);
        invoiceLineRepository.flush();
        log.info("Removed a line from invoice {}", id);
        return saveTotals(invoice, invoiceLineRepository.findByInvoiceIdOrderByIdAsc(id));
    }

    @Override
    public InvoiceResponse issue(Long id) {
        Invoice invoice = lockDraft(id, "Only a DRAFT invoice can be issued");
        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderByIdAsc(id);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("An invoice without lines cannot be issued");
        }
        applyTotals(invoice, lines);
        Instant now = clock.instant();
        invoice.setInvoiceNumber("INV-%d-%06d".formatted(now.atZone(ZoneOffset.UTC).getYear(),
                invoiceRepository.nextInvoiceNumber()));
        invoice.setIssuedAt(now);
        invoice.setStatus(InvoiceStatus.ISSUED);
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        log.info("Issued invoice {}", id);
        return invoiceMapper.toResponse(saved, lines);
    }

    @Override
    public InvoiceResponse cancel(Long id, CancelInvoiceRequest request) {
        Invoice invoice = lockForUpdate(id);
        assertTransition(invoice, InvoiceStatus.CANCELLED);
        if (invoice.getPaidAmount().signum() > 0) {
            throw new BusinessRuleException("An invoice with payments cannot be cancelled");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancellationReason(request.reason().trim());
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        log.info("Cancelled invoice {}", id);
        return present(saved);
    }

    @Override
    public InvoiceResponse recordPayment(Long id, PaymentRequest request, Caller caller) {
        Invoice invoice = lockForUpdate(id);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessRuleException("Payments can only be recorded on an ISSUED invoice");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new BusinessRuleException("The payment exceeds the amount still due (%s)"
                    .formatted(invoice.getOutstandingAmount().toPlainString()));
        }
        Instant now = clock.instant();
        Instant paidAt = request.paidAt() == null ? now : request.paidAt();
        if (paidAt.isAfter(now)) {
            throw new BusinessRuleException("paidAt must not be in the future");
        }
        User recordedBy = userRepository.findByUsernameIgnoreCase(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        paymentRepository.saveAndFlush(Payment.builder()
                .invoice(invoice)
                .amount(amount)
                .method(request.method())
                .paidAt(paidAt)
                .reference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim())
                .recordedBy(recordedBy)
                .build());
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        if (invoice.getOutstandingAmount().signum() == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        }
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        log.info("Recorded a payment on invoice {}", id);
        return present(saved);
    }

    // ---- lookups and locking -------------------------------------------------------------------

    private Invoice find(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Invoice lockForUpdate(Long id) {
        return invoiceRepository.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Invoice lockDraft(Long id, String message) {
        Invoice invoice = lockForUpdate(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(message);
        }
        return invoice;
    }

    private Patient findActivePatient(Long id) {
        Patient patient = patientRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Patient", id));
        if (!patient.isActive()) {
            throw new BusinessRuleException("Patient %d is inactive".formatted(id));
        }
        return patient;
    }

    private void assertTransition(Invoice invoice, InvoiceStatus target) {
        if (!invoice.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("Cannot change an invoice from %s to %s"
                    .formatted(invoice.getStatus(), target));
        }
    }

    // ---- amounts -------------------------------------------------------------------------------

    private InvoiceLine buildLine(Invoice invoice, ChargeItem item, int quantity, String description) {
        BigDecimal unitPrice = item.getUnitPrice().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal net = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal vat = net.multiply(item.getVatRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return InvoiceLine.builder()
                .invoice(invoice)
                .chargeItem(item)
                .code(item.getCode())
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .vatRate(item.getVatRate())
                .netAmount(net)
                .vatAmount(vat)
                .grossAmount(net.add(vat))
                .build();
    }

    private void applyTotals(Invoice invoice, List<InvoiceLine> lines) {
        BigDecimal net = lines.stream().map(InvoiceLine::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = lines.stream().map(InvoiceLine::getVatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setTotalNet(net.setScale(2));
        invoice.setTotalVat(vat.setScale(2));
        invoice.setTotalGross(net.add(vat).setScale(2));
    }

    private InvoiceResponse saveTotals(Invoice invoice, List<InvoiceLine> lines) {
        applyTotals(invoice, lines);
        return invoiceMapper.toResponse(invoiceRepository.saveAndFlush(invoice), lines);
    }

    /** One bed-day per started 24 hours of the stay, at least one. */
    private static int bedDays(Instant admission, Instant discharge) {
        long seconds = Duration.between(admission, discharge).getSeconds();
        return (int) Math.max(1, (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY);
    }

    private InvoiceResponse present(Invoice invoice) {
        return invoiceMapper.toResponse(invoice, invoiceLineRepository.findByInvoiceIdOrderByIdAsc(invoice.getId()));
    }
}
