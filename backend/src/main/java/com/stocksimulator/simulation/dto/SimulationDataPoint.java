package com.stocksimulator.simulation.dto;

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
public class SimulationDataPoint {

    private LocalDate date;

    private Double portfolioValue;

    private Double totalInvested;

    private Double gain;

    private Double gainPercent;

    private Double inflationAdjustedValue;

    private List<Double> perInvestmentValues;
}
