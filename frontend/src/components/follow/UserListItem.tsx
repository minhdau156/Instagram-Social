import { Avatar, ListItem, ListItemAvatar, ListItemText, Link } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { UserSummary } from "../../types/follow";
import { FollowButton } from "./FollowButton";



interface UserListItemProps {
    user: UserSummary;
    currentUsername?: string;
    setOpen: (open: boolean) => void;
}

export const UserListItem = ({ user, currentUsername, setOpen }: UserListItemProps) => {


    return (
        <ListItem >
            <ListItemAvatar>
                <Link component={RouterLink} to={`/${user.username}/bio`} onClick={() => setOpen(false)} underline="none">
                    <Avatar src={user.profilePictureUrl || undefined} alt={user.username} />
                </Link>
            </ListItemAvatar>
            <ListItemText
                primary={
                    <Link component={RouterLink} to={`/${user.username}`} onClick={() => setOpen(false)} underline="none" color="inherit" fontWeight="bold">
                        {user.username}
                    </Link>
                }
                secondary={user.fullName}
            />
            {
                currentUsername !== user.username && <FollowButton username={user.username} status={user.followStatus} />
            }
        </ListItem>
    )
}