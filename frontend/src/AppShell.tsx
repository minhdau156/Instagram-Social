import { AppBar, Badge, Box, Container, IconButton, Toolbar, Typography, alpha } from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import ExploreIcon from '@mui/icons-material/Explore';
import ExploreOutlinedIcon from '@mui/icons-material/ExploreOutlined';
import MailIcon from '@mui/icons-material/Mail';
import MailOutlineIcon from '@mui/icons-material/MailOutline';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import { WebSocketProvider, useWebSocketContext } from './context/WebSocketContext';
import { useConversations } from './hooks/messaging/useConversations';

export default function AppShell() {
  return (
    <WebSocketProvider>
      <AppShellContent />
    </WebSocketProvider>
  );
}

function AppShellContent() {
  const { profile } = useAuth();
  const { totalUnreadCount, isConnected } = useWebSocketContext();
  const { conversations } = useConversations();

  const unreadCount =
    isConnected && totalUnreadCount !== null
      ? totalUnreadCount
      : (conversations ?? []).reduce((sum, c) => sum + (c.unreadCount ?? 0), 0);

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* ── Top Navigation Bar ── */}
      <AppBar position="sticky" elevation={0}>
        <Toolbar sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Box
            component={Link}
            to="/posts"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              textDecoration: 'none',
              color: 'inherit',
            }}
          >
            <Box
              sx={{
                width: 32,
                height: 32,
                borderRadius: 2,
                background: 'linear-gradient(135deg, #8B5CF6 0%, #EC4899 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: (t) => `0 4px 12px ${alpha(t.palette.primary.main, 0.5)}`,
              }}
            >
              <AutoAwesomeIcon sx={{ fontSize: 18, color: '#fff' }} />
            </Box>
            <Typography
              variant="h6"
              fontWeight={700}
              sx={{
                background: 'linear-gradient(135deg, #A78BFA 0%, #F472B6 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              SocialMedia
            </Typography>
          </Box>

          {/* Right Navigation */}
          {profile?.user && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <NavLink to="/explore" aria-label="Explore" style={{ textDecoration: 'none', color: 'inherit' }}>
                {({ isActive }) => (
                  <IconButton color="inherit" component="span">
                    {isActive ? <ExploreIcon /> : <ExploreOutlinedIcon />}
                  </IconButton>
                )}
              </NavLink>
              <NavLink to="/saved" aria-label="Saved posts" style={{ textDecoration: 'none', color: 'inherit' }}>
                {({ isActive }) => (
                  <IconButton color="inherit" component="span">
                    {isActive ? <BookmarkIcon /> : <BookmarkBorderIcon />}
                  </IconButton>
                )}
              </NavLink>
              <NavLink to="/messages" aria-label="Messages" style={{ textDecoration: 'none', color: 'inherit' }}>
                {({ isActive }) => (
                  <Badge badgeContent={unreadCount} color="error" max={99}>
                    <IconButton color="inherit" component="span">
                      {isActive ? <MailIcon /> : <MailOutlineIcon />}
                    </IconButton>
                  </Badge>
                )}
              </NavLink>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      {/* ── Page Content ── */}
      <Container maxWidth="xl" disableGutters>
        <Outlet />
      </Container>
    </Box>
  );
}
