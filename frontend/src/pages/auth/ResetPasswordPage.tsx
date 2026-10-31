import { Container, Typography, Alert, Stack, TextField, Button, CircularProgress } from "@mui/material";
import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";

import { authApi } from "../../api/authApi";

export const ResetPasswordPage = () => {
    const { control, handleSubmit, watch, formState: { errors } } = useForm({
        defaultValues: {
            newPassword: "",
            confirmPassword: ""
        }
    });
    const [apiError, setApiError] = useState("");
    const navigate = useNavigate();
    const [param] = useSearchParams();
    const token = param.get("token");

    const newPassword = watch("newPassword");
    const [loading, setLoading] = useState(false);

    const onSubmit = async (data: any) => {
        try {
            setLoading(true);
            setApiError("");
            await authApi.confirmPasswordReset({ token: token!, newPassword: data.newPassword });
            console.log("Password reset success");
            navigate("/login");
        } catch (error: any) {
            setApiError(error.message || "Failed to reset password");
            console.log(error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Container maxWidth={"xs"}>
            <Typography variant="h4" align="center">Reset Password Page</Typography>
            {apiError && (
                <Alert severity="error" style={{ marginTop: "1rem" }}>
                    {apiError}
                </Alert>
            )}
            <Stack component={"form"} onSubmit={handleSubmit(onSubmit)} style={{ marginTop: "2rem" }} spacing={3}>
                <Controller
                    name="newPassword"
                    control={control}
                    rules={{ required: "New password is required" }}
                    render={({ field }) => (
                        <TextField
                            {...field}
                            label="New password"
                            variant="outlined"
                            type="password"
                            fullWidth
                            error={!!errors.newPassword}
                            helperText={errors.newPassword?.message}
                        />
                    )}
                />
                <Controller
                    name="confirmPassword"
                    control={control}
                    rules={{
                        required: "Confirm password is required",
                        validate: value => value === newPassword || "Passwords do not match"
                    }}
                    render={({ field }) => (
                        <TextField
                            {...field}
                            label="Confirm password"

                            variant="outlined"
                            type="password"
                            fullWidth
                            error={!!errors.confirmPassword}
                            helperText={errors.confirmPassword?.message}
                        />
                    )}
                />
                <Button
                    variant="contained"
                    fullWidth
                    type="submit"
                    disabled={loading}
                >
                    {loading ? <CircularProgress color="inherit" size={"25px"} /> : "Reset"}
                </Button>

            </Stack>



        </Container>
    );
};