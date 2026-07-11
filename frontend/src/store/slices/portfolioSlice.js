import { createSlice } from '@reduxjs/toolkit';

const portfolioSlice = createSlice({
  name: 'portfolio',
  initialState: {
    portfolios: [],
    currentPortfolio: null,
    transactions: [],
    loading: false,
    error: null,
  },
  reducers: {
    setCurrentPortfolio: (state, action) => {
      state.currentPortfolio = action.payload;
    },
  },
});

export const { setCurrentPortfolio } = portfolioSlice.actions;
export default portfolioSlice.reducer;
