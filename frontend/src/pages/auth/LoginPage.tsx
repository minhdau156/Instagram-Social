import { useState } from "react";
import { Alert, Button, CircularProgress, Container, Stack, TextField, Typography } from "@mui/material";
import { useForm, Controller } from "react-hook-form";
import { useAuth } from "../../hooks/useAuth";
import { Navigate, useNavigate } from "react-router-dom";

export const LoginPage = () => {
    const { control, handleSubmit, formState: { errors } } = useForm({
        defaultValues: {
            emailOrUsername: "",
            password: ""
        }
    });
    const [apiError, setApiError] = useState("");
    const navigate = useNavigate();
    const { isLoading, login, isAuthenticated } = useAuth();

    const onSubmit = async (data: any) => {
        try {
            setApiError("");
            await login({ identifier: data.emailOrUsername, password: data.password });
            console.log("Login success");
            navigate("/");
        } catch (error: any) {
            setApiError(error.message || "Failed to login");
            console.log(error);
        }
    };

    if (isAuthenticated) {
        return <Navigate to="/" replace />;
    }

    return (
        <Container maxWidth={"xs"}>
            <Typography variant="h4" align="center">Login Page</Typography>
            {apiError && (
                <Alert severity="error" style={{ marginTop: "1rem" }}>
                    {apiError}
                </Alert>
            )}
            <Stack component={"form"} onSubmit={handleSubmit(onSubmit)} style={{ marginTop: "2rem" }} spacing={3}>
                <Controller
                    name="emailOrUsername"
                    control={control}
                    rules={{ required: "Email or username is required" }}
                    render={({ field }) => (
                        <TextField
                            {...field}
                            label="Email or username"
                            variant="outlined"
                            fullWidth
                            error={!!errors.emailOrUsername}
                            helperText={errors.emailOrUsername?.message}
                        />
                    )}
                />
                <Controller
                    name="password"
                    control={control}
                    rules={{ required: "Password is required" }}
                    render={({ field }) => (
                        <TextField
                            {...field}
                            label="Password"
                            variant="outlined"
                            type="password"
                            fullWidth
                            error={!!errors.password}
                            helperText={errors.password?.message}
                        />
                    )}
                />
                <Button
                    variant="contained"
                    fullWidth
                    type="submit"
                    disabled={isLoading}
                >
                    {isLoading ? <CircularProgress color="inherit" size={"25px"} /> : "Login"}
                </Button>

            </Stack>
            <Stack justifyContent={"center"} alignItems={"center"} style={{ marginTop: "2rem" }}>
                <div>
                    <Button
                        href={`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/oauth2/authorization/google`}
                        variant="outlined"
                        size="small"
                        startIcon={
                            <img
                                src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcThrlaPTiDvfC24xZP2MdareGEoNxolGAKWEw&s"
                                alt="Google"
                                style={{ width: 20, height: 20, borderRadius: "10px" }}
                            />
                        }
                    >
                        Continue with Google
                    </Button>
                </div>
                <Stack direction={"row"} style={{ marginTop: "1rem" }}>
                    <Typography variant="body2" align="center">Don't have an account? <a href="/register">Register</a></Typography>
                    <Typography variant="body2" align="center">Forgot password? <a href="/forgot-password">Reset Password</a></Typography>
                </Stack>
            </Stack>


        </Container>
    )
}
