import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../api/api';

export const runSimulation = createAsyncThunk(
  'simulation/runSimulation',
  async (simulationData, { rejectWithValue }) => {
    try {
      const response = await api.post('/simulation/simulate', simulationData);
      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Simulation failed' }));
        return rejectWithValue(error.message || 'Simulation failed');
      }
      return await response.json();
    } catch (error) {
      return rejectWithValue(error.message || 'Simulation failed');
    }
  }
);

const simulationSlice = createSlice({
  name: 'simulation',
  initialState: {
    result: null,
    loading: false,
    error: null,
    displayMode: 'accumulated',
    inflationAdjusted: false,
  },
  reducers: {
    setDisplayMode: (state, action) => {
      state.displayMode = action.payload;
    },
    setInflationAdjusted: (state, action) => {
      state.inflationAdjusted = action.payload;
    },
    clearSimulation: (state) => {
      state.result = null;
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(runSimulation.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(runSimulation.fulfilled, (state, action) => {
        state.loading = false;
        state.result = action.payload;
      })
      .addCase(runSimulation.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      });
  },
});

export const { setDisplayMode, setInflationAdjusted, clearSimulation } = simulationSlice.actions;
export default simulationSlice.reducer;
