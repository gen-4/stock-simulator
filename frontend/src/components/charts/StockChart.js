import React, { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import '../styles/charts.css';

const StockChart = ({ data, displayMode, inflationAdjusted }) => {
  // Transform data for per_investment mode: compute perInvestmentValue
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return data;
    if (displayMode !== 'per_investment') return data;

    let investmentCount = 0;
    let lastInvested = null;
    return data.map((point) => {
      if (lastInvested === null || point.totalInvested !== lastInvested) {
        investmentCount++;
        lastInvested = point.totalInvested;
      }
      return {
        ...point,
        perInvestmentValue: investmentCount > 0
          ? point.portfolioValue / investmentCount
          : 0,
      };
    });
  }, [data, displayMode]);

  if (!data || data.length === 0) {
    return <div className="no-data">No data available for chart</div>;
  }

  const formatYAxis = (value) => {
    if (displayMode === 'percentage') {
      return `${value.toFixed(1)}%`;
    }
    return `$${value.toLocaleString()}`;
  };

  const formatTooltip = (value, name) => {
    if (displayMode === 'percentage') {
      return [`${value.toFixed(2)}%`, name];
    }
    return [`$${value.toLocaleString()}`, name];
  };

  const getPrimaryColor = () => {
    switch (displayMode) {
      case 'per_investment':
        return '#82ca9d';
      case 'nominal':
        return '#ffc658';
      case 'percentage':
      default:
        return '#8884d8';
    }
  };

  const getPrimaryDataKey = () => {
    switch (displayMode) {
      case 'percentage':
        return 'gainPercent';
      case 'per_investment':
        return 'perInvestmentValue';
      case 'nominal':
        return 'gain';
      default:
        return 'portfolioValue';
    }
  };

  const getPrimaryName = () => {
    switch (displayMode) {
      case 'percentage':
        return 'Return %';
      case 'per_investment':
        return 'Per-Investment Value';
      case 'nominal':
        return 'Gain ($)';
      default:
        return inflationAdjusted ? 'Nominal Value' : 'Portfolio Value';
    }
  };

  return (
    <div className="stock-chart">
      <ResponsiveContainer width="100%" height={400}>
        <LineChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 10 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis 
            dataKey="date" 
            tick={{ fontSize: 12 }}
            tickFormatter={(date) => new Date(date).toLocaleDateString()}
          />
          <YAxis 
            tickFormatter={formatYAxis}
            tick={{ fontSize: 12 }}
          />
          <Tooltip 
            formatter={formatTooltip}
            labelFormatter={(label) => new Date(label).toLocaleDateString()}
          />
          <Legend />
          
          {/* Total Invested line — hidden for percentage mode */}
          {displayMode !== 'percentage' && (
            <Line
              type="monotone"
              dataKey="totalInvested"
              stroke="#ff7300"
              name="Total Invested"
              dot={false}
              strokeWidth={2}
            />
          )}
          
          {/* Primary line (mode-specific) */}
          <Line
            type="monotone"
            dataKey={getPrimaryDataKey()}
            stroke={getPrimaryColor()}
            name={getPrimaryName()}
            dot={false}
            strokeWidth={2}
          />
          
          {/* Inflation-adjusted line — dashed orange, shown when checkbox is on and not in percentage mode */}
          {inflationAdjusted && displayMode !== 'percentage' && (
            <Line
              type="monotone"
              dataKey="inflationAdjustedValue"
              stroke="#e6550d"
              name="Inflation-Adjusted Value"
              dot={false}
              strokeWidth={2}
              strokeDasharray="8 4"
            />
          )}
          
          {/* Zero reference line for percentage mode */}
          {displayMode === 'percentage' && (
            <ReferenceLine y={0} stroke="#666" strokeDasharray="3 3" />
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default StockChart;
