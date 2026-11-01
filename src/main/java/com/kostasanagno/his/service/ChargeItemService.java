package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.ChargeItemRequest;
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.dto.ChargeItemSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChargeItemService {

    ChargeItemResponse create(ChargeItemRequest request);

    ChargeItemResponse getById(Long id);

    Page<ChargeItemResponse> search(ChargeItemSearchCriteria criteria, Pageable pageable);

    /** Changes the price list entry only; invoices already created keep their own copy of price and VAT. */
    ChargeItemResponse update(Long id, ChargeItemRequest request);

    /** Removes the entry from the price list for new lines; existing invoices are unaffected. */
    ChargeItemResponse deactivate(Long id);
}
