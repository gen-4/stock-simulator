import { createSlice } from '@reduxjs/toolkit';
import type { PortfolioState } from '@/types';

const portfolioSlice = createSlice({
  name: 'portfolio',
  initialState: {
    portfolios: [],
    currentPortfolio: null,
    transactions: [],
    loading: false,
    error: null,
  } as PortfolioState,
  reducers: {},
});

export default portfolioSlice.reducer;
