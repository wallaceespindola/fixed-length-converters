package com.wtechitsolutions.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "banking_statements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankingStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private LocalDate statementDate;
    private Integer sequenceNumber;
    private Instant createdAt;
}
