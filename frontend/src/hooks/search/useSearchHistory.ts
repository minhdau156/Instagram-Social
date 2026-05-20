import { searchApi } from "../../api/searchApi";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export const useSearchHistory = () => {
    const queryClient = useQueryClient();

    const { data, isLoading, isError } = useQuery({
        queryKey: ['search-history'],
        queryFn: () => searchApi.getSearchHistory(),
        staleTime: 60_000,
    })

    const { mutateAsync: clearHistory, isPending: isClearing } = useMutation({
        mutationFn: () => searchApi.clearSearchHistory(),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['search-history'] })
        }
    })

    const history = data ?? [];
    return { history, isLoading, isError, clearHistory, isClearing }
}