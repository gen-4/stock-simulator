import { vi } from 'vitest';
import simulationReducer, {
  setDisplayMode,
  setInflationAdjusted,
  clearSimulation,
} from './simulationSlice';

// Mock the api module
vi.mock('../../api/api', () => ({
  post: vi.fn(),
  get: vi.fn(),
}));

describe('simulationSlice', () => {
  const initialState = {
    result: null,
    loading: false,
    error: null,
    displayMode: 'accumulated',
    inflationAdjusted: false,
  };

  describe('initial state', () => {
    it('returns correct initial state', () => {
      const state = simulationReducer(undefined, { type: 'unknown' });
      expect(state).toEqual(initialState);
    });
  });

  describe('reducers', () => {
    it('setDisplayMode updates displayMode', () => {
      const state = simulationReducer(initialState, setDisplayMode('per_investment'));
      expect(state.displayMode).toBe('per_investment');
    });

    it('setInflationAdjusted toggles flag', () => {
      const state = simulationReducer(initialState, setInflationAdjusted(true));
      expect(state.inflationAdjusted).toBe(true);
    });

    it('clearSimulation resets result and error', () => {
      const dirtyState = {
        ...initialState,
        result: { symbol: 'AAPL' },
        error: 'some error',
      };
      const state = simulationReducer(dirtyState, clearSimulation());
      expect(state.result).toBeNull();
      expect(state.error).toBeNull();
    });
  });

  describe('runSimulation async thunk', () => {
    it('pending sets loading true and clears error', () => {
      const action = { type: 'simulation/runSimulation/pending' };
      const state = simulationReducer({ ...initialState, error: 'old error' }, action);
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('fulfilled sets result and loading false', () => {
      const payload = { symbol: 'AAPL', totalInvested: 1000, dataPoints: [] };
      const action = { type: 'simulation/runSimulation/fulfilled', payload };
      const state = simulationReducer({ ...initialState, loading: true }, action);
      expect(state.loading).toBe(false);
      expect(state.result).toEqual(payload);
    });

    it('rejected sets error and loading false', () => {
      const action = { type: 'simulation/runSimulation/rejected', payload: 'Simulation failed' };
      const state = simulationReducer({ ...initialState, loading: true }, action);
      expect(state.loading).toBe(false);
      expect(state.error).toBe('Simulation failed');
    });
  });
});
