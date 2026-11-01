package com.kostasanagno.his.dto;

/** {@code text} matches the code or the name (contains, case-insensitive); {@code active} defaults to true. */
public record ChargeItemSearchCriteria(String text, Boolean active) {
}
