package com.stocksimulator.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResult {

    private String symbol;

    private List<SimulationDataPoint> dataPoints;

    private Double totalInvested;

    private Double finalValue;

    private Double totalGain;

    private Double totalGainPercent;

    private boolean inflationAdjusted;

    private String displayMode;
}
