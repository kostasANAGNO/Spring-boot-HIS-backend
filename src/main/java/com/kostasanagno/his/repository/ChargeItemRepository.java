package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.ChargeItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, JpaSpecificationExecutor<ChargeItem> {

    Optional<ChargeItem> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
