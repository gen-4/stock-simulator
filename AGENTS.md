# AGENTS.md

## Project Overview

Stock Investment Simulator - A web application that allows users to simulate stock investment returns over time with real-time data and inflation adjustment.

## Architecture

### Tech Stack
- **Backend**: Spring Boot 3.2.0, Java 17
- **Frontend**: React 18, Redux Toolkit, Recharts
- **Database**: PostgreSQL 16 (primary), Redis 7 (caching/tokens)
- **Authentication**: JWT (15min access) + Refresh tokens (7 days in Redis)
- **External APIs**: Yahoo Finance (stock data), BLS (inflation data)
- **Containerization**: Docker Compose with 4 services

### Project Structure
```
stock-simulator/
├── backend/                        # Spring Boot REST API
│   ├── src/main/java/com/stocksimulator/
│   │   ├── config/                # Security, Redis, WebClient configs
│   │   ├── auth/                  # JWT auth, login, register, controllers, DTOs
│   │   ├── user/                  # User entity and repository
│   │   ├── market/                # Yahoo Finance integration (MarketDataService)
│   │   ├── simulation/            # Investment simulation engine + inflation
│   │   └── portfolio/             # Portfolio tracking
│   ├── src/test/java/com/stocksimulator/
│   │   ├── auth/                  # JwtTokenProviderTest, AuthServiceTest
│   │   ├── market/                # MarketDataServiceTest
│   │   └── simulation/            # SimulationServiceTest, InflationServiceTest,
│   │                              # SimulationControllerIntegrationTest,
│   │                              # AuthControllerIntegrationTest
│   └── pom.xml
├── frontend/                       # React SPA
│   ├── src/
│   │   ├── api/                   # Fetch-based API helper (api.js)
│   │   ├── store/slices/          # Redux slices (auth, market, simulation, portfolio)
│   │   ├── components/
│   │   │   ├── auth/              # Login/Register forms
│   │   │   ├── charts/            # StockChart (Recharts-based)
│   │   │   ├── simulation/        # SimulationForm, SimulationResults
│   │   │   ├── dashboard/         # Dashboard view
│   │   │   ├── portfolio/         # Portfolio view
│   │   │   └── styles/            # CSS files
│   │   ├── setupTests.js          # Test setup (@testing-library/jest-dom)
│   │   └── styles/
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml             # Orchestrates all services
├── .gitignore
├── .env.example
└── AGENTS.md
```

## Development Commands

### Docker (Recommended)
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend
docker-compose logs -f frontend

# Stop all services
docker-compose down

