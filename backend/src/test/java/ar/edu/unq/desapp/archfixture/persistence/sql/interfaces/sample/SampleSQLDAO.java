package ar.edu.unq.desapp.archfixture.persistence.sql.interfaces.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// De apoyo: un SQLDAO en persistence/sql/interfaces que extiende JpaRepository.
public interface SampleSQLDAO extends JpaRepository<SampleSQL, Long> {
}
