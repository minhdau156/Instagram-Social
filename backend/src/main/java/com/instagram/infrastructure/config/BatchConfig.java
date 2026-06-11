package com.instagram.infrastructure.config;

import com.instagram.adapter.in.batch.PostImportItemProcessor;
import com.instagram.adapter.in.batch.PostImportItemWriter;
import com.instagram.adapter.in.batch.dto.PostImportRow;
import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.PostRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

@Configuration
public class BatchConfig {

    private final PostRepository postRepository;

    public BatchConfig(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Bean
    public Job importPostsJob(JobRepository jobRepository, Step importPostsStep) {
        return new JobBuilder("importPostsJob", jobRepository)
                .start(importPostsStep)
                .build();
    }

    @Bean
    public Step importPostsStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                FlatFileItemReader<PostImportRow> postImportCsvReader,
                                PostImportItemProcessor postImportItemProcessor) {
        return new StepBuilder("importPostsStep", jobRepository)
                .<PostImportRow, Post>chunk(50, transactionManager)
                .reader(postImportCsvReader)
                .processor(postImportItemProcessor)
                .writer(new PostImportItemWriter(postRepository))
                .faultTolerant()
                .skipLimit(100)
                .skip(IllegalArgumentException.class)
                .build();
    }

    // @StepScope: a new instance is created per step execution; jobParameters are late-bound.
    @Bean
    @StepScope
    public FlatFileItemReader<PostImportRow> postImportCsvReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<PostImportRow>()
                .name("postImportCsvReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .names("caption", "location", "mediaUrl", "createdAt")
                .targetType(PostImportRow.class)
                .build();
    }

    @Bean
    @StepScope
    public PostImportItemProcessor postImportItemProcessor(
            @Value("#{jobParameters['userId']}") String userId) {
        return new PostImportItemProcessor(UUID.fromString(userId));
    }
}
