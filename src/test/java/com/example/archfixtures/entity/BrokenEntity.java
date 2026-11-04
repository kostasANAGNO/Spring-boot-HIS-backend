package com.example.archfixtures.entity;

import com.example.archfixtures.dto.BrokenDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Violates: safe equals/hashCode base class, lazy to-one associations, entities knowing DTOs. */
@Entity
public class BrokenEntity {

    @Id
    private Long id;

    @ManyToOne                       // EAGER by default
    private BrokenEntity parent;

    private BrokenDto leakedDto;
}
