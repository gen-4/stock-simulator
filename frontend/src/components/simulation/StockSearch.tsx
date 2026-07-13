import React, { useState, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { searchStocks, getStockQuote, clearSearchResults } from '@/store/slices/marketSlice';
import type { RootState } from '@/store';
import type { SearchResult } from '@/types';
import '@/components/styles/simulation.css';

interface StockSearchProps {
  onSelectStock: (stock: SearchResult) => void;
}

const StockSearch = ({ onSelectStock }: StockSearchProps) => {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const dispatch = useDispatch();
  const { searchResults, loading } = useSelector((state: RootState) => state.market);

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (debouncedQuery.length >= 2) {
      dispatch(searchStocks(debouncedQuery));
    } else {
      dispatch(clearSearchResults());
    }
  }, [debouncedQuery, dispatch]);

  const handleSelectStock = (stock: SearchResult) => {
    onSelectStock(stock);
    dispatch(getStockQuote(stock.symbol));
    setQuery('');
    dispatch(clearSearchResults());
  };

  return (
    <div className="stock-search">
      <div className="search-input-container">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search for a stock (e.g., AAPL, GOOGL, MSFT)"
          className="search-input"
        />
        {loading && <div className="search-loading">Searching...</div>}
      </div>
      
      {searchResults.length > 0 && (
        <div className="search-results">
          {searchResults.map((stock) => (
            <div
              key={stock.symbol}
              className="search-result-item"
              onClick={() => handleSelectStock(stock)}
            >
              <span className="stock-symbol">{stock.symbol}</span>
              <span className="stock-name">{stock.name}</span>
              <span className="stock-exchange">{stock.exchange}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default StockSearch;