# Rebuild after changes
docker-compose up -d --build
```

### Backend Only
```bash
cd backend
./mvnw spring-boot:run                    # Run locally (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod  # Run with prod profile
./mvnw test                               # Run tests
./mvnw clean package                      # Build JAR
```

### Frontend Only
```bash
cd frontend
npm install                     # Install dependencies
npm start                       # Start dev server (port 3000)
npm run build                   # Production build
npm test -- --watchAll=false    # Run tests (non-interactive)
```

## Spring Profiles

| Profile | Database | Redis | DDL Auto | Logging | Use Case |
|---------|----------|-------|----------|---------|----------|
| `dev` (default) | PostgreSQL | Local | update | DEBUG | Local development |
| `test` | H2 in-memory | Excluded | create-drop | DEBUG | Unit/integration tests |
| `prod` | PostgreSQL | Env vars | validate | WARN | Production |

Profile is set via `spring.profiles.active` in `application.yml` or `SPRING_PROFILES_ACTIVE` env var.

## API Endpoints

### Health (No Auth Required)
- `GET /api/health` - Health check (database + Redis connectivity)

### Authentication
- `POST /api/auth/register` - Register new user (201 Created)
- `POST /api/auth/login` - Login (returns access + refresh tokens)
- `POST /api/auth/refresh` - Refresh access token (rotates refresh token)
- `POST /api/auth/logout` - Logout (invalidate refresh token)

### Market Data
- `GET /api/market/search?q={query}` - Search stocks
- `GET /api/market/quote/{symbol}` - Get real-time quote (cached 60s)
- `GET /api/market/historical/{symbol}?startDate={date}&endDate={date}` - Historical prices (cached 1hr)

### Simulation
- `POST /api/simulation/simulate` - Run investment simulation

## Key Features

1. **JWT Authentication**: Access tokens (15min) stored in memory, refresh tokens (7 days) in Redis with rotation
2. **Real-time Stock Data**: Yahoo Finance API with Redis caching (60s TTL for quotes, 1hr for historical)
3. **Investment Simulation**: Dollar-cost averaging with multiple investment dates and weekend/holiday price resolution
4. **Inflation Adjustment**: BLS CPI data with 24-hour Redis cache, gracefully falls back to 1.0 on errors
5. **Multiple Display Modes**:
   - **Accumulated**: Total portfolio value over time
   - **Per-Investment**: Average value per investment dollar (portfolio value / investment count)
   - **Percentage**: Gain/loss as percentage with zero reference line
   - **Nominal**: Absolute dollar gain/loss
6. **Inflation Overlay**: When enabled, shows both nominal and inflation-adjusted values as separate chart lines

## Testing

### Backend Tests (~70 tests)
```bash
cd backend
./mvnw test                                              # Run all tests
./mvnw test -Dtest=JwtTokenProviderTest                  # Run JWT tests (10 tests)
./mvnw test -Dtest=AuthServiceTest                       # Run auth tests (7 tests)
./mvnw test -Dtest=SimulationServiceTest                 # Run simulation tests (13 tests)
./mvnw test -Dtest=InflationServiceTest                  # Run inflation tests (9 tests)
./mvnw test -Dtest=MarketDataServiceTest                 # Run market data tests (11 tests)
./mvnw test -Dtest=SimulationControllerIntegrationTest   # Run simulation controller tests (5 tests)
./mvnw test -Dtest=AuthControllerIntegrationTest         # Run auth controller tests (8 tests)
```

Tests use H2 in-memory database (test profile) and mock all external services (Yahoo Finance, BLS, Redis).

### Backend Test Coverage

| Test Class | Tests | What's Covered |
|------------|-------|----------------|
| `JwtTokenProviderTest` | 10 | Token generation, validation, expiration, cross-secret rejection, expired token handling |
| `AuthServiceTest` | 7 | Register (new/duplicate), login, refresh (valid/invalid/mismatch), logout |
| `SimulationServiceTest` | 13 | Single/multiple investments, display modes, inflation fallback, null validation, weekend price resolution |
| `InflationServiceTest` | 9 | Construction safety, date edge cases, cache hit/miss, invalid JSON, BLS errors, WebClient exceptions |
| `MarketDataServiceTest` | 11 | Quote/historical/search cache hit/miss, API errors, invalid JSON, missing data nodes |
| `SimulationControllerIntegrationTest` | 5 | @WebMvcTest: valid requests, service exceptions, missing fields, null dates |
| `AuthControllerIntegrationTest` | 8 | @WebMvcTest: register/login/refresh/logout, duplicate users, missing fields |

### Frontend Tests (~55 tests)
```bash
cd frontend
npm test -- --watchAll=false --verbose    # Run all tests
```

### Frontend Test Coverage

| Test File | Tests | What's Covered |
|-----------|-------|----------------|
| `StockChart.test.js` | 14 | Empty data, all 4 display modes, per_investment computation, inflation line rendering |
| `SimulationResults.test.js` | 12 | Loading/error/empty states, result cards, inflation note, StockChart prop passthrough |
| `simulationSlice.test.js` | 7 | Initial state, reducers (setDisplayMode, setInflationAdjusted, clearSimulation), thunk lifecycle |
| `authSlice.test.js` | 10 | Initial state, reducers (clearError, setCredentials), login/register/logout/refreshToken thunks |
| `marketSlice.test.js` | 10 | Initial state, reducers (clearSearchResults, clearHistoricalPrices), search/quote/history thunks |

### Testing Libraries
- **Backend**: JUnit 5, Mockito, Spring Boot Test, @WebMvcTest, H2 in-memory database
- **Frontend**: Jest (bundled with react-scripts), @testing-library/react, @testing-library/jest-dom

## Environment Variables

### Backend (application.yml)
- `SPRING_PROFILES_ACTIVE` - Active Spring profile (dev/test/prod)
- `SPRING_DATASOURCE_URL` - PostgreSQL connection URL
- `SPRING_DATA_REDIS_HOST` - Redis host
- `JWT_SECRET` - Secret key for JWT signing (must be changed in production)

### Frontend
- `REACT_APP_API_URL` - Backend API URL (default: http://localhost:8080/api)

## Important Notes

### API Rate Limits
- Yahoo Finance: No official limits, but responses are cached to be respectful
- BLS API: Cache inflation data (updates monthly)

### Security
- Never commit `.env` files (use `.env.example` as template)
- JWT secret must be changed in production
- Refresh tokens stored in Redis with automatic 7-day expiration and rotation on use
- Passwords are BCrypt-encoded

### Database
- PostgreSQL uses DDL-auto: update (dev), validate (prod)
- H2 used only for tests (in-memory, no persistence needed)
- Redis stores: refresh tokens, API cache (quotes 60s, historical 1hr, inflation 24hr)

### Logging
- All controllers log incoming requests with parameters
- Services log external API calls and cache hits/misses
- Auth events (login, register, refresh, logout) are logged at INFO level
- Debug-level logs show detailed flow through filters and services

## Troubleshooting

### Backend won't start
- Check PostgreSQL is running: `docker-compose logs postgres`
- Check Redis is running: `docker-compose logs redis`
- Verify environment variables in `docker-compose.yml`
- Check active profile: look for "The following profiles are active" in startup log

### Backend build fails
- Ensure Java 17: `java -version`
- Check Maven: `./mvnw --version`
- Review compilation errors in build output

### Health check
- `curl http://localhost:8080/api/health` should return JSON with database and redis status

### Frontend can't connect
- Ensure backend is running on port 8080
- Check CORS configuration in `SecurityConfig.java`
- Verify `REACT_APP_API_URL` matches backend
- Check browser console for fetch errors

### Simulation errors
- Check Yahoo Finance API availability
- Verify stock symbol is valid (e.g., AAPL, GOOGL)
- Ensure investment dates have price data available

### Frontend tests fail
- Run `npm install` first to install dev dependencies (@testing-library/react, @testing-library/jest-dom)
- Use `npm test -- --watchAll=false` to run non-interactively
