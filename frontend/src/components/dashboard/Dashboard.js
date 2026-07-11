import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { logout } from '../../store/slices/authSlice';
import StockSearch from '../simulation/StockSearch';
import SimulationForm from '../simulation/SimulationForm';
import SimulationResults from '../simulation/SimulationResults';
import '../styles/dashboard.css';

const Dashboard = () => {
  const dispatch = useDispatch();
  const { isAuthenticated } = useSelector(state => state.auth);
  const [selectedStock, setSelectedStock] = useState(null);

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
