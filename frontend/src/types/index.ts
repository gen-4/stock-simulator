// API Response Types

export interface User {
  id: number;
  username: string;
  email: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginResponse extends AuthTokens {
  user: User;
}

export interface RegisterResponse extends AuthTokens {
  user: User;
}

export interface SearchResult {
  symbol: string;
  name: string;
  exchange: string;
  type: string;
}

export interface StockQuote {
  symbol: string;
  currentPrice: number;
  change: number;
  changePercent: number;
  dayHigh: number;
  dayLow: number;
  volume: number;
  timestamp: string;
}

export interface HistoricalPrice {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  adjustedClose: number;
}

export interface InvestmentInput {
  amount: number;
  date: string;
}

export interface SimulationRequest {
  symbol: string;
  investments: InvestmentInput[];
  endDate: string | null;
  inflationAdjusted: boolean;
  displayMode: string;
}

export interface SimulationDataPoint {
  date: string;
  portfolioValue: number;
  totalInvested: number;
  gain: number;
  gainPercent: number;
  inflationAdjustedValue: number | null;
  perInvestmentValues?: number[];
  inflationAdjustedPerInvestmentValues?: number[];
}

export interface SimulationResult {
  symbol: string;
  totalInvested: number;
  finalValue: number;
  totalGain: number;
  totalGainPercent: number;
  inflationAdjusted: boolean;
  displayMode: string;
  dataPoints: SimulationDataPoint[];
  investmentLabels?: string[];
}

// Redux State Types

export interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null | undefined;
}

export interface MarketState {
  searchResults: SearchResult[];
  currentQuote: StockQuote | null;
  historicalPrices: HistoricalPrice[];
  loading: boolean;
  error: string | null | undefined;
}

export interface SimulationState {
  result: SimulationResult | null;
  loading: boolean;
  error: string | null | undefined;
  displayMode: string;
  inflationAdjusted: boolean;
}

export interface PortfolioState {
  portfolios: unknown[];
  currentPortfolio: unknown | null;
  transactions: unknown[];
  loading: boolean;
  error: string | null | undefined;
}

// Display Mode
export type DisplayMode = 'accumulated' | 'per_investment' | 'percentage' | 'nominal';
