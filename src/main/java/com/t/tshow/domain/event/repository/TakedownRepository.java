package com.t.tshow.domain.event.repository;

import com.t.tshow.domain.event.entity.Takedown;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TakedownRepository extends JpaRepository<Takedown, Long> {
}
