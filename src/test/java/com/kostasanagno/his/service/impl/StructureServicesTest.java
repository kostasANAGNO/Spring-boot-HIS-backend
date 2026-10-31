package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.BedRequest;
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.RoomRequest;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.Room;
import com.kostasanagno.his.entity.Ward;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapperImpl;
import com.kostasanagno.his.mapper.RoomMapperImpl;
import com.kostasanagno.his.mapper.WardMapperImpl;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.BedRepository;
import com.kostasanagno.his.repository.RoomRepository;
import com.kostasanagno.his.repository.WardRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Create / duplicate / parent checks / "cannot deactivate while occupied" for wards, rooms and beds. */
@ExtendWith(MockitoExtension.class)
class StructureServicesTest {

    @Mock
    private WardRepository wardRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private BedAssignmentRepository bedAssignmentRepository;

    private WardServiceImpl wardService;
    private RoomServiceImpl roomService;
    private BedServiceImpl bedService;
    private Ward ward;
    private Room room;
    private Bed bed;

    @BeforeEach
    void setUp() {
        wardService = new WardServiceImpl(wardRepository, bedAssignmentRepository, new WardMapperImpl());
        roomService = new RoomServiceImpl(roomRepository, wardRepository, bedAssignmentRepository, new RoomMapperImpl());
        bedService = new BedServiceImpl(bedRepository, roomRepository, bedAssignmentRepository, new BedMapperImpl());
        ward = Ward.builder().name("Cardiology").build();
        ward.setId(1L);
        room = Room.builder().ward(ward).roomNumber("101").build();
        room.setId(2L);
        bed = Bed.builder().room(room).bedNumber("A").build();
        bed.setId(3L);
    }

    // ---- wards ---------------------------------------------------------------------------------

    @Test
    void wardIsCreatedWithTrimmedName() {
        when(wardRepository.existsByNameIgnoreCase("Oncology")).thenReturn(false);
        when(wardRepository.save(any(Ward.class))).thenAnswer(inv -> inv.getArgument(0));

        WardResponse response = wardService.create(new WardRequest(" Oncology "));

        assertThat(response.name()).isEqualTo("Oncology");
        assertThat(response.active()).isTrue();
    }

    @Test
    void duplicateWardNameIsRejected() {
        when(wardRepository.existsByNameIgnoreCase("Cardiology")).thenReturn(true);

        assertThatThrownBy(() -> wardService.create(new WardRequest("Cardiology")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(wardRepository, never()).save(any());
    }

    @Test
    void wardWithOccupiedBedsCannotBeDeactivated() {
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward));
        when(bedAssignmentRepository.existsByBedRoomWardIdAndEndTimeIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> wardService.deactivate(1L)).isInstanceOf(BusinessRuleException.class);
        assertThat(ward.isActive()).isTrue();
    }

    @Test
    void freeWardIsDeactivated() {
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward));

        assertThat(wardService.deactivate(1L).active()).isFalse();
        assertThat(ward.isActive()).isFalse();
    }

    @Test
    void unknownWardIs404() {
        when(wardRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardService.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- rooms ---------------------------------------------------------------------------------

    @Test
    void roomIsCreatedInAnActiveWard() {
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.create(new RoomRequest(1L, " 102 "));

        assertThat(response.roomNumber()).isEqualTo("102");
        assertThat(response.wardName()).isEqualTo("Cardiology");
    }

    @Test
    void roomRejectsInactiveOrMissingWardAndDuplicateNumber() {
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward));
        when(wardRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> roomService.create(new RoomRequest(9L, "1"))).isInstanceOf(ResourceNotFoundException.class);

        when(roomRepository.existsByWardIdAndRoomNumberIgnoreCase(1L, "101")).thenReturn(true);
        assertThatThrownBy(() -> roomService.create(new RoomRequest(1L, "101")))
                .isInstanceOf(DuplicateResourceException.class);

        ward.setActive(false);
        assertThatThrownBy(() -> roomService.create(new RoomRequest(1L, "102"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void roomWithOccupiedBedsCannotBeDeactivated() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(bedAssignmentRepository.existsByBedRoomIdAndEndTimeIsNull(2L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.deactivate(2L)).isInstanceOf(BusinessRuleException.class);
        assertThat(room.isActive()).isTrue();
    }

    @Test
    void occupiedRoomCannotBeMovedToAnotherWard() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(bedAssignmentRepository.existsByBedRoomIdAndEndTimeIsNull(2L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.update(2L, new RoomRequest(5L, "101")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("moved");
    }

    @Test
    void freeRoomIsDeactivated() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));

        assertThat(roomService.deactivate(2L).active()).isFalse();
    }

    // ---- beds ----------------------------------------------------------------------------------

    @Test
    void bedIsCreatedInAnActiveRoomAndStartsFree() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(bedRepository.save(any(Bed.class))).thenAnswer(inv -> inv.getArgument(0));

        BedResponse response = bedService.create(new BedRequest(2L, "B"));

        assertThat(response.bedNumber()).isEqualTo("B");
        assertThat(response.roomNumber()).isEqualTo("101");
        assertThat(response.wardName()).isEqualTo("Cardiology");
        assertThat(response.occupied()).isFalse();
    }

    @Test
    void bedRejectsDuplicateNumberAndInactiveRoom() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(bedRepository.existsByRoomIdAndBedNumberIgnoreCase(2L, "A")).thenReturn(true);
        assertThatThrownBy(() -> bedService.create(new BedRequest(2L, "A")))
                .isInstanceOf(DuplicateResourceException.class);

        room.setActive(false);
        assertThatThrownBy(() -> bedService.create(new BedRequest(2L, "C"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void occupiedBedCannotBeDeactivated() {
        when(bedRepository.findById(3L)).thenReturn(Optional.of(bed));
        when(bedAssignmentRepository.existsByBedIdAndEndTimeIsNull(3L)).thenReturn(true);

        assertThatThrownBy(() -> bedService.deactivate(3L)).isInstanceOf(BusinessRuleException.class);
        assertThat(bed.isActive()).isTrue();
    }

    @Test
    void freeBedIsDeactivated() {
        when(bedRepository.findById(3L)).thenReturn(Optional.of(bed));

        assertThat(bedService.deactivate(3L).active()).isFalse();
    }

    @Test
    void getByIdReportsOccupancy() {
        when(bedRepository.findById(3L)).thenReturn(Optional.of(bed));
        when(bedAssignmentRepository.existsByBedIdAndEndTimeIsNull(3L)).thenReturn(true);

        assertThat(bedService.getById(3L).occupied()).isTrue();
    }
}
