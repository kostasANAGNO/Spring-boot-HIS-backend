package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.RoomRequest;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.dto.RoomSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    RoomResponse getById(Long id);

    Page<RoomResponse> search(RoomSearchCriteria criteria, Pageable pageable);

    RoomResponse update(Long id, RoomRequest request);

    /** Rejected while any bed of the room is occupied. */
    RoomResponse deactivate(Long id);
}
