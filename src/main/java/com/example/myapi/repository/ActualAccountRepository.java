package com.example.myapi.repository;

import com.example.myapi.entity.ActualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActualAccountRepository extends JpaRepository<ActualAccount, Long> {
}
