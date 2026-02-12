package com.example.repository;

import com.example.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    boolean existsByEmail(String email);
}