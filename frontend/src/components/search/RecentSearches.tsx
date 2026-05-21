import { useSearchHistory } from "../../hooks/search/useSearchHistory";
import { Avatar, Box, Button, Divider, IconButton, List, ListItemAvatar, ListItemButton, ListItemSecondaryAction, ListItemText, Typography } from "@mui/material";
import { formatDistanceToNow } from "date-fns";
import { Fragment, useState } from "react";
import HistoryIcon from '@mui/icons-material/History';
import CloseIcon from '@mui/icons-material/Close';


interface RecentSearchesProps {
    onSelected: (query: string) => void;
}

export const RecentSearches = ({ onSelected }: RecentSearchesProps) => {
    const { history, clearHistory, isClearing } = useSearchHistory();

    const [hiddenIds, setHiddenIds] = useState<Set<string>>(new Set());
    const filterHistory = history.filter(item => !hiddenIds.has(item.id));

    const removeItem = (id: string) => {
        setHiddenIds(prev => {
            const newSet = new Set(prev);
            newSet.add(id);
            return newSet;
        });
    };

    return (
        <Box sx={{ py: 1 }}>
            {history.length > 0 && (
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2, pb: 1 }}>

                    <Typography variant="subtitle2" color="text.secondary">
                        Recent
                    </Typography>

                    <Button variant="text" size="small" color="primary"
                        onClick={
                            () =>
                                clearHistory()

                        }
                        disabled={isClearing}
                    >
                        Clear All
                    </Button>
                </Box>
            )
            }
            {filterHistory.length === 0 && (
                <Box sx={{ py: 2, textAlign: 'center' }}>
                    <Typography color="text.secondary" variant="body2">
                        No recent searches
                    </Typography>
                </Box>

            )}
            {filterHistory.length > 0 && (
                <List disablePadding>
                    {filterHistory.map((item) => (
                        <Fragment key={item.id}>
                            <ListItemButton onClick={() => onSelected(item.query)}>
                                <ListItemAvatar>
                                    <Avatar sx={{ width: 36, height: 36, bgcolor: 'action.hover' }}>
                                        <HistoryIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                                    </Avatar>
                                </ListItemAvatar>
                                <ListItemText
                                    primary={item.query}
                                    primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                                    secondary={(() => {
                                        try {
                                            return formatDistanceToNow(new Date(item.searchedAt), { addSuffix: true });
                                        } catch {
                                            return '';
                                        }
                                    })()}
                                />
                                <ListItemSecondaryAction>
                                    <IconButton edge="end" size="small" onClick={(e) => {
                                        e.stopPropagation();
                                        removeItem(item.id);
                                    }}>
                                        <CloseIcon fontSize="small" />
                                    </IconButton>
                                </ListItemSecondaryAction>
                            </ListItemButton>
                            <Divider component="li" />
                        </Fragment>
                    ))}
                </List>
            )}

        </Box >
    )
};