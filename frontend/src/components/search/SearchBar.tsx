import { Avatar, Box, IconButton, ListItemAvatar, ListItemButton, ListItemText, Paper, Skeleton, TextField, Typography } from "@mui/material";
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import { useState } from "react";
import { useTheme } from "@mui/material/styles";
import { useSearch } from "../../hooks/search/useSearch";
import { useNavigate } from "react-router-dom";
import type { UserSearchResult } from "../../types/search";
import { RecentSearches } from "./RecentSearches";

interface SearchBarProps {
    onNavigate?: () => void;
}

export const SearchBar = ({ onNavigate }: SearchBarProps) => {
    const [query, setQuery] = useState('');
    const [focused, setFocused] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);

    const { results, isFetching } = useSearch(query, 'users');
    const userResults = results as UserSearchResult[];
    const navigate = useNavigate();
    const theme = useTheme();

    function handleNavigate(username: string) {
        navigate(`/${username}/bio`);
        setFocused(false);
        onNavigate?.();
    }

    return (
        <Box sx={{ position: 'relative' }}>
            <TextField
                value={query}
                onChange={e => {
                    setQuery(e.target.value);
                    setActiveIndex(-1);
                }}
                onFocus={() => setFocused(true)}
                onBlur={() => setTimeout(() => setFocused(false), 150)}
                onKeyDown={e => {
                    if (e.key === 'ArrowDown') {
                        e.preventDefault();
                        setActiveIndex(prev => Math.min(prev + 1, userResults.length - 1));
                    } else if (e.key === 'ArrowUp') {
                        e.preventDefault();
                        setActiveIndex(prev => Math.max(prev - 1, -1));
                    } else if (e.key === 'Enter') {
                        if (activeIndex >= 0 && userResults[activeIndex]) {
                            handleNavigate(userResults[activeIndex].username);
                        } else if (query.trim().length > 0) {
                            navigate(`/search?q=${encodeURIComponent(query)}&type=users`);
                            setFocused(false);
                            onNavigate?.();
                        }
                    } else if (e.key === 'Escape') {
                        setFocused(false);
                        setActiveIndex(-1);
                    }
                }}
                placeholder="Search"
                size="small"
                fullWidth
                InputProps={{
                    startAdornment: <SearchIcon />,
                    endAdornment: query.length > 0 && (
                        <IconButton onClick={() => setQuery('')}>
                            <CloseIcon />
                        </IconButton>
                    ),
                }}
            />

            {focused && query.trim().length === 0 && (
                <Paper
                    sx={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        right: 0,
                        zIndex: theme.zIndex.modal,
                        mt: 0.5,
                        maxHeight: 400,
                        overflow: 'auto',
                    }}
                >
                    <RecentSearches onSelected={(q) => {
                        setQuery(q);
                        setActiveIndex(-1);
                    }} />
                </Paper>
            )}

            {focused && query.trim().length > 0 && (
                <Paper
                    sx={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        right: 0,
                        zIndex: theme.zIndex.modal,
                        mt: 0.5,
                        maxHeight: 400,
                        overflow: 'auto',
                    }}
                >
                    {isFetching && [0, 1, 2].map(i => (
                        <Skeleton key={i} variant="rectangular" height={48} sx={{ borderRadius: 1, mb: 0.5 }} />
                    ))}
                    {!isFetching && query.trim().length > 0 && userResults.length === 0 && (
                        <Typography sx={{ p: 2 }}>No results</Typography>
                    )}
                    {!isFetching && userResults.map((r, index) => (
                        <ListItemButton
                            key={r.username}
                            onClick={() => handleNavigate(r.username)}
                            sx={{ bgcolor: activeIndex === index ? 'action.selected' : undefined }}
                        >
                            <ListItemAvatar>
                                <Avatar src={r.avatarUrl || undefined} alt={r.username} />
                            </ListItemAvatar>
                            <ListItemText primary={r.username} secondary={r.fullName} />
                        </ListItemButton>
                    ))}
                </Paper>
            )}
        </Box>
    );
};
