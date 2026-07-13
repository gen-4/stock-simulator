import { vi } from 'vitest';
import marketReducer, {
  clearSearchResults,
  clearHistoricalPrices,
} from './marketSlice';

// Mock the api module
vi.mock('../../api/api', () => ({
  post: vi.fn(),
  get: vi.fn(),
}));

describe('marketSlice', () => {
  const initialState = {
    searchResults: [],
    currentQuote: null,
    historicalPrices: [],
    loading: false,
    error: null,
  };

  describe('initial state', () => {
    it('returns correct initial state', () => {
      const state = marketReducer(undefined, { type: 'unknown' });
      expect(state).toEqual(initialState);
    });
  });

  describe('reducers', () => {
    it('clearSearchResults empties array', () => {
      const stateWithResults = { ...initialState, searchResults: [{ symbol: 'AAPL' }] };
      const state = marketReducer(stateWithResults, clearSearchResults());
      expect(state.searchResults).toEqual([]);
    });

    it('clearHistoricalPrices empties array', () => {
      const stateWithPrices = { ...initialState, historicalPrices: [{ date: '2023-01-03' }] };
      const state = marketReducer(stateWithPrices, clearHistoricalPrices());
      expect(state.historicalPrices).toEqual([]);
    });
  });

  describe('searchStocks async thunk', () => {
    it('pending sets loading true and clears error', () => {
      const state = marketReducer(initialState, { type: 'market/searchStocks/pending' });
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('fulfilled sets searchResults', () => {
      const payload = [{ symbol: 'AAPL', name: 'Apple' }];
      const state = marketReducer(initialState, { type: 'market/searchStocks/fulfilled', payload });
      expect(state.searchResults).toEqual(payload);
      expect(state.loading).toBe(false);
    });

    it('rejected sets error', () => {
      const state = marketReducer(initialState, { type: 'market/searchStocks/rejected', payload: 'Search failed' });
      expect(state.error).toBe('Search failed');
      expect(state.loading).toBe(false);
    });
  });

  describe('getStockQuote async thunk', () => {
    it('pending sets loading true', () => {
      const state = marketReducer(initialState, { type: 'market/getStockQuote/pending' });
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('fulfilled sets currentQuote', () => {
      const payload = { symbol: 'AAPL', currentPrice: 150.0 };
      const state = marketReducer(initialState, { type: 'market/getStockQuote/fulfilled', payload });
      expect(state.currentQuote).toEqual(payload);
      expect(state.loading).toBe(false);
    });

    it('rejected sets error', () => {
      const state = marketReducer(initialState, { type: 'market/getStockQuote/rejected', payload: 'Quote failed' });
      expect(state.error).toBe('Quote failed');
      expect(state.loading).toBe(false);
    });
  });

  describe('getHistoricalPrices async thunk', () => {
    it('pending sets loading true', () => {
      const state = marketReducer(initialState, { type: 'market/getHistoricalPrices/pending' });
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('fulfilled sets historicalPrices', () => {
      const payload = [{ date: '2023-01-03', close: 130.0 }];
      const state = marketReducer(initialState, { type: 'market/getHistoricalPrices/fulfilled', payload });
      expect(state.historicalPrices).toEqual(payload);
      expect(state.loading).toBe(false);
    });

    it('rejected sets error', () => {
      const state = marketReducer(initialState, { type: 'market/getHistoricalPrices/rejected', payload: 'History failed' });
      expect(state.error).toBe('History failed');
      expect(state.loading).toBe(false);
    });
  });
});
