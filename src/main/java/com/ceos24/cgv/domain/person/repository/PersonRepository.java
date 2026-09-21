package com.ceos24.cgv.domain.person.repository;

import com.ceos24.cgv.domain.person.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
