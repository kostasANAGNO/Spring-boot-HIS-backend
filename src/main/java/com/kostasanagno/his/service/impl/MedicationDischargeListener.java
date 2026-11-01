package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.service.HospitalizationDischargedEvent;
import com.kostasanagno.his.service.MedicationOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * When a patient is discharged, whatever medication orders are still open are closed. Runs synchronously inside the
 * discharge transaction, so the discharge and the closing succeed or fail together.
 */
@Component
@RequiredArgsConstructor
public class MedicationDischargeListener {

    private final MedicationOrderService medicationOrderService;

    @EventListener
    public void onDischarged(HospitalizationDischargedEvent event) {
        medicationOrderService.closeOpenOrders(event.hospitalizationId(), null);
    }
}
