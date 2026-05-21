import { useNavigate, useSearchParams } from "react-router-dom";
import type { HashtagSearchResult, PostSearchResult, SearchType, UserSearchResult } from "../../types/search";
import { useEffect, useState } from "react";
import { useSearch } from "../../hooks/search/useSearch";
import {
    Avatar,
    Box,
    Button,
    Divider,
    List,
    ListItemAvatar,
    ListItemButton,
    ListItemText,
    Skeleton,
    Tab,
    Tabs,
    Typography,
} from "@mui/material";
import { RecentSearches } from "../../components/search/RecentSearches";
import TagIcon from '@mui/icons-material/Tag';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';

const PAGE_SIZE = 20;

export default function SearchPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [page, setPage] = useState(0);
    const navigate = useNavigate();

    const q = searchParams.get('q') ?? '';
    const activeTab = (searchParams.get('type') ?? 'users') as SearchType;

    useEffect(() => {
        setPage(0);
    }, [activeTab, q]);

    const handleTabChange = (_: React.SyntheticEvent, newType: SearchType) => {
        setSearchParams({ q, type: newType });
    };

    const { results, isLoading } = useSearch(q, activeTab, page, PAGE_SIZE);

    return (
        <Box sx={{ maxWidth: 700, mx: 'auto', py: 2, px: { xs: 1, sm: 2 } }}>
            <Typography variant="h5" fontWeight={600} mb={2}>
                {q ? `Search results for "${q}"` : 'Search'}
            </Typography>

            <Tabs
                value={activeTab}
                onChange={handleTabChange}
                indicatorColor="primary"
                textColor="primary"
            >
                <Tab label="People" value="users" />
                <Tab label="Hashtags" value="hashtags" />
                <Tab label="Posts" value="posts" />
            </Tabs>
            <Divider />

            {q === '' && (
                <RecentSearches onSelected={(query) => setSearchParams({ q: query, type: activeTab })} />
            )}

            {isLoading && activeTab === 'users' && (
                Array.from({ length: 5 }).map((_, i) => (
                    <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1 }}>
                        <Skeleton variant="circular" width={40} height={40} />
                        <Skeleton variant="text" width={120} />
                    </Box>
                ))
            )}

            {isLoading && activeTab === 'hashtags' && (
                Array.from({ length: 5 }).map((_, i) => (
                    <Skeleton key={i} variant="rectangular" height={40} sx={{ my: 0.5 }} />
                ))
            )}

            {isLoading && activeTab === 'posts' && (
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 0.5, mt: 1 }}>
                    {Array.from({ length: 9 }).map((_, i) => (
                        <Skeleton key={i} variant="rectangular" sx={{ paddingBottom: '100%' }} />
                    ))}
                </Box>
            )}

            {!isLoading && q !== '' && results.length === 0 && (
                <Box sx={{ py: 8, textAlign: 'center' }}>
                    <Typography color="text.secondary">No results found for "{q}"</Typography>
                </Box>
            )}

            {!isLoading && activeTab === 'users' && results.length > 0 && (
                <List>
                    {(results as UserSearchResult[]).map((user) => (
                        <ListItemButton key={user.id} onClick={() => navigate(`/${user.username}/bio`)}>
                            <ListItemAvatar>
                                <Avatar src={user.avatarUrl ?? undefined}>
                                    {user.username[0].toUpperCase()}
                                </Avatar>
                            </ListItemAvatar>
                            <ListItemText primary={user.username} secondary={user.fullName} />
                            <Button
                                variant="outlined"
                                size="small"
                                onClick={(e) => { e.stopPropagation(); navigate(`/${user.username}/bio`); }}
                            >
                                Follow
                            </Button>
                        </ListItemButton>
                    ))}
                </List>
            )}

            {!isLoading && activeTab === 'hashtags' && results.length > 0 && (
                <List>
                    {(results as HashtagSearchResult[]).map((hashtag) => (
                        <ListItemButton key={hashtag.id} onClick={() => navigate(`/hashtag/${hashtag.name}`)}>
                            <ListItemAvatar>
                                <Avatar sx={{ bgcolor: 'primary.main' }}>
                                    <TagIcon />
                                </Avatar>
                            </ListItemAvatar>
                            <ListItemText
                                primary={'#' + hashtag.name}
                                secondary={hashtag.postCount.toLocaleString() + ' posts'}
                            />
                        </ListItemButton>
                    ))}
                </List>
            )}

            {!isLoading && activeTab === 'posts' && results.length > 0 && (
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 0.5, mt: 1 }}>
                    {(results as PostSearchResult[]).map((post) => (
                        <Box
                            key={post.id}
                            sx={{ position: 'relative', paddingBottom: '100%', overflow: 'hidden', cursor: 'pointer' }}
                            onClick={() => navigate(`/posts/${post.id}`)}
                        >
                            <Box
                                component="img"
                                src={post.mediaUrl}
                                alt={post.caption ?? ''}
                                sx={{ position: 'absolute', width: '100%', height: '100%', objectFit: 'cover' }}
                            />
                            {post.mediaType === 'VIDEO' && (
                                <PlayArrowIcon
                                    sx={{ position: 'absolute', top: 4, right: 4, color: 'white', fontSize: 20 }}
                                />
                            )}
                        </Box>
                    ))}
                </Box>
            )}

            {!isLoading && results.length === PAGE_SIZE && (
                <Box sx={{ textAlign: 'center', mt: 2 }}>
                    <Button variant="outlined" onClick={() => setPage((p) => p + 1)}>
                        Load more
                    </Button>
                </Box>
            )}
        </Box>
    );
}
