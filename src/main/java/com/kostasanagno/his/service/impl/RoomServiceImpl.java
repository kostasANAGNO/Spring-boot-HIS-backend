package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.RoomRequest;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.dto.RoomSearchCriteria;
import com.kostasanagno.his.entity.Room;
import com.kostasanagno.his.entity.Ward;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.RoomMapper;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.RoomRepository;
import com.kostasanagno.his.repository.StructureSpecifications;
import com.kostasanagno.his.repository.WardRepository;
import com.kostasanagno.his.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RoomServiceImpl implements RoomService {

    private static final String RESOURCE = "Room";

    private final RoomRepository roomRepository;
    private final WardRepository wardRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final RoomMapper roomMapper;

    @Override
    public RoomResponse create(RoomRequest request) {
        Ward ward = findActiveWard(request.wardId());
        String number = request.roomNumber().trim();
        if (roomRepository.existsByWardIdAndRoomNumberIgnoreCase(ward.getId(), number)) {
            throw DuplicateResourceException.of(RESOURCE, "number", number);
        }
        Room room = roomMapper.toEntity(request);
        room.setRoomNumber(number);
        room.setWard(ward);
        Room saved = roomRepository.save(room);
        log.info("Created room {}", saved.getId());
        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getById(Long id) {
        return roomMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoomResponse> search(RoomSearchCriteria criteria, Pageable pageable) {
        return roomRepository.findAll(StructureSpecifications.rooms(criteria), pageable).map(roomMapper::toResponse);
    }

    @Override
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = find(id);
        String number = request.roomNumber().trim();
        if (roomRepository.existsByWardIdAndRoomNumberIgnoreCaseAndIdNot(request.wardId(), number, id)) {
            throw DuplicateResourceException.of(RESOURCE, "number", number);
        }
        if (!room.getWard().getId().equals(request.wardId())) {
            if (bedAssignmentRepository.existsByBedRoomIdAndEndTimeIsNull(id)) {
                throw new BusinessRuleException("Room %d has occupied beds and cannot be moved".formatted(id));
            }
            room.setWard(findActiveWard(request.wardId()));
        }
        roomMapper.updateEntity(request, room);
        room.setRoomNumber(number);
        log.info("Updated room {}", id);
        return roomMapper.toResponse(roomRepository.saveAndFlush(room));
    }

    @Override
    public RoomResponse deactivate(Long id) {
        Room room = find(id);
        if (bedAssignmentRepository.existsByBedRoomIdAndEndTimeIsNull(id)) {
            throw new BusinessRuleException("Room %d has occupied beds and cannot be deactivated".formatted(id));
        }
        room.setActive(false);
        log.info("Deactivated room {}", id);
        return roomMapper.toResponse(room);
    }

    private Room find(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Ward findActiveWard(Long id) {
        Ward ward = wardRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Ward", id));
        if (!ward.isActive()) {
            throw new BusinessRuleException("Ward %d is inactive".formatted(id));
        }
        return ward;
    }
}
