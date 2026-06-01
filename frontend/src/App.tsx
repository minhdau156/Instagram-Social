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
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AdminRoute } from './components/common/AdminRoute';
import { SuperAdminRoute } from './components/common/SuperAdminRoute';
import { OAuth2CallbackPage } from './pages/auth/OAuth2CallbackPage';
import React from 'react';


const ProfilePage = React.lazy(() => import('./pages/users/ProfllePage').then(m => ({ default: m.ProfilePage })));
const PostPage = React.lazy(() => import('./pages/posts/PostPage').then(m => ({ default: m.PostPage })));
const CreatePostModalPage = React.lazy(() => import('./pages/posts/CreatePostModalPage').then(m => ({ default: m.CreatePostModalPage })));
const PublicProfilePage = React.lazy(() => import('./pages/users/PublicProfilePage').then(m => ({ default: m.PublicProfilePage })));
const FollowRequestsPage = React.lazy(() => import('./pages/follow/FollowRequestsPage'));
const SavedPostsPage = React.lazy(() => import('./pages/profile/SavedPostsPage'));
const HomePage = React.lazy(() => import('./pages/feed/HomePage'));
const ExplorePage = React.lazy(() => import('./pages/explore/ExplorePage'));
const InboxPage = React.lazy(() => import('./pages/messaging/InboxPage'));
const ChatPage = React.lazy(() => import('./pages/messaging/ChatPage'));
const NotificationsPage = React.lazy(() => import('./pages/notifications/NotificationsPage'));
const NotificationSettingsPage = React.lazy(() => import('./pages/notifications/NotificationSettingsPage'));
const BlockedAccountsPage = React.lazy(() => import('./pages/settings/BlockedAccountsPage'));
const AdminDashboardPage = React.lazy(() => import('./pages/admin/AdminDashboardPage'));
const AdminReportsPage = React.lazy(() => import('./pages/admin/AdminReportsPage'));
const AdminUsersPage = React.lazy(() => import('./pages/admin/AdminUsersPage'));
const RoleManagementPage = React.lazy(() => import('./pages/admin/RoleManagementPage'));
const SearchPage = React.lazy(() => import('./pages/search/SearchPage'));
const HashtagPage = React.lazy(() => import('./pages/search/HashtagPage'));

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
                <Route path="/settings/blocked" element={<ErrorBoundary><BlockedAccountsPage /></ErrorBoundary>} />
                {/* AdminRoute handles both unauthenticated (→ /login) and non-admin (→ /) redirects */}
                <Route path="/admin" element={<AdminRoute><ErrorBoundary><AdminDashboardPage /></ErrorBoundary></AdminRoute>} />
                <Route path="/admin/reports" element={<AdminRoute><ErrorBoundary><AdminReportsPage /></ErrorBoundary></AdminRoute>} />
                <Route path="/admin/users" element={<AdminRoute><ErrorBoundary><AdminUsersPage /></ErrorBoundary></AdminRoute>} />
                <Route path="/admin/roles" element={<SuperAdminRoute><ErrorBoundary><RoleManagementPage /></ErrorBoundary></SuperAdminRoute>} />
                <Route path="/search" element={<ErrorBoundary><SearchPage /></ErrorBoundary>} />
                <Route path="/hashtag/:name" element={<ErrorBoundary><HashtagPage /></ErrorBoundary>} />
              </Route>
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
    </ThemeProvider>
  );
}
