package com.stocksimulator.simulation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRequest {

    @NotBlank
    private String symbol;

    @NotNull
    @Valid
    private List<InvestmentInput> investments;

    private LocalDate endDate;

    private boolean inflationAdjusted;

    private String displayMode;
}
