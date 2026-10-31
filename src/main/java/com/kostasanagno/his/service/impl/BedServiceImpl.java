package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.BedRequest;
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.BedSearchCriteria;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.Room;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapper;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.BedRepository;
import com.kostasanagno.his.repository.RoomRepository;
import com.kostasanagno.his.repository.StructureSpecifications;
import com.kostasanagno.his.service.BedService;
import java.util.HashSet;
import java.util.Set;
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
public class BedServiceImpl implements BedService {

    private static final String RESOURCE = "Bed";

    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final BedMapper bedMapper;

    @Override
    public BedResponse create(BedRequest request) {
        Room room = findActiveRoom(request.roomId());
        String number = request.bedNumber().trim();
        if (bedRepository.existsByRoomIdAndBedNumberIgnoreCase(room.getId(), number)) {
            throw DuplicateResourceException.of(RESOURCE, "number", number);
        }
        Bed bed = bedMapper.toEntity(request);
        bed.setBedNumber(number);
        bed.setRoom(room);
        Bed saved = bedRepository.save(bed);
        log.info("Created bed {}", saved.getId());
        return bedMapper.toResponse(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public BedResponse getById(Long id) {
        Bed bed = find(id);
        return bedMapper.toResponse(bed, bedAssignmentRepository.existsByBedIdAndEndTimeIsNull(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BedResponse> search(BedSearchCriteria criteria, Pageable pageable) {
        Page<Bed> beds = bedRepository.findAll(StructureSpecifications.beds(criteria), pageable);
        // one query for the whole page instead of one per bed
        Set<Long> occupied = beds.isEmpty() ? Set.of() : new HashSet<>(
                bedAssignmentRepository.findOccupiedBedIds(beds.getContent().stream().map(Bed::getId).toList()));
        return beds.map(bed -> bedMapper.toResponse(bed, occupied.contains(bed.getId())));
    }

    @Override
    public BedResponse update(Long id, BedRequest request) {
        Bed bed = find(id);
        String number = request.bedNumber().trim();
        if (bedRepository.existsByRoomIdAndBedNumberIgnoreCaseAndIdNot(request.roomId(), number, id)) {
            throw DuplicateResourceException.of(RESOURCE, "number", number);
        }
        boolean occupied = bedAssignmentRepository.existsByBedIdAndEndTimeIsNull(id);
        if (!bed.getRoom().getId().equals(request.roomId())) {
            if (occupied) {
                throw new BusinessRuleException("Bed %d is occupied and cannot be moved".formatted(id));
            }
            bed.setRoom(findActiveRoom(request.roomId()));
        }
        bedMapper.updateEntity(request, bed);
        bed.setBedNumber(number);
        log.info("Updated bed {}", id);
        return bedMapper.toResponse(bedRepository.saveAndFlush(bed), occupied);
    }

    @Override
    public BedResponse deactivate(Long id) {
        Bed bed = find(id);
        if (bedAssignmentRepository.existsByBedIdAndEndTimeIsNull(id)) {
            throw new BusinessRuleException("Bed %d is occupied and cannot be deactivated".formatted(id));
        }
        bed.setActive(false);
        log.info("Deactivated bed {}", id);
        return bedMapper.toResponse(bed, false);
    }

    private Bed find(Long id) {
        return bedRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Room findActiveRoom(Long id) {
        Room room = roomRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Room", id));
        if (!room.isActive()) {
            throw new BusinessRuleException("Room %d is inactive".formatted(id));
        }
        return room;
    }
}
