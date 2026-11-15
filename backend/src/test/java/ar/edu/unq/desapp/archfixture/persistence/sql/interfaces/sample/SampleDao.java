package ar.edu.unq.desapp.archfixture.persistence.sql.interfaces.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// Rompe B3.3 a propósito: extiende JpaRepository sin el sufijo SQLDAO.
public interface SampleDao extends JpaRepository<SampleSQL, Long> {
}
