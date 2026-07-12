import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '@/api/api';

export const searchStocks = createAsyncThunk(
  'market/searchStocks',
  async (query, { rejectWithValue }) => {
    try {
      const response = await api.get(`/market/search?q=${encodeURIComponent(query)}`);
      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Search failed' }));
        return rejectWithValue(error.message || 'Search failed');
      }
      return await response.json();
    } catch (error) {
      return rejectWithValue(error.message || 'Search failed');
    }
  }
);

export const getStockQuote = createAsyncThunk(
  'market/getStockQuote',
  async (symbol, { rejectWithValue }) => {
    try {
      const response = await api.get(`/market/quote/${encodeURIComponent(symbol)}`);
      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Failed to get quote' }));
        return rejectWithValue(error.message || 'Failed to get quote');
      }
      return await response.json();
    } catch (error) {
      return rejectWithValue(error.message || 'Failed to get quote');
    }
  }
);

export const getHistoricalPrices = createAsyncThunk(
  'market/getHistoricalPrices',
  async ({ symbol, startDate, endDate }, { rejectWithValue }) => {
    try {
      const response = await api.get(
        `/market/historical/${encodeURIComponent(symbol)}?startDate=${startDate}&endDate=${endDate}`
      );
      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Failed to get historical data' }));
        return rejectWithValue(error.message || 'Failed to get historical data');
      }
      return await response.json();
    } catch (error) {
      return rejectWithValue(error.message || 'Failed to get historical data');
    }
  }
);

const marketSlice = createSlice({
  name: 'market',
  initialState: {
    searchResults: [],
    currentQuote: null,
    historicalPrices: [],
    loading: false,
    error: null,
  },
  reducers: {
    clearSearchResults: (state) => {
      state.searchResults = [];
    },
    clearHistoricalPrices: (state) => {
      state.historicalPrices = [];
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(searchStocks.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(searchStocks.fulfilled, (state, action) => {
        state.loading = false;
        state.searchResults = action.payload;
      })
      .addCase(searchStocks.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      .addCase(getStockQuote.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(getStockQuote.fulfilled, (state, action) => {
        state.loading = false;
        state.currentQuote = action.payload;
      })
      .addCase(getStockQuote.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      .addCase(getHistoricalPrices.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(getHistoricalPrices.fulfilled, (state, action) => {
        state.loading = false;
        state.historicalPrices = action.payload;
      })
      .addCase(getHistoricalPrices.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      });
  },
});

export const { clearSearchResults, clearHistoricalPrices } = marketSlice.actions;
export default marketSlice.reducer;
