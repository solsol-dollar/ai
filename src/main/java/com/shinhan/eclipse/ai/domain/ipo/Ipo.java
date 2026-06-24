package com.shinhan.eclipse.ai.domain.ipo;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "ipos")
public class Ipo {
    @Id
    private Long id;
    private String ticker;
    @Column(name = "listing_date")
    private LocalDate listingDate;
    @Column(name = "status")
    private String status;
}
