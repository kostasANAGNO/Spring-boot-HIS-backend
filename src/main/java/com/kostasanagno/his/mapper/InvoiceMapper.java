package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.InvoiceLineResponse;
import com.kostasanagno.his.dto.InvoiceResponse;
import com.kostasanagno.his.dto.InvoiceSummaryResponse;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.dto.PaymentResponse;
import com.kostasanagno.his.entity.Invoice;
import com.kostasanagno.his.entity.InvoiceLine;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Payment;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface InvoiceMapper {

    /** Header from the invoice, lines passed in (loaded once by the service). */
    @Mapping(target = "id", source = "invoice.id")
    @Mapping(target = "invoiceNumber", source = "invoice.invoiceNumber")
    @Mapping(target = "status", source = "invoice.status")
    @Mapping(target = "patient", source = "invoice.patient")
    @Mapping(target = "appointmentId", source = "invoice.appointment.id")
    @Mapping(target = "hospitalizationId", source = "invoice.hospitalization.id")
    @Mapping(target = "lines", source = "lines")
    @Mapping(target = "totalNet", source = "invoice.totalNet")
    @Mapping(target = "totalVat", source = "invoice.totalVat")
    @Mapping(target = "totalGross", source = "invoice.totalGross")
    @Mapping(target = "paidAmount", source = "invoice.paidAmount")
    @Mapping(target = "outstandingAmount", expression = "java(invoice.getOutstandingAmount())")
    @Mapping(target = "issuedAt", source = "invoice.issuedAt")
    @Mapping(target = "cancellationReason", source = "invoice.cancellationReason")
    @Mapping(target = "createdAt", source = "invoice.createdAt")
    @Mapping(target = "updatedAt", source = "invoice.updatedAt")
    InvoiceResponse toResponse(Invoice invoice, List<InvoiceLine> lines);

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "hospitalizationId", source = "hospitalization.id")
    @Mapping(target = "outstandingAmount", expression = "java(invoice.getOutstandingAmount())")
    InvoiceSummaryResponse toSummary(Invoice invoice);

    @Mapping(target = "chargeItemId", source = "chargeItem.id")
    InvoiceLineResponse toLineResponse(InvoiceLine line);

    @Mapping(target = "recordedBy", source = "recordedBy.username")
    PaymentResponse toPaymentResponse(Payment payment);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);
}
