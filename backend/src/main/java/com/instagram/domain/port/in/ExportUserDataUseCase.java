package com.instagram.domain.port.in;

import java.io.OutputStream;
import java.util.UUID;

public interface ExportUserDataUseCase {
    void exportPostsToCsv(Command command);

    record Command(UUID userId, OutputStream outputStream) {
    }
}
