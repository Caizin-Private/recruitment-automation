package com.example.repository;

import com.example.model.Candidate;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

@Repository
public class CandidateDynamoRepository {

    private final DynamoDbTable<Candidate> table;

    public CandidateDynamoRepository(DynamoDbEnhancedClient client) {
        this.table = client.table(
                "Candidates",
                TableSchema.fromBean(Candidate.class)
        );
    }

    public void save(Candidate candidate) {
        table.putItem(candidate);
    }

    public Optional<Candidate> findByCandidateId(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            return Optional.empty();
        }
        Candidate item = table.getItem(GetItemEnhancedRequest.builder()
                .key(Key.builder().partitionValue(candidateId).build())
                .build());
        return Optional.ofNullable(item);
    }

    public boolean existsByEmail(String email) {

        SdkIterable<Page<Candidate>> pages =
                table.index("email-index")
                        .query(r -> r.queryConditional(
                                QueryConditional.keyEqualTo(
                                        k -> k.partitionValue(email)
                                )
                        ));

        for (Page<Candidate> page : pages) {
            if (!page.items().isEmpty()) {
                return true;
            }
        }

        return false;
    }

}
