import { createContext, useEffect, useState } from "react";
import { AuthTokens, LoginPayload, RegisterPayload } from "../types/auth";
import { UserProfile } from "../types/user";
import { authApi } from "../api/authApi";
import { usersApi } from "../api/usersApi";

interface AuthContextValue {
    profile: UserProfile | null;
    tokens: AuthTokens | null;
    isLoading: boolean;
    isAuthenticated: boolean;
    isAdmin: boolean;
    login: (payload: LoginPayload) => Promise<void>;
    register: (payload: RegisterPayload) => Promise<void>;
    logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [tokens, setTokens] = useState<AuthTokens | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isAuthenticated, setIsAuthenticated] = useState(false);


    useEffect(() => {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
            setIsLoading(false);
            return;
        }
        usersApi.getMe()
            .then(profile => {
                setProfile(profile);
                setIsAuthenticated(true);
            })
            .catch(() => {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
            })
            .finally(() => setIsLoading(false));
    }, []);

    const isAdmin = profile?.user.role === "ADMIN";

    const login = async (payload: LoginPayload) => {
        const tokens = await authApi.login(payload);
        localStorage.setItem('accessToken', tokens.accessToken);
        localStorage.setItem('refreshToken', tokens.refreshToken);
        setTokens(tokens);
        const profile = await usersApi.getMe();
        setProfile(profile);
        setIsAuthenticated(true);
    };

    const register = async (payload: RegisterPayload) => {
        await authApi.register(payload);
        await login({ identifier: payload.email, password: payload.password });
    };

    const logout = async () => {
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
            await authApi.logout(refreshToken).catch(() => { });
        }
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        setProfile(null);
        setTokens(null);
        setIsAuthenticated(false);
    };

    return (
        <AuthContext.Provider value={{ profile, tokens, isLoading, isAuthenticated, isAdmin, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

