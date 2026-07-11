package com.stocksimulator.simulation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentInput {

    @NotNull
    @Positive(message = "Investment amount must be positive")
    private Double amount;

    @NotNull
    private LocalDate date;
}
