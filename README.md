# Stock Investment Simulator

A full-stack web application that allows users to simulate stock investment returns over time using real market data, with inflation adjustment and multiple visualization modes.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen) ![React](https://img.shields.io/badge/React-18-61DAFB) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1) ![Redis](https://img.shields.io/badge/Redis-7-DC382D) ![License](https://img.shields.io/badge/License-MIT-blue)

## Features

- **Dollar-Cost Averaging Simulation** — Input multiple investment amounts and dates, see how your portfolio would have grown using real historical stock prices
- **Real-Time Stock Data** — Powered by Yahoo Finance API with intelligent Redis caching (60s for quotes, 1hr for historical)
- **Inflation Adjustment** — BLS Consumer Price Index data adjusts your returns for inflation, showing real purchasing power gains
- **4 Display Modes** — Accumulated value, per-investment average, percentage returns, and nominal dollar gain
- **Interactive Charts** — Recharts-powered line charts with inflation overlay (dashed line), total invested baseline, and zero reference lines
- **JWT Authentication** — Secure access/refresh token flow with 15-minute access tokens and 7-day refresh tokens stored in Redis
- **Weekend/Holiday Handling** — Automatically resolves investment dates that fall on non-trading days to the nearest prior trading price

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2.0, Java 17 |
| Frontend | React 18, Redux Toolkit, Recharts |
| Primary DB | PostgreSQL 16 |
| Cache/Session | Redis 7 |
| Auth | JWT (jjwt 0.12.3) + BCrypt |
| External APIs | Yahoo Finance, BLS CPI |
| Containerization | Docker Compose (4 services) |

## Getting Started

### Prerequisites

- **Docker & Docker Compose** (recommended) OR
- Java 17 + Maven (for backend only)
- Node.js 18+ (for frontend only)
- PostgreSQL 16 + Redis 7 (if running locally)

### Quick Start with Docker

```bash
# Clone the repository
git clone https://github.com/your-username/stock-simulator.git
cd stock-simulator

# Start all services (PostgreSQL, Redis, Backend, Frontend)
docker-compose up -d

# Verify everything is running
curl http://localhost:8080/api/health
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key for JWT signing (min 32 chars) | `myDefaultSecretKey12345678901234567890` |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://postgres:5432/stocksimulator` |
| `SPRING_DATA_REDIS_HOST` | Redis hostname | `redis` |

### Running Locally (Without Docker)

**Backend:**
```bash
cd backend
./mvnw spring-boot:run    # Starts on port 8080 with dev profile
```

**Frontend:**
```bash
cd frontend
npm install
npm start                  # Starts on port 3000
```

## API Reference

### Health Check
```
GET /api/health
```
Returns database and Redis connectivity status.

### Authentication
```
POST /api/auth/register    → 201 Created
POST /api/auth/login       → 200 OK
POST /api/auth/refresh     → 200 OK
POST /api/auth/logout      → 200 OK
```

All auth endpoints accept/return JSON. Tokens are included in the response body.

### Market Data
```
GET /api/market/search?q={query}                          → Search stocks
GET /api/market/quote/{symbol}                            → Real-time quote
GET /api/market/historical/{symbol}?startDate={}&endDate={}  → Historical prices
```

### Simulation
```
POST /api/simulation/simulate
```

**Request body:**
```json
{
  "symbol": "AAPL",
  "investments": [
    { "amount": 1000.0, "date": "2023-01-03" },
    { "amount": 1000.0, "date": "2023-06-01" }
  ],
  "endDate": "2024-01-01",
  "inflationAdjusted": true,
  "displayMode": "accumulated"
}
```

**Display modes:** `accumulated` | `per_investment` | `percentage` | `nominal`

**Response:**
```json
{
  "symbol": "AAPL",
  "totalInvested": 2000.0,
  "finalValue": 2543.21,
  "totalGain": 543.21,
  "totalGainPercent": 27.16,
  "inflationAdjusted": true,
  "displayMode": "accumulated",
  "dataPoints": [
    {
      "date": "2023-01-03",
      "portfolioValue": 1000.0,
      "totalInvested": 1000.0,
      "gain": 0.0,
      "gainPercent": 0.0,
      "inflationAdjustedValue": 1000.0
    }
  ]
}
```

## Project Structure

```
stock-simulator/
├── backend/                            # Spring Boot REST API
│   ├── src/main/java/com/stocksimulator/
│   │   ├── auth/                      # JWT auth (JwtTokenProvider, AuthService, AuthController)
│   │   ├── config/                    # SecurityConfig, RedisConfig, WebClientConfig
│   │   ├── market/                    # MarketDataService (Yahoo Finance), DTOs
│   │   ├── simulation/                # SimulationService, InflationService, DTOs
│   │   ├── user/                      # User entity, UserRepository
│   │   └── portfolio/                 # Portfolio, Transaction entities
│   ├── src/test/java/                 # Backend test suite (~70 tests)
│   ├── src/main/resources/            # application.yml + profile configs
│   └── Dockerfile
├── frontend/                           # React SPA
│   ├── src/
│   │   ├── api/                       # api.js (fetch-based with auto token refresh)
│   │   ├── store/slices/              # Redux slices (auth, market, simulation, portfolio)
│   │   ├── components/
│   │   │   ├── charts/                # StockChart (Recharts)
│   │   │   ├── simulation/            # SimulationForm, SimulationResults
│   │   │   ├── auth/                  # Login, Register forms
│   │   │   └── styles/                # CSS files
│   │   └── setupTests.js              # Test setup
│   ├── src/**/*.test.js               # Frontend test suite (~55 tests)
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml
├── .env.example
└── AGENTS.md
```

## Testing

### Backend (~70 tests)

```bash
cd backend
./mvnw test    # Run all tests
```

| Test Class | Tests | Coverage |
|------------|-------|----------|
| JwtTokenProviderTest | 10 | Token generation, validation, expiration, cross-secret rejection |
| AuthServiceTest | 7 | Registration, login, refresh token rotation, logout |
| SimulationServiceTest | 13 | Investments, display modes, inflation fallback, null validation, weekend prices |
| InflationServiceTest | 9 | Construction, date edge cases, cache, BLS errors, WebClient failures |
| MarketDataServiceTest | 11 | Quote/historical/search with cache hit/miss, API errors |
| SimulationControllerIntegrationTest | 5 | @WebMvcTest: request validation, error handling |
| AuthControllerIntegrationTest | 8 | @WebMvcTest: all auth endpoints, validation |

### Frontend (~55 tests)

```bash
cd frontend
npm install
npm test -- --watchAll=false    # Run all tests
```

| Test File | Tests | Coverage |
|-----------|-------|----------|
| StockChart.test.js | 14 | Display modes, inflation line, empty data, per-investment computation |
| SimulationResults.test.js | 12 | Loading/error/empty states, result display, inflation note |
| simulationSlice.test.js | 7 | Redux reducers and async thunk lifecycle |
| authSlice.test.js | 10 | Auth state management, token handling |
| marketSlice.test.js | 10 | Market state management, search/quote/history thunks |

## Development

### Spring Profiles

| Profile | Database | Redis | Logging | Use Case |
|---------|----------|-------|---------|----------|
| `dev` (default) | PostgreSQL | Local | DEBUG | Development |
| `test` | H2 in-memory | Excluded | DEBUG | Testing |
| `prod` | PostgreSQL | Env vars | WARN | Production |

### Useful Commands

```bash
# Docker
docker-compose up -d              # Start all services
docker-compose up -d --build      # Rebuild and start
docker-compose logs -f backend    # Stream backend logs
docker-compose down               # Stop all services

# Backend
cd backend
./mvnw spring-boot:run                         # Run dev server
./mvnw test -Dtest=SimulationServiceTest       # Run specific test
./mvnw clean package -DskipTests               # Build JAR (skip tests)

# Frontend
cd frontend
npm start                                      # Dev server (port 3000)
npm run build                                  # Production build
npm test -- --watchAll=false --verbose         # Run tests with output
```

## Architecture Decisions

- **Fetch over Axios**: Frontend uses native `fetch` with a custom `api.js` wrapper that handles JWT refresh on 401 responses, reducing dependencies
- **Redis for refresh tokens**: Enables server-side token revocation and automatic expiration without database overhead
- **H2 for tests**: In-memory database with `create-drop` DDL eliminates test isolation issues and speeds up the test suite
- **lenient() mock stubs**: Tests use Mockito `lenient()` for shared setup stubs that aren't used by every test, preventing false positives from `UnnecessaryStubbingException`
- **WebClient for external APIs**: Non-blocking HTTP client for Yahoo Finance and BLS API calls, with Redis caching to minimize external requests

## License

MIT
