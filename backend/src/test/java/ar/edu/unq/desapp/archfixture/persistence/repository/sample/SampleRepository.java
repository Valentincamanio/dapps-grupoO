package ar.edu.unq.desapp.archfixture.persistence.repository.sample;

import java.util.Optional;

import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.persistence.mapper.sample.SampleMapper;
import ar.edu.unq.desapp.archfixture.persistence.sql.interfaces.sample.SampleSQLDAO;

// De apoyo: el único punto donde conviven el SQLDAO, el mapper y el modelo.
public class SampleRepository {

    private final SampleSQLDAO sampleSQLDAO;
    private final SampleMapper sampleMapper;

    public SampleRepository(SampleSQLDAO sampleSQLDAO, SampleMapper sampleMapper) {
        this.sampleSQLDAO = sampleSQLDAO;
        this.sampleMapper = sampleMapper;
    }

    public Optional<SampleItem> findById(Long id) {
        return sampleSQLDAO.findById(id).map(sampleMapper::toModel);
    }
}
