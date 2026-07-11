package com.stocksimulator.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stocksimulator.common.exception.GlobalExceptionHandler;
import com.stocksimulator.simulation.dto.InvestmentInput;
import com.stocksimulator.simulation.dto.SimulationRequest;
import com.stocksimulator.simulation.dto.SimulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SimulationControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private SimulationService simulationService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        SimulationController simulationController = new SimulationController(simulationService);
        mockMvc = MockMvcBuilders.standaloneSetup(simulationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void simulate_validRequest_returns200WithResult() throws Exception {
        SimulationResult mockResult = SimulationResult.builder()
                .symbol("AAPL")
                .totalInvested(1000.0)
                .finalValue(1200.0)
                .totalGain(200.0)
                .totalGainPercent(20.0)
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .dataPoints(List.of())
                .build();

        when(simulationService.simulate(any(SimulationRequest.class))).thenReturn(mockResult);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        mockMvc.perform(post("/api/simulation/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.totalInvested").value(1000.0))
                .andExpect(jsonPath("$.finalValue").value(1200.0));
    }

    @Test
    void simulate_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(simulationService.simulate(any(SimulationRequest.class)))
                .thenThrow(new IllegalArgumentException("No price data"));

        SimulationRequest request = SimulationRequest.builder()
                .symbol("INVALID")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        mockMvc.perform(post("/api/simulation/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulate_missingSymbol_returns400() throws Exception {
        String requestJson = "{\"investments\":[{\"amount\":1000,\"date\":\"2023-01-03\"}],\"endDate\":\"2023-01-12\"}";

        mockMvc.perform(post("/api/simulation/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulate_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/simulation/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulate_nullEndDate_handledGracefully() throws Exception {
        SimulationResult mockResult = SimulationResult.builder()
                .symbol("AAPL")
                .totalInvested(1000.0)
                .finalValue(1050.0)
                .totalGain(50.0)
                .totalGainPercent(5.0)
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .dataPoints(List.of())
                .build();

        when(simulationService.simulate(any(SimulationRequest.class))).thenReturn(mockResult);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(null)
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        mockMvc.perform(post("/api/simulation/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
