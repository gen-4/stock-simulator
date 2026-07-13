import { vi } from 'vitest';
import authReducer, {
  clearError,
  setCredentials,
} from './authSlice';

// Mock the api module
vi.mock('../../api/api', () => ({
  post: vi.fn(),
  get: vi.fn(),
}));

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
  };
})();
Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('authSlice', () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  describe('initial state', () => {
    it('returns correct initial state with no stored tokens', () => {
      localStorageMock.getItem.mockReturnValue(null);
      const state = authReducer(undefined, { type: 'unknown' });
      expect(state.isAuthenticated).toBe(false);
      expect(state.accessToken).toBeNull();
      expect(state.refreshToken).toBeNull();
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });
  });

  describe('reducers', () => {
    it('clearError clears error', () => {
      const stateWithError = {
        user: null, accessToken: null, refreshToken: null,
        isAuthenticated: false, loading: false, error: 'Some error',
      };
      const state = authReducer(stateWithError, clearError());
      expect(state.error).toBeNull();
    });

    it('setCredentials sets tokens and isAuthenticated', () => {
      const state = authReducer(undefined, setCredentials({
        accessToken: 'new-access',
        refreshToken: 'new-refresh',
      }));
      expect(state.accessToken).toBe('new-access');
      expect(state.refreshToken).toBe('new-refresh');
      expect(state.isAuthenticated).toBe(true);
    });
  });

  describe('login async thunk', () => {
    it('pending sets loading true and clears error', () => {
      const state = authReducer(undefined, { type: 'auth/login/pending' });
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('fulfilled sets isAuthenticated and tokens', () => {
      const payload = { accessToken: 'access-123', refreshToken: 'refresh-123' };
      const state = authReducer(undefined, { type: 'auth/login/fulfilled', payload });
      expect(state.isAuthenticated).toBe(true);
      expect(state.accessToken).toBe('access-123');
      expect(state.refreshToken).toBe('refresh-123');
      expect(state.loading).toBe(false);
    });

    it('rejected sets error and isAuthenticated false', () => {
      const state = authReducer(undefined, { type: 'auth/login/rejected', payload: 'Invalid credentials' });
      expect(state.isAuthenticated).toBe(false);
      expect(state.error).toBe('Invalid credentials');
      expect(state.loading).toBe(false);
    });
  });

  describe('register async thunk', () => {
    it('fulfilled sets isAuthenticated and tokens', () => {
      const payload = { accessToken: 'access-456', refreshToken: 'refresh-456' };
      const state = authReducer(undefined, { type: 'auth/register/fulfilled', payload });
      expect(state.isAuthenticated).toBe(true);
      expect(state.accessToken).toBe('access-456');
    });

    it('rejected sets error and isAuthenticated false', () => {
      const state = authReducer(undefined, { type: 'auth/register/rejected', payload: 'Registration failed' });
      expect(state.isAuthenticated).toBe(false);
      expect(state.error).toBe('Registration failed');
    });
  });

  describe('logout async thunk', () => {
    it('fulfilled clears all auth state', () => {
      const loggedInState = {
        user: { name: 'test' }, accessToken: 'token', refreshToken: 'refresh',
        isAuthenticated: true, loading: false, error: null,
      };
      const state = authReducer(loggedInState, { type: 'auth/logout/fulfilled' });
      expect(state.isAuthenticated).toBe(false);
      expect(state.accessToken).toBeNull();
      expect(state.refreshToken).toBeNull();
      expect(state.user).toBeNull();
    });
  });

  describe('refreshToken async thunk', () => {
    it('fulfilled updates tokens', () => {
      const payload = { accessToken: 'new-access', refreshToken: 'new-refresh' };
      const state = authReducer(undefined, { type: 'auth/refreshToken/fulfilled', payload });
      expect(state.accessToken).toBe('new-access');
      expect(state.refreshToken).toBe('new-refresh');
    });

    it('rejected clears auth state', () => {
      const loggedInState = {
        user: null, accessToken: 'token', refreshToken: 'refresh',
        isAuthenticated: true, loading: false, error: null,
      };
      const state = authReducer(loggedInState, { type: 'auth/refreshToken/rejected' });
      expect(state.isAuthenticated).toBe(false);
      expect(state.accessToken).toBeNull();
      expect(state.refreshToken).toBeNull();
    });
  });
});
