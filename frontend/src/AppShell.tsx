import { AppBar, Avatar, Badge, Box, Container, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Toolbar, Tooltip, Typography, alpha } from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import BlockIcon from '@mui/icons-material/Block';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import ExploreIcon from '@mui/icons-material/Explore';
import ExploreOutlinedIcon from '@mui/icons-material/ExploreOutlined';
import MailIcon from '@mui/icons-material/Mail';
import MailOutlineIcon from '@mui/icons-material/MailOutline';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import SettingsIcon from '@mui/icons-material/Settings';
import AddIcon from '@mui/icons-material/Add';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import { WebSocketProvider, useWebSocketContext } from './context/WebSocketContext';
import { useConversations } from './hooks/messaging/useConversations';
import { useState } from 'react';
import { useUnreadNotifications } from './hooks/notification/useUnreadNotifications';
import { UnreadBadge } from './components/notifications/UnreadBadge';
import { NotificationDropdown } from './components/notifications/NotificationDropdown';
import { SearchBar } from './components/search/SearchBar';
import { CreatePostModal } from './components/posts/CreatePostModal';

export default function AppShell() {
  return (
    <WebSocketProvider>
      <AppShellContent />
    </WebSocketProvider>
  );
}

function AppShellContent() {
  const { profile } = useAuth();
  const { totalUnreadCount } = useWebSocketContext();
  useConversations();

  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [settingsAnchorEl, setSettingsAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [openCreateModal, setOpenCreateModal] = useState(false);
  const { unreadCountNotification } = useUnreadNotifications();
  const navigate = useNavigate();


  const unreadCount = totalUnreadCount;
    

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* ── Top Navigation Bar ── */}
      <AppBar position="sticky" elevation={0}>
        <Toolbar sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Box
            component={Link}
            to="/home"
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

          {/* Centre — Search */}
          {profile?.user && (
            <Box sx={{ flex: 1, maxWidth: 300 }}>
              <SearchBar />
            </Box>
          )}

          {/* Right Navigation */}
          {profile?.user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Tooltip title="Explore">
                <NavLink to="/explore" aria-label="Explore" style={{ textDecoration: 'none', color: 'inherit' }}>
                  {({ isActive }) => (
                    <IconButton color="inherit" component="span">
                      {isActive ? <ExploreIcon /> : <ExploreOutlinedIcon />}
                    </IconButton>
                  )}
                </NavLink>
              </Tooltip>
              <Tooltip title="Saved posts">
                <NavLink to="/saved" aria-label="Saved posts" style={{ textDecoration: 'none', color: 'inherit' }}>
                  {({ isActive }) => (
                    <IconButton color="inherit" component="span">
                      {isActive ? <BookmarkIcon /> : <BookmarkBorderIcon />}
                    </IconButton>
                  )}
                </NavLink>
              </Tooltip>
              <Tooltip title="Messages">
                <NavLink to="/messages" aria-label="Messages" style={{ textDecoration: 'none', color: 'inherit' }}>
                  {({ isActive }) => (
                    <Badge badgeContent={unreadCount} color="error" max={99}>
                      <IconButton color="inherit" component="span">
                        {isActive ? <MailIcon /> : <MailOutlineIcon />}
                      </IconButton>
                    </Badge>
                  )}
                </NavLink>
              </Tooltip>

              {/* UnreadBadge doesn't forward refs, so the span gives Tooltip something to anchor to */}
              <Tooltip title="Notifications">
                <span>
                  <UnreadBadge
                    unreadCount={unreadCountNotification}
                    onClick={(e) => {
                      setAnchorEl(e.currentTarget);
                    }}
                  />
                </span>
              </Tooltip>
              <Tooltip title="Settings">
                <IconButton
                  color="inherit"
                  aria-label="Settings"
                  onClick={(e) => setSettingsAnchorEl(e.currentTarget)}
                >
                  <SettingsIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Create post">
                <IconButton color="inherit"
                  aria-label="Create Post"
                  onClick={() => setOpenCreateModal(true)}>
                  <AddIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Profile">
                <NavLink to="/profile" aria-label="Profile" style={{ textDecoration: 'none', color: 'inherit' }}>
                  {({ isActive }) => (
                    <IconButton color="inherit" component="span" sx={{ p: 0.5 }}>
                      <Avatar
                        src={profile.user.avatarUrl ?? undefined}
                        alt={profile.user.username}
                        sx={{
                          width: 28,
                          height: 28,
                          fontSize: 14,
                          border: 2,
                          borderColor: isActive ? 'primary.main' : 'transparent',
                        }}
                      >
                        {profile.user.username[0]?.toUpperCase()}
                      </Avatar>
                    </IconButton>
                  )}
                </NavLink>
              </Tooltip>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      {/* ── Page Content ── */}
      <Container maxWidth="xl" disableGutters>
        <Outlet />
      </Container>

      <NotificationDropdown
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
      />

      <CreatePostModal
        open={openCreateModal}
        onClose={() => setOpenCreateModal(false)} />

      {/* Settings menu — only for settings-related links */}
      <Menu
        anchorEl={settingsAnchorEl}
        open={Boolean(settingsAnchorEl)}
        onClose={() => setSettingsAnchorEl(null)}
      >
        <MenuItem onClick={() => { navigate('/settings/notifications'); setSettingsAnchorEl(null); }}>
          <ListItemIcon><NotificationsNoneIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Notification Settings</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { navigate('/settings/blocked'); setSettingsAnchorEl(null); }}>
          <ListItemIcon><BlockIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Blocked Accounts</ListItemText>
        </MenuItem>
      </Menu>
    </Box>
  );
}
