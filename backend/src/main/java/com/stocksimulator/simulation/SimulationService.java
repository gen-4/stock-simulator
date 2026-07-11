package com.stocksimulator.simulation;

import com.stocksimulator.market.MarketDataService;
import com.stocksimulator.market.dto.HistoricalPrice;
import com.stocksimulator.simulation.dto.InvestmentInput;
import com.stocksimulator.simulation.dto.SimulationDataPoint;
import com.stocksimulator.simulation.dto.SimulationRequest;
import com.stocksimulator.simulation.dto.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final MarketDataService marketDataService;
    private final InflationService inflationService;

    public SimulationService(MarketDataService marketDataService, InflationService inflationService) {
        this.marketDataService = marketDataService;
        this.inflationService = inflationService;
    }

    /**
     * Runs a dollar-cost-averaging simulation for the given symbol and investment schedule.
     *
     * <p>For each calendar day between the earliest investment date and the end date,
     * the service checks whether an investment is scheduled. When one is found it
     * converts the investment amount to shares at the closing price for that day and
     * tracks the running portfolio value, cumulative gain and gain percentage.</p>
     *
     * @param request the simulation parameters (symbol, investments, end date, options)
     * @return a fully populated {@link SimulationResult} with data-point time series and summary stats
     * @throws IllegalArgumentException if the request is invalid (missing data, bad dates, etc.)
     */
    public SimulationResult simulate(SimulationRequest request) {
        log.info("Starting simulation for symbol={}, investments={}, endDate={}, inflationAdjusted={}",
                request.getSymbol(), request.getInvestments().size(),
                request.getEndDate(), request.isInflationAdjusted());

        String symbol = request.getSymbol().toUpperCase();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        boolean inflationAdjusted = request.isInflationAdjusted();
        String displayMode = request.getDisplayMode();

        // ── 1. Validate & sort investments ──────────────────────────────────
        List<InvestmentInput> investments = validateAndSortInvestments(request.getInvestments(), endDate);

        LocalDate earliestDate = investments.get(0).getDate();
        LocalDate latestInvestmentDate = investments.get(investments.size() - 1).getDate();

        if (endDate.isBefore(earliestDate)) {
            throw new IllegalArgumentException(
                    "End date (" + endDate + ") is before the earliest investment date (" + earliestDate + ")");
        }

        // ── 2. Fetch historical prices ──────────────────────────────────────
        List<HistoricalPrice> historicalPrices =
                marketDataService.getHistoricalPrices(symbol, earliestDate, endDate);

        if (historicalPrices.isEmpty()) {
            log.warn("No historical price data returned for symbol={} between {} and {}", symbol, earliestDate, endDate);
            throw new IllegalArgumentException(
                    "No price data available for symbol '" + symbol + "' in the requested date range.");
        }

        // Build a lookup map: date -> HistoricalPrice
        Map<LocalDate, HistoricalPrice> priceMap = new HashMap<>();
        for (HistoricalPrice hp : historicalPrices) {
            priceMap.put(hp.getDate(), hp);
        }

        // ── 3. Build investment schedule map ─────────────────────────────────
        // Key: date, Value: total amount to invest on that day
        Map<LocalDate, Double> investmentSchedule = new HashMap<>();
        for (InvestmentInput inv : investments) {
            investmentSchedule.merge(inv.getDate(), inv.getAmount(), Double::sum);
        }

        // ── 4. Walk each trading day and build data points ───────────────────
        List<SimulationDataPoint> dataPoints = new ArrayList<>();
        double accumulatedShares = 0.0;
        double totalInvested = 0.0;

        LocalDate currentDate = earliestDate;
        while (!currentDate.isAfter(endDate)) {
            Double priceOnDay = findClosestPrice(currentDate, priceMap);

            if (priceOnDay != null) {
                // Process any scheduled investments for this day
                Double investAmount = investmentSchedule.get(currentDate);
                if (investAmount != null && investAmount > 0) {
                    double sharesBought = investAmount / priceOnDay;
                    accumulatedShares += sharesBought;
                    totalInvested += investAmount;
                    log.debug("Investment on {}: ${} -> {} shares at ${} (accumulated: {} shares)",
                            currentDate, investAmount, sharesBought, priceOnDay, accumulatedShares);
                }

                // Calculate portfolio metrics
                double portfolioValue = accumulatedShares * priceOnDay;
                double gain = portfolioValue - totalInvested;
                double gainPercent = totalInvested > 0 ? (gain / totalInvested) * 100.0 : 0.0;

                // Inflation-adjusted value
                Double inflationAdjustedValue = null;
                if (inflationAdjusted && totalInvested > 0) {
                    try {
                        double cpiFactor = inflationService.getCumulativeFactor(earliestDate, currentDate);
                        inflationAdjustedValue = totalInvested + (gain / cpiFactor);
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
                        .build();

                dataPoints.add(dataPoint);
            }

            currentDate = currentDate.plusDays(1);
        }

        if (dataPoints.isEmpty()) {
            log.warn("Simulation produced no data points for symbol={}", symbol);
            throw new IllegalArgumentException(
                    "Simulation produced no data points. Check that price data exists for the investment dates.");
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
                .build();

        log.info("Simulation complete for symbol={}: {} data points, invested=${}, final=${}, gain={}%",
                symbol, dataPoints.size(), result.getTotalInvested(), result.getFinalValue(), result.getTotalGainPercent());

        return result;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Validates investment inputs and returns them sorted by date ascending.
     *
     * @throws IllegalArgumentException if investments are null/empty, contain nulls, or have dates after endDate
     */
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
                        "Investment at index " + i + " has invalid amount: " + inv.getAmount());
            }
            if (inv.getDate() == null) {
                throw new IllegalArgumentException("Investment at index " + i + " has a null date");
            }
            if (inv.getDate().isAfter(endDate)) {
                throw new IllegalArgumentException(
                        "Investment at index " + i + " has date " + inv.getDate() + " which is after the end date " + endDate);
            }
        }

        List<InvestmentInput> sorted = new ArrayList<>(investments);
        sorted.sort(Comparator.comparing(InvestmentInput::getDate));
        return sorted;
    }

    /**
     * Finds the closest price for the given date. If the exact date is not present
     * (e.g. a weekend), walks backwards up to 5 calendar days to find the most
     * recent available price.
     *
     * @param targetDate the desired date
     * @param priceMap   map of dates to historical prices
     * @return the closing price, or null if none found within range
     */
    private Double findClosestPrice(LocalDate targetDate, Map<LocalDate, HistoricalPrice> priceMap) {
        // Try the exact date first
        HistoricalPrice exact = priceMap.get(targetDate);
        if (exact != null) {
            return exact.getClose();
        }

        // Walk backwards up to 7 calendar days (covers weekends + holidays)
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
