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
import '@/components/styles/charts.css';

const INVESTMENT_COLORS = [
  '#8884d8', '#82ca9d', '#ffc658', '#ff7300', '#a4de6c',
  '#d0ed57', '#83a6ed', '#8dd1e1', '#ff6b6b', '#c9b1ff',
];

const StockChart = ({ data, displayMode, inflationAdjusted, investmentLabels }) => {
  // Transform data for per_investment mode: flatten perInvestmentValues array into named keys
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return data;
    if (displayMode !== 'per_investment') return data;

    const numInvestments = investmentLabels ? investmentLabels.length : 0;
    if (numInvestments === 0) return data;

    return data.map((point) => {
      const transformed = { ...point };
      const perInvValues = point.perInvestmentValues;
      if (perInvValues && Array.isArray(perInvValues)) {
        for (let i = 0; i < numInvestments; i++) {
          transformed[`inv_${i}`] = perInvValues[i] != null ? perInvValues[i] : undefined;
        }
      }
      const infPerInvValues = point.inflationAdjustedPerInvestmentValues;
      if (infPerInvValues && Array.isArray(infPerInvValues)) {
        for (let i = 0; i < numInvestments; i++) {
          transformed[`inflationInv_${i}`] = infPerInvValues[i] != null ? infPerInvValues[i] : undefined;
        }
      }
      return transformed;
    });
  }, [data, displayMode, investmentLabels]);

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

  const numInvestmentsForFallback = investmentLabels ? investmentLabels.length : 0;
  const hasPerInvestmentDataForFallback = displayMode === 'per_investment' && numInvestmentsForFallback > 0;

  const getPrimaryDataKey = () => {
    switch (displayMode) {
      case 'percentage':
        return 'gainPercent';
      case 'per_investment':
        return hasPerInvestmentDataForFallback ? 'inv_0' : 'portfolioValue';
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

  const numInvestments = investmentLabels ? investmentLabels.length : 0;
  const hasPerInvestmentData = displayMode === 'per_investment' && numInvestments > 0;

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
          
          {!hasPerInvestmentData && displayMode !== 'percentage' && displayMode !== 'nominal' && (
            <Line
              type="monotone"
              dataKey="totalInvested"
              stroke="#ff7300"
              name="Total Invested"
              dot={false}
              strokeWidth={2}
            />
          )}
          
          {hasPerInvestmentData ? (
            // Render one Line per investment + optional inflation-adjusted counterpart
            investmentLabels.map((label, i) => (
              <React.Fragment key={`inv-group-${i}`}>
                <Line
                  type="monotone"
                  dataKey={`inv_${i}`}
                  stroke={INVESTMENT_COLORS[i % INVESTMENT_COLORS.length]}
                  name={label}
                  dot={false}
                  strokeWidth={2}
                  connectNulls={false}
                />
                {inflationAdjusted && (
                  <Line
                    type="monotone"
                    dataKey={`inflationInv_${i}`}
                    stroke={INVESTMENT_COLORS[i % INVESTMENT_COLORS.length]}
                    name={`${label} (inflation-adj.)`}
                    dot={false}
                    strokeWidth={2}
                    strokeDasharray="8 4"
                    connectNulls={false}
                  />
                )}
              </React.Fragment>
            ))
          ) : (
            <Line
              type="monotone"
              dataKey={getPrimaryDataKey()}
              stroke={getPrimaryColor()}
              name={getPrimaryName()}
              dot={false}
              strokeWidth={2}
            />
          )}
          
          {inflationAdjusted && displayMode !== 'percentage' && !hasPerInvestmentData && (
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
          
          {displayMode === 'percentage' && (
            <ReferenceLine y={0} stroke="#666" strokeDasharray="3 3" />
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default StockChart;
