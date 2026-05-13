import { Avatar, Box, Typography } from '@mui/material';
import { keyframes } from '@mui/system';

const bounceDots = keyframes`
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40%           { opacity: 1;   transform: scale(1);   }
`;

interface TypingIndicatorProps {
    typingUsernames: string[];
}

export function TypingIndicator({ typingUsernames }: TypingIndicatorProps) {
    if (typingUsernames.length === 0) return null;

    const label = typingUsernames.length === 1
        ? `${typingUsernames[0]} is typing…`
        : `${typingUsernames.length} people are typing…`;

    return (
        <Box display="flex" alignItems="center" gap={1} px={2} py={0.5}>
            <Avatar sx={{ width: 24, height: 24, fontSize: 12 }}>
                {typingUsernames[0][0].toUpperCase()}
            </Avatar>
            {[0, 0.2, 0.4].map((delay) => (
                <Box
                    key={delay}
                    sx={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        bgcolor: 'text.disabled',
                        animation: `${bounceDots} 1s infinite`,
                        animationDelay: `${delay}s`,
                    }}
                />
            ))}
            <Typography variant="caption" color="text.secondary">
                {label}
            </Typography>
        </Box>
    );
}
