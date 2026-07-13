import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { runSimulation, setDisplayMode, setInflationAdjusted } from '@/store/slices/simulationSlice';
import type { RootState } from '@/store';
import type { SearchResult, SimulationRequest } from '@/types';
import '@/components/styles/simulation.css';

interface SimulationFormProps {
  stock: SearchResult;
}

interface InvestmentEntry {
  amount: string;
  date: string;
}

const SimulationForm = ({ stock }: SimulationFormProps) => {
  const dispatch = useDispatch();
  const { displayMode, inflationAdjusted } = useSelector((state: RootState) => state.simulation);
  
  const [investments, setInvestments] = useState<InvestmentEntry[]>([
    { amount: '', date: '' }
  ]);
  const [endDate, setEndDate] = useState('');

  const addInvestment = () => {
    setInvestments([...investments, { amount: '', date: '' }]);
  };

  const removeInvestment = (index: number) => {
    if (investments.length > 1) {
      setInvestments(investments.filter((_, i) => i !== index));
    }
  };

  const updateInvestment = (index: number, field: keyof InvestmentEntry, value: string) => {
    const updated = investments.map((inv, i) => 
      i === index ? { ...inv, [field]: value } : inv
    );
    setInvestments(updated);
  };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    
    const simulationData: SimulationRequest = {
      symbol: stock.symbol,
      investments: investments.map(inv => ({
        amount: parseFloat(inv.amount),
        date: inv.date,
      })),
      endDate: endDate || null,
      inflationAdjusted,
      displayMode,
    };

    dispatch(runSimulation(simulationData));
  };

  return (
    <div className="simulation-form">
      <form onSubmit={handleSubmit}>
        <div className="investments-section">
          <h3>Investment Amounts</h3>
          {investments.map((investment, index) => (
            <div key={index} className="investment-row">
              <div className="investment-field">
                <label>Amount ($)</label>
                <input
                  type="number"
                  value={investment.amount}
                  onChange={(e) => updateInvestment(index, 'amount', e.target.value)}
                  placeholder="1000"
                  min="1"
                  step="0.01"
                  required
                />
              </div>
              <div className="investment-field">
                <label>Investment Date</label>
                <input
                  type="date"
                  value={investment.date}
                  onChange={(e) => updateInvestment(index, 'date', e.target.value)}
                  max={new Date().toISOString().split('T')[0]}
                  required
                />
              </div>
              {investments.length > 1 && (
                <button
                  type="button"
                  onClick={() => removeInvestment(index)}
                  className="remove-button"
                >
                  ×
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={addInvestment} className="add-button">
            + Add Another Investment
          </button>
        </div>

        <div className="simulation-options">
          <div className="option-group">
            <label>Evaluation End Date (optional)</label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              max={new Date().toISOString().split('T')[0]}
            />
            <small>Leave empty for current date</small>
          </div>

          <div className="option-group">
            <label>Display Mode</label>
            <select
              value={displayMode}
              onChange={(e) => dispatch(setDisplayMode(e.target.value))}
            >
              <option value="accumulated">Accumulated Value</option>
              <option value="per_investment">Per Investment</option>
              <option value="percentage">Percentage Return</option>
              <option value="nominal">Nominal Values</option>
            </select>
          </div>

          <div className="option-group checkbox">
            <label>
              <input
                type="checkbox"
                checked={inflationAdjusted}
                onChange={(e) => dispatch(setInflationAdjusted(e.target.checked))}
              />
              Adjust for Inflation
            </label>
          </div>
        </div>

        <button type="submit" className="simulate-button">
          Run Simulation
        </button>
      </form>
    </div>
  );
};

export default SimulationForm;
