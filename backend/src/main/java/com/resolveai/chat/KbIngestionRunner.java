package com.resolveai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.kb.ingest-enabled", havingValue = "true", matchIfMissing = true)
public class KbIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KbIngestionRunner.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public KbIngestionRunner(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:kb/*.md");
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
            List<Document> documents = new ArrayList<>();
            StringBuilder hashInput = new StringBuilder();
            TokenTextSplitter splitter = new TokenTextSplitter();
            for (Resource resource : resources) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                hashInput.append(resource.getFilename()).append(content);
                Document doc = new Document(content, Map.of("source", resource.getFilename()));
                documents.addAll(splitter.apply(List.of(doc)));
            }
            String hash = sha256(hashInput.toString());
            String existing = jdbcTemplate.query(
                    "SELECT doc_set_hash FROM kb_ingestion ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null);
            if (hash.equals(existing)) {
                log.info("Knowledge base unchanged ({} chunks indexed previously); skipping ingestion", documents.size());
                return;
            }
            jdbcTemplate.update("DELETE FROM vector_store");
            vectorStore.add(documents);
            jdbcTemplate.update("INSERT INTO kb_ingestion (doc_set_hash) VALUES (?)", hash);
            log.info("Ingested {} chunks from {} knowledge base documents into pgvector",
                    documents.size(), resources.length);
        } catch (Exception e) {
            log.error("Knowledge base ingestion failed (chat will work without grounded context until fixed): {}",
                    e.getMessage());
        }
    }

    private String sha256(String input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}