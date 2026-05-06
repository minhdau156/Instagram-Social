import React, { useState } from 'react';
import { IconButton, Menu, MenuItem, Snackbar, Alert } from '@mui/material';
import IosShareIcon from '@mui/icons-material/IosShare';
import LinkIcon from '@mui/icons-material/Link';
import SendIcon from '@mui/icons-material/Send';
import { sharePost } from '../../api/sharesApi';

interface ShareMenuProps {
  postId: string;
  disabled?: boolean;
}

export const ShareMenu: React.FC<ShareMenuProps> = ({ postId, disabled }) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success',
  });

  const handleOpen = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleCopyLink = async () => {
    const url = `${window.location.origin}/p/${postId}`;
    try {
      await navigator.clipboard.writeText(url);
      setSnackbar({
        open: true,
        message: 'Link copied to clipboard!',
        severity: 'success',
      });
    } catch (err) {
      setSnackbar({
        open: true,
        message: 'Failed to copy link',
        severity: 'error',
      });
    }
    handleClose();
  };

  const handleShare = async () => {
    try {
      await sharePost(postId, { shareType: 'DM' });
      setSnackbar({
        open: true,
        message: 'Post shared! (DM delivery coming soon)',
        severity: 'success',
      });
    } catch (error) {
      setSnackbar({
        open: true,
        message: 'Failed to share post',
        severity: 'error',
      });
    }
    handleClose();
  };

  const handleCloseSnackbar = (event?: React.SyntheticEvent | Event, reason?: string) => {
    if (reason === 'clickaway') {
      return;
    }
    setSnackbar((prev) => ({ ...prev, open: false }));
  };

  return (
    <>
      <IconButton aria-label="Share post" onClick={handleOpen}>
        <IosShareIcon />
      </IconButton>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
        <MenuItem onClick={handleCopyLink}>
          <LinkIcon sx={{ mr: 1 }} />
          Copy Link
        </MenuItem>
        <MenuItem onClick={handleShare} disabled={disabled}>
          <SendIcon sx={{ mr: 1 }} />
          Send as Message
        </MenuItem>
      </Menu>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={2000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={handleCloseSnackbar} severity={snackbar.severity} sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};
