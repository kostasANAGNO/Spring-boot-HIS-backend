package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Ward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardRepository extends JpaRepository<Ward, Long> {

    List<Ward> findAllByActiveOrderByNameAsc(boolean active);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
