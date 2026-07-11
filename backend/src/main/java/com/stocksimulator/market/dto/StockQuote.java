package com.stocksimulator.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockQuote {

    private String symbol;
    private String name;
    private Double currentPrice;
    private Double change;
    private Double changePercent;
    private Double previousClose;
    private Double open;
    private Double dayHigh;
    private Double dayLow;
    private Long volume;
    private LocalDateTime timestamp;
}
