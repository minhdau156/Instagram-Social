import { IconButton, CircularProgress } from '@mui/material';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import { useSavePost } from '../../hooks/save/useSavePost';
import { useAuth } from '../../hooks/useAuth';

export interface SaveButtonProps {
  postId: string;
  saved: boolean;
  disabled?: boolean;
}

export function SaveButton({ postId, saved, disabled }: SaveButtonProps) {
  const { profile } = useAuth();
  const userId = profile?.user?.id || '';
  const saveMutation = useSavePost(postId, userId);

  return (
    <IconButton
      onClick={() => !disabled && saveMutation.mutate(saved)}
      disabled={disabled || saveMutation.isPending || !profile}
      aria-label={saved ? 'Unsave post' : 'Save post'}
      size="small"
      sx={{
        transition: 'transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1)',
        '&:active': {
          transform: 'scale(0.8)',
        },
      }}
    >
      {saveMutation.isPending ? (
        <CircularProgress size={16} color="inherit" />
      ) : saved ? (
        <BookmarkIcon color="primary" />
      ) : (
        <BookmarkBorderIcon />
      )}
    </IconButton>
  );
}
