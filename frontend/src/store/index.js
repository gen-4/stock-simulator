import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import marketReducer from './slices/marketSlice';
import simulationReducer from './slices/simulationSlice';
import portfolioReducer from './slices/portfolioSlice';

const store = configureStore({
  reducer: {
    auth: authReducer,
    market: marketReducer,
    simulation: simulationReducer,
    portfolio: portfolioReducer,
  },
});

export default store;
