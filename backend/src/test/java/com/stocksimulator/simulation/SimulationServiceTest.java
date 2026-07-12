package com.stocksimulator.simulation;

import com.stocksimulator.market.MarketDataService;
import com.stocksimulator.market.dto.HistoricalPrice;
import com.stocksimulator.simulation.dto.InvestmentInput;
import com.stocksimulator.simulation.dto.SimulationDataPoint;
import com.stocksimulator.simulation.dto.SimulationRequest;
import com.stocksimulator.simulation.dto.SimulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private InflationService inflationService;

    @InjectMocks
    private SimulationService simulationService;

    private List<HistoricalPrice> samplePrices;

    @BeforeEach
    void setUp() {
        samplePrices = new ArrayList<>();
        LocalDate start = LocalDate.of(2023, 1, 3);
        double basePrice = 130.0;
        for (int i = 0; i < 10; i++) {
            LocalDate date = start.plusDays(i);
            double price = basePrice + i;
            samplePrices.add(HistoricalPrice.builder()
                    .date(date)
                    .open(price - 1)
                    .high(price + 2)
                    .low(price - 2)
                    .close(price)
                    .adjustedClose(price)
                    .volume(1000000L + i)
                    .build());
        }
    }

    // ── Existing tests ────────────────────────────────────────────────

    @Test
    void simulate_singleInvestment_returnsValidResult() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        assertEquals("AAPL", result.getSymbol());
        assertEquals(1000.0, result.getTotalInvested(), 0.01);
        assertTrue(result.getFinalValue() > 0);
        assertFalse(result.getDataPoints().isEmpty());
        assertFalse(result.isInflationAdjusted());
    }

    @Test
    void simulate_multipleInvestments_accumulatesShares() {
        when(marketDataService.getHistoricalPrices(eq("GOOGL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("GOOGL")
                .investments(List.of(
                        InvestmentInput.builder().amount(500.0).date(LocalDate.of(2023, 1, 5)).build(),
                        InvestmentInput.builder().amount(500.0).date(LocalDate.of(2023, 1, 7)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        assertEquals(1000.0, result.getTotalInvested(), 0.01);
        assertTrue(result.getDataPoints().size() > 1);
    }

    @Test
    void simulate_emptyInvestments_throwsException() {
        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of())
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        assertThrows(IllegalArgumentException.class, () -> simulationService.simulate(request));
    }

    @Test
    void simulate_noPriceData_throwsException() {
        when(marketDataService.getHistoricalPrices(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        SimulationRequest request = SimulationRequest.builder()
                .symbol("INVALID")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        assertThrows(IllegalArgumentException.class, () -> simulationService.simulate(request));
    }

    @Test
    void simulate_investmentAfterEndDate_throwsException() {
        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2024, 1, 1)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        assertThrows(IllegalArgumentException.class, () -> simulationService.simulate(request));
    }

    @Test
    void simulate_withInflationAdjusted_callsInflationService() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);
        when(inflationService.getCumulativeFactor(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1.05);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(true)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        assertTrue(result.isInflationAdjusted());
        boolean hasInflationData = result.getDataPoints().stream()
                .anyMatch(dp -> dp.getInflationAdjustedValue() != null);
        assertTrue(hasInflationData);
    }

    // ── New tests ─────────────────────────────────────────────────────

    @Test
    void simulate_perInvestmentMode_setsDisplayModeOnResult() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("per_investment")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertEquals("per_investment", result.getDisplayMode());
    }

    @Test
    void simulate_accumulatedMode_setsDisplayModeOnResult() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertEquals("accumulated", result.getDisplayMode());
    }

    @Test
    void simulate_inflationServiceThrows_gracefullyReturnsNullInflationValue() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);
        when(inflationService.getCumulativeFactor(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new RuntimeException("BLS API unavailable"));

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(true)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        // All inflation-adjusted values should be null since the service threw
        boolean allNull = result.getDataPoints().stream()
                .allMatch(dp -> dp.getInflationAdjustedValue() == null);
        assertTrue(allNull, "All inflation values should be null when InflationService throws");
    }

    @Test
    void simulate_nullInvestment_throwsException() {
        List<InvestmentInput> investments = new ArrayList<>();
        investments.add(null);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(investments)
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> simulationService.simulate(request));
        assertTrue(ex.getMessage().contains("index"));
    }

    @Test
    void simulate_nullAmount_throwsException() {
        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(null).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> simulationService.simulate(request));
        assertTrue(ex.getMessage().toLowerCase().contains("amount") || ex.getMessage().contains("index"));
    }

    @Test
    void simulate_nullDate_throwsException() {
        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(null).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> simulationService.simulate(request));
        assertTrue(ex.getMessage().toLowerCase().contains("date") || ex.getMessage().contains("index"));
    }

    @Test
    void simulate_findClosestPrice_findsWeekendPrice() {
        // Jan 7, 2023 is a Saturday — should use Friday Jan 6 price (133.0)
        List<HistoricalPrice> prices = new ArrayList<>();
        prices.add(HistoricalPrice.builder()
                .date(LocalDate.of(2023, 1, 6)).open(132.0).high(134.0).low(131.0)
                .close(133.0).adjustedClose(133.0).volume(1000000L).build());

        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(prices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1330.0).date(LocalDate.of(2023, 1, 7)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 7))
                .inflationAdjusted(false)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        // 1330 / 133.0 = 10 shares, portfolio = 10 * 133 = 1330
        assertEquals(10.0, result.getDataPoints().get(0).getPortfolioValue() / 133.0, 0.01);
    }

    @Test
    void simulate_inflationAdjusted_deflatesEntirePortfolioValue() {
        // Setup: 10-day price series from 130.0 to 139.0
        // Single investment of $1000 on Jan 3 at price 130.0 → 7.6923 shares
        // By Jan 12 (price 139.0): portfolio = 7.6923 * 139.0 ≈ 1069.23
        // CPI factor = 1.10 (10% inflation)
        // Correct: inflationAdjustedValue = 1069.23 / 1.10 ≈ 972.03
        // Wrong (old formula): 1000 + (69.23 / 1.10) ≈ 1062.93

        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);
        when(inflationService.getCumulativeFactor(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1.10);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 3)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(true)
                .displayMode("accumulated")
                .build();

        SimulationResult result = simulationService.simulate(request);

        // Find the last data point (Jan 12, price = 139.0)
        var lastDp = result.getDataPoints().get(result.getDataPoints().size() - 1);
        double expectedPortfolioValue = 1000.0 / 130.0 * 139.0; // ≈ 1069.23
        double expectedInflationAdjusted = expectedPortfolioValue / 1.10; // ≈ 972.03

        assertEquals(expectedPortfolioValue, lastDp.getPortfolioValue(), 0.01);
        assertEquals(expectedInflationAdjusted, lastDp.getInflationAdjustedValue(), 0.01);

        // Verify it's NOT the old wrong formula
        double wrongFormulaResult = 1000.0 + ((expectedPortfolioValue - 1000.0) / 1.10);
        assertNotEquals(wrongFormulaResult, lastDp.getInflationAdjustedValue(), 0.01,
                "Inflation-adjusted value should not use the old incorrect formula");
    }

    @Test
    void simulate_multipleInvestments_populatesPerInvestmentValues() {
        when(marketDataService.getHistoricalPrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePrices);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("AAPL")
                .investments(List.of(
                        InvestmentInput.builder().amount(500.0).date(LocalDate.of(2023, 1, 3)).build(),
                        InvestmentInput.builder().amount(1000.0).date(LocalDate.of(2023, 1, 5)).build()
                ))
                .endDate(LocalDate.of(2023, 1, 12))
                .inflationAdjusted(false)
                .displayMode("per_investment")
                .build();

        SimulationResult result = simulationService.simulate(request);

        assertNotNull(result);
        assertEquals("per_investment", result.getDisplayMode());
        assertNotNull(result.getInvestmentLabels());
        assertEquals(2, result.getInvestmentLabels().size());
        assertTrue(result.getInvestmentLabels().get(0).contains("$500"));
        assertTrue(result.getInvestmentLabels().get(1).contains("$1000"));

        // Check per-investment values exist in data points
        List<SimulationDataPoint> dataPoints = result.getDataPoints();
        assertFalse(dataPoints.isEmpty());

        // First data point (Jan 3): only investment 1 should have a value
        SimulationDataPoint first = dataPoints.get(0);
        assertNotNull(first.getPerInvestmentValues());
        assertEquals(2, first.getPerInvestmentValues().size());
        assertNotNull(first.getPerInvestmentValues().get(0), "First investment should have value on its date");
        assertNull(first.getPerInvestmentValues().get(1), "Second investment should be null before its date");

        // Last data point: both investments should have values
        SimulationDataPoint last = dataPoints.get(dataPoints.size() - 1);
        assertNotNull(last.getPerInvestmentValues().get(0));
        assertNotNull(last.getPerInvestmentValues().get(1));

        // Sum of per-investment values should equal portfolio value
        double sumPerInvestment = last.getPerInvestmentValues().stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        assertEquals(last.getPortfolioValue(), sumPerInvestment, 0.01,
                "Sum of per-investment values should equal total portfolio value");
    }
}
