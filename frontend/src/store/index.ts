import { configureStore } from '@reduxjs/toolkit';
import authReducer from '@/store/slices/authSlice';
import marketReducer from '@/store/slices/marketSlice';
import simulationReducer from '@/store/slices/simulationSlice';
import portfolioReducer from '@/store/slices/portfolioSlice';

const store = configureStore({
  reducer: {
    auth: authReducer,
    market: marketReducer,
    simulation: simulationReducer,
    portfolio: portfolioReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export default store;
