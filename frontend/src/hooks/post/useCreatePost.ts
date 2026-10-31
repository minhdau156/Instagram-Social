import { useMutation, useQueryClient } from "@tanstack/react-query";
import { MediaType } from "../../types/post";
import { mediaApi } from "../../api/mediaApi";
import { postApi } from "../../api/postApi";


export const useCreatePost = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (data: {
            payload: { location?: string; caption?: string };
            files: File[]
        }) => {
            const mediaItems = await Promise.all(data.files.map(async (file, index) => {
                //get the pre-signed url from the backend
                const { presignedUrl, mediaKey } = await mediaApi.getUploadUrl(file.name, file.type);
                //upload the file to minio
                await mediaApi.uploadToMinio(presignedUrl, file);
                return {
                    mediaKey,
                    mediaType: file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE' as MediaType,
                    sortOrder: String(index)
                };
            }));
            //create the post
            return postApi.createPost({
                ...data.payload,
                mediaItems
            });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['posts'] });
        }
    })
}