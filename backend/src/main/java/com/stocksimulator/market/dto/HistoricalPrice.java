package com.stocksimulator.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalPrice {

    private LocalDate date;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double adjustedClose;
    private Long volume;
}
