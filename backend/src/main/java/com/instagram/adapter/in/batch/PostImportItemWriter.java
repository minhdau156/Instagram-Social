package com.instagram.adapter.in.batch;

import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class PostImportItemWriter implements ItemWriter<Post> {

    private static final Logger log = LoggerFactory.getLogger(PostImportItemWriter.class);

    private final PostRepository postRepository;

    public PostImportItemWriter(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public void write(Chunk<? extends Post> chunk) {
        log.debug("Writing chunk of {} posts", chunk.size());
        chunk.getItems().forEach(postRepository::save);
    }
}
