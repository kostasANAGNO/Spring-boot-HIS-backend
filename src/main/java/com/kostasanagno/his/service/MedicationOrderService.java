package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.AdministrationRequest;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.AdministrationSearchCriteria;
import com.kostasanagno.his.dto.DispenseRequest;
import com.kostasanagno.his.dto.DispensingResponse;
import com.kostasanagno.his.dto.MedicationOrderCloseRequest;
import com.kostasanagno.his.dto.MedicationOrderRequest;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationOrderSearchCriteria;
import com.kostasanagno.his.security.Caller;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Medication for hospitalized patients: ORDER (doctor) -> DISPENSE or REJECT (pharmacy) -> ADMINISTER (bedside).
 * Orders are never edited, dispensings and administrations are never changed or deleted. Every operation that changes
 * an order locks its row first.
 */
public interface MedicationOrderService {

    /** Only a doctor with a linked profile can order; the hospitalization must be ADMITTED. */
    MedicationOrderResponse create(MedicationOrderRequest request, Caller caller);

    MedicationOrderResponse getById(Long id);

    Page<MedicationOrderResponse> search(MedicationOrderSearchCriteria criteria, Pageable pageable);

    /** Orders written by the doctor profile linked to the caller; the prescribedByDoctorId filter is ignored. */
    Page<MedicationOrderResponse> searchForDoctorUser(MedicationOrderSearchCriteria criteria, Pageable pageable,
            Caller caller);

    /** Records a supply for a PENDING or DISPENSED order. Controlled medications: pharmacists only. */
    MedicationOrderResponse dispense(Long id, DispenseRequest request, Caller caller);

    /** PENDING only: the pharmacy refuses the order, with a reason. */
    MedicationOrderResponse reject(Long id, MedicationOrderCloseRequest request, Caller caller);

    /** PENDING only: the order is withdrawn before the pharmacy acted on it. */
    MedicationOrderResponse cancel(Long id, MedicationOrderCloseRequest request, Caller caller);

    /** DISPENSED only: the therapy ends here. */
    MedicationOrderResponse stop(Long id, MedicationOrderCloseRequest request, Caller caller);

    /** Records a dose given, refused or held; the order must be DISPENSED. */
    AdministrationResponse administer(Long id, AdministrationRequest request, Caller caller);

    List<DispensingResponse> getDispensings(Long id);

    List<AdministrationResponse> getAdministrations(Long id);

    Page<AdministrationResponse> searchAdministrations(AdministrationSearchCriteria criteria, Pageable pageable);

    /** Closes the still open orders of a hospitalization (PENDING -> CANCELLED, DISPENSED -> STOPPED). */
    void closeOpenOrders(Long hospitalizationId, String reason);
}
