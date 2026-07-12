import React from 'react';
import { useSelector } from 'react-redux';
import StockChart from '@/components/charts/StockChart';
import '@/components/styles/simulation.css';

const SimulationResults = () => {
  const { result, loading, error } = useSelector(state => state.simulation);

  if (loading) {
    return <div className="loading-results">Running simulation...</div>;
  }

  if (error) {
    return <div className="error-results">Error: {error}</div>;
  }

  if (!result) {
    return (
      <div className="empty-results">
        <p>Select a stock and configure your simulation to see results</p>
      </div>
    );
  }

  return (
    <div className="simulation-results">
      <h3>Simulation Results for {result.symbol}</h3>
      
      <div className="summary-cards">
        <div className="summary-card">
          <h4>Total Invested</h4>
          <p className="value">${result.totalInvested.toLocaleString()}</p>
        </div>
        <div className="summary-card">
          <h4>Final Value</h4>
          <p className="value">${result.finalValue.toLocaleString()}</p>
        </div>
        <div className={`summary-card ${result.totalGain >= 0 ? 'positive' : 'negative'}`}>
          <h4>Total Gain/Loss</h4>
          <p className="value">
            {result.totalGain >= 0 ? '+' : ''}{result.totalGain.toLocaleString()}
          </p>
          <p className="percentage">
            ({result.totalGainPercent >= 0 ? '+' : ''}{result.totalGainPercent.toFixed(2)}%)
          </p>
        </div>
      </div>

      {result.inflationAdjusted && (
        <div className="inflation-note">
          Values are adjusted for inflation
        </div>
      )}

      <div className="chart-container">
        <StockChart
          data={result.dataPoints}
          displayMode={result.displayMode}
          inflationAdjusted={result.inflationAdjusted}
          investmentLabels={result.investmentLabels}
        />
      </div>
    </div>
  );
};

export default SimulationResults;
