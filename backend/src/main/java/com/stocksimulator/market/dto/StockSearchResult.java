package com.stocksimulator.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSearchResult {

    private String symbol;
    private String name;
    private String type;
    private String exchange;
}
