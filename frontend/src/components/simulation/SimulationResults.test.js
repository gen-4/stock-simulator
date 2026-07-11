import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import simulationReducer from '../../store/slices/simulationSlice';
import SimulationResults from './SimulationResults';

// Mock StockChart to avoid recharts rendering complexity
jest.mock('../charts/StockChart', () => {
  return function MockStockChart({ data, displayMode, inflationAdjusted }) {
    return (
      <div data-testid="mock-stock-chart" data-mode={displayMode} data-inflation={String(inflationAdjusted)}>
        Chart with {data?.length || 0} points
      </div>
    );
  };
});

const createMockStore = (simulationState) =>
  configureStore({
    reducer: { simulation: simulationReducer },
    preloadedState: { simulation: simulationState },
  });

const renderWithStore = (simulationState) => {
  const store = createMockStore(simulationState);
  return render(
    <Provider store={store}>
      <SimulationResults />
    </Provider>
  );
};

const sampleResult = {
  symbol: 'AAPL',
  totalInvested: 5000,
  finalValue: 6200,
  totalGain: 1200,
  totalGainPercent: 24.0,
  inflationAdjusted: false,
  displayMode: 'accumulated',
  dataPoints: [
    { date: '2023-01-03', portfolioValue: 5000, totalInvested: 5000, gain: 0, gainPercent: 0 },
    { date: '2023-01-04', portfolioValue: 6200, totalInvested: 5000, gain: 1200, gainPercent: 24 },
  ],
};

describe('SimulationResults', () => {
  describe('loading state', () => {
    it('shows loading message when loading is true', () => {
      renderWithStore({ result: null, loading: true, error: null });
      expect(screen.getByText('Running simulation...')).toBeInTheDocument();
    });
  });

  describe('error state', () => {
    it('shows error message', () => {
      renderWithStore({ result: null, loading: false, error: 'Something went wrong' });
      expect(screen.getByText(/Something went wrong/)).toBeInTheDocument();
    });
  });

  describe('empty state', () => {
    it('shows empty state message when no result', () => {
      renderWithStore({ result: null, loading: false, error: null });
      expect(screen.getByText(/Select a stock/)).toBeInTheDocument();
    });
  });

  describe('result display', () => {
    it('renders result header with symbol', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      expect(screen.getByText('Simulation Results for AAPL')).toBeInTheDocument();
    });

    it('renders total invested value', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      expect(screen.getByText('$5,000')).toBeInTheDocument();
    });

    it('renders final value', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      expect(screen.getByText('$6,200')).toBeInTheDocument();
    });

    it('renders positive gain with + prefix', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      expect(screen.getByText(/+\$1,200/)).toBeInTheDocument();
    });

    it('renders gain card with positive class when gain >= 0', () => {
      const { container } = renderWithStore({ result: sampleResult, loading: false, error: null });
      const gainCard = container.querySelector('.summary-card.positive');
      expect(gainCard).toBeInTheDocument();
    });

    it('renders gain card with negative class when gain < 0', () => {
      const negativeResult = { ...sampleResult, totalGain: -500, totalGainPercent: -10 };
      const { container } = renderWithStore({ result: negativeResult, loading: false, error: null });
      const gainCard = container.querySelector('.summary-card.negative');
      expect(gainCard).toBeInTheDocument();
    });
  });

  describe('inflation note', () => {
    it('shows inflation note when inflationAdjusted is true', () => {
      const result = { ...sampleResult, inflationAdjusted: true };
      renderWithStore({ result, loading: false, error: null });
      expect(screen.getByText('Values are adjusted for inflation')).toBeInTheDocument();
    });

    it('does not show inflation note when inflationAdjusted is false', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      expect(screen.queryByText('Values are adjusted for inflation')).not.toBeInTheDocument();
    });
  });

  describe('chart rendering', () => {
    it('passes correct props to StockChart', () => {
      renderWithStore({ result: sampleResult, loading: false, error: null });
      const chart = screen.getByTestId('mock-stock-chart');
      expect(chart).toHaveAttribute('data-mode', 'accumulated');
      expect(chart).toHaveAttribute('data-inflation', 'false');
    });

    it('passes inflationAdjusted=true to StockChart', () => {
      const result = { ...sampleResult, inflationAdjusted: true };
      renderWithStore({ result, loading: false, error: null });
      const chart = screen.getByTestId('mock-stock-chart');
      expect(chart).toHaveAttribute('data-inflation', 'true');
    });
  });
});
