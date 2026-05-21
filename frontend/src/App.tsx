import { CssBaseline, ThemeProvider } from '@mui/material';
import { BrowserRouter, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { Suspense, useEffect } from 'react';
import theme from './theme';
import AppShell from './AppShell';

import { navigationRef } from './lib/navigationRef';
import { LoginPage } from './pages/auth/LoginPage';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import { PageLoader } from './components/common/PageLoader';
import { RegisterPage } from './pages/auth/RegisterPage';
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage';
import { ProfilePage } from './pages/users/ProfllePage';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { OAuth2CallbackPage } from './pages/auth/OAuth2CallbackPage';

import { PostPage } from './pages/posts/PostPage';
import { CreatePostModalPage } from './pages/posts/CreatePostModalPage';
import { PublicProfilePage } from './pages/users/PublicProfilePage';
import FollowRequestsPage from './pages/follow/FollowRequestsPage';
import SavedPostsPage from './pages/profile/SavedPostsPage';
import React from 'react';


const HomePage = React.lazy(() => import('./pages/feed/HomePage'));
const ExplorePage = React.lazy(() => import('./pages/explore/ExplorePage'));
const InboxPage = React.lazy(() => import('./pages/messaging/InboxPage'));
const ChatPage = React.lazy(() => import('./pages/messaging/ChatPage'));
const NotificationsPage = React.lazy(() => import('./pages/notifications/NotificationsPage'));
const NotificationSettingsPage = React.lazy(() => import('./pages/notifications/NotificationSettingsPage'));

function ChatRoute() {
  const { conversationId } = useParams<{ conversationId: string }>();
  return conversationId ? <ChatPage conversationId={conversationId} /> : null;
}

function GlobalNavigation() {
  const navigate = useNavigate();
  useEffect(() => {
    navigationRef.navigate = navigate;
  }, [navigate]);
  return null;
}

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
          <GlobalNavigation />
          <Routes>
            <Route element={<AppShell />}>
              <Route index element={<Navigate to="/" replace />} />
              <Route path="/login" element={<ErrorBoundary><LoginPage /></ErrorBoundary>} />
              <Route path="/register" element={<ErrorBoundary><RegisterPage /></ErrorBoundary>} />
              <Route path="/forgot-password" element={<ErrorBoundary><ForgotPasswordPage /></ErrorBoundary>} />
              {/* <Route path="/reset-password" element={<ErrorBoundary><ResetPasswordPage /></ErrorBoundary>} /> */}
              <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />
              <Route path="/posts/:postId" element={<PostPage />} />
              <Route path="/p/:postId" element={<PostPage />} />
              <Route path="/create-post" element={<CreatePostModalPage />} />


              <Route element={<ProtectedRoute />}>
                <Route path="/profile" element={<ErrorBoundary><ProfilePage /></ErrorBoundary>} />
                <Route path="/saved" element={<ErrorBoundary><SavedPostsPage /></ErrorBoundary>} />
                <Route path="/follow-requests" element={<ErrorBoundary><FollowRequestsPage /></ErrorBoundary>} />
                <Route path="/:username/bio" element={<ErrorBoundary><PublicProfilePage /></ErrorBoundary>} />
                <Route path="/home" element={<HomePage />} />
                <Route path="/explore" element={<ExplorePage />} />
                <Route path="/messages" element={<ErrorBoundary><InboxPage /></ErrorBoundary>} />
                <Route path="/messages/:conversationId" element={<ErrorBoundary><ChatRoute /></ErrorBoundary>} />
                <Route path="/notifications" element={<ErrorBoundary><NotificationsPage /></ErrorBoundary>} />
                <Route path="/settings/notifications" element={<ErrorBoundary><NotificationSettingsPage /></ErrorBoundary>} />
              </Route>
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
    </ThemeProvider>
  );
}
