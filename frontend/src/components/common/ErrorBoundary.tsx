import * as Sentry from '@sentry/react';
import { Box, Button, Typography } from "@mui/material";
import { Component, ErrorInfo, ReactNode } from "react";

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
    state: State = {
        hasError: false,
        error: null
    };

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error("ErrorBoundary caught an error:", error, info);
        Sentry.captureException(error, {
            extra: { componentStack: info.componentStack },
        });
    }

    render() {
        if (this.state.hasError) {
            return this.props.fallback || (
                <Box
                    display="flex"
                    flexDirection="column"
                    alignItems="center"
                    justifyContent="center"
                    minHeight={200}
                    gap={2}
                >
                    <Typography variant="h6">Something went wrong</Typography>
                    <Box display="flex" gap={2}>
                        <Button 
                            variant="outlined" 
                            onClick={() => this.setState({ hasError: false, error: null })}
                        >
                            Try Again
                        </Button>
                        <Button 
                            variant="contained" 
                            onClick={() => window.location.reload()}
                        >
                            Reload Page
                        </Button>
                    </Box>
                </Box>
            );
        }
        return this.props.children;
    }
}