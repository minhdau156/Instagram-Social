import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { notificationsApi } from "../../api/notificationsApi"
import { NotificationSettings } from "../../types/notification"

export const useNotificationSettings = () => {
    const queryClient = useQueryClient();
    const { data, isLoading, isError } = useQuery({
        queryKey: ['notification-settings'],
        queryFn: async () => {
            return notificationsApi.getSettings()
        },
    })

    const updateSettings = useMutation({
        mutationFn: (settings: NotificationSettings) => notificationsApi.updateSettings(settings),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['notification-settings'] });
        },
    })

    return {
        settings: data,
        isLoading,
        isError,
        updateSettings,
        isUpdating: updateSettings.isPending
    }
}

