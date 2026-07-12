package com.stocksimulator.simulation;

import com.stocksimulator.market.MarketDataService;
import com.stocksimulator.market.dto.HistoricalPrice;
import com.stocksimulator.simulation.dto.InvestmentInput;
import com.stocksimulator.simulation.dto.SimulationDataPoint;
import com.stocksimulator.simulation.dto.SimulationRequest;
import com.stocksimulator.simulation.dto.SimulationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final MarketDataService marketDataService;
    private final InflationService inflationService;

    private static final DateTimeFormatter LABEL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /**
     * Runs a dollar-cost-averaging simulation for the given symbol and investment schedule.
     */
    public SimulationResult simulate(SimulationRequest request) {
        log.info("Starting simulation for symbol={}, investments={}, endDate={}, inflationAdjusted={}",
            request.getSymbol(), request.getInvestments().size(),
            request.getEndDate(), request.isInflationAdjusted()
        );

        String symbol = request.getSymbol().toUpperCase();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        boolean inflationAdjusted = request.isInflationAdjusted();
        String displayMode = request.getDisplayMode();

        // ── 1. Validate & sort investments ──────────────────────────────────
        List<InvestmentInput> investments = validateAndSortInvestments(request.getInvestments(), endDate);

        LocalDate earliestDate = investments.get(0).getDate();

        if (endDate.isBefore(earliestDate)) {
            throw new IllegalArgumentException(
                "End date (" + endDate + ") is before the earliest investment date (" + earliestDate + ")"
            );
        }

        // ── 2. Fetch historical prices ──────────────────────────────────────
        List<HistoricalPrice> historicalPrices =
            marketDataService.getHistoricalPrices(symbol, earliestDate, endDate);

        if (historicalPrices.isEmpty()) {
            log.warn("No historical price data returned for symbol={} between {} and {}", symbol, earliestDate, endDate);
            throw new IllegalArgumentException(
                "No price data available for symbol '" + symbol + "' in the requested date range."
            );
        }

        // Build a lookup map: date -> HistoricalPrice
        Map<LocalDate, HistoricalPrice> priceMap = new HashMap<>();
        for (HistoricalPrice hp : historicalPrices) {
            priceMap.put(hp.getDate(), hp);
        }

        // ── 3. Build per-investment tracking ────────────────────────────────
        int investmentCount = investments.size();
        double[] sharesPerInvestment = new double[investmentCount];
        double totalInvested = 0.0;

        // Build investment labels
        List<String> investmentLabels = new ArrayList<>();
        for (InvestmentInput inv : investments) {
            investmentLabels.add(String.format("$%.0f on %s",
                inv.getAmount(), inv.getDate().format(LABEL_DATE_FORMAT)));
        }

        // ── 4. Walk each trading day and build data points ───────────────────
        List<SimulationDataPoint> dataPoints = new ArrayList<>();
        int nextInvestmentIdx = 0;

        LocalDate currentDate = earliestDate;
        while (!currentDate.isAfter(endDate)) {
            Double priceOnDay = findClosestPrice(currentDate, priceMap);

            if (priceOnDay != null) {
                // Process all investments scheduled for this date
                while (nextInvestmentIdx < investmentCount
                       && investments.get(nextInvestmentIdx).getDate().equals(currentDate)) {
                    double amount = investments.get(nextInvestmentIdx).getAmount();
                    double sharesBought = amount / priceOnDay;
                    sharesPerInvestment[nextInvestmentIdx] += sharesBought;
                    totalInvested += amount;
                    log.debug("Investment #{} on {}: ${} -> {} shares at ${}",
                        nextInvestmentIdx, currentDate, amount, sharesBought, priceOnDay
                    );
                    nextInvestmentIdx++;
                }

                // Compute per-investment values (only when needed)
                List<Double> perInvestmentValues = null;
                if ("per_investment".equals(displayMode)) {
                    perInvestmentValues = new ArrayList<>(investmentCount);
                    for (int i = 0; i < investmentCount; i++) {
                        if (sharesPerInvestment[i] > 0) {
                            perInvestmentValues.add(sharesPerInvestment[i] * priceOnDay);
                        } else {
                            perInvestmentValues.add(null);
                        }
                    }
                }

                // Compute portfolio totals
                double portfolioValue = 0.0;
                for (int i = 0; i < investmentCount; i++) {
                    portfolioValue += sharesPerInvestment[i] * priceOnDay;
                }
                double gain = portfolioValue - totalInvested;
                double gainPercent = totalInvested > 0 ? (gain / totalInvested) * 100.0 : 0.0;

                Double inflationAdjustedValue = null;
                if (inflationAdjusted && totalInvested > 0) {
                    try {
                        double cpiFactor = inflationService.getCumulativeFactor(earliestDate, currentDate);
                        inflationAdjustedValue = portfolioValue / cpiFactor;
                    } catch (Exception e) {
                        log.warn("Failed to get inflation factor for {}: {}", currentDate, e.getMessage());
                        inflationAdjustedValue = null;
                    }
                }

                SimulationDataPoint dataPoint = SimulationDataPoint.builder()
                    .date(currentDate)
                    .portfolioValue(portfolioValue)
                    .totalInvested(totalInvested)
                    .gain(gain)
                    .gainPercent(gainPercent)
                    .inflationAdjustedValue(inflationAdjustedValue)
                    .perInvestmentValues(perInvestmentValues)
                    .build();

                dataPoints.add(dataPoint);
            }

            currentDate = currentDate.plusDays(1);
        }

        if (dataPoints.isEmpty()) {
            log.warn("Simulation produced no data points for symbol={}", symbol);
            throw new IllegalArgumentException(
                "Simulation produced no data points. Check that price data exists for the investment dates."
            );
        }

        // ── 5. Build summary result ─────────────────────────────────────────
        SimulationDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);

        SimulationResult result = SimulationResult.builder()
            .symbol(symbol)
            .dataPoints(dataPoints)
            .totalInvested(lastPoint.getTotalInvested())
            .finalValue(lastPoint.getPortfolioValue())
            .totalGain(lastPoint.getGain())
            .totalGainPercent(lastPoint.getGainPercent())
            .inflationAdjusted(inflationAdjusted)
            .displayMode(displayMode)
            .investmentLabels(investmentLabels)
            .build();

        log.info("Simulation complete for symbol={}: {} data points, invested=${}, final=${}, gain={}%",
            symbol, dataPoints.size(), result.getTotalInvested(), result.getFinalValue(), result.getTotalGainPercent()
        );

        return result;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private List<InvestmentInput> validateAndSortInvestments(List<InvestmentInput> investments, LocalDate endDate) {
        if (investments == null || investments.isEmpty()) {
            throw new IllegalArgumentException("Investments list must not be null or empty");
        }

        for (int i = 0; i < investments.size(); i++) {
            InvestmentInput inv = investments.get(i);
            if (inv == null) {
                throw new IllegalArgumentException("Investment at index " + i + " is null");
            }
            if (inv.getAmount() == null || inv.getAmount() <= 0) {
                throw new IllegalArgumentException(
                    "Investment at index " + i + " has invalid amount: " + inv.getAmount()
                );
            }
            if (inv.getDate() == null) {
                throw new IllegalArgumentException("Investment at index " + i + " has a null date");
            }
            if (inv.getDate().isAfter(endDate)) {
                throw new IllegalArgumentException(
                    "Investment at index " + i + " has date " + inv.getDate() + " which is after the end date " + endDate
                );
            }
        }

        List<InvestmentInput> sorted = new ArrayList<>(investments);
        sorted.sort(Comparator.comparing(InvestmentInput::getDate));
        return sorted;
    }

    private Double findClosestPrice(LocalDate targetDate, Map<LocalDate, HistoricalPrice> priceMap) {
        HistoricalPrice exact = priceMap.get(targetDate);
        if (exact != null) {
            return exact.getClose();
        }

        for (int i = 1; i <= 7; i++) {
            LocalDate priorDate = targetDate.minusDays(i);
            HistoricalPrice prior = priceMap.get(priorDate);
            if (prior != null) {
                return prior.getClose();
            }
        }

        return null;
    }
}
