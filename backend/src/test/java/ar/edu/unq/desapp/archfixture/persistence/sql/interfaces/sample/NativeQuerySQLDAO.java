package ar.edu.unq.desapp.archfixture.persistence.sql.interfaces.sample;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// Rompe B3.5 a propósito: declara una query nativa con @Query y otra con @NativeQuery.
public interface NativeQuerySQLDAO extends JpaRepository<SampleSQL, Long> {

    @Query(value = "SELECT * FROM samples", nativeQuery = true)
    List<SampleSQL> findAllNative();

    @NativeQuery("SELECT * FROM samples")
    List<SampleSQL> findAllWithNativeQuery();
}
