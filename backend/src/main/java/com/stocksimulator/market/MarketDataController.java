package com.stocksimulator.market;

import com.stocksimulator.market.dto.HistoricalPrice;
import com.stocksimulator.market.dto.StockQuote;
import com.stocksimulator.market.dto.StockSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<StockQuote> getQuote(@PathVariable String symbol) {
        log.info("GET /api/market/quote/{}", symbol);
        StockQuote quote = marketDataService.getQuote(symbol);
        if (quote == null) {
            log.warn("No quote data returned for symbol: {}", symbol);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(quote);
    }

    @GetMapping("/historical/{symbol}")
    public ResponseEntity<List<HistoricalPrice>> getHistorical(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        log.info("GET /api/market/historical/{} from {} to {}", symbol, startDate, endDate);
        List<HistoricalPrice> prices = marketDataService.getHistoricalPrices(symbol, startDate, endDate);
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockSearchResult>> search(@RequestParam String q) {
        log.info("GET /api/market/search?q={}", q);
        List<StockSearchResult> results = marketDataService.searchStocks(q);
        return ResponseEntity.ok(results);
    }
}
