import { useState } from "react";
import { useCreatePost } from "../../hooks/post/useCreatePost";
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Step, StepLabel, Stepper, TextField, Typography } from "@mui/material";
import { MediaPicker } from "./MediaPicker";
import { MediaCropEditor } from "./MediaCropEditor";
import { CaptionEditor } from "./CaptionEditor";

const steps = ['Select Media', 'Edit', 'Caption & Location'];

export const CreatePostModal: React.FC<{ open: boolean; onClose: () => void }> =
    ({ open, onClose }) => {
        const [activeStep, setActiveStep] = useState(0);
        const [files, setFiles] = useState<File[]>([]);
        const [caption, setCaption] = useState('');
        const [location, setLocation] = useState('');
        const createPost = useCreatePost();

        const canEditSingleImage = files.length === 1 && files[0]?.type.startsWith('image/');

        const onSaveCrop = (blob: Blob) => {
            const file = new File([blob], files[0].name, { type: files[0].type });
            setFiles(prev => [file, ...prev.slice(1)]);
            setActiveStep(2);
        };

        const handleSubmit = async () => {
            if (files.length === 0) return;
            await createPost.mutateAsync({
                files, payload: { caption, location }
            });
            alert('Post created successfully');
            onClose();
        };
        return (
            <Dialog
                open={open}
                onClose={onClose}
                maxWidth="md"
                fullWidth
            >
                <DialogTitle>Create new post</DialogTitle>
                <Stepper activeStep={activeStep} sx={{ px: 3, pt: 1 }}>
                    {steps.map((label) => (
                        <Step key={label}>
                            <StepLabel>{label}</StepLabel>
                        </Step>
                    ))}
                </Stepper>
                <DialogContent>

                    {activeStep == 0 && <MediaPicker onFilesSelected={setFiles} />}
                    {activeStep == 1 && canEditSingleImage && <MediaCropEditor imageSrc={URL.createObjectURL(files[0])} onCancel={onClose} onSave={onSaveCrop} />}
                    {activeStep == 2 && (
                        <Stack>
                            <CaptionEditor value={caption} onChange={setCaption} />
                            <TextField label="Location" value={location} onChange={e => setLocation(e.target.value)} />
                        </Stack>
                    )}

                </DialogContent>
                <DialogActions>
                    <DialogActions>
                        {activeStep > 0 && <Button onClick={() => setActiveStep(s => (s === 2 && !canEditSingleImage) ? 0 : s - 1)}>Back</Button>}
                        {files.length === 0 && activeStep > 0 ? <Typography color='error'>You should select a media file first</Typography>
                            : activeStep < steps.length - 1 ?
                                <Button variant="contained" onClick={() => setActiveStep(s => s === 0 ? (canEditSingleImage ? 1 : 2) : s + 1)}>Next</Button>
                                : <Button variant="contained" onClick={handleSubmit} loading={createPost.isPending}>Share</Button>}
                    </DialogActions>
                </DialogActions>
            </Dialog>
        );
    };