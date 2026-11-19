import { createContext, useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { setAccessToken, setUnauthorizedHandler } from '../api/axiosClient';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initializing, setInitializing] = useState(true);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  useEffect(() => {
    let active = true;
    async function restoreSession() {
      try {
        const data = await authApi.refresh();
        if (active && data?.accessToken && data?.user) {
          setAccessToken(data.accessToken);
          setUser(data.user);
        }
      } catch {
        if (active) {
          clearSession();
        }
      } finally {
        if (active) {
          setInitializing(false);
        }
      }
    }
    restoreSession();
    return () => {
      active = false;
    };
  }, [clearSession]);

  const register = useCallback(async (payload) => {
    const data = await authApi.register(payload);
    setAccessToken(data.accessToken);
    setUser(data.user);
    return data.user;
  }, []);

  const login = useCallback(async (payload) => {
    const data = await authApi.login(payload);
    setAccessToken(data.accessToken);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Clear local session state even if network request fails
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const updateUserState = useCallback((updatedUser) => {
    setUser(updatedUser);
  }, []);

  const value = {
    user,
    initializing,
    isAuthenticated: Boolean(user),
    register,
    login,
    logout,
    updateUserState,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
