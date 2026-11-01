package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.kostasanagno.his.entity.PaymentMethod;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.InvoiceMapperImpl;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.ChargeItemRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.InvoiceLineRepository;
import com.kostasanagno.his.repository.InvoiceRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.repository.PaymentRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.BillingProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceLineRepository invoiceLineRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ChargeItemRepository chargeItemRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private HospitalizationRepository hospitalizationRepository;
    @Mock
    private UserRepository userRepository;

    private InvoiceServiceImpl service;
    private Patient patient;
    private ChargeItem consult;
    private ChargeItem ecg;
    private ChargeItem bedDay;
    private final List<InvoiceLine> storedLines = new ArrayList<>();
    private final Caller receptionist = new Caller("rita", Set.of(Role.RECEPTIONIST));

    @BeforeEach
    void setUp() {
        service = new InvoiceServiceImpl(invoiceRepository, invoiceLineRepository, paymentRepository,
                chargeItemRepository, patientRepository, appointmentRepository, hospitalizationRepository,
                userRepository, new InvoiceMapperImpl(), new BillingProperties("BED-DAY"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        consult = item(1L, "CONSULT", "General consultation", "50.00", "0.00");
        ecg = item(2L, "ECG", "Electrocardiogram", "33.33", "13.00");
        bedDay = item(3L, "BED-DAY", "Hospital bed day", "150.00", "0.00");

        lenient().when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        lenient().when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(100L);
                i.setCreatedAt(NOW);
                i.setUpdatedAt(NOW);
            }
            return i;
        });
        lenient().when(invoiceLineRepository.saveAndFlush(any(InvoiceLine.class))).thenAnswer(inv -> {
            InvoiceLine line = inv.getArgument(0);
            line.setId(500L + storedLines.size());
            storedLines.add(line);
            return line;
        });
        lenient().when(invoiceLineRepository.findByInvoiceIdOrderByIdAsc(anyLong()))
                .thenAnswer(inv -> new ArrayList<>(storedLines));
        lenient().when(userRepository.findByUsernameIgnoreCase("rita")).thenReturn(Optional.of(user("rita")));
    }

    private static ChargeItem item(long id, String code, String name, String price, String vat) {
        ChargeItem item = ChargeItem.builder().code(code).name(name).unitPrice(new BigDecimal(price))
                .vatRate(new BigDecimal(vat)).build();
        item.setId(id);
        return item;
    }

    private static User user(String username) {
        User user = User.builder().username(username).roles(Set.of(Role.RECEPTIONIST)).build();
        user.setId(7L);
        return user;
    }

    private Invoice invoice(InvoiceStatus status) {
        Invoice invoice = Invoice.builder().patient(patient).status(status).build();
        invoice.setId(100L);
        invoice.setCreatedAt(NOW);
        invoice.setUpdatedAt(NOW);
        return invoice;
    }

    private Invoice lockedInvoice(InvoiceStatus status) {
        Invoice invoice = invoice(status);
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        return invoice;
    }

    /** An ISSUED invoice worth 100.00 with nothing paid yet. */
    private Invoice issuedInvoice() {
        Invoice invoice = lockedInvoice(InvoiceStatus.ISSUED);
        invoice.setInvoiceNumber("INV-2026-000001");
        invoice.setIssuedAt(NOW.minusSeconds(3600));
        invoice.setTotalNet(new BigDecimal("100.00"));
        invoice.setTotalGross(new BigDecimal("100.00"));
        return invoice;
    }

    private void stubPaymentSave() {
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- create --------------------------------------------------------------------------------

    @Test
    void createMakesAnEmptyDraft() {
        InvoiceResponse response = service.create(new InvoiceCreateRequest(10L, null));

        assertThat(response.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(response.invoiceNumber()).isNull();
        assertThat(response.lines()).isEmpty();
        assertThat(response.totalGross()).isEqualByComparingTo("0");
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
    }

    @Test
    void createRejectsMissingAndInactivePatient() {
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(99L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        patient.setActive(false);
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(10L, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createLinksAnAppointmentOfTheSamePatientOnce() {
        Appointment appointment = Appointment.builder().patient(patient).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        assertThat(service.create(new InvoiceCreateRequest(10L, 60L)).appointmentId()).isEqualTo(60L);

        when(invoiceRepository.existsByAppointmentIdAndStatusNot(60L, InvoiceStatus.CANCELLED)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(10L, 60L)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createRejectsAnAppointmentOfAnotherPatient() {
        Patient other = Patient.builder().nationalId("2").build();
        other.setId(11L);
        Appointment appointment = Appointment.builder().patient(other).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(10L, 60L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different patient");
    }

    // ---- lines and amounts ---------------------------------------------------------------------

    @Test
    void addLineCopiesPriceAndComputesVatWithRounding() {
        lockedInvoice(InvoiceStatus.DRAFT);
        when(chargeItemRepository.findById(2L)).thenReturn(Optional.of(ecg));

        InvoiceResponse response = service.addLine(100L, new InvoiceLineRequest(2L, 3));

        // 3 x 33.33 = 99.99 net; VAT 13% = 12.9987 -> 13.00; gross 112.99
        assertThat(response.lines()).hasSize(1);
        var line = response.lines().get(0);
        assertThat(line.code()).isEqualTo("ECG");
        assertThat(line.quantity()).isEqualTo(3);
        assertThat(line.unitPrice()).isEqualByComparingTo("33.33");
        assertThat(line.netAmount()).isEqualByComparingTo("99.99");
        assertThat(line.vatAmount()).isEqualByComparingTo("13.00");
        assertThat(line.grossAmount()).isEqualByComparingTo("112.99");
        assertThat(response.totalNet()).isEqualByComparingTo("99.99");
        assertThat(response.totalVat()).isEqualByComparingTo("13.00");
        assertThat(response.totalGross()).isEqualByComparingTo("112.99");
    }

    @Test
    void totalsAreTheSumOfTheLines() {
        lockedInvoice(InvoiceStatus.DRAFT);
        when(chargeItemRepository.findById(1L)).thenReturn(Optional.of(consult));
        when(chargeItemRepository.findById(2L)).thenReturn(Optional.of(ecg));

        service.addLine(100L, new InvoiceLineRequest(1L, 2));
        InvoiceResponse response = service.addLine(100L, new InvoiceLineRequest(2L, 1));

        assertThat(response.lines()).hasSize(2);
        assertThat(response.totalNet()).isEqualByComparingTo("133.33");   // 100.00 + 33.33
        assertThat(response.totalVat()).isEqualByComparingTo("4.33");     // 0 + 4.3329 -> 4.33
        assertThat(response.totalGross()).isEqualByComparingTo("137.66");
        assertThat(response.outstandingAmount()).isEqualByComparingTo("137.66");
    }

    @Test
    void laterPriceChangesDoNotTouchExistingLines() {
        lockedInvoice(InvoiceStatus.DRAFT);
        when(chargeItemRepository.findById(1L)).thenReturn(Optional.of(consult));
        service.addLine(100L, new InvoiceLineRequest(1L, 1));

        consult.setUnitPrice(new BigDecimal("80.00"));   // the price list changes afterwards

        assertThat(storedLines.get(0).getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(storedLines.get(0).getGrossAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void addLineRejectsInactiveUnknownItemAndNonDraftInvoice() {
        lockedInvoice(InvoiceStatus.DRAFT);
        consult.setActive(false);
        when(chargeItemRepository.findById(1L)).thenReturn(Optional.of(consult));
        when(chargeItemRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addLine(100L, new InvoiceLineRequest(1L, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inactive");
        assertThatThrownBy(() -> service.addLine(100L, new InvoiceLineRequest(9L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(invoiceRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(invoice(InvoiceStatus.ISSUED)));
        assertThatThrownBy(() -> service.addLine(101L, new InvoiceLineRequest(1L, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DRAFT");
    }

    @Test
    void removeLineRecomputesTheTotals() {
        Invoice invoice = lockedInvoice(InvoiceStatus.DRAFT);
        when(chargeItemRepository.findById(1L)).thenReturn(Optional.of(consult));
        when(chargeItemRepository.findById(2L)).thenReturn(Optional.of(ecg));
        service.addLine(100L, new InvoiceLineRequest(1L, 1));
        service.addLine(100L, new InvoiceLineRequest(2L, 1));
        InvoiceLine ecgLine = storedLines.get(1);
        ecgLine.setInvoice(invoice);
        when(invoiceLineRepository.findById(ecgLine.getId())).thenReturn(Optional.of(ecgLine));
        org.mockito.Mockito.doAnswer(inv -> storedLines.remove(inv.<InvoiceLine>getArgument(0)))
                .when(invoiceLineRepository).delete(ecgLine);

        InvoiceResponse response = service.removeLine(100L, ecgLine.getId());

        assertThat(response.lines()).hasSize(1);
        assertThat(response.totalGross()).isEqualByComparingTo("50.00");
    }

    @Test
    void removeLineRejectsALineOfAnotherInvoiceAndNonDraftInvoice() {
        lockedInvoice(InvoiceStatus.DRAFT);
        Invoice other = invoice(InvoiceStatus.DRAFT);
        other.setId(999L);
        InvoiceLine foreign = InvoiceLine.builder().invoice(other).build();
        foreign.setId(77L);
        when(invoiceLineRepository.findById(77L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.removeLine(100L, 77L)).isInstanceOf(ResourceNotFoundException.class);

        when(invoiceRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(invoice(InvoiceStatus.PAID)));
        assertThatThrownBy(() -> service.removeLine(101L, 77L)).isInstanceOf(BusinessRuleException.class);
    }

    // ---- issue ---------------------------------------------------------------------------------

    @Test
    void issueNumbersAndFreezesTheInvoice() {
        lockedInvoice(InvoiceStatus.DRAFT);
        when(chargeItemRepository.findById(1L)).thenReturn(Optional.of(consult));
        service.addLine(100L, new InvoiceLineRequest(1L, 1));
        when(invoiceRepository.nextInvoiceNumber()).thenReturn(42L);

        InvoiceResponse response = service.issue(100L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(response.invoiceNumber()).isEqualTo("INV-2026-000042");
        assertThat(response.issuedAt()).isEqualTo(NOW);
        assertThat(response.totalGross()).isEqualByComparingTo("50.00");
    }

    @Test
    void issueNeedsLinesAndADraft() {
        lockedInvoice(InvoiceStatus.DRAFT);
        assertThatThrownBy(() -> service.issue(100L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("without lines");

        when(invoiceRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(invoice(InvoiceStatus.ISSUED)));
        assertThatThrownBy(() -> service.issue(101L)).isInstanceOf(BusinessRuleException.class);
        verify(invoiceRepository, never()).nextInvoiceNumber();
    }

    // ---- cancel --------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"DRAFT", "ISSUED"})
    void unpaidDraftAndIssuedInvoicesCanBeCancelled(InvoiceStatus status) {
        Invoice invoice = lockedInvoice(status);

        InvoiceResponse response = service.cancel(100L, new CancelInvoiceRequest(" Entered by mistake "));

        assertThat(response.status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Entered by mistake");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void anInvoiceWithPaymentsCannotBeCancelled() {
        Invoice invoice = issuedInvoice();
        invoice.setPaidAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.cancel(100L, new CancelInvoiceRequest("x")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("payments");
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"PAID", "CANCELLED"})
    void finalInvoicesCannotBeCancelled(InvoiceStatus status) {
        lockedInvoice(status);

        assertThatThrownBy(() -> service.cancel(100L, new CancelInvoiceRequest("x")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- payments ------------------------------------------------------------------------------

    @Test
    void partialPaymentsAddUpAndTheLastOneMakesTheInvoicePaid() {
        Invoice invoice = issuedInvoice();
        stubPaymentSave();

        InvoiceResponse first = service.recordPayment(100L,
                new PaymentRequest(new BigDecimal("40.00"), PaymentMethod.CASH, null, " slip 1 "), receptionist);
        assertThat(first.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(first.paidAmount()).isEqualByComparingTo("40.00");
        assertThat(first.outstandingAmount()).isEqualByComparingTo("60.00");

        InvoiceResponse last = service.recordPayment(100L,
                new PaymentRequest(new BigDecimal("60.00"), PaymentMethod.CARD, null, null), receptionist);
        assertThat(last.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(last.outstandingAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        Payment payment = captor.getAllValues().get(0);
        assertThat(payment.getReference()).isEqualTo("slip 1");
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getRecordedBy().getUsername()).isEqualTo("rita");
    }

    @Test
    void aPaymentCannotExceedTheAmountDue() {
        Invoice invoice = issuedInvoice();
        invoice.setPaidAmount(new BigDecimal("30.00"));

        assertThatThrownBy(() -> service.recordPayment(100L,
                new PaymentRequest(new BigDecimal("70.01"), PaymentMethod.CASH, null, null), receptionist))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("70.00");
        verify(paymentRepository, never()).saveAndFlush(any());
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("30.00");
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"DRAFT", "PAID", "CANCELLED"})
    void paymentsNeedAnIssuedInvoice(InvoiceStatus status) {
        lockedInvoice(status);

        assertThatThrownBy(() -> service.recordPayment(100L,
                new PaymentRequest(new BigDecimal("10.00"), PaymentMethod.CASH, null, null), receptionist))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ISSUED");
    }

    @Test
    void aPaymentCannotBeDatedInTheFuture() {
        issuedInvoice();

        assertThatThrownBy(() -> service.recordPayment(100L,
                new PaymentRequest(new BigDecimal("10.00"), PaymentMethod.CASH, NOW.plusSeconds(1), null), receptionist))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void paymentsListMapsTheRecorder() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        Payment payment = Payment.builder().invoice(invoice).amount(new BigDecimal("10.00"))
                .method(PaymentMethod.CASH).paidAt(NOW).recordedBy(user("rita")).build();
        payment.setId(1L);
        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtAscIdAsc(100L)).thenReturn(List.of(payment));

        List<PaymentResponse> payments = service.getPayments(100L);

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).recordedBy()).isEqualTo("rita");
        assertThat(payments.get(0).amount()).isEqualByComparingTo("10.00");
    }

    // ---- invoicing a stay ----------------------------------------------------------------------

    private Hospitalization discharged(Duration stay) {
        Hospitalization h = Hospitalization.builder().patient(patient).status(HospitalizationStatus.DISCHARGED)
                .admissionTime(NOW.minus(Duration.ofDays(10)))
                .dischargeTime(NOW.minus(Duration.ofDays(10)).plus(stay)).build();
        h.setId(40L);
        return h;
    }

    @ParameterizedTest
    @CsvSource({"1,1", "86399,1", "86400,1", "86401,2", "172800,2", "176401,3"})
    void aStayIsBilledOneBedDayPerStarted24Hours(long seconds, int expectedDays) {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged(Duration.ofSeconds(seconds))));
        when(chargeItemRepository.findByCodeIgnoreCase("BED-DAY")).thenReturn(Optional.of(bedDay));

        InvoiceResponse response = service.createFromHospitalization(40L);

        assertThat(response.hospitalizationId()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).quantity()).isEqualTo(expectedDays);
        assertThat(response.totalGross()).isEqualByComparingTo(new BigDecimal("150.00").multiply(BigDecimal.valueOf(expectedDays)));
    }

    @Test
    void theBedDayLineDescribesTheNumberOfDays() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged(Duration.ofHours(50))));
        when(chargeItemRepository.findByCodeIgnoreCase("BED-DAY")).thenReturn(Optional.of(bedDay));

        InvoiceResponse response = service.createFromHospitalization(40L);

        assertThat(response.lines().get(0).description()).isEqualTo("Hospital bed day (3 days)");
    }

    @Test
    void onlyADischargedStayCanBeInvoicedAndOnlyOnce() {
        Hospitalization admitted = discharged(Duration.ofDays(1));
        admitted.setStatus(HospitalizationStatus.ADMITTED);
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(admitted));
        assertThatThrownBy(() -> service.createFromHospitalization(40L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("discharged");

        when(hospitalizationRepository.findById(41L)).thenReturn(Optional.of(discharged(Duration.ofDays(1))));
        when(invoiceRepository.existsByHospitalizationIdAndStatusNot(41L, InvoiceStatus.CANCELLED)).thenReturn(true);
        assertThatThrownBy(() -> service.createFromHospitalization(41L))
                .isInstanceOf(DuplicateResourceException.class);

        when(hospitalizationRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createFromHospitalization(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invoicingAStayNeedsAnActiveBedDayItem() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged(Duration.ofDays(1))));
        when(chargeItemRepository.findByCodeIgnoreCase("BED-DAY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createFromHospitalization(40L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("BED-DAY");

        bedDay.setActive(false);
        when(chargeItemRepository.findByCodeIgnoreCase("BED-DAY")).thenReturn(Optional.of(bedDay));
        assertThatThrownBy(() -> service.createFromHospitalization(40L)).isInstanceOf(BusinessRuleException.class);
    }

    // ---- reads ---------------------------------------------------------------------------------

    @Test
    void unknownInvoiceIs404() {
        when(invoiceRepository.findById(9L)).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getPayments(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.issue(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchReturnsSummariesWithoutLoadingLines() {
        Invoice invoice = invoice(InvoiceStatus.ISSUED);
        invoice.setInvoiceNumber("INV-2026-000001");
        invoice.setTotalGross(new BigDecimal("100.00"));
        invoice.setPaidAmount(new BigDecimal("25.00"));
        Pageable pageable = PageRequest.of(0, 10);
        when(invoiceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

        Page<InvoiceSummaryResponse> page = service.search(
                new InvoiceSearchCriteria(10L, InvoiceStatus.ISSUED, "INV", true, null, null), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).outstandingAmount()).isEqualByComparingTo("75.00");
        verify(invoiceLineRepository, never()).findByInvoiceIdOrderByIdAsc(anyLong());
    }
}
