package com.example.repository;

import com.example.model.Candidate;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

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
