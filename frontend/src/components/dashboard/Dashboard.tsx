import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { logout } from '@/store/slices/authSlice';
import type { RootState } from '@/store';
import type { SearchResult } from '@/types';
import StockSearch from '@/components/simulation/StockSearch';
import SimulationForm from '@/components/simulation/SimulationForm';
import SimulationResults from '@/components/simulation/SimulationResults';
import '@/components/styles/dashboard.css';

const Dashboard = () => {
  const dispatch = useDispatch();
  const { isAuthenticated } = useSelector((state: RootState) => state.auth);
  const [selectedStock, setSelectedStock] = useState<SearchResult | null>(null);

  const handleLogout = () => {
    dispatch(logout());
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>Stock Investment Simulator</h1>
        <button onClick={handleLogout} className="logout-button">
          Logout
        </button>
      </header>
      
      <main className="dashboard-main">
        <section className="stock-selection">
          <h2>Select a Stock</h2>
          <StockSearch onSelectStock={setSelectedStock} />
        </section>

        {selectedStock && (
          <section className="simulation-section">
            <h2>Configure Simulation for {selectedStock.symbol}</h2>
            <SimulationForm 
              stock={selectedStock} 
            />
          </section>
        )}

        <section className="results-section">
          <SimulationResults />
        </section>
      </main>
    </div>
  );
};

export default Dashboard;
