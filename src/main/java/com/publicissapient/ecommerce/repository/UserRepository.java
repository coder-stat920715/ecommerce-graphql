package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
