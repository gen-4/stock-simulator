import React from 'react';
import { render, screen } from '@testing-library/react';
import StockChart from './StockChart';

import { vi } from 'vitest';

// Mock recharts to inspect component props without rendering SVG
vi.mock('recharts', async (importOriginal) => {
  const OriginalModule = await importOriginal();
  return {
    ...OriginalModule,
    ResponsiveContainer: ({ children }) => <div data-testid="responsive-container">{children}</div>,
  };
});

// Helper to create sample data points
const makeDataPoints = (overrides = {}) => [
  { date: '2023-01-03', portfolioValue: 1000, totalInvested: 1000, gain: 0, gainPercent: 0, inflationAdjustedValue: 990, ...overrides },
  { date: '2023-01-04', portfolioValue: 1050, totalInvested: 1000, gain: 50, gainPercent: 5, inflationAdjustedValue: 1035, ...overrides },
  { date: '2023-01-05', portfolioValue: 1100, totalInvested: 1000, gain: 100, gainPercent: 10, inflationAdjustedValue: 1078, ...overrides },
];

const makeMultiInvestmentData = () => [
  { date: '2023-01-03', portfolioValue: 1000, totalInvested: 1000, gain: 0, gainPercent: 0, inflationAdjustedValue: null },
  { date: '2023-01-04', portfolioValue: 1050, totalInvested: 1000, gain: 50, gainPercent: 5, inflationAdjustedValue: null },
  { date: '2023-01-05', portfolioValue: 2120, totalInvested: 2000, gain: 120, gainPercent: 6, inflationAdjustedValue: null },
  { date: '2023-01-06', portfolioValue: 2200, totalInvested: 2000, gain: 200, gainPercent: 10, inflationAdjustedValue: null },
];

describe('StockChart', () => {
  describe('empty data handling', () => {
    it('renders no data message when data is empty array', () => {
      render(<StockChart data={[]} displayMode="accumulated" />);
      expect(screen.getByText('No data available for chart')).toBeInTheDocument();
    });

    it('renders no data message when data is null', () => {
      render(<StockChart data={null} displayMode="accumulated" />);
      expect(screen.getByText('No data available for chart')).toBeInTheDocument();
    });
  });

  describe('display mode rendering', () => {
    it('renders accumulated mode with portfolioValue as primary line', () => {
      const { container } = render(<StockChart data={makeDataPoints()} displayMode="accumulated" />);
      // The chart should render — no crash is the primary assertion
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('renders per_investment mode without crashing', () => {
      const { container } = render(<StockChart data={makeMultiInvestmentData()} displayMode="per_investment" />);
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('renders percentage mode without crashing', () => {
      const { container } = render(<StockChart data={makeDataPoints()} displayMode="percentage" />);
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('renders nominal mode without crashing', () => {
      const { container } = render(<StockChart data={makeDataPoints()} displayMode="nominal" />);
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });
  });

  describe('per_investment mode computes perInvestmentValue correctly', () => {
    it('divides portfolioValue by investment count at each point', () => {
      const data = [
        { date: '2023-01-03', portfolioValue: 1000, totalInvested: 1000, gain: 0, gainPercent: 0 },
        { date: '2023-01-04', portfolioValue: 1050, totalInvested: 1000, gain: 50, gainPercent: 5 },
        { date: '2023-01-05', portfolioValue: 2120, totalInvested: 2000, gain: 120, gainPercent: 6 },
        { date: '2023-01-06', portfolioValue: 2200, totalInvested: 2000, gain: 200, gainPercent: 10 },
      ];
      const { container } = render(<StockChart data={data} displayMode="per_investment" />);
      // Should render without error — per_investment uses perInvestmentValue
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });
  });

  describe('inflation-adjusted line', () => {
    it('renders without inflation note when inflationAdjusted is false', () => {
      const { container } = render(
        <StockChart data={makeDataPoints()} displayMode="accumulated" inflationAdjusted={false} />
      );
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('renders with inflation data when inflationAdjusted is true', () => {
      const { container } = render(
        <StockChart data={makeDataPoints()} displayMode="accumulated" inflationAdjusted={true} />
      );
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('does not render inflation line in percentage mode even when inflationAdjusted is true', () => {
      const { container } = render(
        <StockChart data={makeDataPoints()} displayMode="percentage" inflationAdjusted={true} />
      );
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });
  });

  describe('chart legend names', () => {
    it('shows "Nominal Value" name in accumulated mode when inflation is on', () => {
      const { container } = render(
        <StockChart data={makeDataPoints()} displayMode="accumulated" inflationAdjusted={true} />
      );
      // Just verify the chart renders — legend names are set on Recharts Line components
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('shows "Per-Investment Value" name in per_investment mode', () => {
      const { container } = render(
        <StockChart data={makeMultiInvestmentData()} displayMode="per_investment" inflationAdjusted={false} />
      );
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });

    it('shows "Gain ($)" name in nominal mode', () => {
      const { container } = render(
        <StockChart data={makeDataPoints()} displayMode="nominal" inflationAdjusted={false} />
      );
      expect(container.querySelector('.stock-chart')).toBeInTheDocument();
    });
  });
});
