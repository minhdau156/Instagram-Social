package com.instagram.application.service;

import com.instagram.domain.model.Post;
import com.instagram.domain.port.in.ExportUserDataUseCase;
import com.instagram.domain.port.out.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Service
public class UserDataExportService implements ExportUserDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataExportService.class);

    private final PostRepository postRepository;

    public UserDataExportService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void exportPostsToCsv(Command command) {
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(command.outputStream(), StandardCharsets.UTF_8),
                true);

        writer.println("id,caption,location,status,like_count,comment_count,created_at");

        try (Stream<Post> posts = postRepository.streamByUserId(command.userId())) {
            posts.forEach(post -> writer.println(String.join(",",
                    csvEscape(post.getId().toString()),
                    csvEscape(post.getCaption()),
                    csvEscape(post.getLocation()),
                    csvEscape(post.getStatus() != null ? post.getStatus().name() : ""),
                    String.valueOf(post.getLikeCount()),
                    String.valueOf(post.getCommentCount()),
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : ""
            )));
        }

        log.info("Export completed for userId={}", command.userId());
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
